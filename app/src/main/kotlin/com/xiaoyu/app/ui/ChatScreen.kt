package com.xiaoyu.app.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaoyu.app.R
import com.xiaoyu.app.data.db.MessageEntity
import com.xiaoyu.app.data.db.SessionEntity
import com.xiaoyu.app.data.db.StickerEntity
import com.xiaoyu.app.data.repo.ACTION_PREFIX
import com.xiaoyu.app.data.repo.ROLE_ASSISTANT
import com.xiaoyu.app.data.repo.ROLE_NOTICE
import com.xiaoyu.app.data.repo.ROLE_USER
import com.xiaoyu.app.data.repo.STATUS_FAILED
import com.xiaoyu.app.data.repo.STATUS_SENDING
import com.xiaoyu.app.data.repo.TYPE_STICKER
import com.xiaoyu.app.data.repo.TYPE_CARD
import com.xiaoyu.app.data.settings.ChatMode
import com.xiaoyu.app.data.settings.MessageDensity
import com.xiaoyu.app.ui.background.ChatBackgroundRenderer
import com.xiaoyu.app.ui.background.LocalBubbleVisual
import com.xiaoyu.app.ui.components.YuEmptyView
import com.xiaoyu.app.ui.components.YuNavBar
import com.xiaoyu.app.ui.components.YuNavItem
import com.xiaoyu.app.ui.components.YuTimeDivider
import com.xiaoyu.app.ui.illustrations.YuIllustrationKind
import com.xiaoyu.app.ui.theme.LocalBubbleColors
import com.xiaoyu.app.ui.theme.LocalYuMotion
import com.xiaoyu.app.ui.theme.LocalYuType
import com.xiaoyu.app.ui.theme.Tokens
import com.xiaoyu.app.ui.theme.WindowSizeClass
import com.xiaoyu.app.ui.theme.XiaoyuTheme
import com.xiaoyu.app.ui.theme.YuIcons
import com.xiaoyu.app.ui.theme.windowSizeClassFor
import com.xiaoyu.app.ui.theme.yuSpring
import com.xiaoyu.app.ui.theme.yuTween
import com.xiaoyu.soul.mood.FavorabilityLevel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** v3 §7.3.2：时间行只显示 HH:mm，跨天 / 跨年 / 5 分钟间隔由调用方判定是否渲染。 */
private val timeFormat = SimpleDateFormat("HH:mm", Locale.CHINA)

/** v3 §7.3.2：连续两条消息间隔 ≥ 5 分钟才渲染时间行；第一条消息始终显示。 */
private const val TIME_GAP_FOR_DISPLAY_MS = 5 * 60 * 1000L

/** v3 §14.5：上滑到顶时每次多加载的历史条数。 */
private const val HISTORY_PAGE_SIZE = 80

/** v3 §7.4.1：撤回 / 编辑时限 2 分钟。 */
private const val RECALL_WINDOW_MS = 2 * 60 * 1000L

/** v3 §7.4.2：长按面板快捷 reaction（同一表情再点一次取消）。 */
private val QUICK_REACTIONS = listOf("👍", "❤️", "😂", "😮", "😢", "🎉")

/** 长按菜单项；超过 4 项时折叠进「更多」。 */
private data class MenuAction(
    val labelRes: Int,
    val enabled: Boolean = true,
    val hintRes: Int? = null,
    val action: () -> Unit,
)

@Composable
private fun MenuActionRow(action: MenuAction) {
    TextButton(
        onClick = action.action,
        enabled = action.enabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(stringResource(action.labelRes))
            action.hintRes?.let { hint ->
                Text(
                    stringResource(hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 表情面板的 tab（表情包 / Emoji）。 */
@Composable
private fun PanelTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

/** 全屏输入（双击输入栏打开，v3 §14.5）。 */
@Composable
private fun FullScreenInputDialog(
    initial: String,
    onDismiss: () -> Unit,
    onSend: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.chat_full_input_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.chat_cancel))
                    }
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    placeholder = { Text(stringResource(R.string.chat_input_placeholder)) },
                    textStyle = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { onSend(value) },
                        enabled = value.isNotBlank(),
                    ) { Text(stringResource(R.string.chat_full_input_done)) }
                }
            }
        }
    }
}

