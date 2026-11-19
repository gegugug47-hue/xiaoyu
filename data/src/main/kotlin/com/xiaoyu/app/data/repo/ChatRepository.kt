package com.xiaoyu.app.data.repo

import android.content.Context
import android.util.Log
import com.xiaoyu.app.data.R
import com.xiaoyu.app.data.brain.BrainGateway
import com.xiaoyu.app.data.db.MessageEntity
import com.xiaoyu.app.data.db.MessageFtsEntity
import com.xiaoyu.app.data.db.EmotionEventEntity
import com.xiaoyu.app.data.db.CorrectionEntity
import com.xiaoyu.app.data.db.RequestSnapshotDao
import com.xiaoyu.app.data.db.RequestSnapshotEntity
import com.xiaoyu.app.data.db.SafetyEventDao
import com.xiaoyu.app.data.db.SafetyEventEntity
import com.xiaoyu.app.data.db.SessionDao
import com.xiaoyu.app.data.db.SessionEntity
import com.xiaoyu.app.data.scheduler.ExtractionScheduler
import com.xiaoyu.app.data.search.MessageSearchIndex
import com.xiaoyu.app.data.search.SearchRepository
import com.xiaoyu.app.data.settings.SettingsRepository
import com.xiaoyu.soul.card.CardFactory
import com.xiaoyu.soul.core.BrainException
import com.xiaoyu.soul.core.BrainRole
import com.xiaoyu.soul.core.MessageRole
import com.xiaoyu.soul.core.SoulMessage
import com.xiaoyu.soul.emotion.EmotionAttributor
import com.xiaoyu.soul.correction.CorrectionEngine
import com.xiaoyu.soul.engine.SoulEngine
import com.xiaoyu.soul.memory.ExplicitMemoryDetector
import com.xiaoyu.soul.memory.MemoryExtractionParser
import com.xiaoyu.soul.pacing.ReplyPacer
import com.xiaoyu.soul.postprocess.LengthGuard
import com.xiaoyu.soul.postprocess.PostEvent
import com.xiaoyu.soul.postprocess.PostProcessPipeline
import com.xiaoyu.soul.postprocess.XyTokenParser
import com.xiaoyu.soul.safety.CrisisLevel
import com.xiaoyu.soul.safety.DefaultSafetyGate
import com.xiaoyu.soul.safety.HelpResource
import com.xiaoyu.soul.safety.InjectionGuard
import com.xiaoyu.soul.safety.PromptZone
import com.xiaoyu.soul.safety.SafetyTexts
import com.xiaoyu.soul.safety.SafetyVerdict
import com.xiaoyu.soul.prompt.PromptContext
import com.xiaoyu.soul.sticker.StickerCooldown
import com.xiaoyu.soul.util.TokenEstimator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

private const val TAG = "XiaoyuChat"

/** v3 §8.2：L2 及以上危机后，主动消息总闸暂停 48 小时。 */
private const val PROACTIVE_PAUSE_AFTER_CRISIS_MS = 48 * 60 * 60 * 1000L

/** v3 §8.2：L3 之后保持现实导向的轮数。 */
private const val L3_REALITY_ROUNDS = 3

/** 她刚道过晚安后，这段时间内不再直出同一句，改由模型正常回应。 */
private const val SLEEPY_REPEAT_WINDOW_MS = 6 * 60 * 60 * 1000L

/** 分段发送的输出格式指令（执行书 3.2.4）。 */
private const val SEGMENT_DIRECTIVE =
    "[输出格式] 像微信聊天一样把回复拆成 1~3 条短消息，每条之间用 ||| 分隔，不要用换行代替分隔符；" +
        "每条一句话左右，别写成大段文章。动作描写写成 [xy:act=揉揉你的头发]，情绪变化可用 [xy:sig=joy]。不要输出其它控制标记。"

/** reaction 后她回一个表情的概率（执行书 6.3.7）。 */
private const val REACTION_REPLY_PERCENT = 25

/** v3 §7.4.6：被夸 / 被道歉时她给你的消息加 reaction 的概率（本地规则，不调模型）。 */
private const val HER_REACTION_TO_USER_PERCENT = 30

/** 她可能回的表情池（含品牌彩蛋 🐟）。 */
private val HER_REACTION_POOL = listOf("🥺", "😂", "❤️", "🐟")

/** 触发她 reaction 的被夸 / 被道歉线索（本地关键词，大小写按中文场景处理）。 */
private val PRAISE_HINTS = listOf("谢谢", "感谢", "辛苦了", "真棒", "好棒", "厉害", "喜欢你", "爱你", "可爱", "温柔", "乖")
private val APOLOGY_HINTS = listOf("对不起", "抱歉", "不好意思", "我错了", "原谅我", "别生气", "是我不好")

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
 * 单轮生成的可变状态：flow 被取消后 onCompletion 仍要读取，
 * 所以声明在 flow 作用域之外，避免误伤下一轮刚插入的用户消息。
 */
