package com.xiaoyu.app.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.xiaoyu.app.data.settings.ChatMode
import com.xiaoyu.app.ui.theme.LocalBubbleColors
import com.xiaoyu.app.ui.theme.XiaoyuTheme
import com.xiaoyu.soul.mood.FavorabilityLevel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val timeFormat = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)

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
    onOpenStickers: () -> Unit,
) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val archived by viewModel.archivedSessions.collectAsStateWithLifecycle()
    val sessionId by viewModel.sessionId.collectAsStateWithLifecycle()
    val favorability by viewModel.favorability.collectAsStateWithLifecycle()
    val chatMode by viewModel.chatMode.collectAsStateWithLifecycle()
    val isContinuous = chatMode == ChatMode.CONTINUOUS
    val backgroundFile by viewModel.chatBackgroundFile.collectAsStateWithLifecycle()
    val backgroundBlur by viewModel.chatBackgroundBlur.collectAsStateWithLifecycle()
    val backgroundDim by viewModel.chatBackgroundDim.collectAsStateWithLifecycle()
    val darkTheme = isSystemInDarkTheme()

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
        )
    }
    val openDrawer: () -> Unit = { scope.launch { drawerState.open() } }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // 自定义背景铺在最底层，覆盖整个聊天页（含顶部栏），不被 drawer 遮挡
        backgroundFile?.let { file ->
            ChatBackground(
                file = file,
                blurDp = backgroundBlur,
                dim = backgroundDim + if (darkTheme) 0.12f else 0f,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // 宽屏（平板/横屏）会话列表常驻；其余尺寸保持抽屉，并把聊天内容居中限宽
        val pinned = maxWidth >= 840.dp && !isContinuous
        val paneModifier = if (maxWidth >= 600.dp) {
            Modifier.widthIn(max = 760.dp).fillMaxSize()
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
                )
            }
        }
        if (pinned) {
            PermanentNavigationDrawer(drawerContent = drawerContent) { pane() }
        } else {
            ModalNavigationDrawer(drawerState = drawerState, drawerContent = drawerContent) { pane() }
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
    val archived by viewModel.archivedSessions.collectAsStateWithLifecycle()
    val backgroundFile by viewModel.chatBackgroundFile.collectAsStateWithLifecycle()
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

    val hasBackground = backgroundFile != null
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
    // 顶栏更多菜单（M5-04：导出会话 Markdown）
    var showMoreMenu by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(versionTarget) {
        versions = versionTarget?.let { viewModel.loadVersions(it.id) } ?: emptyList()
    }

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
                        // 单行标题：系统字体放大到 2x 也不会被 64dp 的顶栏裁掉
                        val online by viewModel.onlineStatus.collectAsStateWithLifecycle()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // 双击她的头像 = 戳一下（M3-05 / 执行书 6.3.7）
                            HerAvatar(onPoke = { viewModel.poke() })
                            Spacer(Modifier.width(8.dp))
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
                                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.chat_more))
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
                var instantScroll by remember(sessionId) { mutableStateOf(true) }
                val displayMessages = if (isContinuous) allMessages else messages
                // 引用回复：id -> 被引用消息正文（M1-09）
                val quoteMap = remember(displayMessages) {
                    displayMessages.associate { it.id to it.content }
                }
                val context = LocalContext.current
                val timeline = remember(displayMessages, showTopicDividers, isContinuous) {
                    if (isContinuous) buildTimeline(context, displayMessages, showTopicDividers) else emptyList()
                }
                LaunchedEffect(displayMessages.size, streaming.isNotEmpty(), typing) {
                    val extra = (if (typing) 1 else 0) + (if (streaming.isNotEmpty()) 1 else 0)
                    val count = if (isContinuous) timeline.size else displayMessages.size
                    val target = count + extra - 1
                    if (target >= 0) {
                        // 首次进入/切换会话时直接跳到底部，避免高速滚过整段历史；后续新消息才用动画
                        if (instantScroll) {
                            listState.scrollToItem(target)
                            instantScroll = false
                        } else {
                            listState.animateScrollToItem(target)
                        }
                    }
                }

                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    // 气泡按可用宽度的 78% 自适应、上限 420dp：小屏不贴边，平板不显得孤立
                    val bubbleMaxWidth = (maxWidth * 0.78f).coerceAtMost(420.dp)
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (isContinuous) {
                            timeline.forEach { item ->
                                when (item) {
                                    is TimelineItem.DateHeader -> item(
                                        key = "d-${item.anchorId}",
                                        contentType = "date",
                                    ) { DateHeaderText(item.label, onImage = backgroundFile != null) }
                                    is TimelineItem.TopicDivider -> item(
                                        key = "topic-${item.anchorId}",
                                        contentType = "topic",
                                    ) { TopicDividerText(onImage = backgroundFile != null) }
                                    is TimelineItem.Msg -> item(
                                        key = "m-${item.message.id}",
                                        contentType = "msg",
                                    ) {
                                        MessageRow(
                                            message = item.message,
                                            sticker = item.message.stickerId?.let(stickerById::get),
                                            maxBubbleWidth = bubbleMaxWidth,
                                            onHide = { viewModel.hideMessage(item.message.id) },
                                            onLongPress = { menuMessage = item.message },
                                            onImage = backgroundFile != null,
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
                                        )
                                    }
                                }
                            }
                        } else {
                            items(displayMessages, key = { it.id }) { message ->
                                MessageRow(
                                    message = message,
                                    sticker = message.stickerId?.let(stickerById::get),
                                    maxBubbleWidth = bubbleMaxWidth,
                                    onHide = { viewModel.hideMessage(message.id) },
                                    onLongPress = { menuMessage = message },
                                    onImage = backgroundFile != null,
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
                                    modifier = Modifier.padding(start = 6.dp),
                                )
                            }
                        }
                    }
                    // 空会话/空列表时给一句引导，避免出现一片空白
                    if (displayMessages.isEmpty() && streaming.isEmpty() && !typing) {
                        Text(
                            stringResource(R.string.chat_empty_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.align(Alignment.Center).padding(horizontal = 32.dp),
                        )
                    }
                }

                error?.let { message ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
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
                    enter = slideInVertically(animationSpec = tween(260)) { it / 3 } +
                        fadeIn(animationSpec = tween(200)),
                    exit = slideOutVertically(animationSpec = tween(200)) { it / 3 } +
                        fadeOut(animationSpec = tween(160)),
                ) {
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
                    )
                }

                // 引用回复预览条（M1-09）
                val quoted by viewModel.quoteMessage.collectAsStateWithLifecycle()
                quoted?.let { q ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(24.dp)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                        )
                        Spacer(Modifier.width(8.dp))
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
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.chat_cancel_quote))
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
                    onSend = { text ->
                        viewModel.send(text)
                    },
                )
            }
        }

        // 长按操作面板：reaction / 复制 / 重新生成 / 收藏 / 隐藏
        menuMessage?.let { target ->
            ModalBottomSheet(
                onDismissRequest = { menuMessage = null },
                sheetState = rememberModalBottomSheetState(),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        // M3-05：至少保留 ❤️ 😂 🥺 👍 四个，另有 😮 😢 与品牌彩蛋 🐟
                        listOf("❤️", "😂", "🥺", "👍", "😮", "😢", "🐟").forEach { emoji ->
                            Text(
                                text = emoji,
                                fontSize = 24.sp,
                                modifier = Modifier
                                    .clickable {
                                        viewModel.react(target.id, emoji)
                                        menuMessage = null
                                    }
                                    .padding(4.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    // 戳一下（M3-05）：与顶栏按钮、双击头像共用同一套频控
                    TextButton(onClick = {
                        menuMessage = null
                        viewModel.poke()
                    }) { Text(stringResource(R.string.chat_poke_her)) }
                    if (target.content.isNotBlank() && !target.retracted) {
                        TextButton(onClick = {
                            viewModel.quote(target)
                            menuMessage = null
                        }) { Text(stringResource(R.string.chat_quote_reply)) }
                    }
                    if (target.role == ROLE_USER && !target.retracted) {
                        TextButton(onClick = {
                            menuMessage = null
                            confirmRetract = target
                        }) { Text(stringResource(R.string.chat_retract)) }
                        TextButton(onClick = {
                            menuMessage = null
                            editingMessage = target
                        }) { Text(stringResource(R.string.chat_edit)) }
                    }
                    if (target.content.isNotBlank()) {
                        TextButton(onClick = {
                            clipboard.setText(AnnotatedString(target.content.split("\n").filterNot { it.startsWith(ACTION_PREFIX) }.joinToString("\n")))
                            menuMessage = null
                        }) { Text(stringResource(R.string.chat_copy)) }
                    }
                    if (target.role == ROLE_ASSISTANT) {
                        TextButton(onClick = {
                            menuMessage = null
                            viewModel.regenerate(target)
                        }) { Text(stringResource(R.string.chat_regenerate)) }
                        // M3-06：同组历史版本（重新生成不会丢掉旧版本）
                        if (target.replyGroupId != null) {
                            TextButton(onClick = {
                                menuMessage = null
                                versionTarget = target
                            }) { Text(stringResource(R.string.chat_view_versions)) }
                        }
                    }
                    if (target.content.isNotBlank()) {
                        TextButton(onClick = {
                            menuMessage = null
                            viewModel.rememberMessage(target)
                        }) { Text(stringResource(R.string.chat_remember)) }
                    }
                    TextButton(onClick = {
                        menuMessage = null
                        confirmHide = target
                    }) { Text(stringResource(R.string.chat_hide_message)) }
                    TextButton(onClick = {
                        menuMessage = null
                        selectionMode = true
                        selectedIds = setOf(target.id)
                    }) { Text(stringResource(R.string.chat_multi_select)) }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }

        // 历史版本弹层（M3-06）：同组多版本可切换，旧版本只是被 soft hidden，不删除
        versionTarget?.let {
            ModalBottomSheet(
                onDismissRequest = { versionTarget = null },
                sheetState = rememberModalBottomSheetState(),
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text(stringResource(R.string.chat_versions_title), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
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
                                    .clip(RoundedCornerShape(12.dp))
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
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
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
                    Spacer(Modifier.height(16.dp))
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
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
    maxBubbleWidth: Dp,
    onHide: () -> Unit,
    onLongPress: () -> Unit = {},
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
) {
    val isUser = message.role == ROLE_USER
    val isNotice = message.role == ROLE_NOTICE

    if (isNotice) {
        Text(
            message.content,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
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
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )
        return
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
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
                            RoundedCornerShape(8.dp),
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp),
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
            Box(
                modifier = Modifier.combinedClickable(
                    onClick = {
                        if (selectionMode) onToggleSelect()
                    },
                    onLongClick = {
                        if (!selectionMode) onLongPress()
                    },
                )
            ) {
                Column {
                    if (selectionMode) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
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
                        Spacer(Modifier.height(4.dp))
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
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                    if (message.type == TYPE_STICKER) {
                        // 表情消息：无气泡、无背景（执行书 7.4.4）
                        StickerArt(sticker = sticker, size = 132.dp)
                    } else {
                        Bubble(text = message.content, isUser = isUser)
                    }
                }
            }
            // 照片背景上加一层浅底，时间戳不会糊进图片里
            val timeModifier = if (onImage) {
                Modifier
                    .padding(top = 3.dp, start = 4.dp, end = 4.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), RoundedCornerShape(50))
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            } else {
                Modifier.padding(top = 3.dp, start = 4.dp, end = 4.dp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!isUser && onPlayVoice != null && message.content.isNotBlank()) {
                    when {
                        isSynthesizing -> TextButton(
                            onClick = onPlayVoice,
                            modifier = Modifier.height(28.dp),
                        ) { Text(stringResource(R.string.chat_synthesizing), style = MaterialTheme.typography.labelSmall) }
                        isPlaying -> TextButton(
                            onClick = onPlayVoice,
                            modifier = Modifier.height(28.dp),
                        ) { Text(stringResource(R.string.chat_stop), style = MaterialTheme.typography.labelSmall) }
                        else -> IconButton(
                            onClick = onPlayVoice,
                            modifier = Modifier.height(28.dp).width(28.dp),
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = stringResource(R.string.chat_read_aloud),
                                tint = if (onImage) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Text(
                    timeFormat.format(Date(message.createdAt)),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (onImage) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = timeModifier,
                )
                // 编辑标记（M3-07）
                if (message.editedAt != null) {
                    Text(
                        stringResource(R.string.chat_edited),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(start = 2.dp),
                    )
                }
                // 发送状态（M1-09）：发送中 / 失败可重发
                when (message.status) {
                    STATUS_SENDING -> Text(
                        stringResource(R.string.chat_sending),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(start = 4.dp),
                    )
                    STATUS_FAILED -> Text(
                        "!",
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error)
                            .padding(horizontal = 6.dp)
                            .clickable { onRetry?.invoke() },
                        color = MaterialTheme.colorScheme.onError,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            // 用户给的 reaction（执行书 6.3.7），旁边是她偶尔回的一个表情（M3-05）
            if (message.reactionsJson.isNotBlank() || herReaction != null) {
                Row(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                            RoundedCornerShape(50),
                        )
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    message.reactionsJson.split(',').filter { it.isNotBlank() }.forEach { emoji ->
                        Text(emoji, fontSize = 14.sp, modifier = Modifier.padding(end = 4.dp))
                    }
                    herReaction?.let { emoji ->
                        Text(
                            stringResource(R.string.chat_her_reaction, emoji),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Bubble(text: String, isUser: Boolean, pending: Boolean = false) {
    val bubbles = LocalBubbleColors.current
    val bg = if (isUser) bubbles.myBackground else bubbles.herBackground
    val fg = if (isUser) bubbles.myContent else bubbles.herContent
    Surface(
        color = bg,
        contentColor = fg,
        shape = RoundedCornerShape(
            topStart = 18.dp,
            topEnd = 18.dp,
            bottomStart = if (isUser) 18.dp else 4.dp,
            bottomEnd = if (isUser) 4.dp else 18.dp,
        ),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            text.split("\n").forEach { raw ->
                val isAction = raw.startsWith(ACTION_PREFIX)
                val line = if (isAction) raw.removePrefix(ACTION_PREFIX) else raw
                if (line.isEmpty()) return@forEach
                Text(
                    text = line,
                    style = if (isAction) {
                        MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic)
                    } else {
                        MaterialTheme.typography.bodyLarge
                    },
                    color = if (isAction) fg.copy(alpha = 0.8f) else fg,
                )
            }
            if (pending) Text("▍", style = MaterialTheme.typography.bodyLarge)
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
    onSend: (String) -> Unit,
) {
    var text by rememberSaveable(identityId) { mutableStateOf("") }
    var voiceMode by rememberSaveable { mutableStateOf(false) }
    var holding by remember { mutableStateOf(false) }
    var voiceToggles by remember { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val textInteraction = remember { MutableInteractionSource() }
    val focused by textInteraction.collectIsFocusedAsState()
    val sendContentDescription = stringResource(R.string.chat_send)

    // 切回该人设时自动恢复草稿；切换人设时保存当前草稿（M1-09）
    LaunchedEffect(identityId) {
        identityId?.let {
            val saved = viewModel.getDraft(it)
            if (!saved.isNullOrBlank()) text = saved
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
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "说完后自动转成文字")
        }
        try {
            voiceLauncher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            // 设备没有语音识别服务时给一句提示，不弹系统错误页
            viewModel.notifyVoiceUnavailable()
        }
    }

    val canSend = enabled && text.isNotBlank()
    val send: () -> Unit = {
        if (canSend) {
            onSend(text)
            text = ""
        }
    }

    val inputBorder by animateColorAsState(
        targetValue = if (focused) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
        animationSpec = tween(durationMillis = 180),
        label = "inputBorder",
    )
    val holdBg by animateColorAsState(
        targetValue = if (holding) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        animationSpec = tween(durationMillis = 120),
        label = "holdBg",
    )
    val sendScale by animateFloatAsState(
        targetValue = if (canSend) 1f else 0.88f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 700f),
        label = "sendScale",
    )
    val sendBg by animateColorAsState(
        targetValue = if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(durationMillis = 180),
        label = "sendBg",
    )
    val sendFg by animateColorAsState(
        targetValue = if (canSend) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
        animationSpec = tween(durationMillis = 180),
        label = "sendFg",
    )

    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 手势导航条安全区：背景仍铺到底，内容抬到小白条之上并留出呼吸空间
                .navigationBarsPadding()
                .padding(start = 8.dp, end = 10.dp, top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
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
                    MicGlyph(tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (voiceMode) {
                // 按住说话：松手才唤起系统语音识别，识别结果仍回填到输入框
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
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
                    onValueChange = { text = it },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 46.dp)
                        .clip(RoundedCornerShape(23.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                        .border(1.dp, inputBorder, RoundedCornerShape(23.dp))
                        .padding(horizontal = 16.dp, vertical = 11.dp)
                        .focusRequester(focusRequester)
                        .onFocusChanged { if (it.isFocused && stickersOpen) onCloseStickers() },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    maxLines = 4,
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
                SmileyGlyph(
                    tint = if (stickersOpen) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            // 语音模式下隐藏发送键：输入区已被「按住说话」占满，留着会让人误以为能发旧草稿
            AnimatedVisibility(
                visible = !voiceMode,
                enter = fadeIn(tween(140)) + scaleIn(tween(140), initialScale = 0.6f),
                exit = fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.6f),
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .scale(sendScale)
                        .clip(CircleShape)
                        .background(sendBg)
                        .clickable(enabled = canSend, onClick = send)
                        .semantics { contentDescription = sendContentDescription },
                    contentAlignment = Alignment.Center,
                ) {
                    PlaneGlyph(tint = sendFg)
                }
            }
        }
    }
}

/** 输入栏上的圆形图标键：40dp 命中区 + 圆形水波纹。 */
@Composable
private fun BarIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
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
            Spacer(Modifier.height(16.dp))
            Button(onClick = onNew, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Text(if (continuous) stringResource(R.string.chat_drawer_new_topic) else stringResource(R.string.chat_drawer_new_chat))
            }
        }
        if (!continuous) {
            Divider(color = MaterialTheme.colorScheme.surfaceVariant)
            LazyColumn(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
                items(sessions, key = { it.id }) { session ->
                    SessionRow(
                        session = session,
                        selected = session.id == currentId,
                        onClick = { onSelect(session.id) },
                        onLongClick = { onRename(session) },
                        trailing = { TextButton(onClick = { onArchive(session.id) }) { Text(stringResource(R.string.chat_archive)) } },
                    )
                }
                item {
                    TextButton(onClick = { showArchived = !showArchived }, modifier = Modifier.padding(start = 8.dp)) {
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
        Column(Modifier.padding(vertical = 8.dp)) {
            DrawerEntry(stringResource(R.string.chat_relation), onOpenRelation)
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
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 18.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
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
    data class Msg(val message: MessageEntity) : TimelineItem
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
 * - 话题分隔线落在会话切换处，用新会话第一条消息 id 锚定，保证 LazyColumn key 稳定。
 */
private fun buildTimeline(context: Context, messages: List<MessageEntity>, showTopicDividers: Boolean): List<TimelineItem> {
    val out = ArrayList<TimelineItem>(messages.size * 2)
    var lastDay = ""
    var lastSession: Long? = null
    for (msg in messages) {
        val day = dayFormat.format(Date(msg.createdAt))
        if (day != lastDay) {
            lastDay = day
            out.add(TimelineItem.DateHeader(dateLabel(context, msg.createdAt), msg.id))
        }
        if (showTopicDividers && lastSession != null && lastSession != msg.sessionId) {
            out.add(TimelineItem.TopicDivider(msg.id))
        }
        lastSession = msg.sessionId
        out.add(TimelineItem.Msg(msg))
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
    Text(
        label,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
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
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        textAlign = TextAlign.Center,
    )
}

// 供 Android Studio 预览手机/平板两种宽度下的气泡与分隔线样式
@Preview(name = "手机", device = "spec:width=411dp,height=891dp", showBackground = true)
@Preview(name = "平板", device = "spec:width=1280dp,height=800dp,dpi=240", showBackground = true)
@Composable
private fun ChatBubblePreview() {
    XiaoyuTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
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
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { onPoke() })
            },
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

/** 版本预览：去掉动作/情绪控制标记，只展示可见正文。 */
private fun versionPreview(content: String, emptyText: String): String =
    content.split("\n")
        .filterNot { it.startsWith(ACTION_PREFIX) }
        .joinToString(" ")
        .trim()
        .ifBlank { emptyText }