/** v3 §7.3.3：合并阈值 60s（连续同作者 <60s 视为一组，省略头像、气泡圆角收紧）。 */
internal fun isLastInGroup(
    currentRole: String,
    currentTime: Long,
    nextRole: String?,
    nextTime: Long,
): Boolean = nextRole == null || nextRole != currentRole || nextTime - currentTime >= 60 * 1000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: AppViewModel,
    onOpenMemory: () -> Unit,
    onOpenDiary: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenRelation: () -> Unit,
    onOpenLifeDay: () -> Unit,
    onOpenStickers: () -> Unit,
) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val archived by viewModel.archivedSessions.collectAsStateWithLifecycle()
    val sessionId by viewModel.sessionId.collectAsStateWithLifecycle()
    val favorability by viewModel.favorability.collectAsStateWithLifecycle()
    val chatMode by viewModel.chatMode.collectAsStateWithLifecycle()
    val isContinuous = chatMode == ChatMode.CONTINUOUS
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val backgroundRender by viewModel.chatBackgroundRender.collectAsStateWithLifecycle()
    val darkTheme = isSystemInDarkTheme()
    val identityId = sessions.firstOrNull { it.id == sessionId }?.identityId
        ?: archived.firstOrNull { it.id == sessionId }?.identityId
        ?: settings.chosenIdentityId
        ?: 0L
    val backgroundScroll = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(identityId, darkTheme) { viewModel.refreshChatBackground(identityId, darkTheme) }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var renaming by remember { mutableStateOf<SessionEntity?>(null) }

    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }

    val drawerContent: @Composable () -> Unit = {
        SessionDrawer(
            sessions = sessions,
            archived = archived,
            currentId = sessionId,
            favorability = favorability,
            continuous = isContinuous,
            onNew = {
                if (isContinuous) viewModel.startNewTopic() else viewModel.newSession()
                scope.launch { drawerState.close() }
            },
            onSelect = {
                viewModel.openSession(it)
                scope.launch { drawerState.close() }
            },
            onArchive = { viewModel.archiveSession(it) },
            onUnarchive = { viewModel.unarchiveSession(it) },
            onRename = { renaming = it },
            onOpenMemory = { scope.launch { drawerState.close() }; onOpenMemory() },
            onOpenDiary = { scope.launch { drawerState.close() }; onOpenDiary() },
            onOpenSettings = { scope.launch { drawerState.close() }; onOpenSettings() },
            onOpenSearch = { scope.launch { drawerState.close() }; onOpenSearch() },
            onOpenGallery = { scope.launch { drawerState.close() }; onOpenGallery() },
            onOpenRelation = { scope.launch { drawerState.close() }; onOpenRelation() },
            onOpenLifeDay = { scope.launch { drawerState.close() }; onOpenLifeDay() },
        )
    }
    val openDrawer: () -> Unit = { scope.launch { drawerState.open() } }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val sizeClass = windowSizeClassFor(maxWidth)
        val pinned = sizeClass.supportsDualPane
        val paneModifier = if (sizeClass == WindowSizeClass.EXPANDED) {
            Modifier.widthIn(max = Tokens.SizeContentMax).fillMaxSize()
        } else {
            Modifier.fillMaxSize()
        }
        val pane: @Composable () -> Unit = {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                ChatPane(
                    viewModel = viewModel,
                    isContinuous = isContinuous,
                    onOpenDrawer = if (pinned) null else openDrawer,
                    modifier = paneModifier,
                    onOpenStickers = onOpenStickers,
                    onOpenMemory = onOpenMemory,
                    onOpenDiary = onOpenDiary,
                    onOpenRelation = onOpenRelation,
                    onOpenSettings = onOpenSettings,
                    hasCustomBackground = backgroundRender?.spec?.isEnabled == true,
                    onScrollOffset = { backgroundScroll.floatValue = it },
                    onOpenSearch = onOpenSearch,
                )
            }
        }
        // 五层背景铺在最底层，覆盖整个聊天页（含顶部栏），不被 drawer 遮挡
        ChatBackgroundRenderer(
            input = backgroundRender,
            darkTheme = darkTheme,
            modifier = Modifier.fillMaxSize(),
            parallaxOffsetPx = backgroundScroll,
        ) {
            if (pinned) {
                PermanentNavigationDrawer(
                    drawerContent = { Box(Modifier.width(Tokens.SizeSessionPane)) { drawerContent() } },
                ) { pane() }
            } else {
                ModalNavigationDrawer(drawerState = drawerState, drawerContent = drawerContent) { pane() }
            }
        }
    }

    renaming?.let { session ->
        RenameSessionDialog(
            initial = session.title,
            onDismiss = { renaming = null },
            onConfirm = { title ->
                viewModel.renameSession(session.id, title)
                renaming = null
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatPane(
    viewModel: AppViewModel,
    isContinuous: Boolean,
    onOpenDrawer: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onOpenStickers: () -> Unit = {},
    onOpenMemory: () -> Unit = {},
    onOpenDiary: () -> Unit = {},
    onOpenRelation: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenSearch: () -> Unit = {},
    hasCustomBackground: Boolean = false,
    onScrollOffset: (Float) -> Unit = {},
) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val sessionId by viewModel.sessionId.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val streaming by viewModel.streaming.collectAsStateWithLifecycle()
    val typing by viewModel.typing.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val canContinue by viewModel.canContinue.collectAsStateWithLifecycle()
    val favorability by viewModel.favorability.collectAsStateWithLifecycle()
    val allMessages by viewModel.allMessages.collectAsStateWithLifecycle()
    val showTopicDividers by viewModel.showTopicDividers.collectAsStateWithLifecycle()
    val lastReadMessageId by viewModel.lastReadMessageId.collectAsStateWithLifecycle()
    val messageDensity by viewModel.messageDensity.collectAsStateWithLifecycle()
    val archived by viewModel.archivedSessions.collectAsStateWithLifecycle()
    val pendingSearchContext by viewModel.pendingSearchContext.collectAsStateWithLifecycle()
    val playingMessageId by viewModel.playingMessageId.collectAsStateWithLifecycle()
    val synthesizingMessageId by viewModel.synthesizingMessageId.collectAsStateWithLifecycle()
    val stickers by viewModel.stickers.collectAsStateWithLifecycle()
    val stickerPacks by viewModel.stickerPacks.collectAsStateWithLifecycle()
    val herReactions by viewModel.herReactions.collectAsStateWithLifecycle()
    val stickerById = remember(stickers) { stickers.associateBy { it.id } }
    var showStickers by rememberSaveable(sessionId) { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    // 打开表情面板时收起键盘和输入焦点，避免面板被键盘顶成一条缝
    LaunchedEffect(showStickers) {
        if (showStickers) {
            keyboard?.hide()
            focusManager.clearFocus()
        }
    }
    val current = sessions.firstOrNull { it.id == sessionId }
        ?: archived.firstOrNull { it.id == sessionId }
    val moodLabel = stringResource(favorabilityLabelRes(FavorabilityLevel.of(favorability)))

    val hasBackground = hasCustomBackground
    val paneBg = if (hasBackground) Color.Transparent else MaterialTheme.colorScheme.background

    // 长按消息弹出的操作面板（执行书 6.3.4）
    var menuMessage by remember { mutableStateOf<MessageEntity?>(null) }
    var confirmHide by remember { mutableStateOf<MessageEntity?>(null) }
    var confirmRetract by remember { mutableStateOf<MessageEntity?>(null) }
    var editingMessage by remember { mutableStateOf<MessageEntity?>(null) }
    // 多选模式（M3-07）
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var confirmDeleteSelected by remember { mutableStateOf(false) }
    // 历史版本弹层（M3-06）
    var versionTarget by remember { mutableStateOf<MessageEntity?>(null) }
    var versions by remember { mutableStateOf<List<MessageEntity>>(emptyList()) }
    // 长按菜单分组：常用 4 项 + 「更多」二级（v3 §7.4.1）
    var showMoreMenuItems by remember { mutableStateOf(false) }
    // 表情面板：0 = 表情包，1 = Emoji（v3 §7.4.7）
    var stickerTab by rememberSaveable(sessionId) { mutableIntStateOf(0) }
    var pendingEmoji by remember { mutableStateOf<String?>(null) }
    var stickerPreview by remember { mutableStateOf<StickerEntity?>(null) }
    // 双击输入栏 → 全屏输入（v3 §14.5 手势地图）
    var showFullInput by remember { mutableStateOf(false) }
    var fullInputSeed by remember { mutableStateOf("") }
    // 上滑到顶时按页加载更多历史（v3 §14.5）
    var historyLimit by remember(sessionId) { mutableIntStateOf(HISTORY_PAGE_SIZE) }
    // 顶栏更多菜单（M5-04：导出会话 Markdown）
    var showMoreMenu by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val motion = LocalYuMotion.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    // v3 §7.9 / B-31：长按「朗读」用系统 TTS 播报（本机合成、零上传；初始化失败静默降级）
    val appContext = LocalContext.current
    val tts = remember { SystemTts(appContext) }
    var speakingMessageId by remember { mutableStateOf<Long?>(null) }
    DisposableEffect(tts) {
        tts.onFinished = { speakingMessageId = null }
        onDispose { tts.shutdown() }
    }

    LaunchedEffect(versionTarget) {
        // v3 §7.8：历史版本抽屉最多展示 3 版
        versions = versionTarget?.let { viewModel.loadVersions(it.id).take(3) } ?: emptyList()
    }

    LaunchedEffect(menuMessage) { showMoreMenuItems = false }

    BackHandler(enabled = selectionMode) {
        selectionMode = false
        selectedIds = emptySet()
    }

    Box(modifier = modifier) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = paneBg,
            // 底部导航栏交给输入栏自己处理：背景铺到屏幕最底，内容抬到手势小白条之上，
            // 避免系统手势条（小白条）压在"表情/语音/发送"这一行上。
            contentWindowInsets = WindowInsets.safeDrawing.only(
                WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
            ),
            bottomBar = {
                YuNavBar(
                    items = listOf(
                        YuNavItem(stringResource(R.string.chat_her_name), Icons.Default.Home, selected = true, onClick = {}),
                        YuNavItem(stringResource(R.string.chat_memory), Icons.Default.List, selected = false, onClick = onOpenMemory),
                        YuNavItem(stringResource(R.string.chat_diary), Icons.Default.DateRange, selected = false, onClick = onOpenDiary),
                        YuNavItem(stringResource(R.string.chat_relation), Icons.Default.Favorite, selected = false, onClick = onOpenRelation),
                        YuNavItem(stringResource(R.string.chat_settings), Icons.Default.Settings, selected = false, onClick = onOpenSettings),
                    ),
                )
            },
            topBar = {
                if (selectionMode) {
                    TopAppBar(
                        title = { Text(stringResource(R.string.chat_selected_count, selectedIds.size)) },
                        navigationIcon = {
                            TextButton(onClick = {
                                selectionMode = false
                                selectedIds = emptySet()
                            }) { Text(stringResource(R.string.chat_cancel)) }
                        },
                        actions = {
                            TextButton(onClick = {
                                val all = if (isContinuous) allMessages else messages
                                selectedIds = all.map { it.id }.toSet()
                            }) { Text(stringResource(R.string.chat_select_all)) }
                            TextButton(
                                onClick = { confirmDeleteSelected = true },
                                enabled = selectedIds.isNotEmpty(),
                            ) { Text(stringResource(R.string.chat_delete)) }
                            TextButton(
                                onClick = {
                                    if (selectedIds.isEmpty()) return@TextButton
                                    val ids = selectedIds
                                    scope.launch {
                                        val text = viewModel.exportMessagesText(ids)
                                        clipboard.setText(AnnotatedString(text))
                                    }
                                },
                                enabled = selectedIds.isNotEmpty(),
                            ) { Text(stringResource(R.string.chat_export)) }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = paneBg,
                            scrolledContainerColor = paneBg,
                        ),
                    )
                } else {
                TopAppBar(
                    title = {
                        val online by viewModel.onlineStatus.collectAsStateWithLifecycle()
                        val dotColor by viewModel.presenceDotColor.collectAsStateWithLifecycle()
                        val typingState by viewModel.typing.collectAsStateWithLifecycle()
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // 双击她的头像 = 戳一下（M3-05 / 执行书 6.3.7）
                                HerAvatar(onPoke = { viewModel.poke() })
                                Spacer(Modifier.width(Tokens.SpacingSm))
                                // v3 §1.3#35：在线状态色点（绿=在线 / 橙=忙碌 / 灰=睡觉/离开）
                                val presenceDesc = when (online) {
                                    stringResource(R.string.vm_status_busy) -> stringResource(R.string.chat_presence_busy)
                                    stringResource(R.string.vm_status_sleeping) -> stringResource(R.string.chat_presence_sleeping)
                                    stringResource(R.string.vm_status_away) -> stringResource(R.string.chat_presence_away)
                                    else -> stringResource(R.string.chat_presence_online)
                                }
                                Box(
                                    modifier = Modifier
                                        .size(Tokens.SpacingSm)
                                        .clip(CircleShape)
                                        .background(Color(android.graphics.Color.parseColor("#$dotColor")))
                                        .semantics { contentDescription = presenceDesc }
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (isContinuous) {
                                        stringResource(R.string.chat_title_continuous, moodLabel, online)
                                    } else {
                                        stringResource(
                                            R.string.chat_title_session,
                                            current?.title ?: stringResource(R.string.chat_her_name),
                                            moodLabel,
                                            online,
                                        )
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            // v3 §7.2：她正在输入 → 副标题显示「对方正在输入…」，状态位 3s 显 2s 隐由 VM 控制（当前用 typing state 简化）
                            if (typingState) {
                                Text(
                                    text = stringResource(R.string.chat_typing_subtitle),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .padding(start = Tokens.SpacingSm)
                                        .semantics { liveRegion = LiveRegionMode.Polite },
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        if (onOpenDrawer != null) {
                            IconButton(onClick = onOpenDrawer) {
                                Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.chat_menu))
                            }
                        }
                    },
                    actions = {
                        TextButton(onClick = { viewModel.poke() }) { Text(stringResource(R.string.chat_poke)) }
                        if (isContinuous) {
                            TextButton(onClick = { viewModel.startNewTopic() }) { Text(stringResource(R.string.chat_new_topic)) }
                        } else {
                            TextButton(onClick = { viewModel.newSession() }) { Text(stringResource(R.string.chat_new_chat)) }
                        }
                        Box {
                            IconButton(onClick = { showMoreMenu = true }) {
                                Icon(YuIcons.More, contentDescription = stringResource(R.string.chat_more))
                            }
                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.chat_export_markdown)) },
                                    onClick = {
                                        showMoreMenu = false
                                        sessionId?.let { viewModel.exportSessionMarkdown(it) }
                                    },
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = paneBg,
                        scrolledContainerColor = paneBg,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                        actionIconContentColor = MaterialTheme.colorScheme.primary,
                    ),
                )
                }
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding(),
            ) {
                val listState = rememberLazyListState()
                LaunchedEffect(listState) {
                    snapshotFlow { listState.firstVisibleItemScrollOffset.toFloat() }
                        .collect { onScrollOffset(it) }
                }
                var instantScroll by remember(sessionId) { mutableStateOf(true) }
                val displayMessages = if (isContinuous) allMessages else messages
                // v3 §14.5：只渲染最近 N 条，上滑到顶再逐页放出更早的历史
                val visibleMessages = remember(displayMessages, historyLimit) {
                    if (displayMessages.size > historyLimit) displayMessages.takeLast(historyLimit) else displayMessages
                }
                // 引用回复：id -> 被引用消息正文（M1-09）
                val quoteMap = remember(displayMessages) {
                    displayMessages.associate { it.id to it.content }
                }
                val context = LocalContext.current
                val timeline = remember(visibleMessages, showTopicDividers, isContinuous, lastReadMessageId) {
                    if (isContinuous) buildTimeline(context, visibleMessages, showTopicDividers, lastReadMessageId) else emptyList()
                }
                val lastMessageGroupIds = remember(visibleMessages) {
                    visibleMessages.mapIndexedNotNull { index, message ->
                        val next = visibleMessages.getOrNull(index + 1)
                        if (isLastInGroup(message.role, message.createdAt, next?.role, next?.createdAt ?: 0L)) message.id else null
                    }.toSet()
                }
                // 滚到顶部时继续放出更早的消息（key 不含 historyLimit，避免加载历史时自动跳到底部）
                val canLoadOlder = rememberUpdatedState(visibleMessages.size < displayMessages.size)
                LaunchedEffect(listState, displayMessages.size) {
                    snapshotFlow { listState.firstVisibleItemIndex }
                        .collect { index ->
                            if (index == 0 && canLoadOlder.value) {
                                historyLimit += HISTORY_PAGE_SIZE
                            }
                        }
                }
                LaunchedEffect(displayMessages.size, streaming.isNotEmpty(), typing) {
                    val extra = (if (typing) 1 else 0) + (if (streaming.isNotEmpty()) 1 else 0)
                    val count = if (isContinuous) timeline.size else visibleMessages.size
                    val target = count + extra - 1
                    if (target >= 0) {
                        // 首次进入/切换会话时直接跳到底部，避免高速滚过整段历史；后续新消息才用动画
                        if (instantScroll || motion.reduced) {
                            listState.scrollToItem(target)
                            instantScroll = false
                        } else {
                            listState.animateScrollToItem(target)
                        }
                    }
                }

                // v3 H-11 / 4.11：纠偏条——有未撤销的纠偏时顶部展示，可撤销
                val latestCorrection by viewModel.latestCorrection.collectAsStateWithLifecycle()
                latestCorrection?.let { corr ->
                    CorrectionBanner(
                        trigger = corr.trigger,
                        onRevoke = { viewModel.revokeCorrection() },
                    )
                }

                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val sizeClass = windowSizeClassFor(maxWidth)
                    val fraction = if (sizeClass == WindowSizeClass.COMPACT) {
                        Tokens.BubbleMaxFractionCompact
                    } else {
                        Tokens.BubbleMaxFractionWide
                    }
                    val bubbleMaxWidth = (maxWidth * fraction).coerceAtMost(Tokens.SizeBubbleMax)
                    // v3 §7.2：底部 FAB 「↓ 新消息 N」——滚离底部时出现
                    val atBottom by remember(listState) {
                        androidx.compose.runtime.derivedStateOf {
                            val layout = listState.layoutInfo
                            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
                            lastVisible >= layout.totalItemsCount - 1 || layout.totalItemsCount == 0
                        }
                    }
                    val scope = rememberCoroutineScope()
                    // v3 §14.5 手势地图：列表已在顶部时继续下拉 → 进入「翻旧账」搜索
                    val pullToSearch = remember(listState, onOpenSearch) {
                        object : NestedScrollConnection {
                            private var pull = 0f
                            override fun onPostScroll(
                                consumed: Offset,
                                available: Offset,
                                source: NestedScrollSource,
                            ): Offset {
                                val atTop = listState.firstVisibleItemIndex == 0 &&
                                    listState.firstVisibleItemScrollOffset == 0
                                if (available.y > 0f && atTop) {
                                    pull += available.y
                                    if (pull >= 220f) {
                                        pull = 0f
                                        onOpenSearch()
                                    }
                                } else if (available.y < 0f) {
                                    pull = 0f
                                }
                                return Offset.Zero
                            }

                            override suspend fun onPreFling(available: Velocity): Velocity {
                                pull = 0f
                                return Velocity.Zero
                            }
                        }
                    }
                    Box(Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().nestedScroll(pullToSearch),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = Tokens.SpacingLg, end = Tokens.SpacingLg, top = 10.dp, bottom = 10.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (isContinuous) {
                            timeline.forEach { item ->
                                when (item) {
                                    is TimelineItem.DateHeader -> item(
                                        key = "d-${item.anchorId}",
                                        contentType = "date",
                                    ) { DateHeaderText(item.label, onImage = hasBackground) }
                                    is TimelineItem.TopicDivider -> item(
                                        key = "topic-${item.anchorId}",
                                        contentType = "topic",
                                    ) { TopicDividerText(onImage = hasBackground) }
                                    is TimelineItem.UnreadDivider -> item(
                                        key = "unread-${item.anchorId}",
                                        contentType = "unread",
                                    ) { UnreadDividerText(onImage = hasBackground) }
                                    is TimelineItem.Msg -> item(
                                        key = "m-${item.message.id}",
                                        contentType = "msg",
                                    ) {
                                        MessageRow(
                                            message = item.message,
                                            sticker = item.message.stickerId?.let(stickerById::get),
                                            grouped = item.grouped,
                                            showTime = item.showTime,
                                            lastInGroup = item.message.id in lastMessageGroupIds,
                                            density = messageDensity,
                                            maxBubbleWidth = bubbleMaxWidth,
                                            onHide = { viewModel.hideMessage(item.message.id) },
                                            onLongPress = { menuMessage = item.message },
                                            onDoubleTapHeart = { viewModel.react(item.message.id, "❤️") },
                                            onToggleReaction = { emoji -> viewModel.react(item.message.id, emoji) },
                                            onImage = hasBackground,
                                            onPlayVoice = { viewModel.playVoice(item.message.id, item.message.content) },
                                            isPlaying = playingMessageId == item.message.id,
                                            isSynthesizing = synthesizingMessageId == item.message.id,
                                            quoteText = item.message.quoteToId?.let { quoteMap[it] },
                                            herReaction = herReactions[item.message.id.toString()],
                                            onRetry = { viewModel.resend(item.message.id) },
                                            selected = selectedIds.contains(item.message.id),
                                            selectionMode = selectionMode,
                                            onToggleSelect = {
                                                selectedIds = if (selectedIds.contains(item.message.id))
                                                    selectedIds - item.message.id
                                                else selectedIds + item.message.id
                                            },
                                            lastReadMessageId = lastReadMessageId,
                                        )
                                    }
                                }
                            }
                        } else {
                            itemsIndexed(visibleMessages, key = { _, it -> it.id }) { index, message ->
                                val prev = visibleMessages.getOrNull(index - 1)
                                val showTime = prev == null || message.createdAt - prev.createdAt >= TIME_GAP_FOR_DISPLAY_MS
                                MessageRow(
                                    message = message,
                                    sticker = message.stickerId?.let(stickerById::get),
                                    showTime = showTime,
                                    lastInGroup = message.id in lastMessageGroupIds,
                                    density = messageDensity,
                                    maxBubbleWidth = bubbleMaxWidth,
                                    onHide = { viewModel.hideMessage(message.id) },
                                    onLongPress = { menuMessage = message },
                                    onDoubleTapHeart = { viewModel.react(message.id, "❤️") },
                                    onToggleReaction = { emoji -> viewModel.react(message.id, emoji) },
                                    onImage = hasBackground,
                                    onPlayVoice = { viewModel.playVoice(message.id, message.content) },
                                    isPlaying = playingMessageId == message.id,
                                    isSynthesizing = synthesizingMessageId == message.id,
                                    quoteText = message.quoteToId?.let { quoteMap[it] },
                                    herReaction = herReactions[message.id.toString()],
                                    onRetry = { viewModel.resend(message.id) },
                                    selected = selectedIds.contains(message.id),
                                    selectionMode = selectionMode,
                                    onToggleSelect = {
                                        selectedIds = if (selectedIds.contains(message.id))
                                            selectedIds - message.id
                                        else selectedIds + message.id
                                    },
                                    lastReadMessageId = lastReadMessageId,
                                )
                            }
                        }
                        if (streaming.isNotEmpty()) {
                            item("streaming") {
                                Bubble(text = streaming, isUser = false, pending = true)
                            }
                        } else if (typing) {
                            item("typing") {
                                Text(
                                    stringResource(R.string.chat_typing),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .padding(start = 6.dp)
                                        .semantics { liveRegion = LiveRegionMode.Polite },
                                )
                            }
                        }
                    }
                    // 空会话/空列表时给一句引导，避免出现一片空白
                    if (visibleMessages.isEmpty() && streaming.isEmpty() && !typing) {
                        YuEmptyView(
                            kind = YuIllustrationKind.CHAT,
                            message = stringResource(R.string.chat_empty_hint),
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                    // v3 §7.2：底部 FAB —— 滚离底部时显示，点击回到最新
                    if (!atBottom && visibleMessages.isNotEmpty()) {
                        val fabDescription = stringResource(R.string.chat_a11y_fab_scroll_bottom)
                        val totalItems = listState.layoutInfo.totalItemsCount
                        val newCount = (visibleMessages.size - (totalItems - (if (isContinuous) timeline.size else visibleMessages.size))).coerceAtLeast(0)
                        FloatingActionButton(
                            onClick = {
                                scope.launch {
                                    val target = listState.layoutInfo.totalItemsCount - 1
                                    if (target >= 0) listState.animateScrollToItem(target)
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 64.dp)
                                .size(Tokens.SizeTouchTarget)
                                .semantics {
                                    contentDescription = fabDescription
                                },
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.chat_fab_scroll_bottom),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                    }
                }

                error?.let { message ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = Tokens.SpacingLg, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            message,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (canContinue) {
                            TextButton(onClick = { viewModel.continueReply() }) { Text(stringResource(R.string.chat_continue)) }
                        }
                        TextButton(onClick = { viewModel.dismissError() }) { Text(stringResource(R.string.chat_dismiss_error)) }
                    }
                }

                pendingSearchContext?.let { ctx ->
                    SearchContextBanner(context = ctx, onClear = { viewModel.clearSearchContext() })
                }

                AnimatedVisibility(
                    visible = showStickers,
                    enter = slideInVertically(animationSpec = yuTween(260)) { it / 3 } +
                        fadeIn(animationSpec = yuTween(200)),
                    exit = slideOutVertically(animationSpec = yuTween(200)) { it / 3 } +
                        fadeOut(animationSpec = yuTween(160)),
                ) {
                    // v3 §7.4.7：表情面板分「表情包 / Emoji」两个 tab，高度不超过屏高 40%
                    val maxPanel = (LocalConfiguration.current.screenHeightDp * 0.4f).dp
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = maxPanel)
                            .background(MaterialTheme.colorScheme.surface),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            PanelTab(
                                label = stringResource(R.string.chat_sticker_tab),
                                selected = stickerTab == 0,
                                onClick = { stickerTab = 0 },
                            )
                            PanelTab(
                                label = stringResource(R.string.chat_emoji_tab),
                                selected = stickerTab == 1,
                                onClick = { stickerTab = 1 },
                            )
                        }
                        if (stickerTab == 0) {
                            StickerPanel(
                                stickers = stickers,
                                packs = stickerPacks,
                                onManage = {
                                    showStickers = false
                                    onOpenStickers()
                                },
                                onPick = { sticker ->
                                    viewModel.sendSticker(sticker.id)
                                    showStickers = false
                                },
                                onPreview = { stickerPreview = it },
                            )
                        } else {
                            EmojiPanel(onPick = { emoji -> pendingEmoji = emoji })
                        }
                    }
                }

                // 表情长按预览（v3 §7.4.8）
                stickerPreview?.let { preview ->
                    AlertDialog(
                        onDismissRequest = { stickerPreview = null },
                        text = {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                StickerArt(sticker = preview, size = 180.dp)
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { stickerPreview = null }) {
                                Text(stringResource(R.string.chat_cancel))
                            }
                        },
                    )
                }

                // 引用回复预览条（M1-09）
                val quoted by viewModel.quoteMessage.collectAsStateWithLifecycle()
                quoted?.let { q ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(horizontal = Tokens.SpacingMd, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(Tokens.SpacingXl)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                        )
                        Spacer(Modifier.width(Tokens.SpacingSm))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(
                                    R.string.chat_reply_prefix,
                                    if (q.role == ROLE_USER) {
                                        stringResource(R.string.chat_self)
                                    } else {
                                        stringResource(R.string.chat_her_name)
                                    },
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                q.content.take(50),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(onClick = { viewModel.cancelQuote() }) {
                            Icon(YuIcons.Close, contentDescription = stringResource(R.string.chat_cancel_quote))
                        }
                    }
                }

                InputBar(
                    viewModel = viewModel,
                    identityId = current?.identityId,
                    enabled = !busy && !selectionMode,
                    stickersOpen = showStickers,
                    onToggleStickers = { showStickers = !showStickers },
                    onCloseStickers = { showStickers = false },
                    insertText = pendingEmoji,
                    onInserted = { pendingEmoji = null },
                    onDoubleTapInput = { seed ->
                        fullInputSeed = seed
                        showFullInput = true
                    },
                    onSendSplit = { chunks -> viewModel.sendSplit(chunks) },
                    onSend = { text ->
                        // v3 H-22：发言时标记当前最新消息为已读
                        allMessages.maxOfOrNull { it.id }?.let { viewModel.markChatRead(it) }
                        viewModel.send(text)
                    },
                )
            }
        }

        // v3 §14.5：双击输入栏进入全屏输入
        if (showFullInput) {
            FullScreenInputDialog(
                initial = fullInputSeed,
                onDismiss = { showFullInput = false },
                onSend = { value ->
                    viewModel.send(value)
                    showFullInput = false
                },
            )
        }

        // 长按操作面板（v3 §7.4.1）：6 个常用表情 + 常用 4 项 + 「更多」二级菜单
        menuMessage?.let { target ->
            val now = System.currentTimeMillis()
            // 撤回 / 编辑都限制在发送后 2 分钟内
            val canRecall = target.role == ROLE_USER && !target.retracted &&
                now - target.createdAt <= RECALL_WINDOW_MS
            val hasLaterUserMessage = messages.any {
                it.role == ROLE_USER && it.createdAt > target.createdAt
            }
            val myReactions = target.reactionsJson.split(',').filter { it.isNotBlank() }
            ModalBottomSheet(
                onDismissRequest = { menuMessage = null },
                sheetState = rememberModalBottomSheetState(),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Tokens.SpacingPage, vertical = Tokens.SpacingSm),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        // v3 §7.4.2：👍 ❤️ 😂 😮 😢 🎉，同一表情再点一次取消
                        QUICK_REACTIONS.forEach { emoji ->
                            val active = emoji in myReactions
                            val reactDesc = stringResource(R.string.chat_a11y_react_with, emoji)
                            Box(
                                modifier = Modifier
                                    .size(Tokens.SizeTouchTarget)
                                    .clip(CircleShape)
                                    .background(
                                        if (active) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            Color.Transparent
                                        },
                                    )
                                    .clickable {
                                        viewModel.react(target.id, emoji)
                                        menuMessage = null
                                    }
                                    .semantics { contentDescription = reactDesc },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(text = emoji, fontSize = 24.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(Tokens.SpacingSm))
                    val actions = buildList {
                        if (target.content.isNotBlank()) {
                            add(MenuAction(R.string.chat_copy) {
                                clipboard.setText(
                                    AnnotatedString(
                                        target.content.split("\n")
                                            .filterNot { it.startsWith(ACTION_PREFIX) }
                                            .joinToString("\n"),
                                    ),
                                )
                                menuMessage = null
                            })
                        }
                        if (target.content.isNotBlank() && !target.retracted) {
                            add(MenuAction(R.string.chat_quote_reply) {
                                viewModel.quote(target)
                                menuMessage = null
                            })
                            // v3 §7.9 / B-31：朗读 / 停止朗读（语速、音调跟随设置）
                            val speaking = speakingMessageId == target.id
                            add(
                                MenuAction(
                                    if (speaking) R.string.chat_tts_stop else R.string.chat_tts_speak,
                                ) {
                                    if (speaking) {
                                        tts.stop()
                                        speakingMessageId = null
                                    } else if (
                                        tts.speak(
                                            target.content.split("\n")
                                                .filterNot { it.startsWith(ACTION_PREFIX) }
                                                .joinToString("\n"),
                                            settings.ttsSpeechRate,
                                            settings.ttsPitch,
                                        )
                                    ) {
                                        speakingMessageId = target.id
                                    }
                                    menuMessage = null
                                },
                            )
                        }
                        if (canRecall) {
                            add(MenuAction(R.string.chat_edit) {
                                menuMessage = null
                                editingMessage = target
                            })
                            add(MenuAction(R.string.chat_retract) {
                                menuMessage = null
                                confirmRetract = target
                            })
                        }
                        if (target.role == ROLE_ASSISTANT) {
                            add(
                                MenuAction(
                                    labelRes = R.string.chat_regenerate,
                                    enabled = !hasLaterUserMessage,
                                    hintRes = if (hasLaterUserMessage) R.string.chat_regenerate_blocked else null,
                                ) {
                                    menuMessage = null
                                    if (!hasLaterUserMessage) viewModel.regenerate(target)
                                },
                            )
                            if (target.replyGroupId != null) {
                                add(MenuAction(R.string.chat_view_versions) {
                                    menuMessage = null
                                    versionTarget = target
                                })
                            }
                        }
                        if (!target.imageUri.isNullOrBlank()) {
                            add(MenuAction(R.string.chat_save_image) {
                                menuMessage = null
                                viewModel.saveImageToGallery(target.imageUri!!)
                            })
                        }
                        if (target.content.isNotBlank()) {
                            add(MenuAction(R.string.chat_remember) {
                                menuMessage = null
                                viewModel.rememberMessage(target)
                            })
                        }
                        add(MenuAction(R.string.chat_poke_her) {
                            menuMessage = null
                            viewModel.poke()
                        })
                        add(MenuAction(R.string.chat_multi_select) {
                            menuMessage = null
                            selectionMode = true
                            selectedIds = setOf(target.id)
                        })
                        // 翻译：工程执行书标注「暂不做」，保留禁用项做可发现性
                        add(MenuAction(R.string.chat_translate_pending, enabled = false) {})
                        add(MenuAction(R.string.chat_hide_message) {
                            menuMessage = null
                            confirmHide = target
                        })
                    }
                    actions.take(4).forEach { MenuActionRow(it) }
                    val more = actions.drop(4)
                    if (more.isNotEmpty()) {
                        TextButton(
                            onClick = { showMoreMenuItems = !showMoreMenuItems },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                stringResource(
                                    if (showMoreMenuItems) R.string.chat_menu_less else R.string.chat_menu_more,
                                ),
                            )
                        }
                        if (showMoreMenuItems) more.forEach { MenuActionRow(it) }
                    }
                    Spacer(Modifier.height(Tokens.SpacingLg))
                }
            }
        }

        // 历史版本弹层（M3-06）：同组多版本可切换，旧版本只是被 soft hidden，不删除
        versionTarget?.let {
            ModalBottomSheet(
                onDismissRequest = { versionTarget = null },
                sheetState = rememberModalBottomSheetState(),
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = Tokens.SpacingPage, vertical = Tokens.SpacingSm)) {
                    Text(stringResource(R.string.chat_versions_title), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(Tokens.SpacingXs))
                    Text(
                        stringResource(R.string.chat_versions_desc, versions.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(versions, key = { it.id }) { version ->
                            val isCurrent = !version.hidden
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(Tokens.SpacingMd))
                                    .background(
                                        if (isCurrent) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                        }
                                    )
                                    .clickable {
                                        val groupId = version.replyGroupId
                                        if (groupId != null) {
                                            viewModel.switchVersion(groupId, version.id)
                                            versionTarget = null
                                        }
                                    }
                                    .padding(horizontal = Tokens.SpacingMd, vertical = 10.dp),
                            ) {
                                Column {
                                    Text(
                                        if (isCurrent) {
                                            stringResource(R.string.chat_version_label_current, versions.indexOf(version) + 1)
                                        } else {
                                            stringResource(R.string.chat_version_label, versions.indexOf(version) + 1)
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        versionPreview(version.content, stringResource(R.string.chat_version_empty)),
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(Tokens.SpacingLg))
                }
            }
        }

        confirmHide?.let { target ->
            AlertDialog(
                onDismissRequest = { confirmHide = null },
                title = { Text(stringResource(R.string.chat_hide_confirm_title)) },
                text = { Text(stringResource(R.string.chat_hide_confirm_text)) },
                confirmButton = {
                    TextButton(onClick = {
                        confirmHide = null
                        viewModel.hideMessage(target.id)
                    }) { Text(stringResource(R.string.chat_hide)) }
                },
                dismissButton = { TextButton(onClick = { confirmHide = null }) { Text(stringResource(R.string.chat_cancel)) } },
            )
        }

        confirmRetract?.let { target ->
            AlertDialog(
                onDismissRequest = { confirmRetract = null },
                title = { Text(stringResource(R.string.chat_retract_confirm_title)) },
                text = { Text(stringResource(R.string.chat_retract_confirm_text)) },
                confirmButton = {
                    TextButton(onClick = {
                        val id = target.id
                        confirmRetract = null
                        viewModel.retract(id)
                    }) { Text(stringResource(R.string.chat_retract)) }
                },
                dismissButton = { TextButton(onClick = { confirmRetract = null }) { Text(stringResource(R.string.chat_cancel)) } },
            )
        }

        if (confirmDeleteSelected) {
            AlertDialog(
                onDismissRequest = { confirmDeleteSelected = false },
                title = { Text(stringResource(R.string.chat_delete_selected_title, selectedIds.size)) },
                text = { Text(stringResource(R.string.chat_hide_confirm_text)) },
                confirmButton = {
                    TextButton(onClick = {
                        val ids = selectedIds
                        confirmDeleteSelected = false
                        selectionMode = false
                        selectedIds = emptySet()
                        viewModel.hideMessages(ids)
                    }) { Text(stringResource(R.string.chat_delete)) }
                },
                dismissButton = { TextButton(onClick = { confirmDeleteSelected = false }) { Text(stringResource(R.string.chat_cancel)) } },
            )
        }

        editingMessage?.let { target ->
            var text by remember(target.id) { mutableStateOf(target.content) }
            AlertDialog(
                onDismissRequest = { editingMessage = null },
                title = { Text(stringResource(R.string.chat_edit_message_title)) },
                text = {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val newText = text
                            editingMessage = null
                            if (newText.isNotBlank()) viewModel.editAndResend(target.id, newText)
                        },
                        enabled = text.isNotBlank(),
                    ) { Text(stringResource(R.string.chat_send)) }
                },
                dismissButton = { TextButton(onClick = { editingMessage = null }) { Text(stringResource(R.string.chat_cancel)) } },
            )
        }
    }
}

