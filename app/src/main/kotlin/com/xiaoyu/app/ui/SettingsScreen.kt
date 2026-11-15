package com.xiaoyu.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaoyu.app.data.brain.ProviderHealth
import com.xiaoyu.app.data.settings.AppLockMode
import com.xiaoyu.app.data.settings.AppSettings
import com.xiaoyu.app.data.settings.ProviderConfig
import com.xiaoyu.app.data.backup.BackupImporter
import com.xiaoyu.app.data.settings.ProviderKind
import com.xiaoyu.app.ui.theme.BubbleStyle
import com.xiaoyu.app.ui.theme.FontScaleSteps
import com.xiaoyu.app.ui.theme.ThemeMode
import com.xiaoyu.soul.core.BrainRole
import kotlin.math.roundToInt

private enum class Slot(val label: String, val role: BrainRole) {
    CHAT("聊天主模型", BrainRole.CHAT),
    CHAT_FALLBACK("聊天备用模型", BrainRole.CHAT),
    EXTRACT("记忆 / 日记模型", BrainRole.EXTRACT),
    EXTRACT_FALLBACK("记忆备用模型", BrainRole.EXTRACT),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onOpenPersona: () -> Unit,
    onOpenDiagnostics: () -> Unit = {},
    onOpenUsage: () -> Unit = {},
    onOpenStickers: () -> Unit = {},
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val health by viewModel.health.collectAsStateWithLifecycle()
    val checking by viewModel.checkingHealth.collectAsStateWithLifecycle()
    val backups by viewModel.backups.collectAsStateWithLifecycle()

