package com.xiaoyu.app.data.repo

import android.content.Context
import android.util.Log
import com.xiaoyu.app.data.R
import com.xiaoyu.app.data.brain.BrainGateway
import com.xiaoyu.app.data.db.MessageEntity
import com.xiaoyu.app.data.db.MessageFtsEntity
import com.xiaoyu.app.data.db.RequestSnapshotDao
import com.xiaoyu.app.data.db.RequestSnapshotEntity
import com.xiaoyu.app.data.db.SessionDao
import com.xiaoyu.app.data.db.SessionEntity
import com.xiaoyu.app.data.scheduler.ExtractionScheduler
import com.xiaoyu.app.data.search.MessageSearchIndex
import com.xiaoyu.app.data.search.SearchRepository
import com.xiaoyu.app.data.settings.SettingsRepository
import com.xiaoyu.soul.core.BrainException
import com.xiaoyu.soul.core.BrainRole
import com.xiaoyu.soul.core.MessageRole
import com.xiaoyu.soul.core.SoulMessage
import com.xiaoyu.soul.engine.SoulEngine
import com.xiaoyu.soul.memory.ExplicitMemoryDetector
import com.xiaoyu.soul.memory.MemoryExtractionParser
import com.xiaoyu.soul.pacing.ReplyPacer
import com.xiaoyu.soul.postprocess.PostEvent
import com.xiaoyu.soul.postprocess.PostProcessPipeline
import com.xiaoyu.soul.postprocess.XyTokenParser
import com.xiaoyu.soul.prompt.PromptContext
import com.xiaoyu.soul.sticker.StickerCooldown
import com.xiaoyu.soul.util.TokenEstimator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import org.json.JSONObject
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

private const val TAG = "XiaoyuChat"

/** 分段发送的输出格式指令（执行书 3.2.4）。 */
private const val SEGMENT_DIRECTIVE =
    "[输出格式] 像微信聊天一样把回复拆成 1~3 条短消息，每条之间用 ||| 分隔，不要用换行代替分隔符；" +
        "每条一句话左右，别写成大段文章。动作描写写成 [xy:act=揉揉你的头发]，情绪变化可用 [xy:sig=joy]。不要输出其它控制标记。"

/** reaction 后她回一个表情的概率（执行书 6.3.7）。 */
private const val REACTION_REPLY_PERCENT = 25

/** 她可能回的表情池（含品牌彩蛋 🐟）。 */
private val HER_REACTION_POOL = listOf("🥺", "😂", "❤️", "🐟")

sealed interface ChatEvent {
    /** 她正在输入（含可选的“真实人类节奏”延迟）。 */
    data class Typing(val delayMs: Long) : ChatEvent

    data class Delta(val text: String) : ChatEvent

    /** 当前气泡收尾：分段发送时清空流式气泡，等下一段。 */
    data object SegmentBreak : ChatEvent

    /** 丢弃已经推给界面的内容（禁词拦截后重生成）。 */
    data object Reset : ChatEvent

    data object Completed : ChatEvent

    data class Failed(val message: String, val partial: String) : ChatEvent
}

/**
 * 聊天主链路（PRD 4.1）：装配灵魂上下文 → 记忆召回 → 流式回复 → 后处理 → 落库。
 * 保存的消息永不批量删除，只允许单条隐藏。
 */