@Composable
private fun SearchContextBanner(
    context: String,
    onClear: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Tokens.SpacingLg, vertical = Tokens.SpacingSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.chat_search_context_prefix, context.take(40)) + if (context.length > 40) "…" else "",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(onClick = onClear) { Text(stringResource(R.string.chat_cancel)) }
        }
    }
}

@Composable
private fun RenameSessionDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var title by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_rename_title)) },
        text = {
            OutlinedTextField(value = title, onValueChange = { title = it }, singleLine = true)
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(title.trim()) }, enabled = title.isNotBlank()) { Text(stringResource(R.string.chat_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.chat_cancel)) } },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageRow(
    message: MessageEntity,
    sticker: StickerEntity? = null,
    /** v3 §7.3.3：与上一条同作者且间隔<60s，省略头像、收紧间距 */
    grouped: Boolean = false,
    /** v3 §7.3.2：间隔≥5min / 换天 / 换会话时渲染时间行 */
    showTime: Boolean = true,
    lastInGroup: Boolean = true,
    /** v3 H-22 / 1.3#48：消息密度，控制气泡上下间距 */
    density: MessageDensity = MessageDensity.STANDARD,
    maxBubbleWidth: Dp,
    onHide: () -> Unit,
    onLongPress: () -> Unit = {},
    /** v3 §7.4.6：双击气泡 = ❤️ reaction */
    onDoubleTapHeart: () -> Unit = {},
    /** v3 §7.4.4：点下方头像上的表情徽标可取消该 reaction */
    onToggleReaction: (String) -> Unit = {},
    onImage: Boolean = false,
    onPlayVoice: (() -> Unit)? = null,
    isPlaying: Boolean = false,
    isSynthesizing: Boolean = false,
    quoteText: String? = null,
    /** 她回给你的 reaction（M3-05），存在 Settings 里，不占表字段 */
    herReaction: String? = null,
    onRetry: (() -> Unit)? = null,
    selected: Boolean = false,
    selectionMode: Boolean = false,
    onToggleSelect: () -> Unit = {},
    /** 已读位置：仅用于无障碍朗读「已读 / 未读」（§7.15） */
    lastReadMessageId: Long = 0L,
) {
    val isUser = message.role == ROLE_USER
    val isNotice = message.role == ROLE_NOTICE
    // v3 §7.4.4：超过 3 个 reaction 折叠成「+N」
    var reactionsExpanded by remember(message.id) { mutableStateOf(false) }

    if (isNotice) {
        Text(
            message.content,
            modifier = Modifier.fillMaxWidth().padding(vertical = Tokens.SpacingXs),
            style = MaterialTheme.typography.labelMedium,
            color = if (onImage) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        return
    }

    // 撤回占位（M3-07）：正文不展示，只留一条居中提示
    if (message.retracted) {
        Text(
            stringResource(R.string.chat_retracted_placeholder),
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        return
    }

    // v3 §7.15 / M5-07：整条消息合并成一句描述，含发送者、正文、时间、已读与 reaction
    val senderName = stringResource(if (isUser) R.string.chat_self else R.string.chat_her_name)
    val spokenBody = when {
        message.imageUri != null -> stringResource(R.string.chat_a11y_image)
        message.type == TYPE_STICKER -> stringResource(R.string.chat_a11y_sticker)
        else -> message.content
    }
    val myReactions = message.reactionsJson.split(',').filter { it.isNotBlank() }
    val spokenReaction = listOfNotNull(
        myReactions.takeIf { it.isNotEmpty() }?.let {
            stringResource(R.string.chat_a11y_my_reaction, it.joinToString(" "))
        },
        herReaction?.let { stringResource(R.string.chat_a11y_her_reaction, it) },
    ).joinToString("，")
    val spokenStatus = if (isUser) {
        stringResource(if (message.id <= lastReadMessageId) R.string.chat_a11y_read else R.string.chat_a11y_unread)
    } else {
        ""
    }
    val messageDescription = stringResource(
        R.string.chat_a11y_message,
        senderName,
        spokenBody,
        listOf(
            timeFormat.format(Date(message.createdAt)),
            spokenStatus,
            spokenReaction,
            if (selectionMode) {
                stringResource(if (selected) R.string.chat_a11y_selected else R.string.chat_a11y_select)
            } else {
                ""
            },
        )
            .filter { it.isNotBlank() }
            .joinToString("，"),
    )

    Box(
        // v3 H-22 / 1.3#48：连续同作者消息间距减半，正常间距由消息密度档位决定
        modifier = Modifier.fillMaxWidth().padding(
            top = if (grouped) (density.verticalPaddingDp / 2).dp else density.verticalPaddingDp.dp
        ),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = maxBubbleWidth),
        ) {
            // 引用回复的灰色引用条（M1-09）
            quoteText?.takeIf { it.isNotBlank() }?.let { quoted ->
                Row(
                    modifier = Modifier
                        .padding(bottom = 3.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            RoundedCornerShape(Tokens.SpacingSm),
                        )
                        .padding(horizontal = Tokens.SpacingSm, vertical = Tokens.SpacingXs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(28.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        quoted.take(60).let { if (quoted.length > 60) "$it…" else it },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = maxBubbleWidth - 40.dp),
                    )
                }
            }
            val haptic = LocalHapticFeedback.current
            Box(
                modifier = Modifier
                    .semantics(mergeDescendants = true) { contentDescription = messageDescription }
                    .combinedClickable(
                    onClick = {
                        if (selectionMode) onToggleSelect()
                    },
                    // v3 §7.4.6：双击气泡 = ❤️ reaction（已反应再次双击即取消）
                    onDoubleClick = {
                        if (!selectionMode) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onDoubleTapHeart()
                        }
                    },
                    onLongClick = {
                        if (!selectionMode) {
                            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                            onLongPress()
                        }
                    },
                )
            ) {
                Column {
                    if (selectionMode) {
                        Box(
                            modifier = Modifier
                                .size(Tokens.SpacingPage)
                                .clip(CircleShape)
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                )
                                .border(
                                    1.5.dp,
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline,
                                    CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(Tokens.SpacingXs))
                    }
                    message.imageUri?.let { path ->
                        val imgFile = java.io.File(path)
                        if (imgFile.exists()) {
                            FileImage(
                                file = imgFile,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 280.dp)
                                    .clip(RoundedCornerShape(14.dp)),
                            )
                            Spacer(Modifier.height(Tokens.SpacingXs))
                        }
                    }
                    if (message.type == TYPE_STICKER) {
                        // 表情消息：无气泡、无背景（执行书 7.4.4）
                        StickerArt(sticker = sticker, size = 132.dp)
                    } else if (message.type == TYPE_CARD) {
                        // v3 ADR-15 / 7.4.5：结构化卡片消息
                        CardBubble(rawContent = message.content)
                    } else {
                        Bubble(text = message.content, isUser = isUser, showTail = lastInGroup)
                    }
                }
            }
            // 照片背景上加一层浅底，时间戳不会糊进图片里
            val timeModifier = if (onImage) {
                Modifier
                    .padding(top = 3.dp, start = Tokens.SpacingXs, end = Tokens.SpacingXs)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), RoundedCornerShape(50))
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            } else {
                Modifier.padding(top = 3.dp, start = Tokens.SpacingXs, end = Tokens.SpacingXs)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!isUser && onPlayVoice != null && message.content.isNotBlank()) {
                    when {
                        isSynthesizing -> TextButton(
                            onClick = onPlayVoice,
                            modifier = Modifier.heightIn(min = Tokens.SizeTouchTarget),
                        ) { Text(stringResource(R.string.chat_synthesizing), style = MaterialTheme.typography.labelSmall) }
                        isPlaying -> TextButton(
                            onClick = onPlayVoice,
                            modifier = Modifier.heightIn(min = Tokens.SizeTouchTarget),
                        ) { Text(stringResource(R.string.chat_stop), style = MaterialTheme.typography.labelSmall) }
                        else -> IconButton(
                            onClick = onPlayVoice,
                            modifier = Modifier.size(Tokens.SizeTouchTarget),
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = stringResource(R.string.chat_read_aloud),
                                tint = if (onImage) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                // v3 §7.3.2：间隔 <5min 时不渲染时间行，消息密度显著提升
                if (showTime) {
                    Text(
                        timeFormat.format(Date(message.createdAt)),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (onImage) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = timeModifier,
                    )
                }
                // 编辑标记（M3-07）
                if (message.editedAt != null) {
                    Text(
                        stringResource(R.string.chat_edited),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 2.dp),
                    )
                }
                // 发送状态（M1-09）：发送中 / 失败可重发
                when (message.status) {
                    STATUS_SENDING -> Text(
                        stringResource(R.string.chat_sending),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = Tokens.SpacingXs),
                    )
                    STATUS_FAILED -> {
                        val resendDesc = stringResource(R.string.chat_a11y_resend)
                        TextButton(
                            onClick = { onRetry?.invoke() },
                            enabled = onRetry != null,
                            modifier = Modifier
                                .size(Tokens.SizeTouchTarget)
                                .semantics { contentDescription = resendDesc },
                        ) {
                            Text(
                                "!",
                                color = MaterialTheme.colorScheme.error,
                                style = LocalYuType.current.section,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
            // v3 §7.4.4：reaction 显示在气泡下方，超过 3 个折叠为「+N」，点徽标可取消
            val myReactions = remember(message.reactionsJson) {
                message.reactionsJson.split(',').filter { it.isNotBlank() }
            }
            if (myReactions.isNotEmpty() || herReaction != null) {
                Row(
                    modifier = Modifier.padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val shown = if (reactionsExpanded || myReactions.size <= 3) {
                        myReactions
                    } else {
                        myReactions.take(3)
                    }
                    shown.forEach { emoji ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f))
                                .clickable { onToggleReaction(emoji) }
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(emoji, fontSize = 14.sp)
                        }
                    }
                    if (!reactionsExpanded && myReactions.size > 3) {
                        val showAll = stringResource(R.string.chat_a11y_show_reactions)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f))
                                .clickable { reactionsExpanded = true }
                                .semantics { contentDescription = showAll }
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                stringResource(R.string.chat_reaction_more, myReactions.size - 3),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    herReaction?.let { emoji ->
                        Text(
                            stringResource(R.string.chat_her_reaction, emoji),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

/** v3 H-11 / 4.11：纠偏条——提示已记录风格纠偏，可点击撤销。 */
@Composable
private fun CorrectionBanner(trigger: String, onRevoke: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Tokens.SpacingMd, vertical = Tokens.SpacingXs),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Tokens.SpacingMd, vertical = Tokens.SpacingSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.chat_correction_recorded, trigger),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier
                    .weight(1f)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
            TextButton(onClick = onRevoke) {
                Text(stringResource(R.string.chat_correction_revoke), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun Bubble(text: String, isUser: Boolean, pending: Boolean = false, showTail: Boolean = true) {
    val bubbles = LocalBubbleColors.current
    val visual = LocalBubbleVisual.current
    val bg = (if (isUser) bubbles.myBackground else bubbles.herBackground)
        .copy(alpha = visual.alpha)
    val fg = if (isUser) bubbles.myContent else bubbles.herContent
    val shape = RoundedCornerShape(
        topStart = Tokens.RadiusBubble,
        topEnd = Tokens.RadiusBubble,
        bottomStart = if (!isUser && showTail) Tokens.RadiusBubbleTail else Tokens.RadiusBubble,
        bottomEnd = if (isUser && showTail) Tokens.RadiusBubbleTail else Tokens.RadiusBubble,
    )
    val textStyle = if (visual.textShadow) {
        LocalYuType.current.chat.copy(
            shadow = Shadow(
                color = Color.Black.copy(alpha = 0.65f),
                offset = Offset(0.5f, 1f),
                blurRadius = 1.5f,
            ),
        )
    } else {
        LocalYuType.current.chat
    }
    Box {
        if (showTail) {
            Canvas(
                Modifier
                    .align(if (isUser) Alignment.BottomEnd else Alignment.BottomStart)
                    .offset(x = if (isUser) Tokens.RadiusBubbleTail else -Tokens.RadiusBubbleTail)
                    .size(Tokens.RadiusBubbleTail),
            ) {
                val tail = Path().apply {
                    if (isUser) {
                        moveTo(0f, 0f)
                        lineTo(size.width, size.height)
                        lineTo(0f, size.height)
                    } else {
                        moveTo(size.width, 0f)
                        lineTo(0f, size.height)
                        lineTo(size.width, size.height)
                    }
                    close()
                }
                drawPath(tail, bg)
            }
        }
        // 气泡底衬：背景图很花时先铺一层浅色，避免气泡本身透出图案
        if (visual.underlay && visual.alpha < 1f) {
            Box(
                Modifier
                    .matchParentSize()
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)),
            )
        }
        Surface(
            color = bg,
            contentColor = fg,
            shape = shape,
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                text.split("\n").forEach { raw ->
                    val isAction = raw.startsWith(ACTION_PREFIX)
                    val line = if (isAction) raw.removePrefix(ACTION_PREFIX) else raw
                    if (line.isEmpty()) return@forEach
                    Text(
                        text = line,
                        style = if (isAction) {
                            textStyle.copy(fontStyle = FontStyle.Italic)
                        } else {
                            textStyle
                        },
                        color = if (isAction) fg.copy(alpha = 0.8f) else fg,
                    )
                }
                if (pending) Text("▍", style = textStyle)
            }
        }
    }
}

@Composable
private fun InputBar(
    viewModel: AppViewModel,
    identityId: Long?,
    enabled: Boolean,
    stickersOpen: Boolean = false,
    onToggleStickers: () -> Unit = {},
    onCloseStickers: () -> Unit = {},
    insertText: String? = null,
    onInserted: () -> Unit = {},
    onDoubleTapInput: (String) -> Unit = {},
    onSend: (String) -> Unit,
    onSendSplit: (List<String>) -> Unit = { chunks -> chunks.forEach(onSend) },
) {
    var text by rememberSaveable(identityId) { mutableStateOf("") }
    var voiceMode by rememberSaveable { mutableStateOf(false) }
    var holding by remember { mutableStateOf(false) }
    var voiceToggles by remember { mutableIntStateOf(0) }
    // v3 §7.10：一次粘贴超长文本时提示是否拆分发送
    var longPaste by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val textInteraction = remember { MutableInteractionSource() }
    val focused by textInteraction.collectIsFocusedAsState()
    val sendContentDescription = stringResource(R.string.chat_send)
    val context = LocalContext.current

    // 切回该人设时自动恢复草稿；切换人设时保存当前草稿（M1-09）
    LaunchedEffect(identityId) {
        identityId?.let {
            val saved = viewModel.getDraft(it)
            if (!saved.isNullOrBlank()) text = saved
        }
    }
    // v3 §7.4.7：表情面板点 Emoji 后插入到输入框光标末尾
    LaunchedEffect(insertText) {
        if (!insertText.isNullOrEmpty()) {
            text += insertText
            onInserted()
        }
    }
    LaunchedEffect(text, identityId) {
        identityId?.let { viewModel.saveDraft(it, text) }
    }
    // 从「按住说话」切回键盘时补一次焦点；首次进入聊天页不抢焦点，避免自动弹键盘
    LaunchedEffect(voiceMode) {
        if (!voiceMode && voiceToggles > 0) focusRequester.requestFocus()
    }

    // M7-06：优先走系统语音输入（输入法 / 系统语音服务），把识别结果填进输入框
    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                text = if (text.isBlank()) spoken else "$text $spoken"
            }
        }
    }
    val launchVoice: () -> Unit = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PROMPT, context.getString(R.string.chat_voice_prompt))
        }
        try {
            voiceLauncher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            // 设备没有语音识别服务时给一句提示，不弹系统错误页
            viewModel.notifyVoiceUnavailable()
        }
    }

    val haptic = LocalHapticFeedback.current
    val canSend = enabled && text.isNotBlank()
    val send: () -> Unit = {
        if (canSend) {
            // v3 §7.15：发送时短振动确认
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onSend(text)
            text = ""
        }
    }

    val inputBorder by animateColorAsState(
        targetValue = if (focused) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
        animationSpec = yuTween(durationMillis = 180),
        label = "inputBorder",
    )
    val holdBg by animateColorAsState(
        targetValue = if (holding) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        animationSpec = yuTween(durationMillis = 120),
        label = "holdBg",
    )
    val sendScale by animateFloatAsState(
        targetValue = if (canSend) 1f else 0.88f,
        animationSpec = yuSpring(dampingRatio = 0.5f, stiffness = 700f),
        label = "sendScale",
    )
    val sendBg by animateColorAsState(
        targetValue = if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = yuTween(durationMillis = 180),
        label = "sendBg",
    )
    val sendFg by animateColorAsState(
        targetValue = if (canSend) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
        animationSpec = yuTween(durationMillis = 180),
        label = "sendFg",
    )

    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Tokens.SpacingSm, end = 10.dp, top = Tokens.SpacingSm, bottom = Tokens.SpacingMd),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(Tokens.SpacingXs),
        ) {
            BarIconButton(
                onClick = {
                    voiceToggles++
                    voiceMode = !voiceMode
                    if (voiceMode) {
                        keyboard?.hide()
                        focusManager.clearFocus()
                    }
                },
                contentDescription = if (voiceMode) stringResource(R.string.chat_switch_to_keyboard) else stringResource(R.string.chat_voice_input),
            ) {
                if (voiceMode) {
                    KeyboardGlyph(tint = MaterialTheme.colorScheme.primary)
                } else {
                    // v3 G-08：自绘麦克风图标
                    Icon(
                        imageVector = YuIcons.Voice,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(21.dp),
                    )
                }
            }

            if (voiceMode) {
                // 按住说话：松手才唤起系统语音识别，识别结果仍回填到输入框
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 46.dp)
                        .clip(RoundedCornerShape(23.dp))
                        .background(holdBg)
                        .border(1.dp, inputBorder, RoundedCornerShape(23.dp))
                        .pointerInput(Unit) {
                            detectTapGestures(onPress = {
                                holding = true
                                val released = tryAwaitRelease()
                                holding = false
                                if (released) launchVoice()
                            })
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (holding) stringResource(R.string.chat_voice_release) else stringResource(R.string.chat_voice_hold),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (holding) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            } else {
                BasicTextField(
                    value = text,
                    onValueChange = { new ->
                        // 粘贴超长文本：提示「内容较长，是否拆分发送」
                        if (new.length - text.length >= 200 && new.length >= 800) {
                            longPaste = new
                        } else {
                            text = new
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 46.dp)
                        .clip(RoundedCornerShape(23.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                        .border(1.dp, inputBorder, RoundedCornerShape(23.dp))
                        .padding(horizontal = Tokens.SpacingLg, vertical = 11.dp)
                        .focusRequester(focusRequester)
                        .onFocusChanged { if (it.isFocused && stickersOpen) onCloseStickers() }
                        // v3 §14.5：双击输入栏 → 全屏输入（Initial pass 抢在输入框消费前识别）
                        .pointerInput(Unit) {
                            var lastUp = 0L
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                val up = waitForUpOrCancellation(pass = PointerEventPass.Initial)
                                if (up != null) {
                                    val now = up.uptimeMillis
                                    if (now - lastUp <= 280L) {
                                        lastUp = 0L
                                        onDoubleTapInput(text)
                                    } else {
                                        lastUp = now
                                    }
                                }
                            }
                        },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    // v3 §7.10：1~5 行自适应
                    maxLines = 5,
                    interactionSource = textInteraction,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (text.isEmpty()) {
                                Text(
                                    stringResource(R.string.chat_input_placeholder),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                )
                            }
                            innerTextField()
                        }
                    },
                )
            }

            BarIconButton(
                onClick = onToggleStickers,
                contentDescription = if (stickersOpen) stringResource(R.string.chat_collapse_stickers) else stringResource(R.string.chat_open_stickers),
            ) {
                // v3 G-08：自绘表情图标
                Icon(
                    imageVector = YuIcons.Emoji,
                    contentDescription = null,
                    tint = if (stickersOpen) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(21.dp),
                )
            }

            // 语音模式下隐藏发送键：输入区已被「按住说话」占满，留着会让人误以为能发旧草稿
            AnimatedVisibility(
                visible = !voiceMode,
                enter = fadeIn(yuTween(140)) + scaleIn(yuTween(140), initialScale = 0.6f),
                exit = fadeOut(yuTween(120)) + scaleOut(yuTween(120), targetScale = 0.6f),
            ) {
                Box(
                    modifier = Modifier
                        .size(Tokens.SizeTouchTarget)
                        .scale(sendScale)
                        .clip(CircleShape)
                        .background(sendBg)
                        .clickable(enabled = canSend, onClick = send)
                        .semantics { contentDescription = sendContentDescription },
                    contentAlignment = Alignment.Center,
                ) {
                    // v3 G-08：自绘发送图标
                    Icon(
                        imageVector = YuIcons.Send,
                        contentDescription = null,
                        tint = sendFg,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }

    // v3 §7.10：超长粘贴二次确认
    longPaste?.let { pasted ->
        AlertDialog(
            onDismissRequest = { longPaste = null },
            title = { Text(stringResource(R.string.chat_paste_long_title)) },
            text = { Text(stringResource(R.string.chat_paste_long_text)) },
            confirmButton = {
                TextButton(onClick = {
                    longPaste = null
                    text = ""
                    onSendSplit(splitLongText(pasted))
                }) { Text(stringResource(R.string.chat_paste_split)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { longPaste = null }) {
                        Text(stringResource(R.string.chat_cancel))
                    }
                    TextButton(onClick = {
                        text = pasted
                        longPaste = null
                    }) { Text(stringResource(R.string.chat_paste_whole)) }
                }
            },
        )
    }
}

/** 把超长文本按空行拆段，每段不超过 400 字。 */
private fun splitLongText(input: String): List<String> {
    val paragraphs = input.split(Regex("\\n\\s*\\n")).map { it.trim() }.filter { it.isNotEmpty() }
    val chunks = mutableListOf<String>()
    val buffer = StringBuilder()
    fun flush() {
        if (buffer.isNotBlank()) {
            chunks += buffer.toString().trim()
            buffer.clear()
        }
    }
    paragraphs.forEach { paragraph ->
        if (buffer.length + paragraph.length + 2 > 400) flush()
        if (paragraph.length > 400) {
            flush()
            paragraph.chunked(400).forEach { chunks += it }
        } else {
            if (buffer.isNotEmpty()) buffer.append("\n\n")
            buffer.append(paragraph)
        }
    }
    flush()
    return chunks.ifEmpty { listOf(input) }
}

/** 输入栏上的圆形图标键：48dp 命中区 + 圆形水波纹。 */
@Composable
private fun BarIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(Tokens.SizeTouchTarget)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

// Material icons-core 里没有麦克风/键盘/表情，手绘保证四个图标的线宽、圆角一致
private const val GLYPH_BOX = 24f

@Composable
private fun MicGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(21.dp)) {
        val u = size.minDimension / GLYPH_BOX
        val stroke = Stroke(width = 1.9f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawRoundRect(
            color = tint,
            topLeft = Offset(9.3f * u, 2.6f * u),
            size = Size(5.4f * u, 10.4f * u),
            cornerRadius = CornerRadius(2.7f * u),
            style = stroke,
        )
        drawArc(
            color = tint,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(6.6f * u, 6.6f * u),
            size = Size(10.8f * u, 10.8f * u),
            style = stroke,
        )
        drawLine(tint, Offset(12f * u, 17.4f * u), Offset(12f * u, 20.2f * u), 1.9f * u, StrokeCap.Round)
        drawLine(tint, Offset(8.8f * u, 21f * u), Offset(15.2f * u, 21f * u), 1.9f * u, StrokeCap.Round)
    }
}

@Composable
private fun KeyboardGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(21.dp)) {
        val u = size.minDimension / GLYPH_BOX
        val stroke = Stroke(width = 1.8f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawRoundRect(
            color = tint,
            topLeft = Offset(2.6f * u, 6.6f * u),
            size = Size(18.8f * u, 10.8f * u),
            cornerRadius = CornerRadius(2.6f * u),
            style = stroke,
        )
        listOf(6.2f, 9.2f, 12.2f, 15.2f, 18.2f).forEach { x ->
            drawCircle(tint, radius = 0.95f * u, center = Offset(x * u, 10.4f * u))
        }
        drawLine(tint, Offset(8.6f * u, 14.2f * u), Offset(15.4f * u, 14.2f * u), 1.8f * u, StrokeCap.Round)
    }
}

