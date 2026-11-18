package com.xiaoyu.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
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
    // 返回上一页后保留各页面内部状态（滚动位置、开关等），不因重组被丢弃
    val screenStates = rememberSaveableStateHolder()

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
    val pageDuration = LocalYuMotion.current.page

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
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                // 左右边缘侧滑返回：手指往哪边滑，整页就往哪边退出去
                .pointerInput(stack.size) {
                    if (stack.size <= 1) return@pointerInput
                    val edge = 28.dp.toPx()
                    val threshold = 56.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val fromLeft = down.position.x <= edge
                        val fromRight = down.position.x >= size.width - edge
                        if (!fromLeft && !fromRight) return@awaitEachGesture
                        var total = 0f
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            total += change.positionChange().x
                            val moved = if (fromLeft) total else -total
                            if (moved >= threshold) {
                                change.consume()
                                // 从左边缘右滑：上一页从左进；右边缘左滑：上一页从右进
                                backFromLeft = fromLeft
                                pop()
                                break
                            }
                            if (kotlin.math.abs(total) > threshold * 1.5f) break
                        }
                    }
                },
        ) {
            // 页面切换加转场动画，避免硬切造成"闪一下"和卡顿感
            AnimatedContent(
                targetState = screen,
                transitionSpec = {
                    val slide = tween<IntOffset>(pageDuration)
                    val fade = tween<Float>(pageDuration)
                    if (navigationForward) {
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
        }
    }
}

@Composable
private fun CenteredWidth(maxWidth: Dp = Tokens.SizePageMax, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = maxWidth).fillMaxSize()) { content() }
    }
}
