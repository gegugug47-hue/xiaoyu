package com.xiaoyu.app.ui

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaoyu.app.R
import com.xiaoyu.app.data.prompt.PromptAssetSource
import com.xiaoyu.app.data.backup.BackupImporter
import com.xiaoyu.app.data.backup.BackupRepository
import com.xiaoyu.app.data.export.MarkdownExporter
import com.xiaoyu.app.data.export.PublicDownloads
import com.xiaoyu.app.data.persona.PersonaPackArchive
import com.xiaoyu.app.data.sticker.StickerPackArchive
import com.xiaoyu.app.data.repo.VisionRepository
import com.xiaoyu.soul.prompt.PromptKeys
import com.xiaoyu.app.data.brain.BrainRouter
import com.xiaoyu.app.data.brain.ProviderHealth
import com.xiaoyu.app.data.db.DiaryEntity
import com.xiaoyu.app.data.db.FactCardEntity
import com.xiaoyu.app.data.db.IdentityEntity
import com.xiaoyu.app.data.db.LifeFragmentEntity
import com.xiaoyu.app.data.db.MemoryEntity
import com.xiaoyu.app.data.db.MessageEntity
import com.xiaoyu.app.data.db.RequestSnapshotEntity
import com.xiaoyu.app.data.db.SessionEntity
import com.xiaoyu.app.data.db.StickerEntity
import com.xiaoyu.app.data.db.StickerPackEntity
import com.xiaoyu.app.data.db.StyleEntity
import com.xiaoyu.app.data.media.ChatBackgroundStore
import com.xiaoyu.app.data.repo.BackgroundRenderInput
import com.xiaoyu.app.data.repo.ChatBackgroundRepository
import com.xiaoyu.app.data.settings.ChatBackgroundSpec
import com.xiaoyu.app.data.peripheral.LocalAiRepository
import com.xiaoyu.app.data.repo.ChatEvent
import com.xiaoyu.app.data.repo.ChatRepository
import com.xiaoyu.app.data.repo.DraftRepository
import com.xiaoyu.app.data.repo.DiagnosticsRepository
import com.xiaoyu.app.data.repo.MemoryRepository
import com.xiaoyu.app.data.repo.PersonaRepository
import com.xiaoyu.app.data.repo.RelationRepository
import com.xiaoyu.app.data.repo.RelationSummary
import com.xiaoyu.app.data.repo.SoulStateRepository
import com.xiaoyu.app.data.repo.StickerRepository
import com.xiaoyu.app.data.secure.AppLockManager
import com.xiaoyu.app.data.settings.AppLockMode
import com.xiaoyu.app.data.settings.AppSettings
import com.xiaoyu.app.data.settings.BrainSettings
import com.xiaoyu.app.data.settings.ChatMode
import com.xiaoyu.app.data.settings.CostGuardState
import com.xiaoyu.app.data.settings.MessageDensity
import com.xiaoyu.app.data.settings.SettingsRepository
import com.xiaoyu.soul.core.BrainRole
import com.xiaoyu.soul.memory.MemoryType
import com.xiaoyu.soul.mood.FavorabilityLevel
import com.xiaoyu.soul.mood.PresenceEngine
import com.xiaoyu.soul.mood.PresenceResult
import com.xiaoyu.soul.mood.PresenceStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/** 连戳上限（执行书 6.3.7）：戳到第 10 次她会直接说「别戳啦」。 */
private const val POKE_DAILY_LIMIT = 10