@Composable
private fun SmileyGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(21.dp)) {
        val u = size.minDimension / GLYPH_BOX
        val stroke = Stroke(width = 1.8f * u, cap = StrokeCap.Round)
        drawCircle(tint, radius = 9.2f * u, center = Offset(12f * u, 12f * u), style = stroke)
        drawCircle(tint, radius = 1.1f * u, center = Offset(8.9f * u, 9.8f * u))
        drawCircle(tint, radius = 1.1f * u, center = Offset(15.1f * u, 9.8f * u))
        drawArc(
            color = tint,
            startAngle = 25f,
            sweepAngle = 130f,
            useCenter = false,
            topLeft = Offset(7.2f * u, 7.6f * u),
            size = Size(9.6f * u, 9.6f * u),
            style = stroke,
        )
    }
}

/** 极简纸飞机：两片机翼在机头交汇，中间留一道折痕，小尺寸下也能认出是纸飞机。 */
@Composable
private fun PlaneGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(19.dp)) {
        val u = size.minDimension / GLYPH_BOX
        // 上翼
        drawPath(
            Path().apply {
                moveTo(21.6f * u, 2.6f * u)
                lineTo(2.2f * u, 9.2f * u)
                lineTo(10.4f * u, 12.4f * u)
                close()
            },
            tint,
        )
        // 下翼
        drawPath(
            Path().apply {
                moveTo(21.6f * u, 2.6f * u)
                lineTo(11.2f * u, 13.4f * u)
                lineTo(14.4f * u, 21.8f * u)
                close()
            },
            tint,
        )
    }
}

