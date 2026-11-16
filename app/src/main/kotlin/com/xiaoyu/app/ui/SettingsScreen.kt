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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaoyu.app.R
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

private enum class Slot(val labelRes: Int, val role: BrainRole) {
    CHAT(R.string.settings_model_chat, BrainRole.CHAT),
    CHAT_FALLBACK(R.string.settings_model_chat_fallback, BrainRole.CHAT),
    EXTRACT(R.string.settings_model_extract, BrainRole.EXTRACT),
    EXTRACT_FALLBACK(R.string.settings_model_extract_fallback, BrainRole.EXTRACT),
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
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.settings_back)) }
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
                SectionCard(stringResource(R.string.settings_section_model)) {
                    Slot.entries.forEach { slot ->
                        ProviderRow(
                            title = stringResource(slot.labelRes),
                            config = settings.brain.configFor(slot),
                            onEdit = { editingSlot = slot },
                            onClear = { viewModel.updateSettings { it.withConfig(slot, null) } },
                        )
                        if (slot != Slot.EXTRACT_FALLBACK) Divider(color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.checkHealth(BrainRole.CHAT) }, enabled = !checking) {
                            Text(if (checking) stringResource(R.string.settings_checking) else stringResource(R.string.settings_check_connection))
                        }
                        TextButton(onClick = { viewModel.updateSettings { it.copy(immersiveEnabled = !it.immersiveEnabled) } }) {
                            Text(stringResource(R.string.settings_use_demo_mode))
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
                SectionCard(stringResource(R.string.settings_section_relationship)) {
                    ToggleRow(
                        stringResource(R.string.settings_reply_pace_title),
                        stringResource(R.string.settings_reply_pace_subtitle),
                        settings.immersiveEnabled,
                    ) { enabled -> viewModel.updateSettings { it.copy(immersiveEnabled = enabled) } }
                    if (settings.immersiveEnabled) {
                        Text(
                            stringResource(R.string.settings_pace_intensity, String.format("%.1f", settings.immersiveIntensity)),
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
                        stringResource(R.string.settings_segment_sending_title),
                        stringResource(R.string.settings_segment_sending_subtitle),
                        settings.segmentSending,
                    ) { enabled -> viewModel.updateSettings { it.copy(segmentSending = enabled) } }
                    Divider(color = MaterialTheme.colorScheme.surfaceVariant)
                    ToggleRow(
                        stringResource(R.string.settings_proactive_title),
                        stringResource(R.string.settings_proactive_subtitle),
                        settings.proactiveEnabled,
                    ) { enabled ->
                        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        viewModel.updateSettings { it.copy(proactiveEnabled = enabled) }
                    }
                    Divider(color = MaterialTheme.colorScheme.surfaceVariant)
                    TextButton(onClick = { showHours = true }) {
                        Text(stringResource(R.string.settings_dnd_button, settings.dndStartHour, settings.dndEndHour))
                    }
                }
            }

            item {
                SectionCard(stringResource(R.string.settings_section_chat_mode)) {
                    Text(
                        stringResource(R.string.settings_chat_mode_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = settings.chatMode == "continuous",
                            onClick = { viewModel.updateSettings { it.copy(chatMode = "continuous") } },
                            label = { Text(stringResource(R.string.settings_chat_mode_continuous)) },
                        )
                        FilterChip(
                            selected = settings.chatMode == "session_list",
                            onClick = { viewModel.updateSettings { it.copy(chatMode = "session_list") } },
                            label = { Text(stringResource(R.string.settings_chat_mode_session_list)) },
                        )
                    }
                    if (settings.chatMode == "continuous") {
                        Spacer(Modifier.height(6.dp))
                        Divider(color = MaterialTheme.colorScheme.surfaceVariant)
                        ToggleRow(
                            stringResource(R.string.settings_topic_divider_title),
                            stringResource(R.string.settings_topic_divider_subtitle),
                            settings.showTopicDividers,
                        ) { enabled -> viewModel.updateSettings { it.copy(showTopicDividers = enabled) } }
                    }
                }
            }

            item {
                SectionCard(stringResource(R.string.settings_section_appearance)) {
                    Text(
                        stringResource(R.string.settings_theme_title),
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
                        stringResource(R.string.settings_dynamic_color_title),
                        stringResource(R.string.settings_dynamic_color_subtitle),
                        settings.dynamicColor,
                    ) { enabled -> viewModel.updateSettings { it.copy(dynamicColor = enabled) } }
                    Divider(color = MaterialTheme.colorScheme.surfaceVariant)
                    Text(
                        stringResource(R.string.settings_font_size_title),
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
                        stringResource(R.string.settings_bubble_title),
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
                                            BubbleStyle.BRAND -> stringResource(R.string.settings_bubble_brand)
                                            BubbleStyle.MINT -> stringResource(R.string.settings_bubble_mint)
                                            BubbleStyle.WARM -> stringResource(R.string.settings_bubble_warm)
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
                SectionCard(stringResource(R.string.settings_section_persona)) {
                    Text(
                        stringResource(R.string.settings_persona_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onOpenPersona) { Text(stringResource(R.string.settings_persona_edit)) }
                }
            }

            item {
                SectionCard(stringResource(R.string.settings_section_stickers)) {
                    Text(
                        stringResource(R.string.settings_stickers_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onOpenStickers) { Text(stringResource(R.string.settings_manage_stickers)) }
                }
            }

            item {
                SectionCard(stringResource(R.string.settings_section_usage)) {
                    Text(
                        stringResource(R.string.settings_usage_month, settings.usage.chatRequests, settings.usage.extractRequests),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        stringResource(R.string.settings_usage_total_tokens, settings.usage.totalTokens),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        stringResource(R.string.settings_usage_estimated_cost, String.format("%.3f", settings.usage.estimatedCost)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        stringResource(R.string.settings_usage_note),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onOpenUsage) { Text(stringResource(R.string.settings_usage_open)) }
                }
            }

            item {
                SectionCard(stringResource(R.string.settings_section_backup)) {
                    Text(
                        stringResource(R.string.settings_backup_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.createLocalBackup(if (encryptBackup) backupPassword.ifBlank { null } else null) }) { Text(stringResource(R.string.settings_backup_now)) }
                        TextButton(onClick = {
                            exportLauncher.launch("xiaoyu-backup.xiaoyu")
                        }) { Text(stringResource(R.string.settings_backup_export)) }
                        TextButton(onClick = {
                            importLauncher.launch(arrayOf("*/*"))
                        }) { Text(stringResource(R.string.settings_backup_restore)) }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.settings_backup_mode_title),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = importMode == "MERGE",
                            onClick = { importMode = "MERGE" },
                            label = { Text(stringResource(R.string.settings_backup_mode_merge)) },
                        )
                        FilterChip(
                            selected = importMode == "OVERWRITE",
                            onClick = { importMode = "OVERWRITE" },
                            label = { Text(stringResource(R.string.settings_backup_mode_overwrite)) },
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = encryptBackup, onCheckedChange = { encryptBackup = it })
                        Text(stringResource(R.string.settings_backup_encrypt), style = MaterialTheme.typography.bodyMedium)
                    }
                    if (encryptBackup) {
                        OutlinedTextField(
                            value = backupPassword,
                            onValueChange = { backupPassword = it },
                            label = { Text(stringResource(R.string.settings_backup_password_label)) },
                            singleLine = true,
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    val localBackups = backups
                    if (localBackups.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.settings_backup_local_count, localBackups.size, localBackups.first().name),
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
                SectionCard(stringResource(R.string.settings_section_diagnostics)) {
                    Text(
                        stringResource(R.string.settings_diagnostics_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onOpenDiagnostics) { Text(stringResource(R.string.settings_diagnostics_open)) }
                }
            }

            item {
                Text(
                    stringResource(R.string.settings_footer),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
    }

    editingSlot?.let { slot ->
        ProviderEditDialog(
            title = stringResource(slot.labelRes),
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

    SectionCard(stringResource(R.string.settings_section_app_lock)) {
        Text(
            stringResource(R.string.settings_app_lock_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = mode == AppLockMode.OFF,
                onClick = { viewModel.disableAppLock() },
                label = { Text(stringResource(R.string.settings_app_lock_off)) },
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
                label = { Text(stringResource(R.string.settings_app_lock_biometric)) },
            )
        }
        if (mode != AppLockMode.OFF) {
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { pinTarget = mode }) { Text(stringResource(R.string.settings_app_lock_change_pin)) }
                TextButton(onClick = { viewModel.lockNow() }) { Text(stringResource(R.string.settings_app_lock_now)) }
            }
            Text(
                stringResource(R.string.settings_app_lock_auto_hint, settings.autoLockSeconds),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    pinTarget?.let { target ->
        PinSetupDialog(
            title = if (viewModel.appLockHasPin()) stringResource(R.string.settings_app_lock_change_pin) else stringResource(R.string.settings_app_lock_set_pin),
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
                    stringResource(R.string.settings_pin_dialog_desc),
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
                    label = { Text(stringResource(R.string.settings_pin_confirm_label)) },
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
            TextButton(onClick = { onConfirm(pin) }, enabled = valid) { Text(stringResource(R.string.settings_action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_action_cancel)) }
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

    SectionCard(stringResource(R.string.settings_section_chat_background)) {
        Text(
            stringResource(R.string.settings_background_desc),
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
                    stringResource(R.string.settings_background_default),
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
            ) { Text(if (current == null) stringResource(R.string.settings_background_pick) else stringResource(R.string.settings_background_change)) }
            if (current != null) {
                TextButton(onClick = { viewModel.clearChatBackground() }) { Text(stringResource(R.string.settings_background_reset)) }
            }
        }
        if (current != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.settings_background_tune, blurValue.roundToInt(), (dimValue * 100).roundToInt()),
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
                    stringResource(R.string.settings_background_blur_hint),
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

    SectionCard(stringResource(R.string.settings_section_local_ai)) {
        Text(
            stringResource(R.string.settings_local_ai_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.settings_local_ai_url_label)) },
            placeholder = { Text("http://10.x.x.x:8300") },
            singleLine = true,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Token") },
            placeholder = { Text(stringResource(R.string.settings_local_ai_token_placeholder)) },
            singleLine = true,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = !checking,
                onClick = { viewModel.saveAndTestLocalAi(url, token) },
            ) { Text(if (checking) stringResource(R.string.settings_local_ai_connecting) else stringResource(R.string.settings_local_ai_save_test)) }
            if (settings.localAiUrl != null) {
                TextButton(onClick = {
                    url = ""
                    token = ""
                    viewModel.updateSettings {
                        it.copy(localAiUrl = null, localAiToken = null)
                    }
                    viewModel.clearLocalAiOptions()
                }) { Text(stringResource(R.string.settings_local_ai_disable)) }
            }
        }

        if (settings.localAiUrl != null) {
            Spacer(Modifier.height(12.dp))
            Divider(color = MaterialTheme.colorScheme.surfaceVariant)
            Spacer(Modifier.height(12.dp))

            OptionDropdown(
                title = stringResource(R.string.settings_voice_title),
                hint = if (voiceOptions.isEmpty()) {
                    stringResource(R.string.settings_voice_empty)
                } else {
                    stringResource(R.string.settings_voice_count, voiceOptions.size)
                },
                options = voiceOptions.ifEmpty { listOf(settings.ttsVoice) },
                selected = settings.ttsVoice,
                onSelect = { voice -> viewModel.updateSettings { it.copy(ttsVoice = voice) } },
            )
            Spacer(Modifier.height(12.dp))
            OptionChipGroup(
                title = stringResource(R.string.settings_style_title),
                hint = if (styleOptions.isEmpty()) stringResource(R.string.settings_style_empty) else null,
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
                Icon(Icons.Filled.ArrowDropDown, contentDescription = stringResource(R.string.settings_voice_expand))
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
                if (config == null) stringResource(R.string.settings_provider_unset) else "${config.label()} · ${config.model}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onEdit) { Text(if (config == null) stringResource(R.string.settings_action_set) else stringResource(R.string.settings_action_edit)) }
        if (config != null) TextButton(onClick = onClear) { Text(stringResource(R.string.settings_action_clear)) }
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
                OutlinedTextField(value = baseUrl, onValueChange = { baseUrl = it }, label = { Text(stringResource(R.string.settings_provider_base_url)) }, singleLine = true)
                OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text(stringResource(R.string.settings_provider_model)) }, singleLine = true)
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text(if (kind == ProviderKind.OLLAMA) stringResource(R.string.settings_provider_api_key_optional) else "API Key") },
                    singleLine = true,
                )
                Text(
                    stringResource(R.string.settings_provider_key_note),
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
            }) { Text(stringResource(R.string.settings_action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_action_cancel)) } },
    )
}

@Composable
private fun DndDialog(start: Int, end: Int, onDismiss: () -> Unit, onConfirm: (Int, Int) -> Unit) {
    var s by remember { mutableStateOf(start) }
    var e by remember { mutableStateOf(end) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_dnd_dialog_title)) },
        text = {
            Column {
                Text(stringResource(R.string.settings_dnd_start, s), style = MaterialTheme.typography.bodyMedium)
                Slider(value = s.toFloat(), onValueChange = { s = it.roundToInt() }, valueRange = 0f..23f, steps = 22)
                Text(stringResource(R.string.settings_dnd_end, e), style = MaterialTheme.typography.bodyMedium)
                Slider(value = e.toFloat(), onValueChange = { e = it.roundToInt() }, valueRange = 0f..23f, steps = 22)
                Text(
                    stringResource(R.string.settings_dnd_desc),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(s, e) }) { Text(stringResource(R.string.settings_action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_action_cancel)) } },
    )
}
