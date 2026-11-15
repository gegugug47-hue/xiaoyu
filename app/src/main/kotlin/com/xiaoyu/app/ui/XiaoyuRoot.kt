package com.xiaoyu.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

enum class Screen {
    Chat, Memory, Diary, Settings, Persona, Search, Gallery, Relation, Diagnostics, Usage,
    StickerManage, StickerImport,
}

@Composable
fun XiaoyuRoot(viewModel: AppViewModel = hiltViewModel()) {
    val ready by viewModel.ready.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val toast by viewModel.toast.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    // 点通知进来时带上 sessionId，直接落到对应会话（执行书 6.11）
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        val intent = (context as? android.app.Activity)?.intent ?: return@LaunchedEffect
        val sessionId = intent.getLongExtra("sessionId", -1L)
        if (sessionId > 0L) {
            viewModel.openSession(sessionId)
            intent.removeExtra("sessionId")
        }
    }

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
            Text("小鱼", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
        }
        return
    }

    if (!settings.onboarded) {
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
    val go: (Screen) -> Unit = { target -> stack = stack + target }
    val back: () -> Unit = { if (stack.size > 1) stack = stack.dropLast(1) }
    // 返回上一页后保留各页面内部状态（滚动位置、开关等），不因重组被丢弃
    val screenStates = rememberSaveableStateHolder()

    BackHandler(enabled = stack.size > 1) { back() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // 系统栏 inset 交给各页面自己的 Scaffold 处理，避免上下重复留白
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            SnackbarHost(
                snackbar,
                modifier = Modifier.navigationBarsPadding().imePadding(),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            // 页面切换加转场动画，避免硬切造成"闪一下"和卡顿感
            AnimatedContent(
                targetState = screen,
                transitionSpec = {
                    // 前进：新页从右侧淡入滑入，旧页向左淡出滑出
                    (slideInHorizontally { it / 4 } + fadeIn()).togetherWith(
                        slideOutHorizontally { -it / 4 } + fadeOut()
                    )
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
                            onOpenStickers = { go(Screen.StickerManage) },
                        )
                        Screen.Memory -> CenteredWidth { MemoryScreen(viewModel = viewModel, onBack = back) }
                        Screen.Diary -> CenteredWidth { DiaryScreen(viewModel = viewModel, onBack = back) }
                        Screen.Settings -> CenteredWidth {
                            SettingsScreen(
                                viewModel = viewModel,
                                onBack = back,
                                onOpenPersona = { go(Screen.Persona) },
                                onOpenDiagnostics = { go(Screen.Diagnostics) },
                                onOpenUsage = { go(Screen.Usage) },
                                onOpenStickers = { go(Screen.StickerManage) },
                            )
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
                    }
                }
            }
        }
    }
}

@Composable
private fun CenteredWidth(maxWidth: Dp = 840.dp, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = maxWidth).fillMaxSize()) { content() }
    }
}