@Composable
private fun SessionDrawer(
    sessions: List<SessionEntity>,
    archived: List<SessionEntity>,
    currentId: Long?,
    favorability: Int,
    continuous: Boolean,
    onNew: () -> Unit,
    onSelect: (Long) -> Unit,
    onArchive: (Long) -> Unit,
    onUnarchive: (Long) -> Unit,
    onRename: (SessionEntity) -> Unit,
    onOpenMemory: () -> Unit,
    onOpenDiary: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenRelation: () -> Unit,
    onOpenLifeDay: () -> Unit,
) {
    var showArchived by remember { mutableStateOf(false) }
    ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 22.dp)) {
            Text(stringResource(R.string.chat_her_name), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Text(
                stringResource(favorabilityLabelRes(FavorabilityLevel.of(favorability))),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Tokens.SpacingLg))
            Button(onClick = onNew, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Text(if (continuous) stringResource(R.string.chat_drawer_new_topic) else stringResource(R.string.chat_drawer_new_chat))
            }
        }
        if (!continuous) {
            Divider(color = MaterialTheme.colorScheme.surfaceVariant)
            LazyColumn(modifier = Modifier.weight(1f).padding(vertical = Tokens.SpacingSm)) {
                items(sessions, key = { it.id }) { session ->
                    SessionRow(
                        session = session,
                        selected = session.id == currentId,
                        onClick = { onSelect(session.id) },
                        onLongClick = { onRename(session) },
                        onSwipeArchive = { onArchive(session.id) },
                        trailing = { TextButton(onClick = { onArchive(session.id) }) { Text(stringResource(R.string.chat_archive)) } },
                    )
                }
                item {
                    TextButton(onClick = { showArchived = !showArchived }, modifier = Modifier.padding(start = Tokens.SpacingSm)) {
                        Text(
                            if (showArchived) {
                                stringResource(R.string.chat_hide_archived)
                            } else {
                                stringResource(R.string.chat_archived_count, archived.size)
                            },
                        )
                    }
                }
                if (showArchived) {
                    items(archived, key = { "a-${it.id}" }) { session ->
                        SessionRow(
                            session = session,
                            selected = false,
                            onClick = { onSelect(session.id) },
                            onLongClick = { onRename(session) },
                            trailing = { TextButton(onClick = { onUnarchive(session.id) }) { Text(stringResource(R.string.chat_restore)) } },
                        )
                    }
                }
            }
        } else {
            Spacer(Modifier.weight(1f))
        }
        Divider(color = MaterialTheme.colorScheme.surfaceVariant)
        Column(Modifier.padding(vertical = Tokens.SpacingSm)) {
            DrawerEntry(stringResource(R.string.chat_relation), onOpenRelation)
            DrawerEntry(stringResource(R.string.life_day_title), onOpenLifeDay)
            DrawerEntry(stringResource(R.string.chat_search), onOpenSearch)
            DrawerEntry(stringResource(R.string.chat_gallery), onOpenGallery)
            DrawerEntry(stringResource(R.string.chat_memory), onOpenMemory)
            DrawerEntry(stringResource(R.string.chat_diary), onOpenDiary)
            DrawerEntry(stringResource(R.string.chat_settings), onOpenSettings)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionRow(
    session: SessionEntity,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    trailing: @Composable () -> Unit,
    /** v3 §14.5：会话行左滑归档 */
    onSwipeArchive: (() -> Unit)? = null,
) {
    var dragX by remember { mutableStateOf(0f) }
    val swipeThreshold = with(LocalDensity.current) { 96.dp.toPx() }
    val swipeModifier = if (onSwipeArchive == null) {
        Modifier
    } else {
        Modifier.pointerInput(onSwipeArchive) {
            detectHorizontalDragGestures(
                onDragEnd = {
                    if (dragX <= -swipeThreshold) onSwipeArchive()
                    dragX = 0f
                },
                onDragCancel = { dragX = 0f },
                onHorizontalDrag = { change, amount ->
                    change.consume()
                    dragX = (dragX + amount).coerceIn(-swipeThreshold * 1.6f, 0f)
                },
            )
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(dragX.roundToInt(), 0) }
            .then(swipeModifier),
    ) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Tokens.SizeTouchTarget)
            .background(
                if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics(mergeDescendants = true) {}
            .padding(start = 18.dp, end = Tokens.SpacingSm, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                session.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                timeFormat.format(Date(session.lastMessageAt)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        trailing()
    }
    }
}

@Composable
private fun DrawerEntry(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
    }
}