/** 戳一下的频控：3 秒内的连点只算一次。 */
private const val POKE_COOLDOWN_MS = 3000L

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AppViewModel @Inject constructor(
    private val settingsRepo: SettingsRepository,
    private val chatRepo: ChatRepository,
    private val personaRepo: PersonaRepository,
    private val memoryRepo: MemoryRepository,
    private val stateRepo: SoulStateRepository,
    private val router: BrainRouter,
    private val backupRepo: BackupRepository,
    private val backgroundStore: ChatBackgroundStore,
    private val chatBackgroundRepo: ChatBackgroundRepository,
    private val localAiRepo: LocalAiRepository,
    private val stickerRepo: StickerRepository,
    private val relationRepo: RelationRepository,
    private val diagnosticsRepo: DiagnosticsRepository,
    private val appLock: AppLockManager,
    private val draftRepo: DraftRepository,
    private val promptSource: PromptAssetSource,
    private val backupImporter: BackupImporter,
    private val markdownExporter: MarkdownExporter,
    private val personaPackArchive: PersonaPackArchive,
    private val stickerPackArchive: StickerPackArchive,
    private val visionRepo: VisionRepository,
    private val publicDownloads: PublicDownloads,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: Context,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepo.state

    /** 她回给你的 reaction（M3-05）：messageId -> emoji，存在 Settings JSON 里，不动表结构。 */
    val herReactions: StateFlow<Map<String, String>> = settings
        .map { it.herReactions }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** 成本护栏状态（执行书 4.6），设置页与聊天页横幅共用。 */
    val costGuard: StateFlow<CostGuardState> = settingsRepo.state
        .map { settingsRepo.guardState() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CostGuardState())

    /** 诊断快照（执行书 6.13），只保留最近 50 次。 */
    val diagnostics: StateFlow<List<RequestSnapshotEntity>> = diagnosticsRepo.observeRecent(50)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 关系页数据（执行书 6.5），进入页面时刷新。 */
    private val _relation = MutableStateFlow<RelationSummary?>(null)
    val relation: StateFlow<RelationSummary?> = _relation

    /** 表情面板（执行书 7.4）：内置小鱼 + 用户表情。 */
    val stickers: StateFlow<List<StickerEntity>> = stickerRepo.observeStickers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 表情面板的分包 tab 数据源（内置包 + 用户导入包）。 */
    val stickerPacks: StateFlow<List<StickerPackEntity>> = stickerRepo.observePacks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val sessions: StateFlow<List<SessionEntity>> = chatRepo.observeSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val archivedSessions: StateFlow<List<SessionEntity>> = chatRepo.observeArchivedSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val imageMessages: StateFlow<List<MessageEntity>> = chatRepo.observeImageMessages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val identities: StateFlow<List<IdentityEntity>> = personaRepo.observeIdentities()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val styles: StateFlow<List<StyleEntity>> = personaRepo.observeStyles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val shortMemories: StateFlow<List<MemoryEntity>> = memoryRepo.observeShortTerm()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val binMemories: StateFlow<List<MemoryEntity>> = memoryRepo.observeBin()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** v3 H-12 / 7.6：「她的一天」——今天产生的生活碎片时间线。 */
    val todayLife: StateFlow<List<LifeFragmentEntity>> = run {
        val todayStart = java.time.ZonedDateTime.now()
            .toLocalDate().atStartOfDay(java.time.ZoneId.systemDefault())
            .toInstant().toEpochMilli()
        stateRepo.observeLifeSince(todayStart)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    }

    val factCards: StateFlow<List<FactCardEntity>> = memoryRepo.observeCards()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val diaries: StateFlow<List<DiaryEntity>> = stateRepo.observeDiaries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favorability: StateFlow<Int> = stateRepo.observeFavorability()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** 聊天形态：连续流 / 会话列表（PRD 4.2.1）。 */
    val chatMode: StateFlow<ChatMode> = settings
        .map { ChatMode.from(it.chatMode) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatMode.CONTINUOUS)

    /** 连续流模式下是否显示话题分隔线。 */
    val showTopicDividers: StateFlow<Boolean> = settings
        .map { it.showTopicDividers }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** v3 H-22：最后一条已读消息 id，用于「以下为新消息」分割线。 */
    val lastReadMessageId: StateFlow<Long> = settings
        .map { it.lastReadMessageId }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    /** v3 H-22 / 1.3#48：消息密度三档，控制气泡上下间距。 */
    val messageDensity: StateFlow<MessageDensity> = settings
        .map { MessageDensity.from(it.messageDensity) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MessageDensity.STANDARD)

    /** 当前聊天背景图片；未设置时为 null。 */
    val chatBackgroundFile: StateFlow<File?> = settings
        .map { backgroundStore.fileFor(it.chatBackground) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 旧版背景三键（只读兼容，新入口走自定义背景设置页）。 */
    val chatBackgroundBlur: StateFlow<Float> = settings
        .map { it.chatBackgroundBlur }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 6f)

    val chatBackgroundDim: StateFlow<Float> = settings
        .map { it.chatBackgroundDim }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.35f)

    // ---------------- 自定义聊天背景（v3 §14.6） ----------------

    private val _chatBackgroundRender = MutableStateFlow<BackgroundRenderInput?>(null)

    /** 当前身份的背景渲染输入；切换身份时保留旧值直到新值就绪（不闪）。 */
    val chatBackgroundRender: StateFlow<BackgroundRenderInput?> = _chatBackgroundRender.asStateFlow()

    private var backgroundDarkTheme = false

    suspend fun refreshChatBackground(identityId: Long, darkTheme: Boolean) {
        backgroundDarkTheme = darkTheme
        _chatBackgroundRender.value = chatBackgroundRepo.resolveRender(identityId, darkTheme)
    }

    suspend fun loadBackgroundSpec(identityId: Long): ChatBackgroundSpec =
        chatBackgroundRepo.load(identityId)

    suspend fun importBackgroundImage(uri: Uri): String? = chatBackgroundRepo.importImage(uri)

    fun backgroundImageFile(sha1: String?): File? = backgroundStore.fileFor(sha1)

    fun backgroundCacheBytes(): Long = backgroundStore.cacheSizeBytes()

    fun applyChatBackground(identityId: Long, spec: ChatBackgroundSpec, applyToAll: Boolean) {
        viewModelScope.launch {
            chatBackgroundRepo.save(identityId, spec, applyToAll)
            refreshChatBackground(identityId, backgroundDarkTheme)
            _toast.value = appContext.getString(R.string.vm_background_updated)
        }
    }

    fun resetChatBackground(identityId: Long, applyToAll: Boolean) {
        viewModelScope.launch {
            chatBackgroundRepo.reset(identityId, applyToAll)
            refreshChatBackground(identityId, backgroundDarkTheme)
            _toast.value = appContext.getString(R.string.vm_background_reset)
        }
    }

    private val _sessionId = MutableStateFlow<Long?>(null)
    val sessionId: StateFlow<Long?> = _sessionId.asStateFlow()

    /** 会话列表模式：当前会话消息；没有当前会话时必须是空列表，避免残留上一个会话的内容。 */
    val messages: StateFlow<List<MessageEntity>> = _sessionId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else chatRepo.observeMessages(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 连续流模式：跨所有会话的全部可见消息，按时间排序。 */
    val allMessages: StateFlow<List<MessageEntity>> = chatRepo.observeAllMessages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _streaming = MutableStateFlow("")
    val streaming: StateFlow<String> = _streaming.asStateFlow()

    private val _typing = MutableStateFlow(false)
    val typing: StateFlow<Boolean> = _typing.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _canContinue = MutableStateFlow(false)
    val canContinue: StateFlow<Boolean> = _canContinue.asStateFlow()

    private val _health = MutableStateFlow<List<ProviderHealth>>(emptyList())
    val health: StateFlow<List<ProviderHealth>> = _health.asStateFlow()

    private val _checkingHealth = MutableStateFlow(false)
    val checkingHealth: StateFlow<Boolean> = _checkingHealth.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    private val _backups = MutableStateFlow<List<File>>(emptyList())
    val backups: StateFlow<List<File>> = _backups.asStateFlow()

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    /**
     * 是否已有历史会话（v3 §7.12 引导兜底）。
     * 老用户升级后 [AppSettings.onboardingDone] 还是 false，靠这个标记避免被重新弹引导。
     */
    private val _hasHistory = MutableStateFlow(false)
    val hasHistory: StateFlow<Boolean> = _hasHistory.asStateFlow()

    // ---- 翻旧账搜索（PRD US-020） ----
    private val _searchResults = MutableStateFlow<List<MessageEntity>>(emptyList())
    val searchResults: StateFlow<List<MessageEntity>> = _searchResults.asStateFlow()

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    /** 选中的旧账上下文，仅下一轮发送时注入，用完即清。 */
    private val _pendingSearchContext = MutableStateFlow<String?>(null)
    val pendingSearchContext: StateFlow<String?> = _pendingSearchContext.asStateFlow()

    fun searchMessages(keyword: String) {
        viewModelScope.launch {
            _searching.value = true
            _searchResults.value = runCatching { chatRepo.searchMessages(keyword) }
                .getOrDefault(emptyList())
            _searching.value = false
        }
    }

    /** 把一条旧消息设为下一轮的临时上下文。 */
    fun useMessageAsContext(message: MessageEntity) {
        val role = when (message.role) {
            com.xiaoyu.app.data.repo.ROLE_USER -> "我"
            com.xiaoyu.app.data.repo.ROLE_ASSISTANT -> "她"
            else -> "系统"
        }
        _pendingSearchContext.value = "$role：${message.content}"
    }

    fun clearSearchContext() {
        _pendingSearchContext.value = null
    }

    // ---- 本地 AI 网关配置（生图 + 语音共用） ----
    val localAiUrl: StateFlow<String?> = settings
        .map { it.localAiUrl }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _voiceOptions = MutableStateFlow<List<String>>(emptyList())
    val voiceOptions: StateFlow<List<String>> = _voiceOptions.asStateFlow()

    private val _styleOptions = MutableStateFlow<List<String>>(emptyList())
    val styleOptions: StateFlow<List<String>> = _styleOptions.asStateFlow()

    private val _localAiChecking = MutableStateFlow(false)
    val localAiChecking: StateFlow<Boolean> = _localAiChecking.asStateFlow()

    /** 停用本地 AI 服务时清空已拉到的预设列表。 */
    fun clearLocalAiOptions() {
        _voiceOptions.value = emptyList()
        _styleOptions.value = emptyList()
    }

    /** 拉取网关的音色 / 画风预设，并顺带做一次健康检查。 */
    fun refreshLocalAiOptions(
        urlOverride: String? = null,
        tokenOverride: String? = null,
    ) {
        if (_localAiChecking.value) return
        val base = urlOverride ?: localAiUrl.value
        if (!localAiRepo.isConfigured(base)) {
            _voiceOptions.value = emptyList()
            _styleOptions.value = emptyList()
            return
        }
        val token = tokenOverride ?: settings.value.localAiToken
        _localAiChecking.value = true
        viewModelScope.launch {
            val health = localAiRepo.health(base)
            // health 已带预设名，省两次请求；拿不到再单独拉列表
            val voices = health?.voices?.takeIf { it.isNotEmpty() }
                ?: localAiRepo.listVoices(base, token)
            val styles = health?.styles?.takeIf { it.isNotEmpty() }
                ?: localAiRepo.listStyles(base, token)
            _voiceOptions.value = voices
            _styleOptions.value = styles
            // 保存的音色/画风若已不存在（电脑上改过配置），回落到服务端默认值
            val fallbackStyle = health?.defaultStyle
            val fallbackVoice = health?.defaultVoice
            if (styles.isNotEmpty() && settings.value.imageStyle !in styles && fallbackStyle != null) {
                updateSettings { it.copy(imageStyle = fallbackStyle) }
            }
            if (voices.isNotEmpty() && settings.value.ttsVoice !in voices && fallbackVoice != null) {
                updateSettings { it.copy(ttsVoice = fallbackVoice) }
            }
            _localAiChecking.value = false
            _toast.value = when {
                health == null ->
                    appContext.getString(R.string.vm_local_ai_unreachable, base)
                !health.comfyAvailable && !health.ttsAvailable ->
                    appContext.getString(R.string.vm_local_ai_not_ready)
                !health.comfyAvailable -> appContext.getString(R.string.vm_local_ai_image_unavailable)
                !health.ttsAvailable -> appContext.getString(R.string.vm_local_ai_tts_unavailable)
                else -> appContext.getString(R.string.vm_local_ai_ok)
            }
        }
    }

    /** 保存并立即测试：避免 updateSettings 异步写盘导致读到旧地址/旧 Token。 */
    fun saveAndTestLocalAi(url: String, token: String) {
        val cleanUrl = url.trim()
        val cleanToken = token.trim()
        viewModelScope.launch {
            settingsRepo.update {
                it.copy(
                    localAiUrl = cleanUrl.ifBlank { null },
                    localAiToken = cleanToken.ifBlank { null },
                )
            }
            refreshLocalAiOptions(
                urlOverride = cleanUrl.ifBlank { null },
                tokenOverride = cleanToken.ifBlank { null },
            )
        }
    }

    // ---- 语音朗读（PRD US-019） ----
    private val _playingMessageId = MutableStateFlow<Long?>(null)
    val playingMessageId: StateFlow<Long?> = _playingMessageId.asStateFlow()

    private val _synthesizingMessageId = MutableStateFlow<Long?>(null)
    val synthesizingMessageId: StateFlow<Long?> = _synthesizingMessageId.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null

    /** 播放某条消息的语音；已在播放/合成中则停止。 */
    fun playVoice(messageId: Long, text: String) {
        if (_playingMessageId.value == messageId || _synthesizingMessageId.value == messageId) {
            stopVoice()
            return
        }
        stopVoice()
        val base = localAiUrl.value
        if (!localAiRepo.isConfigured(base)) {
            _toast.value = appContext.getString(R.string.vm_local_ai_not_configured)
            return
        }
        _synthesizingMessageId.value = messageId
        viewModelScope.launch {
            val file = localAiRepo.synthesize(
                text = text,
                voice = settings.value.ttsVoice,
                baseUrl = base,
                token = settings.value.localAiToken,
            )
            if (_synthesizingMessageId.value != messageId) return@launch
            _synthesizingMessageId.value = null
            if (file == null) {
                _toast.value = appContext.getString(R.string.vm_tts_failed)
                return@launch
            }
            _playingMessageId.value = messageId
            runCatching {
                val mp = MediaPlayer().apply {
                    setDataSource(file.absolutePath)
                    setOnCompletionListener {
                        _playingMessageId.value = null
                        release()
                        if (mediaPlayer === this) mediaPlayer = null
                    }
                    prepare()
                    start()
                }
                mediaPlayer = mp
            }.onFailure {
                _toast.value = appContext.getString(R.string.vm_voice_play_failed)
                _playingMessageId.value = null
            }
        }
    }

    fun stopVoice() {
        mediaPlayer?.let {
            runCatching { if (it.isPlaying) it.stop() }
            it.release()
        }
        mediaPlayer = null
        _playingMessageId.value = null
        _synthesizingMessageId.value = null
    }

    override fun onCleared() {
        super.onCleared()
        stopVoice()
    }

    // ---- 生图（PRD US-018） ----
    private val _generatingImage = MutableStateFlow(false)
    val generatingImage: StateFlow<Boolean> = _generatingImage.asStateFlow()

    /**
     * 识别是否要画图，并取出中文描述交给网关扩写。
     * 只在明确的画图句式下触发，避免"画得真好 / 画风 / 动画 / 你会画画吗"误触。
     */
    private fun detectImagePrompt(text: String): String? {
        val t = text.trim().trimEnd('。', '！', '!', '？', '?', '～', '~', ' ')
        if (t.isEmpty()) return null
        if (listOf("画风", "画得", "画家", "动画", "漫画", "画画吗").any { t.contains(it) }) return null

        // "帮我画…/给我画…/你能不能画…"
        val ask = Regex("""^(?:请|帮我|给我|能不能|可以|你会|你)(?:帮我|给我)?(?:画|绘制|生成|来)(?:一|1)?(?:张|副|幅|个)?(.*)$""")
        // "画一张…/画一只…"
        val quantified = Regex("""^(?:画|绘制|生成)(?:一|1)?(?:张|副|幅|个|只|位|条|朵|座|片)(.*)$""")
        // "…的图片/照片/壁纸"
        val imageNoun = Regex("""^(.+?)(?:的)?(?:图|图片|照片|壁纸)$""")

        val raw = (ask.find(t) ?: quantified.find(t))?.groupValues?.getOrNull(1)
            ?: imageNoun.find(t)?.groupValues?.getOrNull(1)
        val prompt = raw
            ?.replace(Regex("""(?:吧|好吗|可以吗|好不好|行吗|的样子|给我看)$"""), "")
            ?.trim()
            ?.trim('，', ',', '的')
        return prompt?.takeIf { it.length >= 2 }
    }

    /** 生图并把结果作为一条 assistant 图片消息插入当前会话。 */
    fun generateImage(prompt: String, sessionId: Long) {
        if (_generatingImage.value) return
        val base = localAiUrl.value
        if (!localAiRepo.isConfigured(base)) {
            _toast.value = appContext.getString(R.string.vm_local_ai_not_configured)
            return
        }
        _generatingImage.value = true
        viewModelScope.launch {
            val file = localAiRepo.generateImage(
                prompt = prompt,
                // base 是网关的故障回退占位画风，常规出图传了会被 400 拒绝
                style = settings.value.imageStyle.takeIf { it != "base" },
                baseUrl = base,
                token = settings.value.localAiToken,
            )
            _generatingImage.value = false
            if (file == null) {
                _toast.value = appContext.getString(R.string.vm_image_generate_failed)
                return@launch
            }
            chatRepo.insertImageMessage(sessionId, "画了一张：$prompt", file.absolutePath)
        }
    }

    init {
        viewModelScope.launch {
            stickerRepo.ensureBuiltinSeeded()
            settingsRepo.ensureLoaded()
            _hasHistory.value = runCatching { chatRepo.latestSessionId() != null }.getOrDefault(false)
            _ready.value = true
            _backups.value = runCatching { backupRepo.listBackups() }.getOrDefault(emptyList())
            settingsRepo.state.map { it.onboarded }.distinctUntilChanged().collect { onboarded ->
                if (onboarded && _sessionId.value == null) {
                    // 只恢复上次的会话，不在启动时新建，避免产生空会话污染会话列表
                    _sessionId.value = chatRepo.latestSessionId()
                }
            }
        }
    }

    fun completeOnboarding(
        identityId: Long? = null,
        styleId: Long? = null,
        herName: String? = null,
        nickname: String? = null,
        formOfAddress: String? = null,
        themePreset: String? = null,
        brain: BrainSettings? = null,
    ) {
        viewModelScope.launch {
            val before = settingsRepo.current()
            val trimmedName = herName?.trim().orEmpty()
            // 起了新名字就写进她的人设（identity），设置里随时还能改。
            if (trimmedName.isNotBlank() && identityId != null) {
                val current = identities.value.firstOrNull { it.id == identityId }
                if (current != null && current.name != trimmedName) {
                    runCatching { personaRepo.updateIdentity(identityId, trimmedName, current.contentMd) }
                }
            }
            settingsRepo.update {
                it.copy(
                    onboarded = true,
                    onboardingDone = true,
                    chosenIdentityId = identityId ?: it.chosenIdentityId,
                    chosenStyleId = styleId ?: it.chosenStyleId,
                    userNickname = nickname?.trim().orEmpty().ifBlank { it.userNickname },
                    userFormOfAddress = formOfAddress?.trim().orEmpty().ifBlank {
                        trimmedName.ifBlank { it.userFormOfAddress }
                    },
                    themePreset = themePreset?.ifBlank { null } ?: it.themePreset,
                    brain = brain ?: it.brain,
                )
            }
            sendFirstGreeting(before.greetedAt)
        }
    }

    /**
     * 引导页「跳过」：同样落 `onboardingDone=true`，但不发开场白、不改人设与主题，
     * 直接进入聊天，后续可在设置里补齐。
     */
    fun skipOnboarding() {
        viewModelScope.launch {
            settingsRepo.update {
                it.copy(
                    onboarded = true,
                    onboardingDone = true,
                    chosenIdentityId = it.chosenIdentityId ?: identities.value.firstOrNull()?.id,
                    chosenStyleId = it.chosenStyleId ?: styles.value.firstOrNull()?.id,
                )
            }
        }
    }

    /** 诊断页「性能」导出：写 `Download/小鱼导出/perf-<ts>.txt`。 */
    fun exportPerfReport(fileName: String, content: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    publicDownloads.saveText("小鱼导出", fileName, "text/plain", content)
                }.getOrNull()
            }
            onResult(saved?.displayPath)
        }
    }

    /**
     * 引导结束后的开场白（执行书 6.10 Step 6）：模板外置在 assets/prompts/first_greeting.md，
     * 只发一次，时间/星期由本地渲染。没有模板就静默跳过，不阻断进入聊天。
     */
    private suspend fun sendFirstGreeting(alreadyGreetedAt: Long) {
        if (alreadyGreetedAt > 0L) return
        // v3 §8.2：危机后的 48 小时主动消息暂停期内，开场白也静默跳过
        if (chatRepo.isProactivePaused()) return
        val current = settingsRepo.current()
        val template = runCatching { promptSource.read(PromptKeys.FIRST_GREETING) }.getOrDefault("")
        if (template.isBlank()) return
        val now = java.time.LocalDateTime.now()
        val period = when (now.hour) {
            in 5..10 -> "早上"
            in 11..12 -> "中午"
            in 13..17 -> "下午"
            in 18..22 -> "晚上"
            else -> "深夜"
        }
        val weekday = now.dayOfWeek.getDisplayName(
            java.time.format.TextStyle.FULL,
            java.util.Locale.CHINA,
        )
        val text = template
            .replace("{{user_name}}", current.userNickname.ifBlank { "你" })
            .replace("{{period}}", period)
            .replace("{{weekday}}", weekday)
            .trim()
        if (text.isBlank()) return
        runCatching {
            val sessionId = chatRepo.ensureSessionForProactive()
            chatRepo.saveProactiveMessage(sessionId, text)
            settingsRepo.update { it.copy(greetedAt = System.currentTimeMillis()) }
            _sessionId.value = sessionId
        }
    }

    fun openSession(id: Long) {
        if (_sessionId.value != id) {
            cancelGeneration()
            _error.value = null
        }
        _sessionId.value = id
    }

    /** 会话内切换人设：只影响这个对话之后的回复，历史消息不动。 */
    fun switchSessionIdentity(sessionId: Long, identityId: Long) {
        viewModelScope.launch { chatRepo.switchIdentity(sessionId, identityId) }
    }

    fun newSession() {
        cancelGeneration()
        _error.value = null
        viewModelScope.launch {
            _sessionId.value = chatRepo.createSession()
        }
    }

    fun renameSession(id: Long, title: String) {
        viewModelScope.launch { chatRepo.renameSession(id, title) }
    }

    fun archiveSession(id: Long) {
        val wasCurrent = _sessionId.value == id
        if (wasCurrent) cancelGeneration()
        viewModelScope.launch {
            chatRepo.archiveSession(id)
            // 归档当前会话后回落到最近一个会话；没有就不新建，等用户真正开口再建
            if (wasCurrent) _sessionId.value = chatRepo.latestSessionId()
        }
    }

    fun unarchiveSession(id: Long) {
        viewModelScope.launch { chatRepo.unarchiveSession(id) }
    }

    fun hideMessage(id: Long) {
        viewModelScope.launch { chatRepo.hideMessage(id) }
    }

    /** v3 H-22：标记消息已读，更新未读分割线位置。 */
    fun markChatRead(messageId: Long) {
        if (messageId <= 0) return
        viewModelScope.launch { settingsRepo.update { it.copy(lastReadMessageId = messageId) } }
    }

    /** 多选软删（M3-07）。 */
    fun hideMessages(ids: Set<Long>) {
        viewModelScope.launch { chatRepo.hideMessages(ids.toList()) }
    }

    /** 多选导出（M3-07）：返回纯文本，由 UI 层写入剪贴板。 */
    suspend fun exportMessagesText(ids: Set<Long>): String =
        chatRepo.exportMessagesText(ids.toList())

    // ---- 消息交互（执行书 6.3.4-6.3.8） ----

    /** 给消息加 / 取消 reaction。 */
    fun react(messageId: Long, emoji: String) {
        viewModelScope.launch { chatRepo.toggleReaction(messageId, emoji) }
    }

    /** v3 §7.4.1：把图片消息存到相册（走 PublicDownloads，Android 10+ MediaStore）。 */
    fun saveImageToGallery(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = runCatching {
                val file = File(path)
                if (!file.exists()) return@runCatching false
                val name = "xiaoyu-${System.currentTimeMillis()}.jpg"
                publicDownloads.saveBytes("小鱼图片", name, "image/jpeg", file.readBytes()) != null
            }.getOrDefault(false)
            _toast.value = appContext.getString(
                if (ok) R.string.vm_image_saved else R.string.vm_image_save_failed,
            )
        }
    }

    /** 同一条回复的历史生成版本（M3-06），含被软隐藏的旧版本。 */
    suspend fun loadVersions(messageId: Long): List<MessageEntity> = chatRepo.versions(messageId)

    /** 切换到某一历史版本（M3-06）。 */
    fun switchVersion(groupId: String, messageId: Long) {
        viewModelScope.launch { chatRepo.switchVersion(groupId, messageId) }
    }

    /** 系统没有可用的语音输入时给一句提示（M7-06）。 */
    fun notifyVoiceUnavailable() {
        _toast.value = appContext.getString(R.string.vm_voice_input_unavailable)
    }

    /** 重新生成：软隐藏这条 AI 消息，让她换个说法。 */
    fun regenerate(message: MessageEntity) {
        if (_busy.value) return
        cancelGeneration()
        val serial = generationSerial
        _busy.value = true
        _error.value = null
        _streaming.value = ""
        _canContinue.value = false
        generationJob = viewModelScope.launch {
            try {
                chatRepo.regenerate(message.sessionId, message.id).collect { event ->
                    if (serial == generationSerial) onChatEvent(event)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (serial == generationSerial) _error.value = e.message ?: appContext.getString(R.string.vm_regenerate_failed)
            } finally {
                finishGeneration(serial)
            }
        }
    }

    private var pokeDay = ""
    private var pokeCount = 0
    private var lastPokeAt = 0L

    /**
     * 戳一下（执行书 6.3.7）：
     * - 3 秒内连点只算一次，避免误触刷满次数；
     * - 每天前 9 次按人设即兴回应，第 10 次她直接说「别戳啦」（心情 -2，不走模型）。
     */
    fun poke() {
        if (_busy.value) return
        val now = System.currentTimeMillis()
        if (now - lastPokeAt < POKE_COOLDOWN_MS) return
        lastPokeAt = now
        val today = java.time.LocalDate.now().toString()
        if (pokeDay != today) {
            pokeDay = today
            pokeCount = 0
        }
        if (pokeCount >= POKE_DAILY_LIMIT) {
            _toast.value = appContext.getString(R.string.vm_poke_limit)
            return
        }
        pokeCount += 1
        val streak = pokeCount
        cancelGeneration()
        val serial = generationSerial
        _busy.value = true
        _error.value = null
        _streaming.value = ""
        _canContinue.value = false
        generationJob = viewModelScope.launch {
            try {
                val id = if (chatMode.value == ChatMode.CONTINUOUS) {
                    chatRepo.ensureSession().also { _sessionId.value = it }
                } else {
                    _sessionId.value ?: chatRepo.ensureSession().also { _sessionId.value = it }
                }
                if (streak >= POKE_DAILY_LIMIT) {
                    chatRepo.pokeLimitReply(id)
                } else {
                    chatRepo.poke(id, streak).collect { event ->
                        if (serial == generationSerial) onChatEvent(event)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (serial == generationSerial) _error.value = e.message ?: appContext.getString(R.string.vm_poke_failed)
            } finally {
                finishGeneration(serial)
            }
        }
    }

    /** 收藏并让她记住这句话（执行书 6.3.8）。 */
    fun rememberMessage(message: MessageEntity) {
        val text = message.content.split("\n")
            .filterNot { it.startsWith(com.xiaoyu.app.data.repo.ACTION_PREFIX) }
            .joinToString("\n")
            .trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            memoryRepo.writeUserCommandCard(text, listOf("收藏"), "EVENT")
            _toast.value = appContext.getString(R.string.vm_message_saved_memory)
        }
    }

    /** v3 §7.10：超长粘贴选择「拆分发送」时，按顺序逐条发出，避免被 busy 拦截。 */
    private val splitQueue = ArrayDeque<String>()

    fun sendSplit(chunks: List<String>) {
        splitQueue.clear()
        splitQueue.addAll(chunks.filter { it.isNotBlank() })
        drainSplitQueue()
    }

    private fun drainSplitQueue() {
        if (splitQueue.isEmpty() || _busy.value) return
        send(splitQueue.removeFirst())
        viewModelScope.launch {
            while (_busy.value) kotlinx.coroutines.delay(120)
            drainSplitQueue()
        }
    }

    fun send(text: String): Boolean {
        if (text.isBlank() || _busy.value) return false
        cancelGeneration()
        val serial = generationSerial
        _busy.value = true
        _error.value = null
        _streaming.value = ""
        _canContinue.value = false
        val quoteToId = _quoteToId.value
        _quoteToId.value = null
        // 取出翻旧账临时上下文，仅本轮生效，用完即清（PRD US-020）
        val transientContext = _pendingSearchContext.value
        _pendingSearchContext.value = null
        generationJob = viewModelScope.launch {
            var targetSessionId: Long? = null
            try {
                // 连续流模式：每次发言前刷新当前会话，30 分钟未说话则幕后新建会话，UI 不断流（PRD 4.2.1）。
                val id = if (chatMode.value == ChatMode.CONTINUOUS) {
                    chatRepo.ensureSession().also { _sessionId.value = it }
                } else {
                    _sessionId.value ?: chatRepo.ensureSession().also { _sessionId.value = it }
                }
                targetSessionId = id
                chatRepo.send(id, text, transientContext = transientContext, quoteToId = quoteToId).collect { event ->
                    // 被取消的旧任务不再写入界面
                    if (serial == generationSerial) onChatEvent(event)
                }
                // 回复完成后识别生图意图，自动出图（PRD US-018）
                detectImagePrompt(text)?.let { prompt ->
                    if (serial == generationSerial) generateImage(prompt, id)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (serial == generationSerial) {
                    // 兜底分支也要收干净：半截流式气泡不能留在界面上一直闪光标
                    _streaming.value = ""
                    Log.w("XiaoyuChat", "发送失败: ${e.message}", e)
                    _error.value = e.message ?: appContext.getString(R.string.vm_send_failed)
                }
            } finally {
                finishGeneration(serial)
            }
        }
        return true
    }

    // ---- 引用回复（M1-09） ----
    private val _quoteToId = MutableStateFlow<Long?>(null)
    val quoteToId: StateFlow<Long?> = _quoteToId.asStateFlow()
    val quoteMessage: StateFlow<MessageEntity?> = combine(quoteToId, messages) { qid, list ->
        qid?.let { id -> list.find { it.id == id } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    fun quote(message: MessageEntity) { _quoteToId.value = message.id }
    fun cancelQuote() { _quoteToId.value = null }

    // ---- 撤回 / 编辑重发 / 重发失败消息（M3-07 / M1-09） ----
    fun retract(messageId: Long, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val ok = chatRepo.retract(messageId)
            if (!ok) _toast.value = appContext.getString(R.string.vm_retract_too_late)
            onResult(ok)
        }
    }

    fun editAndResend(messageId: Long, newText: String) {
        if (newText.isBlank() || _busy.value) return
        cancelGeneration()
        val serial = generationSerial
        _busy.value = true
        _streaming.value = ""
        generationJob = viewModelScope.launch {
            try {
                val id = _sessionId.value ?: chatRepo.ensureSession().also { _sessionId.value = it }
                chatRepo.editAndResend(id, messageId, newText).collect { event ->
                    if (serial == generationSerial) onChatEvent(event)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (serial == generationSerial) _error.value = e.message ?: appContext.getString(R.string.vm_edit_resend_failed)
            } finally {
                finishGeneration(serial)
            }
        }
    }

    fun resend(messageId: Long) {
        if (_busy.value) return
        cancelGeneration()
        val serial = generationSerial
        _busy.value = true
        _streaming.value = ""
        generationJob = viewModelScope.launch {
            try {
                val id = _sessionId.value ?: chatRepo.ensureSession().also { _sessionId.value = it }
                chatRepo.resend(id, messageId).collect { event ->
                    if (serial == generationSerial) onChatEvent(event)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (serial == generationSerial) _error.value = e.message ?: appContext.getString(R.string.vm_resend_failed)
            } finally {
                finishGeneration(serial)
            }
        }
    }

    // ---- 草稿箱（M1-09） ----
    suspend fun getDraft(identityId: Long): String? = draftRepo.get(identityId)
    fun saveDraft(identityId: Long, content: String) {
        viewModelScope.launch { draftRepo.save(identityId, content) }
    }

    // ---- 在线状态（执行书 v3 1.3#35）：由 energy + 时段 + 生活碎片推导 ----
    private val _presence = stateRepo.observeMood()
        .map { row ->
            PresenceEngine.derive(
                energy = row?.energy ?: 60,
                hourOfDay = java.time.LocalTime.now().hour,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000),
            PresenceResult(PresenceStatus.ONLINE, ""))

    val onlineStatus: StateFlow<String> = _presence
        .map { presenceLabel(it.status) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000),
            appContext.getString(R.string.vm_status_online))

    /** 在线状态点颜色（十六进制），供顶栏小圆点展示。 */
    val presenceDotColor: StateFlow<String> = _presence
        .map { it.status.dotColor }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PresenceStatus.ONLINE.dotColor)

    private fun presenceLabel(status: PresenceStatus): String = when (status) {
        PresenceStatus.ONLINE -> appContext.getString(R.string.vm_status_online)
        PresenceStatus.BUSY -> appContext.getString(R.string.vm_status_busy)
        PresenceStatus.SLEEPING -> appContext.getString(R.string.vm_status_sleeping)
        PresenceStatus.AWAY -> appContext.getString(R.string.vm_status_away)
    }

    // ---- 纠偏条（v3 H-11 / 4.11）----
    val latestCorrection: StateFlow<com.xiaoyu.app.data.db.CorrectionEntity?> =
        chatRepo.latestCorrection()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun revokeCorrection() = viewModelScope.launch { chatRepo.revokeLatestCorrection() }

    /** 刷新关系页（进入页面时调用）。 */
    fun refreshRelation() {
        viewModelScope.launch {
            _relation.value = relationRepo.summary()
        }
    }

    /** 导出脱敏诊断包，回调绝对路径（失败为 null）。 */
    fun exportDiagnostics(onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val file = runCatching { diagnosticsRepo.exportZip() }.getOrNull()
            onResult(file?.absolutePath)
        }
    }

    fun clearDiagnostics() {
        viewModelScope.launch { diagnosticsRepo.clear() }
    }

    fun curlOf(snapshot: RequestSnapshotEntity): String = diagnosticsRepo.curl(snapshot)

    // ---- 应用锁（执行书 5.6.3） ----

    fun appLockHasPin(): Boolean = appLock.hasPin()

    /** 设置 PIN 并切换到指定模式；PIN 不合法时回调 false。 */
    fun enableAppLock(pin: String, mode: AppLockMode, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val ok = appLock.setPin(pin)
            if (ok) appLock.setMode(mode)
            onResult(ok)
        }
    }

    fun setAppLockMode(mode: AppLockMode) {
        viewModelScope.launch {
            if (mode != AppLockMode.OFF && !appLock.hasPin()) return@launch
            appLock.setMode(mode)
        }
    }

    fun disableAppLock() {
        viewModelScope.launch { appLock.disable() }
    }

    fun lockNow() = appLock.lockNow()

    /** 从表情面板发一枚表情；不打断正在生成的回复。 */
    fun sendSticker(stickerId: String) {
        if (_busy.value) return
        viewModelScope.launch {
            val id = if (chatMode.value == ChatMode.CONTINUOUS) {
                chatRepo.ensureSession().also { _sessionId.value = it }
            } else {
                _sessionId.value ?: chatRepo.ensureSession().also { _sessionId.value = it }
            }
            chatRepo.sendSticker(id, stickerId)
        }
    }

    /** 手动开启新话题：立即结束当前会话并触发提炼，无缝续聊（PRD 4.2.1）。 */
    fun startNewTopic() {
        cancelGeneration()
        viewModelScope.launch {
            val current = _sessionId.value
            if (current != null) chatRepo.endSession(current)
            _sessionId.value = chatRepo.ensureSession()
            _error.value = null
        }
    }

    fun continueReply() {
        val id = _sessionId.value ?: return
        if (_busy.value) return
        cancelGeneration()
        val serial = generationSerial
        _busy.value = true
        _error.value = null
        _canContinue.value = false
        generationJob = viewModelScope.launch {
            try {
                chatRepo.continueGeneration(id).collect { event ->
                    if (serial == generationSerial) onChatEvent(event)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (serial == generationSerial) _error.value = e.message ?: appContext.getString(R.string.vm_continue_failed)
            } finally {
                finishGeneration(serial)
            }
        }
    }

    private fun onChatEvent(event: ChatEvent) {
        when (event) {
            is ChatEvent.Typing -> _typing.value = true
            is ChatEvent.Delta -> {
                _typing.value = false
                _streaming.value = _streaming.value + event.text
            }
            ChatEvent.SegmentBreak -> {
                // 一段说完：清空流式气泡，已落库的气泡由消息列表接上
                _streaming.value = ""
                _typing.value = false
            }
            ChatEvent.Reset -> {
                _streaming.value = ""
            }
            ChatEvent.Completed -> {
                _streaming.value = ""
                _canContinue.value = false
            }
            is ChatEvent.Failed -> {
                _streaming.value = ""
                _error.value = event.message
                _canContinue.value = event.partial.isEmpty()
            }
        }
    }

    /** 当前生成任务；切换会话/新建/归档时立即取消，避免旧流继续写入界面。 */
    private var generationJob: Job? = null
    private var generationSerial = 0

    private fun cancelGeneration() {
        generationSerial += 1
        generationJob?.cancel()
        generationJob = null
        _busy.value = false
        _typing.value = false
        _streaming.value = ""
        _canContinue.value = false
    }

    /** 只有当前这批生成任务才能收尾，被取消的旧任务不覆盖新任务状态。 */
    private fun finishGeneration(serial: Int) {
        if (serial != generationSerial) return
        generationJob = null
        _busy.value = false
        _typing.value = false
    }

    fun dismissError() {
        _error.value = null
    }

    fun setStyle(sessionId: Long, styleId: Long) {
        viewModelScope.launch { chatRepo.setStyle(sessionId, styleId) }
    }

    // ---------------- 设置 ----------------

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settingsRepo.update(transform) }
    }

    // ---------------- 聊天背景 ----------------

    /** 串行化背景图的导入/清理，避免快速连点导致新旧文件互相删除。 */
    private val backgroundMutex = Mutex()

    fun pickChatBackground(uri: Uri) {
        viewModelScope.launch {
            backgroundMutex.withLock {
                val name = chatBackgroundRepo.importImage(uri)
                if (name == null) {
                    _toast.value = appContext.getString(R.string.vm_background_open_failed)
                } else {
                    // 旧键继续写入以兼容已发布版本；配额由新仓库保留各身份仍在引用的图片。
                    settingsRepo.update { it.copy(chatBackground = name) }
                    _toast.value = appContext.getString(R.string.vm_background_updated)
                }
            }
        }
    }

    fun clearChatBackground() {
        viewModelScope.launch {
            backgroundMutex.withLock {
                settingsRepo.update { it.copy(chatBackground = null) }
                // 原图可能仍被 chat_backgrounds 中的身份引用，留待全局配额安全淘汰。
                _toast.value = appContext.getString(R.string.vm_background_reset)
            }
        }
    }

    fun checkHealth(role: BrainRole) {
        if (_checkingHealth.value) return
        _checkingHealth.value = true
        viewModelScope.launch {
            _health.value = runCatching { router.health(role) }.getOrDefault(emptyList())
            _checkingHealth.value = false
        }
    }

    // ---------------- 人设 ----------------

    /** 把人设设为默认：之后新建的对话都用她；已有对话仍沿用各自创建时绑定的人设。 */
    fun useIdentity(id: Long) {
        viewModelScope.launch { settingsRepo.update { it.copy(chosenIdentityId = id) } }
    }

    fun saveIdentity(id: Long?, name: String, content: String) {
        viewModelScope.launch {
            if (id == null) personaRepo.createIdentity(name, content)
            else personaRepo.updateIdentity(id, name, content)
        }
    }

    fun deleteIdentity(id: Long) {
        viewModelScope.launch { personaRepo.deleteIdentity(id) }
    }

    fun saveStyle(id: Long?, name: String, content: String) {
        viewModelScope.launch {
            if (id == null) personaRepo.createStyle(name, content)
            else personaRepo.updateStyle(id, name, content)
        }
    }

    fun deleteStyle(id: Long) {
        viewModelScope.launch { personaRepo.deleteStyle(id) }
    }

    // ---------------- 记忆 ----------------

    fun editMemory(id: Long, content: String, type: MemoryType, tags: String, importance: Int) {
        viewModelScope.launch { memoryRepo.editMemory(id, content, type, tags, importance) }
    }

    fun deleteMemory(id: Long) {
        viewModelScope.launch { memoryRepo.deleteMemory(id) }
    }

    fun restoreMemory(id: Long) {
        viewModelScope.launch { memoryRepo.restoreMemory(id) }
    }

    fun editCard(id: Long, content: String, category: String, tags: String) {
        viewModelScope.launch { memoryRepo.updateCard(id, content, category, tags) }
    }

    fun deleteCard(id: Long) {
        viewModelScope.launch { memoryRepo.deleteCard(id) }
    }

    fun createCard(content: String, category: String, tags: List<String>) {
        viewModelScope.launch { memoryRepo.writeUserCommandCard(content, tags, category) }
    }

    // ---------------- 日记 ----------------

    fun openDiary(diary: DiaryEntity) {
        viewModelScope.launch { stateRepo.markDiaryRead(diary.id) }
    }

    fun unlockDiary(id: Long) {
        viewModelScope.launch {
            if (stateRepo.secretShareable()) {
                stateRepo.unlockDiary(id)
            } else {
                _toast.value = appContext.getString(R.string.vm_diary_locked)
            }
        }
    }

    // ---------------- 备份 ----------------

    private fun refreshBackups() {
        _backups.value = runCatching { backupRepo.listBackups() }.getOrDefault(emptyList())
    }

    fun createLocalBackup(password: String? = null) {
        viewModelScope.launch {
            val file = backupRepo.createBackupFile(password)
            refreshBackups()
            _toast.value = if (file != null) {
                appContext.getString(
                    if (password.isNullOrBlank()) R.string.vm_backup_created else R.string.vm_backup_created_encrypted,
                    file.name,
                )
            } else {
                appContext.getString(R.string.vm_backup_failed)
            }
        }
    }

    fun exportBackup(uri: Uri, password: String? = null) {
        viewModelScope.launch {
            _toast.value = if (backupRepo.exportTo(uri, password)) {
                appContext.getString(
                    if (password.isNullOrBlank()) R.string.vm_backup_exported else R.string.vm_backup_exported_encrypted,
                )
            } else {
                appContext.getString(R.string.vm_backup_export_failed)
            }
        }
    }

    fun importBackup(
        uri: Uri,
        password: String? = null,
        mode: BackupImporter.Mode = BackupImporter.Mode.MERGE,
    ) {
        viewModelScope.launch {
            when (val result = backupImporter.importFrom(uri, password, mode)) {
                is BackupImporter.ImportResult.Success -> {
                    refreshBackups()
                    _toast.value = if (result.restartRequired) {
                        appContext.getString(R.string.vm_backup_imported_restart)
                    } else {
                        appContext.getString(R.string.vm_backup_merged, result.mergedRows)
                    }
                }
                is BackupImporter.ImportResult.Failure -> _toast.value = result.message
            }
        }
    }

    /** 导出整段会话为 Markdown（执行书 7.4 / M5-04）。 */
    fun exportSessionMarkdown(sessionId: Long) {
        viewModelScope.launch {
            _toast.value = when (val result = markdownExporter.exportSession(sessionId)) {
                is MarkdownExporter.ExportResult.Success -> appContext.getString(R.string.vm_exported_to, result.displayPath)
                is MarkdownExporter.ExportResult.Failure -> result.message
            }
        }
    }

    /** 导出单篇日记为 Markdown（执行书 M5-04）。 */
    fun exportDiaryMarkdown(diaryId: Long) {
        viewModelScope.launch {
            _toast.value = when (val result = markdownExporter.exportDiary(diaryId)) {
                is MarkdownExporter.ExportResult.Success -> appContext.getString(R.string.vm_exported_to, result.displayPath)
                is MarkdownExporter.ExportResult.Failure -> result.message
            }
        }
    }

    // ---- 人格包 .xypersona（执行书 11.6 / M7-09） ----

    /** 导出并分享人格包。 */
    fun sharePersonaPack(identityId: Long) {
        viewModelScope.launch {
            val result = personaPackArchive.export(identityId)
            val exported = result.getOrElse {
                _toast.value = appContext.getString(
                    R.string.vm_persona_export_failed,
                    it.message ?: appContext.getString(R.string.vm_unknown_error),
                )
                return@launch
            }
            shareFile(personaPackArchive.shareIntent(exported.file), appContext.getString(R.string.vm_share_persona))
            _toast.value = appContext.getString(R.string.vm_persona_created, exported.bytes / 1024)
        }
    }

    /** 从 SAF 选择的人格文件导入：`.md` / `.txt` 直接作为身份，`.xypersona` / `.zip` 走人格包；超大身份需要二次确认。 */
    fun importPersonaPack(uri: Uri, confirmLargeIdentity: Boolean = false) {
        viewModelScope.launch {
            val temp = copyToCache(uri, "persona") ?: run {
                _toast.value = appContext.getString(R.string.vm_persona_read_failed)
                return@launch
            }
            try {
                // 大多数人设就是一份 Markdown：直接读文本，省得先打包
                if (!isZipArchive(temp)) {
                    val text = runCatching { temp.readText(Charsets.UTF_8) }.getOrNull()
                    if (text.isNullOrBlank()) {
                        _toast.value = appContext.getString(R.string.vm_persona_read_failed)
                        return@launch
                    }
                    val name = personaNameFrom(uri, text)
                    val newId = personaRepo.createIdentity(name, text.trim())
                    // 导入即生效：设为默认人设，新建的对话直接由她来聊
                    settingsRepo.update { it.copy(chosenIdentityId = newId) }
                    _toast.value = appContext.getString(R.string.vm_persona_imported_current, name)
                    return@launch
                }
                val result = personaPackArchive.import(temp, confirmLargeIdentity)
                result.onSuccess { imported ->
                    _toast.value = when {
                        imported.requiresConfirmation ->
                            imported.warnings.firstOrNull() ?: appContext.getString(R.string.vm_persona_too_large)
                        else -> buildString {
                            append(appContext.getString(R.string.vm_persona_imported, imported.identityName))
                            if (imported.styleIds.isNotEmpty()) {
                                append(appContext.getString(R.string.vm_persona_imported_styles, imported.styleIds.size))
                            }
                            if (imported.promptFiles.isNotEmpty()) {
                                append(appContext.getString(R.string.vm_persona_imported_prompts, imported.promptFiles.size))
                            }
                        }
                    }
                }.onFailure { _toast.value = it.message ?: appContext.getString(R.string.vm_import_failed) }
            } finally {
                runCatching { temp.delete() }
            }
        }
    }

    // ---- 表情包 .xystickers（执行书 7.4 / M7-07） ----

    fun shareStickerPack(packId: String) {
        viewModelScope.launch {
            val result = stickerPackArchive.export(packId)
            val file = result.getOrElse {
                _toast.value = appContext.getString(
                    R.string.vm_sticker_export_failed,
                    it.message ?: appContext.getString(R.string.vm_unknown_error),
                )
                return@launch
            }
            shareFile(stickerPackArchive.shareIntent(file), appContext.getString(R.string.vm_share_sticker))
        }
    }

    fun importStickerPack(uri: Uri) {
        viewModelScope.launch {
            val temp = copyToCache(uri, "xystickers") ?: run {
                _toast.value = appContext.getString(R.string.vm_sticker_read_failed)
                return@launch
            }
            try {
                val result = stickerPackArchive.import(temp)
                result.onSuccess { imported ->
                    _toast.value = when {
                        imported.alreadyExists ->
                            appContext.getString(R.string.vm_sticker_already_exists, imported.packName)
                        else ->
                            appContext.getString(R.string.vm_sticker_imported, imported.packName, imported.imported)
                    }
                }.onFailure { _toast.value = it.message ?: appContext.getString(R.string.vm_import_failed) }
            } finally {
                runCatching { temp.delete() }
            }
        }
    }

    // ---- 看图（执行书 M7-05） ----

    /**
     * 让她看一张已经存在的图片，并把她的感受作为一条消息落进对话。
     * 未配置视觉模型时只提示，不打断聊天。
     */
    fun describeImageForHer(path: String, sessionId: Long) {
        viewModelScope.launch {
            _toast.value = appContext.getString(R.string.vm_vision_looking)
            when (val result = visionRepo.describeImage(path)) {
                is VisionRepository.Result.Described -> {
                    val line = result.description.ifBlank { "这幅画我看不太懂，但我喜欢它。" }
                    chatRepo.saveProactiveMessage(sessionId, line)
                    _toast.value = appContext.getString(R.string.vm_vision_done)
                }
                is VisionRepository.Result.Unsupported -> _toast.value = result.message
                is VisionRepository.Result.Failed -> _toast.value = result.message
            }
        }
    }

    private fun shareFile(intent: Intent, chooserTitle: String) {
        val chooser = Intent.createChooser(intent, chooserTitle)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { appContext.startActivity(chooser) }
            .onFailure { _toast.value = appContext.getString(R.string.vm_no_share_app) }
    }

    /** 通过文件头判断是不是 zip 容器（人格包），否则按纯文本 Markdown 处理。 */
    private fun isZipArchive(file: File): Boolean = runCatching {
        file.inputStream().use { input ->
            val head = ByteArray(4)
            input.read(head) == 4 && head[0] == 0x50.toByte() && head[1] == 0x4B.toByte()
        }
    }.getOrDefault(false)

    /** 人设名：优先文件名，其次正文第一个一级标题，最后用默认名。 */
    private fun personaNameFrom(uri: Uri, text: String): String {
        val display = runCatching {
            appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }.getOrNull()
        display?.substringBeforeLast('.')?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        text.lineSequence()
            .firstOrNull { it.trimStart().startsWith("# ") }
            ?.trimStart()?.removePrefix("# ")?.trim()?.takeIf { it.isNotBlank() }
            ?.let { return it }
        return appContext.getString(R.string.persona_default_identity_name)
    }

    /** 把 SAF 选择的文件复制到 cache，供解析器按真实路径读取；失败返回 null。 */
    private suspend fun copyToCache(uri: Uri, extension: String): File? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val file = File(appContext.cacheDir, "import-${System.currentTimeMillis()}.$extension")
                appContext.contentResolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                } ?: return@runCatching null
                file
            }.getOrNull()
        }

    fun clearToast() {
        _toast.value = null
    }

    fun favorabilityLevel(): FavorabilityLevel = FavorabilityLevel.of(favorability.value)
}