private class ReplyTurnState {
    var userMessageId: Long? = null
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
    private val emotionEventDao: com.xiaoyu.app.data.db.EmotionEventDao,
    private val correctionDao: com.xiaoyu.app.data.db.CorrectionDao,
    private val safetyEventDao: SafetyEventDao,
    @ApplicationContext private val appContext: Context,
) {
    private val staleSessionMillis = 30 * 60 * 1000L
    /** AI 发表情冷却（执行书 7.4.7），跨会话共享。 */
    private val stickerCooldown = StickerCooldown(4)
    /** v3 §4.4：连续超长回复的自适应反馈，跨会话共享。 */
    private val lengthGuard = LengthGuard()
    /** 正在生成的用户轮次：撤回时取消对应收集协程，避免继续落库。 */
    private val activeGenerations = ConcurrentHashMap<Long, Job>()

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

    /** 会话内切换人设：只改该会话的绑定，历史消息不动，下一条回复开始用新人设。 */
    suspend fun switchIdentity(sessionId: Long, identityId: Long) {
        if (personaRepo.identity(identityId) == null) return
        sessionDao.setIdentity(sessionId, identityId)
    }

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
        // 正文已清空，同步删掉全文索引，避免撤回内容还能被搜索命中
        runCatching { messageDao.deleteFts(messageId) }
        activeGenerations.remove(messageId)?.let { job ->
            job.cancel(CancellationException("用户消息已撤回，本轮生成已取消"))
            // 取消前已经落下的半截分段也一并收起，只保留 hidden=1 的历史数据
            val visible = messageDao.visibleForSession(msg.sessionId)
            hideRepliesAfter(visible, visible.indexOfFirst { it.id == messageId })
        }
        return true
    }

    /**
     * 编辑重发（M3-07）：更新正文 + 标记 edited_at，然后基于新文本再触发一轮回复。
     * 语义选择：编辑后这条消息会排到时间线末尾，因此把它之后、下一条用户消息之前的
     * 她的回复整段软隐藏（只改 hidden），避免出现「她先答、你再问」的倒序；不删任何数据。
     * 若后面已经有新的用户消息，挪到末尾会打乱整段时间线，此时直接拒绝并说明原因。
     */
    suspend fun editAndResend(sessionId: Long, messageId: Long, newText: String): Flow<ChatEvent> {
        val visible = messageDao.visibleForSession(sessionId)
        val index = visible.indexOfFirst { it.id == messageId }
        if (index >= 0 && visible.drop(index + 1).any { it.role == ROLE_USER }) {
            return flow {
                emit(ChatEvent.Failed(appContext.getString(R.string.data_chat_edit_blocked), ""))
            }
        }
        val now = System.currentTimeMillis()
        hideRepliesAfter(visible, index)
        messageDao.updateContent(messageId, XyTokenParser.escapeUserText(newText.trim()), now)
        // 复用手上这条用户消息并把它排回时间线末尾，避免同一句话再插一条导致 history 里重复
        messageDao.updateCreatedAt(messageId, now)
        reindexMessage(messageId)
        // 与 send 共用安全判定和回复前置管线，编辑后的危机文本不能绕过 L3
        return sendInternal(sessionId, newText, existingUserMessageId = messageId)
    }

    /** 软隐藏某条用户消息之后、下一条用户消息之前的 assistant 回复（编辑重发用）。 */
    private suspend fun hideRepliesAfter(visible: List<MessageEntity>, index: Int) {
        if (index < 0) return
        val ids = visible.drop(index + 1)
            .takeWhile { it.role != ROLE_USER }
            .filter { it.role == ROLE_ASSISTANT }
            .map { it.id }
        if (ids.isNotEmpty()) messageDao.hideBatch(ids)
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
        val now = System.currentTimeMillis()
        // 上次失败可能已落下可见的半截分段，重发前先收起旧轮，避免与新回复并存
        val visible = messageDao.visibleForSession(sessionId)
        hideRepliesAfter(visible, visible.indexOfFirst { it.id == messageId })
        messageDao.updateStatus(messageId, STATUS_SENDING)
        // 复用这条已存在的用户消息并排回时间线末尾，避免重发时同一句话在库里出现两次、history 里也重复
        messageDao.updateCreatedAt(messageId, now)
        return replyFlow(sessionId, msg.content, continueMode = false, existingUserMessageId = messageId)
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

    /** 主动聊天落库：作为她的消息出现在会话里。总闸由调用方（Worker / 开场白）先判定。 */
    suspend fun saveProactiveMessage(sessionId: Long, text: String) {
        persistAssistant(sessionId, text)
    }

    /** v3 §8.2：主动消息总闸——L2/L3 后 48 小时内不再主动打扰。 */
    fun isProactivePaused(now: Long = System.currentTimeMillis()): Boolean =
        now < settings.current().proactivePausedUntilMs

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

    suspend fun send(sessionId: Long, text: String, transientContext: String? = null, quoteToId: Long? = null): Flow<ChatEvent> =
        sendInternal(sessionId, text, transientContext, quoteToId)

    /** 新发送与编辑重发共用的安全前置管线。 */
    private suspend fun sendInternal(
        sessionId: Long,
        text: String,
        transientContext: String? = null,
        quoteToId: Long? = null,
        existingUserMessageId: Long? = null,
    ): Flow<ChatEvent> {
        val session = sessionDao.byId(sessionId) ?: error("会话不存在")
        if (session.endedAt != null) sessionDao.reopen(sessionId)
        val content = text.trim()

        // v3 §8.1：send() 第 1 步永远是安全判定，先于一切灵魂逻辑
        val verdict = DefaultSafetyGate.default.assess(content)
        if (verdict.level != CrisisLevel.NONE) {
            recordSafetyEvent(sessionId, verdict.level.name, verdict.matched, actionOf(verdict.level))
        }

        if (verdict.level == CrisisLevel.L3_IMMINENT) {
            // v3 §8.2：暂停人格演绎 + 暂停主动消息 + 会话标记 safety（但绝不结束对话）
            pauseProactive()
            return imminentFlow(sessionId, content, verdict, existingUserMessageId)
        }

        var directive: String? = when (verdict.level) {
            CrisisLevel.L2_SELF_HARM_IDEA -> {
                pauseProactive()
                SafetyTexts.L2_DIRECTIVE
            }
            CrisisLevel.L1_DISTRESS -> SafetyTexts.L1_DIRECTIVE
            else -> null
        }
        // L3 之后的 3 轮：L8 追加「保持现实导向」（危机时永不结束对话）
        realityDirective(sessionId, session.identityId)?.let { extra ->
            directive = listOfNotNull(directive, extra).joinToString("\n\n")
        }

        // v3 H-9 / 4.10.3：情绪归因——检测触发词并记录事件（半衰期衰减 + 提起回升）
        recordEmotionIfAny(sessionId, content)
        // v3 H-11 / 4.11：纠偏检测——用户说「不像她」时落一条可撤销的风格补丁
        recordCorrectionIfAny(sessionId, content)

        val reply = replyFlow(
            sessionId = sessionId,
            userText = content,
            continueMode = false,
            transientContext = transientContext,
            quoteToId = quoteToId,
            extraDirective = directive,
            existingUserMessageId = existingUserMessageId,
        )
        // L2：会话内首次出现求助资源卡，放在她的回复下方（可关）
        val showCareCard = verdict.level == CrisisLevel.L2_SELF_HARM_IDEA &&
            messageDao.countCareCards(sessionId) == 0
        if (!showCareCard) return reply
        return flow {
            reply.collect { emit(it) }
            insertCareCard(sessionId, verdict.zhHelp)
        }
    }

    /** v3 §8.2：L2/L3 后主动消息总闸暂停 48 小时（设置页可手动提前解除）。 */
    private suspend fun pauseProactive() {
        settings.update {
            it.copy(proactivePausedUntilMs = System.currentTimeMillis() + PROACTIVE_PAUSE_AFTER_CRISIS_MS)
        }
    }

    /** L3 之后 3 轮内的 L8 现实导向指令；超出轮数返回 null。 */
    private suspend fun realityDirective(sessionId: Long, identityId: Long): String? {
        val latest = safetyEventDao.latest(identityId, CrisisLevel.L3_IMMINENT.name) ?: return null
        val turnsSince = messageDao.countUserSince(sessionId, latest.createdAtMs)
        return if (turnsSince in 1..L3_REALITY_ROUNDS) SafetyTexts.L3_REALITY_DIRECTIVE else null
    }

    private fun actionOf(level: CrisisLevel): String = when (level) {
        CrisisLevel.L1_DISTRESS -> "l1_slow_down"
        CrisisLevel.L2_SELF_HARM_IDEA -> "l2_care_card"
        CrisisLevel.L3_IMMINENT -> "l3_persona_paused"
        CrisisLevel.NONE -> "none"
    }

    /** 安全事件只存级别、命中词与动作，绝不存用户原文。 */
    private suspend fun recordSafetyEvent(
        sessionId: Long,
        level: String,
        matched: List<String>,
        action: String,
    ) {
        val identityId = sessionDao.byId(sessionId)?.identityId ?: 0L
        safetyEventDao.insert(
            SafetyEventEntity(
                identityId = identityId,
                level = level,
                matchedJson = JSONArray(matched).toString(),
                createdAtMs = System.currentTimeMillis(),
                action = action,
            )
        )
    }

    /** 求助资源卡：关怀文案 + 热线号码 + 拨号按钮（v3 §8.2）。 */
    private suspend fun insertCareCard(
        sessionId: Long,
        help: List<HelpResource>,
        createdAt: Long = System.currentTimeMillis(),
    ) {
        insertMessage(
            MessageEntity(
                sessionId = sessionId,
                role = ROLE_ASSISTANT,
                content = CardFactory.careCard(help),
                createdAt = createdAt,
                type = TYPE_CARD,
                status = STATUS_SENT,
            )
        )
    }

    /** v3 H-9：命中情绪触发词则落一条 emotion_events，强度半衰期衰减，再次提及时回升。 */
    private suspend fun recordEmotionIfAny(sessionId: Long, text: String) {
        val detection = EmotionAttributor.detect(text) ?: return
        val now = System.currentTimeMillis()
        emotionEventDao.insert(
            EmotionEventEntity(
                emotion = detection.emotion,
                trigger = detection.trigger,
                intensity = detection.intensity,
                createdAt = now,
                lastMentionAt = now,
                sessionId = sessionId,
            )
        )
    }

    /** v3 H-11：命中纠偏触发词则落一条 corrections，最近一条未撤销的会注入 prompt。 */
    private suspend fun recordCorrectionIfAny(sessionId: Long, text: String) {
        val correction = CorrectionEngine.detect(text) ?: return
        // v3 §8.3：纠偏 note 是用户输入进 L2b，命中注入模式即丢弃
        val patch = InjectionGuard.sanitizeForeign(correction.patch, PromptZone.CORRECTION_NOTE)
        if (patch.isBlank()) return
        val now = System.currentTimeMillis()
        correctionDao.insert(
            CorrectionEntity(
                intent = correction.intent.name,
                patch = patch,
                trigger = correction.trigger,
                createdAt = now,
                revoked = false,
                sessionId = sessionId,
            )
        )
    }

    /** v3 H-11：观察最近一条未撤销的纠偏（供 UI 展示纠偏条）。 */
    fun latestCorrection(): kotlinx.coroutines.flow.Flow<CorrectionEntity?> =
        correctionDao.observeLatestActive()

    /** v3 H-11：撤销最近一条纠偏（用户点击「撤销」）。 */
    suspend fun revokeLatestCorrection() {
        correctionDao.latestActive()?.let { correctionDao.revoke(it.id) }
    }

    /**
     * v3 §8.2 L3：暂停人格演绎——固定现实导向回复 + 热线卡，不调用模型、不触发收束，
     * 也绝不结束会话（只把 closed_by 标成 safety 供后续分析）。
     */
    private fun imminentFlow(
        sessionId: Long,
        userText: String,
        verdict: SafetyVerdict,
        existingUserMessageId: Long? = null,
    ): Flow<ChatEvent> = flow {
        val now = System.currentTimeMillis()
        val userMessageId = if (existingUserMessageId == null) {
            insertMessage(
                MessageEntity(
                    sessionId = sessionId,
                    role = ROLE_USER,
                    content = XyTokenParser.escapeUserText(userText),
                    createdAt = now,
                    status = STATUS_SENT,
                )
            )
        } else {
            messageDao.updateStatus(existingUserMessageId, STATUS_SENT)
            existingUserMessageId
        }
        currentCoroutineContext()[Job]?.let { job -> activeGenerations[userMessageId] = job }
        try {
            // 撤回可能发生在安全判定之后：落回复前再确认一次
            ensureUserNotRetracted(userMessageId)
            insertMessage(
                MessageEntity(
                    sessionId = sessionId,
                    role = ROLE_ASSISTANT,
                    content = SafetyTexts.IMMINENT_REPLY,
                    createdAt = now + 1,
                    status = STATUS_SENT,
                )
            )
            insertCareCard(sessionId, verdict.zhHelp, now + 2)
            sessionDao.markClosedBy(sessionId, "safety")
            sessionDao.touch(sessionId, now + 2)
            emit(ChatEvent.Completed)
        } finally {
            activeGenerations.remove(userMessageId)
            // 竞态兜底：撤回后晚到的回复也要收起（只改 hidden，不删数据）
            withContext(NonCancellable) {
                if (messageDao.byId(userMessageId)?.retracted == true) {
                    val visible = messageDao.visibleForSession(sessionId)
                    hideRepliesAfter(visible, visible.indexOfFirst { it.id == userMessageId })
                }
            }
        }
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
     * 重新生成（执行书 6.3.6）：同族旧版本整族软隐藏，复用当次请求的 prompt 快照重来一次。
     * 新旧版本共享 version_family_id，供「查看之前的版本」按轮切换。
     * 若本轮生成失败 / 被取消，回滚成生成前的可见版本，保证永远能看到上一条有效回复。
     */
    suspend fun regenerate(sessionId: Long, messageId: Long): Flow<ChatEvent> = flow {
        val target = messageDao.byId(messageId)
        val familyId = target?.let {
            it.versionFamilyId ?: it.replyGroupId ?: UUID.randomUUID().toString()
        }
        if (target != null && target.versionFamilyId == null && familyId != null) {
            // 兼容旧消息：给旧轮所有分段补上与新版本共同的版本族，不改表结构
            messageDao.assignVersionFamily(target.id, target.replyGroupId, familyId)
        }
        // 记录生成前可见的版本，失败时用于恢复
        val previousVisible = if (familyId != null) {
            messageDao.visibleInFamily(familyId)
        } else {
            listOfNotNull(target)
        }
        // 整族收起（只改 hidden），避免切换版本时出现两条同时可见的回复
        if (familyId != null) {
            messageDao.hideFamily(familyId)
        } else {
            messageDao.hide(messageId)
        }
        // 预先分配本轮 id，失败时才能精确回滚这一轮的半截分段
        val roundId = UUID.randomUUID().toString()
        val snapshot = findSnapshot(sessionId, target?.content.orEmpty())
        var succeeded = false
        try {
            replyFlow(
                sessionId = sessionId,
                userText = null,
                continueMode = false,
                extraDirective = "请换一种说法重新回答上一条，不要重复刚才的表达。",
                versionFamilyId = familyId,
                roundId = roundId,
                reuseSnapshot = snapshot,
            ).collect { event ->
                if (event is ChatEvent.Completed) succeeded = true
                emit(event)
            }
        } finally {
            if (!succeeded) {
                withContext(NonCancellable) {
                    // 生成失败 / 无内容 / 被取消：新版本可能只落了半截分段，整轮收起后恢复旧版本
                    messageDao.hideGroup(roundId)
                    previousVisible.ifEmpty { listOfNotNull(target) }
                        .forEach { messageDao.unhide(it.id) }
                }
            }
        }
    }

    /**
     * 同族的全部生成版本（含已隐藏），按轮聚合后每轮取第一条文本分段做代表。
     * 分段发送时同一轮的多个气泡共享 reply_group_id，不能把每个分段都当成一个版本。
     */
    suspend fun versions(messageId: Long): List<MessageEntity> {
        val message = messageDao.byId(messageId) ?: return emptyList()
        val family = message.versionFamilyId ?: message.replyGroupId ?: return listOf(message)
        return messageDao.assistantVersions(family)
            .filter { it.type == TYPE_TEXT }
            .groupBy { it.replyGroupId ?: "solo-${it.id}" }
            .values
            .map { round -> round.minBy { it.createdAt } }
            .sortedBy { it.createdAt }
    }

    /** 切换到某一历史版本：整族收起后整轮放亮（同轮的其它分段和表情一起回来）。 */
    suspend fun switchVersion(groupId: String, messageId: Long) {
        val target = messageDao.byId(messageId) ?: return
        val family = target.versionFamilyId ?: target.replyGroupId ?: groupId
        messageDao.hideFamily(family)
        val round = target.replyGroupId
        if (round != null) messageDao.unhideRound(round) else messageDao.unhide(messageId)
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

    /**
     * v3 §7.4.6：被夸 / 被道歉时，她有 30% 概率给你的消息加一个 reaction。
     * 纯本地规则，不请求模型；与她回礼的 reaction 共用同一张 Settings 映射表。
     */
    private suspend fun maybeReactToUserMessage(messageId: Long, text: String) {
        val matched = (PRAISE_HINTS + APOLOGY_HINTS).any { text.contains(it) }
        if (!matched) return
        if (Random.nextInt(100) >= HER_REACTION_TO_USER_PERCENT) return
        val herEmoji = HER_REACTION_POOL.random()
        settings.update { it.copy(herReactions = it.herReactions + (messageId.toString() to herEmoji)) }
    }

    private suspend fun replyFlow(
        sessionId: Long,
        userText: String?,
        continueMode: Boolean,
        transientContext: String? = null,
        extraDirective: String? = null,
        quoteToId: Long? = null,
        /** 重新生成时与旧版本共享的版本家族 id（M3-06）；为空表示这条回复只有一个版本。 */
        versionFamilyId: String? = null,
        /** 重新生成时预先分配的本轮 id，便于失败时精确回滚这一轮。 */
        roundId: String? = null,
        /** 重新生成时复用的请求快照（M3-06）。 */
        reuseSnapshot: RequestSnapshotEntity? = null,
        /** 重发 / 编辑重发时复用已有的用户消息 id，避免同一句用户消息在库里出现两次。 */
        existingUserMessageId: Long? = null,
        turnState: ReplyTurnState = ReplyTurnState(),
    ): Flow<ChatEvent> = flow {
        val session = sessionDao.byId(sessionId) ?: throw IllegalStateException("会话不存在")
        stickerCooldown.onTurnStarted()
        val now = System.currentTimeMillis()
        val directives = mutableListOf<String>()

        // v3 §4.4：连续超长回复累计 3 次后，本轮追加长度约束
        lengthGuard.directive()?.let { directives += it }

        if (userText != null) {
            turnState.userMessageId = existingUserMessageId ?: insertMessage(
                MessageEntity(
                    sessionId = sessionId,
                    role = ROLE_USER,
                    // 用户消息里的 [xy:*] 一律转义，防止伪造协议（3.3.1）
                    content = XyTokenParser.escapeUserText(userText),
                    createdAt = now,
                    // 自己发的消息一落库就算「已发送」：真人聊天里对方还没回，
                    // 也不会一直把你的消息显示成「发送中」
                    status = STATUS_SENT,
                    quoteToId = quoteToId,
                )
            )
            turnState.userMessageId?.let { id ->
                currentCoroutineContext()[Job]?.let { job -> activeGenerations[id] = job }
            }
            sessionDao.touch(sessionId, now)
            autoTitle(sessionId, session.title, userText)
            // v3 §7.4.6：被夸 / 被道歉时，她可能给你的消息补一个 reaction（本地规则）
            turnState.userMessageId?.let { maybeReactToUserMessage(it, userText) }
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

        // v3 H-11 / 4.11：纠偏风格补丁——最近一条未撤销的纠偏会注入本轮 prompt
        correctionDao.latestActive()?.let { corr ->
            // v3 §8.3：注入前再扫一次，命中即丢弃（落库时已扫过，防历史数据）
            val patch = InjectionGuard.sanitizeForeign(corr.patch, PromptZone.CORRECTION_NOTE)
            if (patch.isNotBlank()) directives += "[风格纠偏] $patch"
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
        val hour = LocalTime.now().hour
        val config = settings.current()
        if (config.segmentSending) directives += SEGMENT_DIRECTIVE
        val replyGroupId = roundId ?: UUID.randomUUID().toString()
        // v3 §4.14 / H-10：深夜 + 低精力时自然收束，说晚安结束本轮（不依赖沉浸模式开关）
        // 一晚只说一次：她刚道过晚安就不要再直出同一句，继续聊时交给模型按低精力自然回应
        val lastAssistant = messageDao.latestAssistantForSession(sessionId)
        val alreadySaidGoodNight = lastAssistant != null &&
            now - lastAssistant.createdAt < SLEEPY_REPEAT_WINDOW_MS &&
            ReplyPacer.isSleepyLine(lastAssistant.content)
        if (!alreadySaidGoodNight && ReplyPacer.shouldSaySleepy(hour, mood.energy, Random.Default)) {
            val line = ReplyPacer.sleepyLine(Random.Default, avoid = lastAssistant?.content.orEmpty())
            ensureUserNotRetracted(turnState.userMessageId)
            // 重新生成时也要挂在同一版本族下，否则旧版本会被藏起来又切不回去
            persistAssistant(
                sessionId = sessionId,
                content = line,
                replyGroupId = if (versionFamilyId != null) replyGroupId else null,
                versionFamilyId = versionFamilyId,
            )
            // 这条分支也必须走完正常收尾：用户消息不能一直停在「发送中」
            turnState.userMessageId?.let { messageDao.updateStatus(it, STATUS_SENT) }
            stateRepo.drainEnergy(1)
            scheduleIncrementalExtractionIfDue(sessionId)
            emit(ChatEvent.Delta(line))
            emit(ChatEvent.Completed)
            return@flow
        }

        // 只有确定要请求模型后才消耗记忆复习次数和生活碎片，晚安直出不做这些副作用
        val memories = if (userText != null) memoryRepo.recallForChat(userText) else emptyList()
        memoryRepo.markRecalled(memories.map { it.id })
        val cards = memoryRepo.cards()
        val life = stateRepo.freshLifeForChat(3)
        val historyEntities = messageDao.latestForSession(sessionId, 24).reversed()
        val historyStickerNames = stickerRepo.namesFor(
            historyEntities.mapNotNull { it.stickerId }
        )
        val history = historyEntities.map { it.toSoulMessage(historyStickerNames[it.stickerId]) }
        // L3 表情目录：空表时为空，PromptAssembler 会整段省略（7.4.2）
        // v3 §12.9 / H-23：默认 none，用户开启后才注入表情目录
        val stickerCatalog = if (settings.current().stickersEnabled) stickerRepo.catalogForPrompt(40) else emptyList()
        if (config.immersiveEnabled) {
            // 停顿按「读你这句 + 组织语言」估算，落在 0.6~9 秒；不再有十分钟级的等待
            val delayMs = ReplyPacer.delayMillis(
                intensity = config.immersiveIntensity,
                energy = mood.energy,
                hourOfDay = hour,
                random = Random.Default,
                userTextLength = userText?.length ?: 20,
            )
            emit(ChatEvent.Typing(delayMs))
            delay(delayMs)
        } else {
            emit(ChatEvent.Typing(0))
        }

        val segmentSending = config.segmentSending
        var attempt = 0
        var persisted = 0
        var failure: BrainException? = null
        var lastVisible = ""
        val pendingStickers = mutableListOf<String>()
        val generatedStickers = mutableListOf<String>()
        var stickerSentThisAttempt = false
        val pendingSignals = mutableListOf<String?>()
        /** 模型本轮是否打了 `[xy:memo]` 标记（PRD 5.2），回复落库后再排队提炼。 */
        var memoRequested = false

        /** 一段文字落库后再落表情，保证气泡顺序是「先说话，后甩表情」。 */
        suspend fun flushStickers() {
            if (pendingStickers.isEmpty()) return
            for (id in pendingStickers) {
                if (!stickerCooldown.canSend() || stickerSentThisAttempt) {
                    Log.d(TAG, "表情冷却中，已丢弃: $id")
                    continue
                }
                ensureUserNotRetracted(turnState.userMessageId)
                persistSticker(sessionId, id, replyGroupId, versionFamilyId, touchSession = false)
                generatedStickers += id
                stickerSentThisAttempt = true
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
                    ensureUserNotRetracted(turnState.userMessageId)
                    persistAssistant(sessionId, content, replyGroupId, versionFamilyId, touchSession = false)
                    persisted += 1
                    flushStickers()
                    emit(ChatEvent.SegmentBreak)
                    if (segmentSending) {
                        // 连发之间的停顿：像真人打完一句、停一下再补一句；
                        // 关掉回复节奏时保留一个固定的短停顿，不做长时间等待
                        val gap = if (config.immersiveEnabled) {
                            ReplyPacer.segmentGapMillis(config.immersiveIntensity, hour, Random.Default)
                        } else {
                            600L
                        }
                        delay(gap)
                    }
                }
                is PostEvent.Sticker -> {
                    val sticker = stickerRepo.resolve(ev.stickerId, stickerCatalog)
                    when {
                        sticker == null -> Log.d(TAG, "表情编号无效，已忽略: ${ev.stickerId}")
                        pendingStickers.isNotEmpty() -> Log.d(TAG, "本轮已有表情，忽略多余: ${sticker.name}")
                        else -> pendingStickers += sticker.id
                    }
                }
                is PostEvent.Signal -> pendingSignals += ev.emotion
                is PostEvent.Action -> actions += ev.text
                PostEvent.Memo -> memoRequested = true
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 读流被切断、连接重置这类底层异常以前会直接漏到 UI 兜底分支，
                // 界面上只剩一句笼统的「消息发送失败」，半截气泡也一直挂着光标。
                // 统一收成本轮失败，走和模型报错一样的收尾流程。
                Log.w(TAG, "回复流异常中断", e)
                failure = BrainException(
                    message = e.message ?: e::class.java.simpleName,
                    userMessage = appContext.getString(R.string.data_chat_network_lost),
                    role = BrainRole.CHAT,
                    cause = e,
                )
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
                // 第 0 次尝试已经按段落落库的文本必须先软删，否则重试成功后同一句话会出现两次
                runCatching { messageDao.hideGroup(replyGroupId) }
                    .onFailure { Log.w(TAG, "回滚拦截段落失败: ${it.message}") }
                persisted = 0
                memoRequested = false
                pendingSignals.clear()
                generatedStickers.clear()
                stickerSentThisAttempt = false
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

        // 只有本轮最终留下可见回复，才统一提交情绪、表情和会话副作用
        if (persisted > 0) {
            pendingSignals.forEach { applySignal(it) }
            generatedStickers.forEach { stickerRepo.recordUse(it) }
            if (generatedStickers.isNotEmpty()) stickerCooldown.markSent()
            sessionDao.touch(sessionId, System.currentTimeMillis())
            stateRepo.drainEnergy(1)

            // v3 §4.4：记录本轮回复长度，连续超长则下一轮追加约束
            if (failure == null) {
                lengthGuard.record(lastVisible.length, lastVisible.contains("|||"))
            }
        }

        when {
            failure != null -> {
                turnState.userMessageId?.let { messageDao.updateStatus(it, STATUS_FAILED) }
                emit(ChatEvent.Failed(failure.userMessage, lastVisible))
            }
            persisted == 0 -> {
                turnState.userMessageId?.let { messageDao.updateStatus(it, STATUS_FAILED) }
                emit(
                    ChatEvent.Failed(
                        appContext.getString(R.string.data_chat_reply_empty),
                        "",
                    )
                )
            }
            else -> {
                turnState.userMessageId?.let { messageDao.updateStatus(it, STATUS_SENT) }
                emit(ChatEvent.Completed)
            }
        }

        // 等本轮回复落库后再排队提炼，避免 Worker 读不到刚生成的那条回复
        if (memoRequested && failure == null && persisted > 0) {
            Log.d(TAG, "模型标记了值得记住的内容，已排入记忆提炼: session=$sessionId")
            scheduler.scheduleExtraction(sessionId)
        }

        // v3 H-14：会话内增量提炼——未提炼消息 ≥60 条，或 ≥4 小时且 ≥10 条时触发
        if (failure == null && persisted > 0) {
            scheduleIncrementalExtractionIfDue(sessionId)
        }
    }.onCompletion { cause ->
        turnState.userMessageId?.let { activeGenerations.remove(it) }
        if (cause == null) return@onCompletion
        // 取消 / 异常中断也要把本轮的用户消息落定，不能永远停在「发送中」（P1-2）
        val userMessageId = turnState.userMessageId
        if (userMessageId != null) {
            withContext(NonCancellable) { settleInterruptedTurn(userMessageId) }
        }
    }

    /** 每次 assistant 落库前复核撤回状态；取消信号由 retract 同时发出。 */
    private suspend fun ensureUserNotRetracted(userMessageId: Long?) {
        val id = userMessageId ?: return
        if (messageDao.byId(id)?.retracted == true) {
            throw CancellationException("用户消息已撤回，停止回复落库")
        }
    }

    /**
     * 生成被取消 / 异常中断时的兜底：
     * 本轮已经落下她的回复就置为已发送，否则置为失败（UI 可重发）。
     * 已落库的 assistant 分段保留可见，与生成失败（BrainException）时的现有语义一致。
     */
    private suspend fun settleInterruptedTurn(userMessageId: Long) {
        val pending = messageDao.byId(userMessageId) ?: return
        if (pending.retracted) {
            // 再次兜住「落库前检查后、写入完成前撤回」的竞态
            val visible = messageDao.visibleForSession(pending.sessionId)
            hideRepliesAfter(visible, visible.indexOfFirst { it.id == userMessageId })
            if (pending.status == STATUS_SENDING) messageDao.updateStatus(userMessageId, STATUS_SENT)
            return
        }
        if (pending.status != STATUS_SENDING) return
        val replied = messageDao.countAssistantSince(pending.sessionId, pending.createdAt) > 0
        messageDao.updateStatus(userMessageId, if (replied) STATUS_SENT else STATUS_FAILED)
    }

    /** 增量提炼触发判定（v3 H-14）：≥60 条或 ≥4h 且 ≥10 条未提炼消息。 */
    private suspend fun scheduleIncrementalExtractionIfDue(sessionId: Long) {
        val session = sessionDao.byId(sessionId) ?: return
        if (session.endedAt != null) return
        val unextracted = messageDao.countSince(sessionId, session.extractCursor)
        val hoursSinceCursor =
            (System.currentTimeMillis() - session.extractCursor) / 3_600_000L
        val due = unextracted >= 60 || (hoursSinceCursor >= 4 && unextracted >= 10)
        if (due) {
            Log.d(TAG, "增量提炼触发: session=$sessionId unextracted=$unextracted hours=$hoursSinceCursor")
            scheduler.scheduleExtraction(sessionId)
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

    private suspend fun persistAssistant(
        sessionId: Long,
        content: String,
        replyGroupId: String? = null,
        versionFamilyId: String? = null,
        touchSession: Boolean = true,
    ) {
        // v3 §8.1：assistant 文本入库前过输出闸门；命中伤害性表达就换成 care 兜底并记 event
        val screen = DefaultSafetyGate.default.screenOutput(content)
        val finalContent = if (screen.blocked) {
            recordSafetyEvent(sessionId, "OUTPUT_BLOCKED", screen.hits, "output_blocked")
            screen.fallback ?: SafetyTexts.CARE_FALLBACK
        } else {
            content
        }
        insertMessage(
            MessageEntity(
                sessionId = sessionId,
                role = ROLE_ASSISTANT,
                content = finalContent,
                createdAt = System.currentTimeMillis(),
                replyGroupId = replyGroupId,
                versionFamilyId = versionFamilyId,
            )
        )
        if (touchSession) sessionDao.touch(sessionId, System.currentTimeMillis())
    }

    /** 表情消息：content 为空，type=sticker（执行书 7.4.4）。 */
    private suspend fun persistSticker(
        sessionId: Long,
        stickerId: String,
        replyGroupId: String? = null,
        versionFamilyId: String? = null,
        touchSession: Boolean = true,
    ) {
        val now = System.currentTimeMillis()
        insertMessage(
            MessageEntity(
                sessionId = sessionId,
                role = ROLE_ASSISTANT,
                content = "",
                createdAt = now,
                replyGroupId = replyGroupId,
                versionFamilyId = versionFamilyId,
                type = TYPE_STICKER,
                stickerId = stickerId,
            )
        )
        if (touchSession) sessionDao.touch(sessionId, now)
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