// ---------------- 连续流时间线（PRD 4.2.1） ----------------

private sealed interface TimelineItem {
    data class DateHeader(val label: String, val anchorId: Long) : TimelineItem
    data class TopicDivider(val anchorId: Long) : TimelineItem
    data class UnreadDivider(val anchorId: Long) : TimelineItem
    /**
     * @param grouped 与上一条同作者且间隔<60s，渲染时省略头像、收紧间距（v3 §7.3.3）
     * @param showTime 是否渲染时间行（v3 §7.3.2：间隔≥5min 或换天/换会话时显示）
     */
    data class Msg(val message: MessageEntity, val grouped: Boolean = false, val showTime: Boolean = true) : TimelineItem
}

private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)

private fun dateLabel(context: Context, timestamp: Long): String {
    val now = System.currentTimeMillis()
    val today = dayFormat.format(Date(now))
    val that = dayFormat.format(Date(timestamp))
    if (that == today) return context.getString(R.string.chat_date_today)
    val yesterday = dayFormat.format(Date(now - 24L * 60 * 60 * 1000))
    if (that == yesterday) return context.getString(R.string.chat_date_yesterday)
    return that
}

/** 好感度等级名走资源，保证英文界面不出现中文 */
internal fun favorabilityLabelRes(level: FavorabilityLevel): Int = when (level) {
    FavorabilityLevel.STRANGER -> R.string.chat_level_stranger
    FavorabilityLevel.FAMILIAR -> R.string.chat_level_familiar
    FavorabilityLevel.INTIMATE -> R.string.chat_level_intimate
    FavorabilityLevel.BELOVED -> R.string.chat_level_beloved
}