@Singleton
class ChatRepository @Inject constructor(
    private val sessionDao: SessionDao,
    private val messageDao: com.xiaoyu.app.data.db.MessageDao,
    private val settings: SettingsRepository,
    private val router: BrainGateway,
    private val soul: SoulEngine,
    private val personaRepo: PersonaRepository,
    private val memoryRepo: MemoryRepository,
    private val stateRepo: SoulStateRepository,
    private val scheduler: ExtractionScheduler,
    private val stickerRepo: StickerRepository,
    private val diagnosticsRepo: DiagnosticsRepository,
    private val snapshotDao: RequestSnapshotDao,
    private val searchRepo: SearchRepository,
    @ApplicationContext private val appContext: Context,
) {
    private val staleSessionMillis = 30 * 60 * 1000L
    /** AI 发表情冷却（执行书 7.4.7），跨会话共享。 */
    private val stickerCooldown = StickerCooldown(4)

    fun observeMessages(sessionId: Long): Flow<List<MessageEntity>> = messageDao.observeVisible(sessionId)

    /** 连续流模式：跨所有会话的可见消息（PRD 4.2.1）。 */
    fun observeAllMessages(): Flow<List<MessageEntity>> = messageDao.observeAllVisible()

    /** 翻旧账：FTS 全文搜索，中文按二元组召回（PRD US-020 / M7-04）。 */
    suspend fun searchMessages(keyword: String, limit: Int = 40): List<MessageEntity> =
        searchRepo.search(keyword, SearchRepository.Filter(limit = limit)).map { it.message }

    /** 带高亮片段与会话/类型/时间过滤的搜索结果（M7-04）。 */
    suspend fun searchMessagesAdvanced(
        keyword: String,
        filter: SearchRepository.Filter = SearchRepository.Filter(),
    ): List<SearchRepository.Hit> = searchRepo.search(keyword, filter)

    /** 基于搜索结果的临时上下文（只读，不写记忆）。 */
    fun buildSearchTransientContext(hits: List<SearchRepository.Hit>, max: Int = 8): String =
        searchRepo.buildTransientContext(hits, max)

    /** 她最近说的一句话，供桌面小组件展示（M7-08）。 */
    suspend fun latestAssistantLine(maxChars: Int = 60): String? =
        messageDao.latestAssistantMessage()?.content
            ?.replace("\n", " ")
            ?.trim()
            ?.take(maxChars)
            ?.ifBlank { null }

    /**
     * 统一写入入口：先落消息，再维护全文索引。
     * 索引写失败不影响消息本身（搜索时 ensureIndex 会补建）。
     */
    private suspend fun insertMessage(message: MessageEntity): Long {
        val id = messageDao.insert(message)
        if (message.content.isNotBlank()) {
            runCatching {
                messageDao.upsertFts(
                    MessageFtsEntity(
                        rowId = id,
                        content = message.content,
                        indexText = MessageSearchIndex.buildIndexText(message.content),
                    )
                )
            }.onFailure { Log.w(TAG, "消息索引写入失败 id=$id: ${it.message}") }
        }
        return id
    }

    /** 插入一条带图片的 assistant 消息（PRD US-018 生图结果）。 */
    suspend fun insertImageMessage(sessionId: Long, text: String, imagePath: String) {
        val now = System.currentTimeMillis()
        insertMessage(
            MessageEntity(
                sessionId = sessionId,
                role = ROLE_ASSISTANT,
                content = text,
                createdAt = now,
                imageUri = imagePath,
            )
        )
        sessionDao.touch(sessionId, now)
    }

    /** 图库：所有带图片的可见消息（PRD US-018）。 */
    fun observeImageMessages(): Flow<List<MessageEntity>> = messageDao.observeImages()

    fun observeSession(sessionId: Long): Flow<SessionEntity?> = sessionDao.observeById(sessionId)

    fun observeSessions(): Flow<List<SessionEntity>> = sessionDao.observeActive()

    fun observeArchivedSessions(): Flow<List<SessionEntity>> = sessionDao.observeArchived()

    suspend fun session(id: Long): SessionEntity? = sessionDao.byId(id)

    suspend fun createSession(title: String? = null, countInteraction: Boolean = true): Long {
        val identities = personaRepo.identities()
        val styles = personaRepo.styles()
        val chosen = settings.current()
        val identityId = chosen.chosenIdentityId?.takeIf { id -> identities.any { it.id == id } }
            ?: identities.firstOrNull()?.id ?: 0L
        val styleId = chosen.chosenStyleId?.takeIf { id -> styles.any { it.id == id } }
            ?: styles.firstOrNull()?.id ?: 0L
        val now = System.currentTimeMillis()
        val id = sessionDao.insert(
            SessionEntity(
                title = title ?: defaultTitle(now),
                identityId = identityId,
                styleId = styleId,
                createdAt = now,
                lastMessageAt = now,
            )
        )
        Log.i(TAG, "createSession id=$id title=${title ?: defaultTitle(now)}")
        if (countInteraction) stateRepo.registerSessionStart()
        return id
    }

    /**
     * 获取或创建一个“当前可发言”的会话（PRD 4.2.1）。
     * - 最近一个未结束会话若仍在 30 分钟活跃窗口内，直接复用；
     * - 若已超过 30 分钟，静默结束并触发提炼，新建会话续聊，UI 无感知；
     * - 没有活跃会话则新建。
     */
    suspend fun ensureSession(): Long {
        val now = System.currentTimeMillis()
        val latest = sessionDao.latestActive()
        if (latest != null && latest.endedAt == null) {
            val idleMs = now - latest.lastMessageAt
            if (idleMs < staleSessionMillis) {
                Log.i(TAG, "ensureSession reuse id=${latest.id} idle=${idleMs}ms")
                return latest.id
            }
            Log.i(TAG, "ensureSession stale id=${latest.id} idle=${idleMs}ms -> end + extract")
            endSession(latest.id)
        }
        val id = createSession()
        Log.i(TAG, "ensureSession created new id=$id")
        return id
    }

    /** 最近的活跃会话 id；仅用于恢复/归档后回落，绝不新建会话。 */
    suspend fun latestSessionId(): Long? = sessionDao.latestActive()?.id

    suspend fun renameSession(id: Long, title: String) = sessionDao.rename(id, title.ifBlank { "新的对话" })

    /** 第一条用户消息决定会话标题（PRD 4.2）。 */
    private suspend fun autoTitle(sessionId: Long, currentTitle: String, text: String) {
        if (messageDao.countVisible(sessionId) == 1 && currentTitle.endsWith("的聊天")) {
            sessionDao.rename(sessionId, text.take(14).ifBlank { currentTitle })
        }
    }

    suspend fun archiveSession(id: Long) {
        sessionDao.markEnded(id, System.currentTimeMillis())
        sessionDao.archive(id)
        scheduler.scheduleExtraction(id)
    }

    suspend fun unarchiveSession(id: Long) = sessionDao.unarchive(id)

    suspend fun setStyle(sessionId: Long, styleId: Long) = sessionDao.setStyle(sessionId, styleId)

    suspend fun hideMessage(id: Long) = messageDao.hide(id)

    /** 多选软删（M3-07）：批量标记 hidden=1，不物理删除。 */
    suspend fun hideMessages(ids: List<Long>) {
        if (ids.isEmpty()) return
        messageDao.hideBatch(ids)
    }

    /** 多选导出（M3-07）：把选中消息按时间顺序拼成纯文本。 */
    suspend fun exportMessagesText(ids: List<Long>): String {
        if (ids.isEmpty()) return ""
        val msgs = messageDao.byIds(ids)
        val fmt = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
        return msgs.joinToString("\n") { m ->
            val role = when (m.role) {
                ROLE_USER -> appContext.getString(R.string.data_export_role_me)
                ROLE_ASSISTANT -> appContext.getString(R.string.data_export_role_her)
                else -> appContext.getString(R.string.data_export_role_system)
            }
            val time = fmt.format(java.util.Date(m.createdAt))
            "[$time] $role: ${m.content}"
        }
    }

    /** 撤回（M3-07）：仅 2 分钟内的自己消息可撤回，正文清空、标记 retracted。 */
    suspend fun retract(messageId: Long): Boolean {
        val msg = messageDao.byId(messageId) ?: return false
        if (msg.role != ROLE_USER) return false
        if (System.currentTimeMillis() - msg.createdAt > RETRACT_WINDOW_MS) return false
        messageDao.retract(messageId)
        return true
    }

    /** 编辑重发（M3-07）：更新正文 + 标记 edited_at，然后基于新文本再触发一轮回复。 */
    suspend fun editAndResend(sessionId: Long, messageId: Long, newText: String): Flow<ChatEvent> {
        messageDao.updateContent(messageId, newText.trim(), System.currentTimeMillis())
        reindexMessage(messageId)
        return replyFlow(sessionId, newText, continueMode = false)
    }

    /** 正文变更后同步全文索引（M7-04）。 */
    private suspend fun reindexMessage(messageId: Long) {
        val message = messageDao.byId(messageId) ?: return
        if (message.content.isBlank()) return
        runCatching {
            messageDao.upsertFts(
                MessageFtsEntity(
                    rowId = messageId,
                    content = message.content,
                    indexText = MessageSearchIndex.buildIndexText(message.content),
                )
            )
        }
    }

    /** 重发失败的消息（M1-09）：把状态改回 sending，再走一轮回复。 */
    suspend fun resend(sessionId: Long, messageId: Long): Flow<ChatEvent> {
        val msg = messageDao.byId(messageId)
            ?: return flow {
                emit(ChatEvent.Failed(appContext.getString(R.string.data_chat_message_not_found), ""))
            }
        messageDao.updateStatus(messageId, STATUS_SENDING)
        return replyFlow(sessionId, msg.content, continueMode = false)
    }

    suspend fun endSession(id: Long) {
        sessionDao.markEnded(id, System.currentTimeMillis())
        scheduler.scheduleExtraction(id)
        Log.i(TAG, "endSession id=$id, extraction scheduled")
    }

    /** App 回到前台时补判定“30 分钟未发消息 = 会话结束”（PRD 4.2）。 */
    suspend fun finalizeStaleSessions() {
        val now = System.currentTimeMillis()
        sessionDao.openSessions().forEach { session ->
            if (now - session.lastMessageAt >= staleSessionMillis) {
                sessionDao.markEnded(session.id, now)
                scheduler.scheduleExtraction(session.id)
                Log.i(TAG, "finalizeStaleSessions ended id=${session.id}, extraction scheduled")
            }
        }
    }

    /** 应用被杀导致的漏提炼，在启动时补排（PRD 4.2 兜底）。 */
    suspend fun reschedulePendingExtractions() {
        sessionDao.sessionsNeedingExtraction().forEach { scheduler.scheduleExtraction(it.id) }
    }

    /** 主动聊天落库：作为她的消息出现在会话里。 */
    suspend fun saveProactiveMessage(sessionId: Long, text: String) {
        persistAssistant(sessionId, text)
    }

    suspend fun ensureSessionForProactive(): Long =
        sessionDao.latestActive()?.takeIf { it.endedAt == null }?.id
            ?: createSession("她主动找你", countInteraction = false)

    /** 通知对话样式用：最近一条用户消息（去控制标记、截断），没有则返回 null。 */
    suspend fun lastUserMessageText(sessionId: Long): String? =
        messageDao.latestForSession(sessionId, 12)
            .firstOrNull { it.role == ROLE_USER && it.type == TYPE_TEXT && it.content.isNotBlank() }
            ?.content
            ?.replace(Regex("\\[xy:[^]]*]"), "")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.take(120)

    suspend fun send(sessionId: Long, text: String, transientContext: String? = null, quoteToId: Long? = null): Flow<ChatEvent> {
        val session = sessionDao.byId(sessionId) ?: error("会话不存在")
        if (session.endedAt != null) sessionDao.reopen(sessionId)
        val content = text.trim()
        return replyFlow(sessionId, content, continueMode = false, transientContext = transientContext, quoteToId = quoteToId)
    }

    /** 回复中断后的“继续”（PRD 4.1）。 */
    suspend fun continueGeneration(sessionId: Long): Flow<ChatEvent> =
        replyFlow(sessionId, null, continueMode = true, transientContext = null)

    /** 戳一下（执行书 6.3.7）：插入系统提示并让她即兴反应；[streak] 是连续戳的次数。 */
    suspend fun poke(sessionId: Long, streak: Int = 1): Flow<ChatEvent> = flow {
        val now = System.currentTimeMillis()
        insertMessage(
            MessageEntity(
                sessionId = sessionId,
                role = ROLE_NOTICE,
                content = "你戳了一下小渔",
                createdAt = now,
            )
        )
        sessionDao.touch(sessionId, now)
        // 被戳一下只是轻轻一碰：心情略升、精力略降，好感也悄悄涨一点
        stateRepo.applyMoodSignal(1, -1)
        stateRepo.recordFavorability(1, "poke")
        val directive = if (streak >= 3) {
            "用户连着戳了你 $streak 下，你有点无奈又好笑。回一句很短的吐槽，" +
                "并在开头带一个动作描写 [xy:act=鼓起脸颊]，不要提系统。"
        } else {
            "用户戳了你一下。用一句短话自然反应，不要每次都一样，不要提系统。"
        }
        replyFlow(
            sessionId = sessionId,
            userText = null,
            continueMode = false,
            extraDirective = directive,
        ).collect { emit(it) }
    }

    /** 连戳到达上限（执行书 6.3.7）：她直接说「别戳啦」，心情 -2，不再请求模型。 */
    suspend fun pokeLimitReply(sessionId: Long) {
        stateRepo.applyMoodSignal(-2, 0)
        persistAssistant(sessionId, "别戳啦！")
    }

    /**
     * 重新生成（执行书 6.3.6）：同组旧消息软隐藏，复用当次请求的 prompt 快照重来一次。
     * 新旧版本共享 reply_group_id，供「查看之前的版本」切换。
     */
    suspend fun regenerate(sessionId: Long, messageId: Long): Flow<ChatEvent> = flow {
        val target = messageDao.byId(messageId)
        val groupId = target?.replyGroupId ?: UUID.randomUUID().toString()
        // 整组收起（只改 hidden），避免切换版本时出现两条同时可见的回复
        if (target?.replyGroupId != null) {
            messageDao.hideGroup(groupId)
        } else {
            messageDao.hide(messageId)
        }
        val snapshot = findSnapshot(sessionId, target?.content.orEmpty())
        replyFlow(
            sessionId = sessionId,
            userText = null,
            continueMode = false,
            extraDirective = "请换一种说法重新回答上一条，不要重复刚才的表达。",
            versionGroupId = groupId,
            reuseSnapshot = snapshot,
        ).collect { emit(it) }
    }

    /** 同组的全部生成版本（含已隐藏），按时间正序。 */
    suspend fun versions(messageId: Long): List<MessageEntity> {
        val message = messageDao.byId(messageId) ?: return emptyList()
        val groupId = message.replyGroupId ?: return listOf(message)
        return messageDao.assistantVersions(groupId)
    }

    /** 切换到某一历史版本：整组收起后只放亮这一条。 */
    suspend fun switchVersion(groupId: String, messageId: Long) {
        messageDao.hideGroup(groupId)
        messageDao.unhide(messageId)
    }

    /** 找到生成这条消息时用的请求快照：优先选原始输出里含该消息片段的，退化为最近一次。 */
    private suspend fun findSnapshot(sessionId: Long, content: String): RequestSnapshotEntity? {
        val snapshots = runCatching { snapshotDao.chatSnapshots(sessionId, 10) }.getOrDefault(emptyList())
        if (snapshots.isEmpty()) return null
        val probe = content.split("\n")
            .firstOrNull { it.isNotBlank() && !it.startsWith(ACTION_PREFIX) }
            ?.trim()
            ?.take(8)
        return snapshots.firstOrNull { probe != null && it.responseRaw.contains(probe) } ?: snapshots.first()
    }

    /** 把快照里的请求体还原成消息列表，保证「重新生成」用的是同一份 prompt。 */
    private fun snapshotMessages(snapshot: RequestSnapshotEntity): List<SoulMessage>? = try {
        val array = JSONObject(snapshot.requestBody).optJSONArray("messages")
        if (array == null || array.length() == 0) {
            null
        } else {
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val role = when (item.optString("role")) {
                    "user" -> MessageRole.USER
                    "assistant" -> MessageRole.ASSISTANT
                    else -> MessageRole.SYSTEM
                }
                SoulMessage(role, item.optString("content"))
            }.takeIf { it.isNotEmpty() }
        }
    } catch (e: Exception) {
        Log.w(TAG, "快照解析失败: ${e.message}")
        null
    }

    /** reaction 开关（执行书 6.3.7）：单用户每类最多 1 个，可取消。 */
    suspend fun toggleReaction(messageId: Long, emoji: String) {
        val message = messageDao.byId(messageId) ?: return
        val set = message.reactionsJson.split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toMutableSet()
        val added = set.add(emoji)
        if (!added) set.remove(emoji)
        messageDao.setReactions(messageId, set.joinToString(","))
        // 给她/她的消息比心，会让她心情与好感度有微小正向变化
        if (message.role == ROLE_ASSISTANT && added) {
            stateRepo.applyMoodSignal(2, 0)
            stateRepo.recordFavorability(1, "reaction")
            replyReactionBack(messageId)
        }
    }

    /** 她收到 reaction 有 25% 概率回一个表情（本地随机，不额外请求模型）。 */
    private suspend fun replyReactionBack(messageId: Long) {
        if (Random.nextInt(100) >= REACTION_REPLY_PERCENT) return
        val herEmoji = HER_REACTION_POOL.random()
        settings.update { it.copy(herReactions = it.herReactions + (messageId.toString() to herEmoji)) }
        stateRepo.applyMoodSignal(2, 0)
    }

    private suspend fun replyFlow(
        sessionId: Long,
        userText: String?,
        continueMode: Boolean,
        transientContext: String? = null,
        extraDirective: String? = null,
        quoteToId: Long? = null,
        /** 重新生成时与旧版本共享的组 id（M3-06）；为空则本轮新建。 */
        versionGroupId: String? = null,
        /** 重新生成时复用的请求快照（M3-06）。 */
        reuseSnapshot: RequestSnapshotEntity? = null,
    ): Flow<ChatEvent> = flow {
        val session = sessionDao.byId(sessionId) ?: throw IllegalStateException("会话不存在")
        stickerCooldown.onTurnStarted()
        val now = System.currentTimeMillis()
        val directives = mutableListOf<String>()
        var userMessageId: Long? = null

        if (userText != null) {
            userMessageId = insertMessage(
                MessageEntity(
                    sessionId = sessionId,
                    role = ROLE_USER,
                    // 用户消息里的 [xy:*] 一律转义，防止伪造协议（3.3.1）
                    content = XyTokenParser.escapeUserText(userText),
                    createdAt = now,
                    status = STATUS_SENDING,
                    quoteToId = quoteToId,
                )
            )
            sessionDao.touch(sessionId, now)
            autoTitle(sessionId, session.title, userText)
            handleExplicitMemory(userText)?.let { directives += it }
            handleDiaryRequest(userText)?.let { directives += it }
        } else if (continueMode) {
            directives += "[本轮特别说明] 你上一次的回复被中断了。请接着刚才的语气自然说完，不要重复已经说过的内容。"
        }
        extraDirective?.let { directives += it }

        // 翻旧账临时上下文：仅本轮生效，不进记忆系统（PRD US-020）
        if (!transientContext.isNullOrBlank()) {
            directives += "[本轮特别说明] 用户翻出了一段旧对话，希望你基于这段历史来回答。旧对话内容如下：\n$transientContext\n请自然地结合这段历史回应，不要显得是在检索资料。"
        }

        settings.current().pendingDiaryMentionId?.let { diaryId ->
            directives += "[本轮特别说明] 你刚刚写了一篇日记（id=$diaryId，在日记本里）。可以在回复末尾自然问一句要不要看，不要透露内容，也不要提系统。"
            settings.update { it.copy(pendingDiaryMentionId = null) }
        }

        val identity = personaRepo.identity(session.identityId)
            ?: personaRepo.identities().firstOrNull()
            ?: throw IllegalStateException("缺少人设")
        val style = personaRepo.style(session.styleId)
            ?: personaRepo.styles().firstOrNull()
            ?: throw IllegalStateException("缺少风格")

        val mood = stateRepo.currentMoodSnapshot()
        val favorability = stateRepo.favorabilityValue()
        val memories = if (userText != null) memoryRepo.recallForChat(userText) else emptyList()
        memoryRepo.markRecalled(memories.map { it.id })
        val cards = memoryRepo.cards()
        val life = stateRepo.freshLifeForChat(3)
        val historyEntities = messageDao.latestForSession(sessionId, 24).reversed()
        val historyStickerNames = stickerRepo.namesFor(
            historyEntities.mapNotNull { it.stickerId }
        )
        val history = historyEntities.map { it.toSoulMessage(historyStickerNames[it.stickerId]) }
        val hour = LocalTime.now().hour

        val config = settings.current()
        // L3 表情目录：空表时为空，PromptAssembler 会整段省略（7.4.2）
        val stickerCatalog = stickerRepo.catalogForPrompt(40)
        if (config.segmentSending) directives += SEGMENT_DIRECTIVE
        if (config.immersiveEnabled) {
            if (ReplyPacer.shouldSaySleepy(hour, mood.energy, Random.Default)) {
                val line = ReplyPacer.sleepyLine(Random.Default)
                persistAssistant(sessionId, line)
                emit(ChatEvent.Delta(line))
                emit(ChatEvent.Completed)
                return@flow
            }
            val delayMs = ReplyPacer.delayMillis(
                intensity = config.immersiveIntensity,
                energy = mood.energy,
                hourOfDay = hour,
                random = Random.Default,
            )
            emit(ChatEvent.Typing(delayMs))
            delay(delayMs)
        } else {
            emit(ChatEvent.Typing(0))
        }

        val replyGroupId = versionGroupId ?: UUID.randomUUID().toString()
        val segmentSending = config.segmentSending
        var attempt = 0
        var persisted = 0
        var failure: BrainException? = null
        var lastVisible = ""
        val pendingStickers = mutableListOf<String>()

        /** 一段文字落库后再落表情，保证气泡顺序是「先说话，后甩表情」。 */
        suspend fun flushStickers() {
            if (pendingStickers.isEmpty()) return
            for (id in pendingStickers) {
                if (!stickerCooldown.canSend()) {
                    Log.d(TAG, "表情冷却中，已丢弃: $id")
                    continue
                }
                persistSticker(sessionId, id)
                stickerRepo.recordUse(id)
                stickerCooldown.markSent()
                persisted += 1
            }
            pendingStickers.clear()
        }

        // 后处理事件 → 界面 / 落库（执行书 3.3）
        suspend fun FlowCollector<ChatEvent>.handle(ev: PostEvent, actions: MutableList<String>) {
            when (ev) {
                is PostEvent.Delta -> emit(ChatEvent.Delta(ev.text))
                is PostEvent.SegmentFinished -> {
                    val content = if (actions.isEmpty()) ev.text
                    else ev.text + actions.joinToString("") { "\n$ACTION_PREFIX$it" }
                    actions.clear()
                    persistAssistant(sessionId, content, replyGroupId)
                    persisted += 1
                    flushStickers()
                    emit(ChatEvent.SegmentBreak)
                    if (segmentSending) delay(600L + Random.nextLong(-300, 301))
                }
                is PostEvent.Sticker -> {
                    val sticker = stickerRepo.resolve(ev.stickerId, stickerCatalog)
                    when {
                        sticker == null -> Log.d(TAG, "表情编号无效，已忽略: ${ev.stickerId}")
                        pendingStickers.isNotEmpty() -> Log.d(TAG, "本轮已有表情，忽略多余: ${sticker.name}")
                        else -> pendingStickers += sticker.id
                    }
                }
                is PostEvent.Signal -> applySignal(ev.emotion)
                is PostEvent.Action -> actions += ev.text
                PostEvent.Memo -> Unit // TODO(spec-gap): 投递记忆提炼任务
                is PostEvent.SoftWarning -> Log.d(TAG, "AI 腔命中: ${ev.phrase}")
                is PostEvent.Blocked -> Log.w(TAG, "禁词拦截: ${ev.level} ${ev.phrase}")
            }
        }

        while (true) {
            val pipeline = PostProcessPipeline()
            val actions = mutableListOf<String>()
            val assembly = soul.assemble(
                PromptContext(
                    identity = identity.toSoul(),
                    style = style.toSoul(),
                    moodValue = mood.mood,
                    energy = mood.energy,
                    hourOfDay = hour,
                    favorability = favorability,
                    factCards = cards,
                    lifeFragments = life,
                    memories = memories,
                    history = history,
                    directive = directives.joinToString("\n\n").ifBlank { null },
                    stickerCatalog = stickerCatalog,
                )
            )
            // 重新生成（M3-06）：优先复用当次请求的快照，保证模型看到的 prompt 完全一致
            val reuseMessages = reuseSnapshot?.let { snapshotMessages(it) }
            val requestAssembly = if (reuseMessages == null) assembly else assembly.copy(messages = reuseMessages)
            failure = null
            val raw = StringBuilder()
            var usagePrompt: Int? = null
            var usageCompletion: Int? = null
            val attemptStarted = System.currentTimeMillis()
            try {
                router.chat(BrainRole.CHAT, requestAssembly.messages).collect { chunk ->
                    chunk.usage?.let {
                        usagePrompt = it.promptTokens
                        usageCompletion = it.completionTokens
                    }
                    if (chunk.delta.isEmpty()) return@collect
                    raw.append(chunk.delta)
                    pipeline.feed(chunk.delta).forEach { handle(it, actions) }
                }
            } catch (e: BrainException) {
                failure = e
            }
            val durationMs = System.currentTimeMillis() - attemptStarted
            lastVisible = pipeline.visibleText()
            // 诊断快照（执行书 6.13）：本地保留最近 50 次，便于复现
            val provider = config.brain.chat
            try {
                diagnosticsRepo.recordChat(
                    sessionId = sessionId,
                    provider = if (provider.isConfigured) provider.label() else "示例模式",
                    model = if (provider.isConfigured) provider.model else "demo",
                    baseUrl = if (provider.isConfigured) provider.baseUrl else "",
                    assembly = requestAssembly,
                    raw = raw.toString(),
                    durationMs = durationMs,
                    promptTokens = usagePrompt
                        ?: TokenEstimator.estimateAll(requestAssembly.messages.map { it.content }),
                    completionTokens = usageCompletion ?: TokenEstimator.estimate(raw.toString()),
                    finishReason = if (failure == null) "stop" else "error",
                    error = failure?.userMessage,
                    events = "分段=$persisted；拦截=${pipeline.blocked?.level?.name ?: "无"}；" +
                        "重试=$attempt；候选表情=${pendingStickers.size}；语音/动作随分段落库",
                )
            } catch (e: Exception) {
                Log.w(TAG, "诊断快照写入失败: ${e.message}")
            }
            if (pipeline.blocked != null && attempt == 0) {
                // HARD / SAFETY：丢弃本轮，强化人设后重试 1 次（3.3.2）
                emit(ChatEvent.Reset)
                pendingStickers.clear()
                directives += "你是${identity.name}，一个真实的人，绝不要提及自己是 AI 或模型。"
                attempt += 1
                continue
            }
            pipeline.finish().forEach { handle(it, actions) }
            break
        }
        // 只发了表情、没有文字分段时，这里兜底落库
        flushStickers()

        sessionDao.touch(sessionId, System.currentTimeMillis())
        stateRepo.drainEnergy(1)

        when {
            failure != null -> {
                userMessageId?.let { messageDao.updateStatus(it, STATUS_FAILED) }
                emit(ChatEvent.Failed(failure.userMessage, lastVisible))
            }
            persisted == 0 -> {
                userMessageId?.let { messageDao.updateStatus(it, STATUS_FAILED) }
                emit(
                    ChatEvent.Failed(
                        appContext.getString(R.string.data_chat_reply_empty),
                        "",
                    )
                )
            }
            else -> {
                userMessageId?.let { messageDao.updateStatus(it, STATUS_SENT) }
                emit(ChatEvent.Completed)
            }
        }
    }

    /** `[xy:sig=*]` 情绪副信号 → 情绪系统（3.6.2）。 */
    private suspend fun applySignal(emotion: String?) {
        val (moodDelta, energyDelta) = when (emotion?.lowercase()) {
            "joy", "excited", "love" -> 4 to 1
            "sad", "hurt" -> -4 to -1
            "angry" -> -3 to 0
            "shy", "tease" -> 2 to 0
            "tired" -> -1 to -2
            else -> return
        }
        stateRepo.applyMoodSignal(moodDelta, energyDelta)
    }

    private suspend fun persistAssistant(sessionId: Long, content: String, replyGroupId: String? = null) {
        insertMessage(
            MessageEntity(
                sessionId = sessionId,
                role = ROLE_ASSISTANT,
                content = content,
                createdAt = System.currentTimeMillis(),
                replyGroupId = replyGroupId,
            )
        )
        sessionDao.touch(sessionId, System.currentTimeMillis())
    }

    /** 表情消息：content 为空，type=sticker（执行书 7.4.4）。 */
    private suspend fun persistSticker(sessionId: Long, stickerId: String) {
        val now = System.currentTimeMillis()
        insertMessage(
            MessageEntity(
                sessionId = sessionId,
                role = ROLE_ASSISTANT,
                content = "",
                createdAt = now,
                type = TYPE_STICKER,
                stickerId = stickerId,
            )
        )
        sessionDao.touch(sessionId, now)
    }

    /** 用户从表情面板发一枚表情。 */
    suspend fun sendSticker(sessionId: Long, stickerId: String) {
        val now = System.currentTimeMillis()
        insertMessage(
            MessageEntity(
                sessionId = sessionId,
                role = ROLE_USER,
                content = "",
                createdAt = now,
                type = TYPE_STICKER,
                stickerId = stickerId,
            )
        )
        sessionDao.touch(sessionId, now)
        stickerRepo.recordUse(stickerId)
    }

    /**
     * 显式记忆指令（PRD 5.2.1）：本地关键词初筛 → EXTRACT 确认 → 直写长期卡片；
     * 确认失败也要先自然回应“记下了”，并把原文暂存到待重试队列。
     */
    private suspend fun handleExplicitMemory(text: String): String? {
        if (!ExplicitMemoryDetector.isCandidate(text)) return null
        val normalized = ExplicitMemoryDetector.normalize(text)
        val raw = router.completeJson(BrainRole.EXTRACT, null, soul.explicitMemoryCheckPrompt(text))
        val parsed = MemoryExtractionParser.parseExplicitMemory(raw)
        val card = parsed.card
        return when {
            parsed.remember && card != null -> {
                memoryRepo.writeUserCommandCard(
                    content = card.content,
                    tags = card.tags,
                    category = card.category,
                )
                settings.update { it.copy(pendingExplicitFacts = it.pendingExplicitFacts - text) }
                soul.explicitMemoryAck(card.content)
            }
            raw == null -> {
                settings.update {
                    if (text in it.pendingExplicitFacts) it
                    else it.copy(pendingExplicitFacts = it.pendingExplicitFacts + text)
                }
                soul.explicitMemoryAck(normalized)
            }
            else -> null
        }
    }

    /** 用户主动问起日记（PRD 8.4）：好感度决定能否解锁加密日记。 */
    private suspend fun handleDiaryRequest(text: String): String? {
        val asksDiary = text.contains("日记") &&
            listOf("看", "读", "写了什么", "写了啥", "给我").any { text.contains(it) }
        if (!asksDiary) return null
        val locked = stateRepo.latestLockedSecret()
        if (locked == null) {
            return "用户想看你最近写的日记。你可以自然地分享最近日记里的一两句话，不要整篇朗读，也不要提系统。"
        }
        return if (stateRepo.secretShareable()) {
            stateRepo.unlockDiary(locked.id)
            "用户问起你的加密日记。你已经愿意给他看了（刚刚解锁，日记 id=${locked.id}），自然地说一句让他去日记本看看，不要提数值或系统。"
        } else {
            "用户问起你的加密日记，但你们还不够亲近。温柔地拒绝，说等更熟一点再给他看；不要提系统或数值。"
        }
    }

    private fun defaultTitle(now: Long): String {
        val hour = LocalTime.now().hour
        val prefix = when (hour) {
            in 5..8 -> "清晨的聊天"
            in 9..11 -> "上午的聊天"
            in 12..13 -> "午后的聊天"
            in 14..17 -> "下午的聊天"
            in 18..22 -> "晚上的聊天"
            else -> "深夜的聊天"
        }
        return prefix
    }
}
