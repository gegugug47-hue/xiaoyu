package com.xiaoyu.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaoyu.app.R
import com.xiaoyu.app.ui.background.ChatBackgroundScreen
import com.xiaoyu.app.ui.components.YuSnackbar
import com.xiaoyu.app.ui.onboarding.OnboardingScreen
import com.xiaoyu.app.ui.theme.LocalYuMotion
import com.xiaoyu.app.ui.theme.Tokens
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class Screen {
    Chat, Memory, Diary, Settings, Persona, Search, Gallery, Relation, Diagnostics, Usage,
    StickerManage, StickerImport, LifeDay, ChatBackground,
}

@Composable
fun XiaoyuRoot(
    viewModel: AppViewModel = hiltViewModel(),
    pendingSessionId: Long? = null,
    onSessionConsumed: () -> Unit = {},
) {
    val ready by viewModel.ready.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val hasHistory by viewModel.hasHistory.collectAsStateWithLifecycle()
    val toast by viewModel.toast.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(toast) {
        if (!toast.isNullOrBlank()) {
            snackbar.showSnackbar(toast!!)
            viewModel.clearToast()
        }
    }

    if (!ready) {
        Box(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
        }
        return
    }

    // v3 §7.12：未完成新引导、且没有历史会话的新用户才进引导。
    // 老用户升级后 onboardingDone 为默认 false，但 onboarded / hasHistory 已为 true，
    // 不会被重新弹引导。
    if (!settings.onboardingDone && !settings.onboarded && !hasHistory) {
        OnboardingScreen(viewModel = viewModel)
        return
    }

    var stack by rememberSaveable(
        stateSaver = listSaver(
            save = { it.map { screen -> screen.name } },
            restore = { it.map { name -> Screen.valueOf(name) } },
        ),
    ) { mutableStateOf(listOf(Screen.Chat)) }
    val screen = stack.last()
    // 表情包导入页需要记住要导入到哪个包
    var stickerPackId by rememberSaveable { mutableStateOf("") }
    var stickerPackName by rememberSaveable { mutableStateOf("") }
    // 转场方向：前进（新页从右进）还是返回（当前页按手势方向整体滑出）
    var navigationForward by remember { mutableStateOf(true) }
    // 返回时上一页从哪一侧进来：左边缘右滑 → 从左进；右边缘左滑 → 从右进
    var backFromLeft by remember { mutableStateOf(true) }
    val go: (Screen) -> Unit = { target ->
        navigationForward = true
        stack = stack + target
    }
    val pop: () -> Unit = {
        if (stack.size > 1) {
            navigationForward = false
            stack = stack.dropLast(1)
        }
    }
    val back: () -> Unit = {
        backFromLeft = true
        pop()
    }
    // 跟手返回：拖动时把当前页整体推开，上一页在后面跟着露出来
    val pageDuration = LocalYuMotion.current.page
    val scope = rememberCoroutineScope()
    var dragActive by remember { mutableStateOf(false) }
    var dragFromLeft by remember { mutableStateOf(true) }
    var dragTarget by remember { mutableStateOf<Screen?>(null) }
    var dragProgress by remember { mutableFloatStateOf(0f) }
    // 手势已经播完退场动画，出栈时不要再补一次转场
    var skipNextTransition by remember { mutableStateOf(false) }
    // 转场开始后复位，不影响下一次普通返回
    LaunchedEffect(screen) { skipNextTransition = false }
    // 返回上一页后保留各页面内部状态（滚动位置、开关等），不因重组被丢弃
    val screenStates = rememberSaveableStateHolder()

    // 单页内容：正常转场和拖动时露出的上一页共用同一份渲染
    val renderScreen: @Composable (Screen) -> Unit = { currentScreen ->
        screenStates.SaveableStateProvider(currentScreen.name) {
            when (currentScreen) {
                Screen.Chat -> ChatScreen(
                    viewModel = viewModel,
                    onOpenMemory = { go(Screen.Memory) },
                    onOpenDiary = { go(Screen.Diary) },
                    onOpenSettings = { go(Screen.Settings) },
                    onOpenSearch = { go(Screen.Search) },
                    onOpenGallery = { go(Screen.Gallery) },
                    onOpenRelation = { go(Screen.Relation) },
                    onOpenLifeDay = { go(Screen.LifeDay) },
                    onOpenStickers = { go(Screen.StickerManage) },
                )
                Screen.Memory -> CenteredWidth { MemoryScreen(viewModel = viewModel, onBack = back) }
                Screen.Diary -> CenteredWidth { DiaryScreen(viewModel = viewModel, onBack = back) }
                Screen.Settings -> SettingsScreen(
                    viewModel = viewModel,
                    onBack = back,
                    onOpenPersona = { go(Screen.Persona) },
                    onOpenDiagnostics = { go(Screen.Diagnostics) },
                    onOpenUsage = { go(Screen.Usage) },
                    onOpenStickers = { go(Screen.StickerManage) },
                    onOpenChatBackground = { go(Screen.ChatBackground) },
                )
                Screen.ChatBackground -> CenteredWidth {
                    ChatBackgroundScreen(viewModel = viewModel, onBack = back)
                }
                Screen.Diagnostics -> CenteredWidth {
                    DiagnosticsScreen(viewModel = viewModel, onBack = back)
                }
                Screen.Usage -> CenteredWidth {
                    UsageScreen(viewModel = viewModel, onBack = back)
                }
                Screen.StickerManage -> CenteredWidth {
                    StickerManageScreen(
                        appViewModel = viewModel,
                        onBack = back,
                        onImport = { packId, packName ->
                            stickerPackId = packId
                            stickerPackName = packName
                            go(Screen.StickerImport)
                        },
                    )
                }
                Screen.StickerImport -> CenteredWidth {
                    StickerImportScreen(
                        packId = stickerPackId,
                        packName = stickerPackName,
                        onBack = back,
                        onStarted = { back() },
                    )
                }
                Screen.Persona -> CenteredWidth { PersonaScreen(viewModel = viewModel, onBack = back) }
                Screen.Search -> CenteredWidth {
                    SearchScreen(
                        viewModel = viewModel,
                        onBack = back,
                        onUseContext = { back() },
                    )
                }
                Screen.Gallery -> CenteredWidth {
                    GalleryScreen(
                        viewModel = viewModel,
                        onBack = back,
                        onOpenMessage = { sessionId ->
                            viewModel.openSession(sessionId)
                            back()
                        },
                        onDescribe = { message ->
                            message.imageUri?.let { path ->
                                viewModel.describeImageForHer(path, message.sessionId)
                            }
                        },
                    )
                }
                Screen.Relation -> CenteredWidth {
                    RelationScreen(
                        viewModel = viewModel,
                        onBack = back,
                        onOpenDiary = { go(Screen.Diary) },
                        onOpenMemory = { go(Screen.Memory) },
                        onOpenPersona = { go(Screen.Persona) },
                    )
                }
                Screen.LifeDay -> CenteredWidth {
                    LifeDayScreen(viewModel = viewModel, onBack = back)
                }
            }
        }
    }

    // 点通知进来（冷启动或热启动）时带上 sessionId，直接落到对应会话（执行书 6.11）。
    // 同时重置到聊天页，避免热启动后仍停留在子页面；消费后由宿主置空，避免重复跳转。
    LaunchedEffect(pendingSessionId) {
        if (pendingSessionId != null) {
            stack = listOf(Screen.Chat)
            viewModel.openSession(pendingSessionId)
            onSessionConsumed()
        }
    }

    BackHandler(enabled = stack.size > 1) { back() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // 系统栏 inset 交给各页面自己的 Scaffold 处理，避免上下重复留白
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            SnackbarHost(
                snackbar,
                modifier = Modifier.navigationBarsPadding().imePadding(),
            ) { data ->
                YuSnackbar { Text(data.visuals.message) }
            }
        },
    ) { padding ->
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .padding(padding)
                // 左右边缘跟手侧滑返回：页面跟着手指走，松手按进度决定走完还是弹回
                .pointerInput(stack.size) {
                    if (stack.size <= 1) return@pointerInput
                    val edge = 28.dp.toPx()
                    val commitFraction = 0.32f
                    val wrongWay = 24.dp.toPx()
                    val width = size.width.toFloat()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val fromLeft = down.position.x <= edge
                        val fromRight = down.position.x >= width - edge
                        if (!fromLeft && !fromRight) return@awaitEachGesture
                        val previous = stack.getOrNull(stack.size - 2) ?: return@awaitEachGesture
                        var total = 0f
                        var started = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            total += change.positionChange().x
                            val moved = if (fromLeft) total else -total
                            if (!started) {
                                // 方向不对或已被子组件接管，就交还，避免抢走正常滑动
                                if (moved < -wrongWay || change.isConsumed) break
                                if (moved > 0f) {
                                    started = true
                                    dragFromLeft = fromLeft
                                    dragTarget = previous
                                    dragProgress = 0f
                                    dragActive = true
                                }
                            }
                            if (started) {
                                change.consume()
                                dragProgress = (moved / width).coerceIn(0f, 1f)
                            }
                            if (!change.pressed) break
                        }
                        if (started) {
                            val commit = dragProgress >= commitFraction
                            scope.launch {
                                animate(
                                    initialValue = dragProgress,
                                    targetValue = if (commit) 1f else 0f,
                                    animationSpec = tween(if (commit) 160 else 200),
                                ) { value, _ -> dragProgress = value }
                                if (commit) {
                                    // 手势已经把当前页推出去了，出栈时不要再补一次转场
                                    backFromLeft = fromLeft
                                    skipNextTransition = true
                                    pop()
                                }
                                dragActive = false
                                dragTarget = null
                                dragProgress = 0f
                            }
                        }
                    }
                },
        ) {
            // 拖动中的当前页整体跟手位移，上一页垫在底下带一点视差露出来
            val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
            val dragDirection = if (dragFromLeft) 1f else -1f
            if (dragActive) {
                dragTarget?.let { behind ->
                    Box(
                        Modifier
                            .fillMaxSize()
                            .offset {
                                IntOffset(
                                    (-dragDirection * (1f - dragProgress) * widthPx * 0.25f).roundToInt(),
                                    0,
                                )
                            },
                    ) { renderScreen(behind) }
                }
            }
            // 页面切换加转场动画，避免硬切造成"闪一下"和卡顿感
            AnimatedContent(
                targetState = screen,
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset((dragDirection * dragProgress * widthPx).roundToInt(), 0) },
                transitionSpec = {
                    val slide = tween<IntOffset>(pageDuration)
                    val fade = tween<Float>(pageDuration)
                    if (skipNextTransition) {
                        // 手势已经把手上的页推走了，出栈不再补一次转场
                        EnterTransition.None togetherWith ExitTransition.None
                    } else if (navigationForward) {
                        // 前进：新页整页从右侧滑入，旧页轻微左移淡出
                        (slideInHorizontally(slide) { it } + fadeIn(fade)).togetherWith(
                            slideOutHorizontally(slide) { -it / 4 } + fadeOut(fade)
                        )
                    } else {
                        // 返回：当前页整页朝手势方向退出去，上一页从对应方向滑回来
                        (slideInHorizontally(slide) { if (backFromLeft) -it else it } + fadeIn(fade)).togetherWith(
                            slideOutHorizontally(slide) { if (backFromLeft) it else -it } + fadeOut(fade)
                        )
                    }
                },
                label = "screen",
            ) { currentScreen ->
                renderScreen(currentScreen)
            }
        }
    }
}

@Composable
private fun CenteredWidth(maxWidth: Dp = Tokens.SizePageMax, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = maxWidth).fillMaxSize()) { content() }
    }
}