    var editingSlot by remember { mutableStateOf<Slot?>(null) }
    var showHours by remember { mutableStateOf(false) }
    var encryptBackup by remember { mutableStateOf(false) }
    var backupPassword by remember { mutableStateOf("") }
    var importMode by rememberSaveable { mutableStateOf("MERGE") }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri -> uri?.let { viewModel.exportBackup(it, backupPassword.ifBlank { null }) } }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            viewModel.importBackup(
                it,
                backupPassword.ifBlank { null },
                if (importMode == "OVERWRITE") BackupImporter.Mode.OVERWRITE else BackupImporter.Mode.MERGE,
            )
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "返回") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard("模型") {
                    Slot.entries.forEach { slot ->
                        ProviderRow(
                            title = slot.label,
                            config = settings.brain.configFor(slot),
                            onEdit = { editingSlot = slot },
                            onClear = { viewModel.updateSettings { it.withConfig(slot, null) } },
                        )
                        if (slot != Slot.EXTRACT_FALLBACK) Divider(color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.checkHealth(BrainRole.CHAT) }, enabled = !checking) {
                            Text(if (checking) "检测中…" else "检测连接")
                        }
                        TextButton(onClick = { viewModel.updateSettings { it.copy(immersiveEnabled = !it.immersiveEnabled) } }) {
                            Text("不需要？先用示例模式")
                        }
                    }
                    health.forEach { item: ProviderHealth ->
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "${item.label} · ${item.model}：${item.message}",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (item.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            item {
                SectionCard("相处方式") {
                    ToggleRow(
                        "回复节奏",
                        "让她像真人一样思考几秒再回，而不是瞬间弹出",
                        settings.immersiveEnabled,
                    ) { enabled -> viewModel.updateSettings { it.copy(immersiveEnabled = enabled) } }
                    if (settings.immersiveEnabled) {
                        Text(
                            "节奏强度 ${String.format("%.1f", settings.immersiveIntensity)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Slider(
                            value = settings.immersiveIntensity,
                            onValueChange = { value -> viewModel.updateSettings { it.copy(immersiveIntensity = value) } },
                            valueRange = 0.2f..2.0f,
                        )
                    }
                    Divider(color = MaterialTheme.colorScheme.surfaceVariant)
                    ToggleRow(
                        "分段发送",
                        "长回复拆成几条短消息连发，更像真人在聊天",
                        settings.segmentSending,
                    ) { enabled -> viewModel.updateSettings { it.copy(segmentSending = enabled) } }
                    Divider(color = MaterialTheme.colorScheme.surfaceVariant)
                    ToggleRow(
                        "允许她主动找你",
                        "每天在合适的时间，她可能会发来一条消息",
                        settings.proactiveEnabled,
                    ) { enabled ->
                        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        viewModel.updateSettings { it.copy(proactiveEnabled = enabled) }
                    }
                    Divider(color = MaterialTheme.colorScheme.surfaceVariant)
                    TextButton(onClick = { showHours = true }) {
                        Text("免打扰：${settings.dndStartHour}:00 - ${settings.dndEndHour}:00")
                    }
                }
            }

            item {
                SectionCard("聊天模式") {
                    Text(
                        "连续流：像微信一样只有一个聊天窗口，所有消息按时间连续展示；会话列表：按话题分成多个对话。两种模式共用同一份数据。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = settings.chatMode == "continuous",
                            onClick = { viewModel.updateSettings { it.copy(chatMode = "continuous") } },
                            label = { Text("连续流（默认）") },
                        )
                        FilterChip(
                            selected = settings.chatMode == "session_list",
                            onClick = { viewModel.updateSettings { it.copy(chatMode = "session_list") } },
                            label = { Text("会话列表") },
                        )
                    }
                    if (settings.chatMode == "continuous") {
                        Spacer(Modifier.height(6.dp))
                        Divider(color = MaterialTheme.colorScheme.surfaceVariant)
                        ToggleRow(
                            "话题分隔线",
                            "幕后会话切换处插入“—— 新的话题 ——”淡色分隔线",
                            settings.showTopicDividers,
                        ) { enabled -> viewModel.updateSettings { it.copy(showTopicDividers = enabled) } }
                    }
                }
            }

            item {
                SectionCard("外观") {
                    Text(
                        "主题",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = ThemeMode.from(settings.themeMode) == mode,
                                onClick = { viewModel.updateSettings { it.copy(themeMode = mode.name) } },
                                label = { Text(mode.label) },
                            )
                        }
                    }
                    Divider(color = MaterialTheme.colorScheme.surfaceVariant)
                    ToggleRow(
                        "动态取色",
                        "Android 12+ 跟随壁纸取色；关闭时使用品牌青灰蓝",
                        settings.dynamicColor,
                    ) { enabled -> viewModel.updateSettings { it.copy(dynamicColor = enabled) } }
                    Divider(color = MaterialTheme.colorScheme.surfaceVariant)
                    Text(
                        "字号",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FontScaleSteps.forEach { scale ->
                            FilterChip(
                                selected = kotlin.math.abs(settings.fontScale - scale) < 0.01f,
                                onClick = { viewModel.updateSettings { it.copy(fontScale = scale) } },
                                label = { Text("${(scale * 100).roundToInt()}%") },
                            )
                        }
                    }
                    Divider(color = MaterialTheme.colorScheme.surfaceVariant)
                    Text(
                        "我的气泡",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BubbleStyle.entries.forEach { style ->
                            FilterChip(
                                selected = BubbleStyle.from(settings.bubbleStyle) == style,
                                onClick = { viewModel.updateSettings { it.copy(bubbleStyle = style.name) } },
                                label = {
                                    Text(
                                        when (style) {
                                            BubbleStyle.BRAND -> "青灰"
                                            BubbleStyle.MINT -> "薄荷"
                                            BubbleStyle.WARM -> "暖橙"
                                        },
                                    )
                                },
                            )
                        }
                    }
                }
            }

            item {
                BackgroundSection(viewModel)
            }

            item {
                LocalAiSection(settings, viewModel)
            }

            item {
                SectionCard("人设") {
                    Text(
                        "她的身份与说话风格都可以改。新的对话会使用当前设置。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onOpenPersona) { Text("编辑她是谁 / 怎么说话") }
                }
            }

            item {
                SectionCard("表情包") {
                    Text(
                        "新建分组、改名、调排序、停用或删除，也能看到表情占用的空间。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onOpenStickers) { Text("管理表情包") }
                }
            }

            item {
                SectionCard("用量") {
                    Text(
                        "本月请求：聊天 ${settings.usage.chatRequests} 次 · 记忆 ${settings.usage.extractRequests} 次",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "Token 合计：${settings.usage.totalTokens}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "估算花费：¥${String.format("%.3f", settings.usage.estimatedCost)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "示例模式和本地 Ollama 不计费；数值仅供参考。",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onOpenUsage) { Text("查看用量统计与成本护栏") }
                }
            }

            item {
                SectionCard("备份") {
                    Text(
                        "聊天、记忆、日记全部在本地。备份文件包含数据库和你改过的提示词。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.createLocalBackup(if (encryptBackup) backupPassword.ifBlank { null } else null) }) { Text("立即备份") }
                        TextButton(onClick = {
                            exportLauncher.launch("xiaoyu-backup.xiaoyu")
                        }) { Text("导出") }
                        TextButton(onClick = {
                            importLauncher.launch(arrayOf("*/*"))
                        }) { Text("恢复") }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "恢复方式",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = importMode == "MERGE",
                            onClick = { importMode = "MERGE" },
                            label = { Text("合并（保留现有聊天）") },
                        )
                        FilterChip(
                            selected = importMode == "OVERWRITE",
                            onClick = { importMode = "OVERWRITE" },
                            label = { Text("覆盖（重启后生效）") },
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = encryptBackup, onCheckedChange = { encryptBackup = it })
                        Text("用口令加密备份（AES-GCM）", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (encryptBackup) {
                        OutlinedTextField(
                            value = backupPassword,
                            onValueChange = { backupPassword = it },
                            label = { Text("备份口令（恢复时需要）") },
                            singleLine = true,
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    val localBackups = backups
                    if (localBackups.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "本机备份 ${localBackups.size} 份，最近：${localBackups.first().name}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                AppLockSection(viewModel)
            }

            item {
                BackgroundSelfCheckCard(lastPushAt = settings.lastPushAt)
            }

            item {
                SectionCard("诊断") {
                    Text(
                        "查看最近 50 次模型请求的 Prompt 分段、模型原文与后处理事件。数据只留在这台设备上，可随时清空。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onOpenDiagnostics) { Text("查看最近请求") }
                }
            }

            item {
                Text(
                    "小鱼 v1.0 · 数据只保存在这台设备上\n聊天记录只增不改，删除记忆只影响她的记忆，不影响聊天原文。",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
    }

    editingSlot?.let { slot ->
        ProviderEditDialog(
            title = slot.label,
            config = settings.brain.configFor(slot),
            onDismiss = { editingSlot = null },
            onConfirm = { config ->
                viewModel.updateSettings { it.withConfig(slot, config) }
                editingSlot = null
            },
        )
    }

    if (showHours) {
        DndDialog(
            start = settings.dndStartHour,
            end = settings.dndEndHour,
            onDismiss = { showHours = false },
            onConfirm = { start, end ->
                viewModel.updateSettings { it.copy(dndStartHour = start, dndEndHour = end) }
                showHours = false
            },
        )
    }
}

private fun com.xiaoyu.app.data.settings.BrainSettings.configFor(slot: Slot): ProviderConfig? = when (slot) {
    Slot.CHAT -> chat
    Slot.CHAT_FALLBACK -> chatFallback
    Slot.EXTRACT -> extract
    Slot.EXTRACT_FALLBACK -> extractFallback
}

private fun AppSettings.withConfig(slot: Slot, config: ProviderConfig?): AppSettings {
    val brain = when (slot) {
        Slot.CHAT -> if (config == null) brain else brain.copy(chat = config)
        Slot.CHAT_FALLBACK -> brain.copy(chatFallback = config)
        Slot.EXTRACT -> if (config == null) brain else brain.copy(extract = config)
        Slot.EXTRACT_FALLBACK -> brain.copy(extractFallback = config)
    }
    return copy(brain = brain)
}

/** 应用锁（执行书 5.6.3）：PIN / 生物识别，PIN 只存 PBKDF2 hash，不进备份。 */
@Composable
private fun AppLockSection(viewModel: AppViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val mode = AppLockMode.from(settings.appLockMode)
    var pinTarget by remember { mutableStateOf<AppLockMode?>(null) }

    SectionCard("隐私与安全") {
        Text(
            "开启后，每次回到 App 都要先解锁；聊天内容与通知正文在锁定时不会露出。PIN 只存加密后的摘要，不写入备份。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = mode == AppLockMode.OFF,
                onClick = { viewModel.disableAppLock() },
                label = { Text("关闭") },
            )
            FilterChip(
                selected = mode == AppLockMode.PIN,
                onClick = {
                    if (viewModel.appLockHasPin()) viewModel.setAppLockMode(AppLockMode.PIN)
                    else pinTarget = AppLockMode.PIN
                },
                label = { Text("PIN") },
            )
            FilterChip(
                selected = mode == AppLockMode.BIOMETRIC,
                onClick = {
                    if (viewModel.appLockHasPin()) viewModel.setAppLockMode(AppLockMode.BIOMETRIC)
                    else pinTarget = AppLockMode.BIOMETRIC
                },
                label = { Text("生物识别") },
            )
        }
        if (mode != AppLockMode.OFF) {
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { pinTarget = mode }) { Text("修改 PIN") }
                TextButton(onClick = { viewModel.lockNow() }) { Text("立即锁定") }
            }
            Text(
                "回到前台超过 ${settings.autoLockSeconds} 秒需要重新解锁；应用锁开启时通知只显示「小鱼发来一条消息」。",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    pinTarget?.let { target ->
        PinSetupDialog(
            title = if (viewModel.appLockHasPin()) "修改 PIN" else "设置 PIN",
            onDismiss = { pinTarget = null },
            onConfirm = { pin ->
                viewModel.enableAppLock(pin, target) { ok -> if (ok) pinTarget = null }
            },
        )
    }
}

@Composable
private fun PinSetupDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val valid = pin.length in 4..6 && pin.all { it.isDigit() } && pin == confirm
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    "输入 4~6 位数字。忘了 PIN 只能关闭应用锁重新设置。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { value -> pin = value.filter { it.isDigit() }.take(6) },
                    label = { Text("PIN") },
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { value -> confirm = value.filter { it.isDigit() }.take(6) },
                    label = { Text("再输一次") },
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(pin) }, enabled = valid) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun BackgroundSection(viewModel: AppViewModel) {
    val file by viewModel.chatBackgroundFile.collectAsStateWithLifecycle()
    val blur by viewModel.chatBackgroundBlur.collectAsStateWithLifecycle()
    val dim by viewModel.chatBackgroundDim.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::pickChatBackground)
    }
    // 滑块拖动时只在本地更新，松手才落盘，避免每帧写一次设置
    var blurValue by remember(blur) { mutableStateOf(blur) }
    var dimValue by remember(dim) { mutableStateOf(dim) }

    SectionCard("聊天背景") {
        Text(
            "选一张喜欢的图片铺在聊天下面。图片会压缩后保存在这台设备上，不会上传，也不需要相册权限。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        val current = file
        if (current != null) {
            FileImage(
                file = current,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(14.dp)),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "当前是默认纯色背景",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    picker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
            ) { Text(if (current == null) "选择图片" else "更换图片") }
            if (current != null) {
                TextButton(onClick = { viewModel.clearChatBackground() }) { Text("恢复默认") }
            }
        }
        if (current != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                "模糊 ${blurValue.roundToInt()} · 暗化 ${(dimValue * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = blurValue,
                onValueChange = { blurValue = it },
                onValueChangeFinished = {
                    viewModel.updateSettings { it.copy(chatBackgroundBlur = blurValue) }
                },
                valueRange = 0f..24f,
            )
            Slider(
                value = dimValue,
                onValueChange = { dimValue = it },
                onValueChangeFinished = {
                    viewModel.updateSettings { it.copy(chatBackgroundDim = dimValue) }
                },
                valueRange = 0f..0.8f,
            )
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                Text(
                    "模糊效果需要 Android 12 及以上；当前系统会自动只用暗化来保证气泡清晰。",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LocalAiSection(settings: AppSettings, viewModel: AppViewModel) {
    var url by remember(settings.localAiUrl) { mutableStateOf(settings.localAiUrl ?: "") }
    var token by remember(settings.localAiToken) { mutableStateOf(settings.localAiToken ?: "") }
    val voiceOptions by viewModel.voiceOptions.collectAsStateWithLifecycle()
    val styleOptions by viewModel.styleOptions.collectAsStateWithLifecycle()
    val checking by viewModel.localAiChecking.collectAsStateWithLifecycle()

    // 已配置但还没拉到预设时，进设置页自动拉一次（避免每次进都弹提示）
    LaunchedEffect(settings.localAiUrl) {
        if (settings.localAiUrl != null && styleOptions.isEmpty()) viewModel.refreshLocalAiOptions()
    }

    SectionCard("本地 AI 服务（生图 + 语音）") {
        Text(
            "家里电脑跑着 DSHA Gateway 时可用：生图走 ComfyUI，语音走 CosyVoice3。" +
                "填网关地址和 Token，电脑关机不影响聊天。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("网关地址") },
            placeholder = { Text("http://10.x.x.x:8300") },
            singleLine = true,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Token") },
            placeholder = { Text("Gateway 配置里的 [server].token") },
            singleLine = true,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = !checking,
                onClick = { viewModel.saveAndTestLocalAi(url, token) },
            ) { Text(if (checking) "连接中…" else "保存并测试") }
            if (settings.localAiUrl != null) {
                TextButton(onClick = {
                    url = ""
                    token = ""
                    viewModel.updateSettings {
                        it.copy(localAiUrl = null, localAiToken = null)
                    }
                    viewModel.clearLocalAiOptions()
                }) { Text("停用") }
            }
        }

        if (settings.localAiUrl != null) {
            Spacer(Modifier.height(12.dp))
            Divider(color = MaterialTheme.colorScheme.surfaceVariant)
            Spacer(Modifier.height(12.dp))

            OptionDropdown(
                title = "朗读音色",
                hint = if (voiceOptions.isEmpty()) {
                    "未能读取音色列表，保存地址后重试"
                } else {
                    "共 ${voiceOptions.size} 个音色，点击切换"
                },
                options = voiceOptions.ifEmpty { listOf(settings.ttsVoice) },
                selected = settings.ttsVoice,
                onSelect = { voice -> viewModel.updateSettings { it.copy(ttsVoice = voice) } },
            )
            Spacer(Modifier.height(12.dp))
            OptionChipGroup(
                title = "生图画风",
                hint = if (styleOptions.isEmpty()) "未能读取画风列表，保存地址后重试" else null,
                options = styleOptions.ifEmpty { listOf(settings.imageStyle) },
                selected = settings.imageStyle,
                onSelect = { style -> viewModel.updateSettings { it.copy(imageStyle = style) } },
            )
        }
    }
}

/** 选项较多的预设（如音色，几十个）用下拉选择，避免 Chip 铺满整屏。 */
@Composable
private fun OptionDropdown(
    title: String,
    hint: String?,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
        )
        if (hint != null) {
            Text(
                hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = selected,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Icon(Icons.Filled.ArrowDropDown, contentDescription = "展开音色列表")
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.heightIn(max = 320.dp).fillMaxWidth(0.9f),
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                option,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (option == selected) FontWeight.Bold else FontWeight.Normal,
                            )
                        },
                        onClick = {
                            expanded = false
                            onSelect(option)
                        },
                    )
                }
            }
        }
    }
}

/** 预设选项：数量少时用横向换行的 FilterChip。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OptionChipGroup(
    title: String,
    hint: String?,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
        )
        if (hint != null) {
            Text(
                hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            options.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { option ->
                        FilterChip(
                            selected = option == selected,
                            onClick = { onSelect(option) },
                            label = {
                                Text(
                                    option,
                                    maxLines = 1,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderRow(title: String, config: ProviderConfig?, onEdit: () -> Unit, onClear: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(
                if (config == null) "未设置" else "${config.label()} · ${config.model}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onEdit) { Text(if (config == null) "设置" else "修改") }
        if (config != null) TextButton(onClick = onClear) { Text("清除") }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ProviderEditDialog(
    title: String,
    config: ProviderConfig?,
    onDismiss: () -> Unit,
    onConfirm: (ProviderConfig) -> Unit,
) {
    var kind by remember { mutableStateOf(config?.kindEnum ?: ProviderKind.DEEPSEEK) }
    var baseUrl by remember { mutableStateOf(config?.baseUrl ?: kind.defaultBaseUrl) }
    var model by remember { mutableStateOf(config?.model ?: kind.defaultModel) }
    var apiKey by remember { mutableStateOf(config?.apiKey.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ProviderKind.entries.take(2).forEach { item ->
                        FilterChip(
                            selected = kind == item,
                            onClick = {
                                kind = item
                                baseUrl = item.defaultBaseUrl
                                model = item.defaultModel
                            },
                            label = { Text(item.label) },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ProviderKind.entries.drop(2).forEach { item ->
                        FilterChip(
                            selected = kind == item,
                            onClick = {
                                kind = item
                                baseUrl = item.defaultBaseUrl
                                model = item.defaultModel
                            },
                            label = { Text(item.label) },
                        )
                    }
                }
                OutlinedTextField(value = baseUrl, onValueChange = { baseUrl = it }, label = { Text("接口地址") }, singleLine = true)
                OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text("模型名") }, singleLine = true)
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text(if (kind == ProviderKind.OLLAMA) "API Key（本地可不填）" else "API Key") },
                    singleLine = true,
                )
                Text(
                    "Key 会加密保存在本机数据库，不会上传到任何第三方。",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(
                    ProviderConfig(
                        kind = kind.name,
                        baseUrl = baseUrl.trim(),
                        apiKey = apiKey.trim(),
                        model = model.trim(),
                    )
                )
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun DndDialog(start: Int, end: Int, onDismiss: () -> Unit, onConfirm: (Int, Int) -> Unit) {
    var s by remember { mutableStateOf(start) }
    var e by remember { mutableStateOf(end) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("免打扰时段") },
        text = {
            Column {
                Text("开始：$s:00", style = MaterialTheme.typography.bodyMedium)
                Slider(value = s.toFloat(), onValueChange = { s = it.roundToInt() }, valueRange = 0f..23f, steps = 22)
                Text("结束：$e:00", style = MaterialTheme.typography.bodyMedium)
                Slider(value = e.toFloat(), onValueChange = { e = it.roundToInt() }, valueRange = 0f..23f, steps = 22)
                Text(
                    "这段时间她不会主动发消息；跨过午夜也可以。",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(s, e) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