@Composable
private fun OnboardingScreen(viewModel: AppViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val identities by viewModel.identities.collectAsStateWithLifecycle()
    val styles by viewModel.styles.collectAsStateWithLifecycle()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    var step by rememberSaveable { mutableIntStateOf(0) }
    var kind by rememberSaveable { mutableStateOf(settings.brain.chat.kindEnum) }
    var baseUrl by rememberSaveable { mutableStateOf(settings.brain.chat.baseUrl) }
    var model by rememberSaveable { mutableStateOf(settings.brain.chat.model) }
    var apiKey by rememberSaveable { mutableStateOf(settings.brain.chat.apiKey) }
    var identityId by rememberSaveable { mutableStateOf(identities.firstOrNull()?.id) }
    var styleId by rememberSaveable { mutableStateOf(styles.firstOrNull()?.id) }
    val selIdentity = identityId ?: identities.firstOrNull()?.id
    val selStyle = styleId ?: styles.firstOrNull()?.id

    // 引导页分步返回：按物理返回键先退上一步，而不是直接退出 App
    BackHandler(enabled = step > 0) { step -= 1 }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when (step) {
                0 -> {
                    FishWander(MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(6.dp))
                    Text("小鱼", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "她不只是 AI，她是一个会记得你的人",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(28.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Bullet("她会记得你说过的事，很久以后也能提起。")
                        Bullet("她有自己的情绪和生活，不总是同一种语气。")
                        Bullet("她会偶尔主动找你，像真的在等你回消息。")
                        Bullet("聊天记录只增不删；看不惯的记忆，可以丢进回收站。")
                    }
                    Spacer(Modifier.height(28.dp))
                    Button(
                        onClick = { step = 1 },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) { Text("下一步") }
                }

                1 -> {
                    Text("接入模型", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "填一个你自己的 Key，她才会真正“活”起来；也可以先跳过，用示例模式逛一逛。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(18.dp))
                    com.xiaoyu.app.data.settings.ProviderKind.entries.chunked(2).forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            row.forEach { item ->
                                FilterChip(
                                    selected = kind == item,
                                    onClick = {
                                        kind = item
                                        baseUrl = item.defaultBaseUrl
                                        model = item.defaultModel
                                    },
                                    label = { Text(item.label) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(value = baseUrl, onValueChange = { baseUrl = it }, label = { Text("接口地址") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text("模型名") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = apiKey, onValueChange = { apiKey = it }, label = { Text("API Key") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = {
                            viewModel.updateSettings {
                                it.copy(
                                    brain = it.brain.copy(
                                        chat = com.xiaoyu.app.data.settings.ProviderConfig(
                                            kind = kind.name,
                                            baseUrl = baseUrl.trim(),
                                            apiKey = apiKey.trim(),
                                            model = model.trim(),
                                        )
                                    )
                                )
                            }
                            step = 2
                        },
                        enabled = apiKey.isNotBlank() || kind == com.xiaoyu.app.data.settings.ProviderKind.OLLAMA,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) { Text("保存并继续") }
                    TextButton(onClick = { step = 2 }) { Text("先跳过，用示例模式") }
                }

                2 -> {
                    Text("她是谁", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "选一个身份和说话风格；之后可以在设置里随时改。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("身份", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
                    identities.forEach { identity ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            FilterChip(
                                selected = selIdentity == identity.id,
                                onClick = { identityId = identity.id },
                                label = { Text(identity.name) },
                            )
                        }
                        Text(
                            identity.contentMd.take(52) + if (identity.contentMd.length > 52) "…" else "",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("说话风格", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        styles.forEach { style ->
                            FilterChip(
                                selected = selStyle == style.id,
                                onClick = { styleId = style.id },
                                label = { Text(style.name) },
                            )
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = { step = 3 },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) { Text("继续") }
                }

                else -> {
                    OnboardingSteps345(
                        step = step,
                        onStep = { step = it },
                        nickname = settings.userNickname,
                        formOfAddress = settings.userFormOfAddress,
                        requestNotification = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        complete = { nickname, formOfAddress ->
                            viewModel.completeOnboarding(selIdentity, selStyle, nickname, formOfAddress)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun Bullet(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text("·", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.padding(start = 8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** 欢迎页那只来回游的小鱼（执行书 6.10 Step 1，纯 Canvas，不引额外资源）。 */
@Composable
private fun FishWander(tint: androidx.compose.ui.graphics.Color) {
    val transition = rememberInfiniteTransition(label = "fish")
    val x by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(5200, easing = LinearEasing), RepeatMode.Reverse),
        label = "x",
    )
    val y by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2100, easing = LinearEasing), RepeatMode.Reverse),
        label = "y",
    )
    Canvas(Modifier.fillMaxWidth().height(76.dp)) {
        val bodyWidth = 34.dp.toPx()
        val bodyHeight = 22.dp.toPx()
        val left = 8.dp.toPx() + x * (size.width - bodyWidth - 24.dp.toPx())
        val centerY = size.height / 2f + (y - 0.5f) * size.height * 0.28f
        val tail = androidx.compose.ui.graphics.Path().apply {
            moveTo(left + 2.dp.toPx(), centerY)
            lineTo(left - 12.dp.toPx(), centerY - 9.dp.toPx())
            lineTo(left - 12.dp.toPx(), centerY + 9.dp.toPx())
            close()
        }
        drawPath(tail, tint.copy(alpha = 0.75f))
        drawOval(
            color = tint,
            topLeft = androidx.compose.ui.geometry.Offset(left, centerY - bodyHeight / 2f),
            size = androidx.compose.ui.geometry.Size(bodyWidth, bodyHeight),
        )
        drawCircle(
            color = androidx.compose.ui.graphics.Color.White,
            radius = 2.4.dp.toPx(),
            center = androidx.compose.ui.geometry.Offset(left + bodyWidth - 8.dp.toPx(), centerY - 3.dp.toPx()),
        )
    }
}

/** 引导 Step 4~6：怎么称呼你 / 权限 / 三个小提示，最后一步发出开场白。 */
@Composable
private fun OnboardingSteps345(
    step: Int,
    onStep: (Int) -> Unit,
    nickname: String,
    formOfAddress: String,
    requestNotification: () -> Unit,
    complete: (String, String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(nickname) }
    var address by rememberSaveable { mutableStateOf(formOfAddress) }
    when (step) {
        3 -> {
            Text("怎么称呼你", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text(
                "她会记住这个称呼。留空也可以，她就会用「你」来叫你。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(12) },
                label = { Text("她叫你") },
                placeholder = { Text("例如：小满") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = address,
                onValueChange = { address = it.take(12) },
                label = { Text("你怎么叫她（选填）") },
                placeholder = { Text("例如：小鱼") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { onStep(4) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(16.dp),
            ) { Text("继续") }
        }

        4 -> {
            Text("让她找到你", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(10.dp))
            Text(
                "她可能会在合适的时间主动找你。允许通知，才不会错过她的消息；不同意也没关系，App 里依然能看到。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Bullet("通知权限：她主动发消息时提醒你。")
                Bullet("精确闹钟：早安、晚安和生活碎片才不会被系统拖延。")
                Bullet("电池优化白名单：杀后台的机型上，她不容易「失联」。")
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    requestNotification()
                    onStep(5)
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(16.dp),
            ) { Text("开启通知") }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { onStep(5) }) { Text("先不开启，继续") }
        }

        else -> {
            Text("三个小提示", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Bullet("长按消息可以引用、编辑或撤回；她的记忆会跟着修正。")
                Bullet("聊天记录只增不删，删除只是收进回收站，随时能捞回来。")
                Bullet("设置里可以改她的身份、说话风格，以及备份与恢复。")
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { complete(name, address) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(16.dp),
            ) { Text("开始和她聊天") }
            Spacer(Modifier.height(10.dp))
            Text(
                "清空应用数据可以重新走一遍引导。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