/**
 * 把跨会话的消息流压成带日期分隔线 +（可选）话题分隔线的时间线。
 * - 日期按天聚合：今天 / 昨天 / 具体日期；
 * - 话题分隔线落在会话切换处，用新会话第一条消息 id 锚定，保证 LazyColumn key 稳定；
 * - v3 §7.3.3：连续同作者且间隔 <60s 标记 grouped，渲染时省略头像；
 * - v3 §7.3.2：间隔 ≥5min 或换天/换会话时 showTime=true，第一条始终显示；
 * - v3 §7.3.3：在 lastReadMessageId 之后插入「以下为新消息」分割线。
 */
private fun buildTimeline(
    context: Context,
    messages: List<MessageEntity>,
    showTopicDividers: Boolean,
    lastReadMessageId: Long = 0,
): List<TimelineItem> {
    val out = ArrayList<TimelineItem>(messages.size * 2)
    var lastDay = ""
    var lastSession: Long? = null
    var lastRole: String? = null
    var lastTime: Long = 0
    var unreadInserted = false
    var isFirst = true
    for (msg in messages) {
        val day = dayFormat.format(Date(msg.createdAt))
        if (day != lastDay) {
            lastDay = day
            out.add(TimelineItem.DateHeader(dateLabel(context, msg.createdAt), msg.id))
        }
        val sessionChanged = showTopicDividers && lastSession != null && lastSession != msg.sessionId
        if (sessionChanged) {
            out.add(TimelineItem.TopicDivider(msg.id))
        }
        // v3 H-22 / 1.3#5：未读分割线插在最后一条已读消息之后
        if (!unreadInserted && lastReadMessageId > 0 && msg.id > lastReadMessageId) {
            unreadInserted = true
            out.add(TimelineItem.UnreadDivider(msg.id))
        }
        val gap = msg.createdAt - lastTime
        val grouped = lastRole == msg.role && gap < 60 * 1000L
        val showTime = isFirst || gap >= TIME_GAP_FOR_DISPLAY_MS || day != dayFormat.format(Date(lastTime)) || sessionChanged
        isFirst = false
        lastRole = msg.role
        lastTime = msg.createdAt
        lastSession = msg.sessionId
        out.add(TimelineItem.Msg(msg, grouped, showTime))
    }
    return out
}

@Composable
private fun DateHeaderText(label: String, onImage: Boolean = false) {
    if (onImage) {
        // 背景是照片时给日期加一层半透明底，避免和图片糊在一起
        Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
            Text(
                label,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f), RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        return
    }
    YuTimeDivider(
        text = label,
        modifier = Modifier.padding(vertical = Tokens.SpacingSm),
    )
}

@Composable
private fun TopicDividerText(onImage: Boolean = false) {
    if (onImage) {
        Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.chat_topic_divider),
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    Text(
        stringResource(R.string.chat_topic_divider),
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

/** v3 H-22 / 1.3#5：未读分割线「以下为新消息」。 */
@Composable
private fun UnreadDividerText(onImage: Boolean = false) {
    val bg = if (onImage) MaterialTheme.colorScheme.surface.copy(alpha = 0.7f) else MaterialTheme.colorScheme.primaryContainer
    val fg = if (onImage) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimaryContainer
    Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
        Text(
            stringResource(R.string.chat_unread_divider),
            modifier = Modifier
                .background(bg, RoundedCornerShape(50))
                .padding(horizontal = Tokens.SpacingMd, vertical = 3.dp),
            style = MaterialTheme.typography.labelMedium,
            color = fg,
        )
    }
}

// 供 Android Studio 预览手机/平板两种宽度下的气泡与分隔线样式
@Preview(name = "手机", device = "spec:width=411dp,height=891dp", showBackground = true)
@Preview(name = "平板", device = "spec:width=1280dp,height=800dp,dpi=240", showBackground = true)
@Composable
private fun ChatBubblePreview() {
    XiaoyuTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(Tokens.SpacingLg),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                DateHeaderText("今天")
                Bubble(text = "刚下班，有点累。", isUser = true)
                Bubble(text = "嗯…我在听。你说的这个，我记着了。", isUser = false)
                TopicDividerText()
            }
        }
    }
}

/** 她的头像（M3-05）：双击 = 戳一下，长按菜单里也保留了「戳一下」入口。 */
@Composable
private fun HerAvatar(onPoke: () -> Unit) {
    val pokeDesc = stringResource(R.string.chat_a11y_poke)
    Box(
        modifier = Modifier
            .size(Tokens.SizeTouchTarget)
            .semantics(mergeDescendants = true) { contentDescription = pokeDesc }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { onPoke() })
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(Tokens.SpacingXxl)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.chat_avatar_letter),
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** 版本预览：去掉动作/情绪控制标记，只展示可见正文。 */
private fun versionPreview(content: String, emptyText: String): String =
    content.split("\n")
        .filterNot { it.startsWith(ACTION_PREFIX) }
        .joinToString(" ")
        .trim()
        .ifBlank { emptyText }

/**
 * v3 §7.9 / B-31：系统 TTS 朗读封装（只做本机合成，不上传任何文本）。
 *
 * - 初始化失败（机型没有语音引擎 / 缺中文语音包 / ROM 构造即抛异常）时 `ready` 保持 false，
 *   [speak] 直接返回 false，不弹错误、不崩溃，聊天继续可用；
 * - 长文本按 120 字切段，首段 FLUSH 其余 ADD，按顺序播完；
 * - [stop] 随时打断；最后一段播完回调 [onFinished]（已切回主线程），用于恢复「朗读」按钮文案。
 */
private class SystemTts(context: Context) {
    var onFinished: (() -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())
    private var engine: TextToSpeech? = null
    private var ready = false
    private var total = 0

    init {
        try {
            engine = TextToSpeech(context.applicationContext) { status ->
                val tts = engine
                if (status == TextToSpeech.SUCCESS && tts != null) {
                    val availability = tts.setLanguage(Locale.SIMPLIFIED_CHINESE)
                    ready = availability != TextToSpeech.LANG_MISSING_DATA &&
                        availability != TextToSpeech.LANG_NOT_SUPPORTED
                    tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) = Unit

                        override fun onDone(utteranceId: String?) {
                            if (utteranceId == idOf(total - 1)) {
                                main.post { onFinished?.invoke() }
                            }
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            main.post { onFinished?.invoke() }
                        }

                        override fun onError(utteranceId: String?, errorCode: Int) {
                            main.post { onFinished?.invoke() }
                        }
                    })
                } else {
                    ready = false
                }
            }
        } catch (e: Exception) {
            // 某些精简 ROM 没有 TTS 服务，构造阶段就会抛；这里静默降级
            engine = null
            ready = false
        }
    }

    /** 开始朗读；引擎不可用或内容为空时返回 false（调用方据此不切换按钮状态）。 */
    fun speak(text: String, rate: Float, pitch: Float): Boolean {
        val tts = engine ?: return false
        if (!ready) return false
        val chunks = splitForSpeech(text)
        if (chunks.isEmpty()) return false
        tts.stop()
        tts.setSpeechRate(rate.coerceIn(0.5f, 2.0f))
        tts.setPitch(pitch.coerceIn(0.5f, 1.5f))
        total = chunks.size
        chunks.forEachIndexed { index, chunk ->
            val mode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            if (tts.speak(chunk, mode, null, idOf(index)) != TextToSpeech.SUCCESS) return false
        }
        return true
    }

    fun stop() {
        engine?.stop()
    }

    fun shutdown() {
        engine?.stop()
        engine?.shutdown()
        engine = null
        ready = false
    }

    private fun idOf(index: Int) = "yu_tts_$index"
}

/** 长文本按 120 字分段，尽量在标点处断开，避免一口气读太长。 */
private fun splitForSpeech(text: String, limit: Int = 120): List<String> {
    val clean = text.split("\n")
        .filterNot { it.startsWith(ACTION_PREFIX) }
        .joinToString("\n")
        .trim()
    if (clean.isEmpty()) return emptyList()
    val chunks = mutableListOf<String>()
    var start = 0
    while (start < clean.length) {
        val end = minOf(start + limit, clean.length)
        var cut = end
        if (end < clean.length) {
            val window = clean.substring(start, end)
            val punct = window.lastIndexOfAny(charArrayOf('。', '！', '？', '；', '，', '\n', '.', '!', '?', ';', ','))
            if (punct > limit / 3) cut = start + punct + 1
        }
        chunks += clean.substring(start, cut)
        start = cut
    }
    return chunks
}
