package com.xiaoyu.app.ui.background

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaoyu.app.R
import com.xiaoyu.app.data.repo.BackgroundRenderInput
import com.xiaoyu.app.data.settings.ChatBackgroundCodec
import com.xiaoyu.app.data.settings.ChatBackgroundPreset
import com.xiaoyu.app.data.settings.ChatBackgroundSpec
import com.xiaoyu.app.ui.AppViewModel
import com.xiaoyu.app.ui.FileImage
import com.xiaoyu.app.ui.LegibilityGuard
import com.xiaoyu.app.ui.theme.Tokens
import kotlinx.coroutines.launch

/**
 * 自定义聊天背景设置页（执行书 v3 §14.6.5 / G-12）。
 *
 * 结构：预览区（3 气泡 + 长中文 + emoji + 缩略图）→ 三档预设 → 五来源 →
 * 全部滑杆 → 实时对比度读数 → 「只对她 / 所有她」→ 恢复默认 / 取消 / 应用。
 * **应用前不写库**；旧版三键由仓库层首次读取时迁移，这里不碰。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChatBackgroundScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val identityId = settings.chosenIdentityId ?: 0L
    val scope = rememberCoroutineScope()

    var loaded by remember { mutableStateOf(false) }
    var lightSpec by remember { mutableStateOf(ChatBackgroundSpec()) }
    var darkSpec by remember { mutableStateOf(ChatBackgroundSpec()) }
    var darkEnabled by remember { mutableStateOf(false) }
    var editingDark by remember { mutableStateOf(false) }
    var applyToAll by remember { mutableStateOf(false) }
    var importFailed by remember { mutableStateOf(false) }
    var problems by remember { mutableStateOf<List<String>>(emptyList()) }
    var report by remember { mutableStateOf<LegibilityGuard.Result?>(null) }

    fun updateSpec(block: (ChatBackgroundSpec) -> ChatBackgroundSpec) {
        if (editingDark) darkSpec = block(darkSpec) else lightSpec = block(lightSpec)
    }

    fun resetLocal() {
        lightSpec = ChatBackgroundSpec()
        darkSpec = ChatBackgroundSpec()
        darkEnabled = false
        editingDark = false
        problems = emptyList()
    }

    LaunchedEffect(identityId) {
        val loadedSpec = viewModel.loadBackgroundSpec(identityId)
        lightSpec = loadedSpec
        darkSpec = loadedSpec.darkJson
            ?.let { runCatching { ChatBackgroundCodec.decode(it) }.getOrNull() }
            ?: loadedSpec
        darkEnabled = !loadedSpec.darkJson.isNullOrBlank()
        loaded = true
    }

    val spec = if (editingDark) darkSpec else lightSpec

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val sha1 = viewModel.importBackgroundImage(uri)
            if (sha1 == null) {
                importFailed = true
            } else {
                importFailed = false
                updateSpec { it.copy(source = ChatBackgroundSpec.SOURCE_IMAGE, imageSha1 = sha1) }
            }
        }
    }

    val imageFile = remember(spec.imageSha1) { viewModel.backgroundImageFile(spec.imageSha1) }
    val previewInput = remember(spec, imageFile) {
        BackgroundRenderInput(
            spec = spec,
            imageFile = imageFile,
            preRenderedFile = null,
            effectiveBlurDp = spec.blurDp,
            fromCache = false,
            lowRam = false,
        )
    }
    val cacheMb = remember(loaded) { viewModel.backgroundCacheBytes() / (1024 * 1024) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.bg_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.bg_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(Modifier.navigationBarsPadding()) {
                    if (problems.isNotEmpty()) {
                        Text(
                            problems.first(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = Tokens.SpacingLg, vertical = Tokens.SpacingXs),
                        )
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Tokens.SpacingLg, vertical = Tokens.SpacingMd),
                        horizontalArrangement = Arrangement.spacedBy(Tokens.SpacingSm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = ::resetLocal) { Text(stringResource(R.string.bg_reset)) }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onBack) { Text(stringResource(R.string.bg_cancel)) }
                        Button(
                            onClick = {
                                val finalSpec = lightSpec.copy(
                                    darkJson = if (darkEnabled) ChatBackgroundCodec.encode(darkSpec) else null,
                                )
                                val found = finalSpec.validate()
                                problems = found
                                if (found.isEmpty()) {
                                    viewModel.applyChatBackground(identityId, finalSpec, applyToAll)
                                    onBack()
                                }
                            },
                        ) { Text(stringResource(R.string.bg_apply)) }
                    }
                }
            }
        },
    ) { padding ->
        if (!loaded) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Tokens.SpacingLg, vertical = Tokens.SpacingMd),
            verticalArrangement = Arrangement.spacedBy(Tokens.SpacingMd),
        ) {
            // 预览区
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(PreviewHeight)
                    .clip(RoundedCornerShape(Tokens.RadiusLg)),
            ) {
                ChatBackgroundRenderer(
                    input = previewInput,
                    darkTheme = editingDark,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    val current = LocalLegibility.current
                    SideEffect { if (current != null) report = current }
                    PreviewContent(imageFile = imageFile)
                }
            }
            Text(
                stringResource(R.string.bg_preview_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // 实时对比度读数
            val result = report
            if (result != null) {
                ContrastRow(
                    result = result,
                    onAutoFix = {
                        updateSpec {
                            it.copy(
                                bubbleAlpha = result.bubbleAlpha,
                                scrimAlpha = result.scrimAlpha,
                                legibilityAuto = true,
                            )
                        }
                    },
                )
            }

            // 三档预设
            SectionCard(stringResource(R.string.bg_preset_title)) {
                val presetLabels = listOf(
                    ChatBackgroundPreset.CLEAR to stringResource(R.string.bg_preset_clear),
                    ChatBackgroundPreset.SOFT to stringResource(R.string.bg_preset_soft),
                    ChatBackgroundPreset.IMMERSIVE to stringResource(R.string.bg_preset_immersive),
                )
                ChipRow(presetLabels) { index ->
                    val preset = ChatBackgroundPreset.values()[index]
                    updateSpec { preset.applyTo(it) }
                }
                Text(
                    stringResource(R.string.bg_preset_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // 五来源
            SectionCard(stringResource(R.string.bg_source_title)) {
                val sourceLabels = listOf(
                    ChatBackgroundSpec.SOURCE_THEME to stringResource(R.string.bg_source_theme),
                    ChatBackgroundSpec.SOURCE_SOLID to stringResource(R.string.bg_source_solid),
                    ChatBackgroundSpec.SOURCE_GRADIENT to stringResource(R.string.bg_source_gradient),
                    ChatBackgroundSpec.SOURCE_IMAGE to stringResource(R.string.bg_source_image),
                    ChatBackgroundSpec.SOURCE_TEXTURE to stringResource(R.string.bg_source_texture),
                )
                ChipRow(sourceLabels.map { it.first to it.second }, selectedIndex = ChatBackgroundSpec.SOURCES.indexOf(spec.source)) { index ->
                    val value = sourceLabels[index].first
                    if (value == ChatBackgroundSpec.SOURCE_IMAGE && spec.imageSha1 == null) {
                        picker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    } else {
                        updateSpec { it.copy(source = value) }
                    }
                }

                when (spec.source) {
                    ChatBackgroundSpec.SOURCE_IMAGE -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val file = imageFile
                            if (file != null) {
                                FileImage(
                                    file = file,
                                    modifier = Modifier
                                        .size(ThumbSize)
                                        .clip(RoundedCornerShape(Tokens.RadiusSm)),
                                )
                                Spacer(Modifier.width(Tokens.SpacingSm))
                            }
                            Button(
                                onClick = {
                                    picker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                    )
                                },
                            ) { Text(if (file == null) stringResource(R.string.bg_pick_image) else stringResource(R.string.bg_change_image)) }
                        }
                        if (importFailed) {
                            Text(
                                stringResource(R.string.bg_import_failed),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        Text(
                            stringResource(R.string.bg_image_note),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            stringResource(R.string.bg_scale_title),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        ChipRow(
                            listOf(
                                ChatBackgroundSpec.SCALE_COVER to stringResource(R.string.bg_scale_cover),
                                ChatBackgroundSpec.SCALE_CONTAIN to stringResource(R.string.bg_scale_contain),
                                ChatBackgroundSpec.SCALE_TILE to stringResource(R.string.bg_scale_tile),
                            ),
                            selectedIndex = when (spec.scale) {
                                ChatBackgroundSpec.SCALE_CONTAIN -> 1
                                ChatBackgroundSpec.SCALE_TILE -> 2
                                else -> 0
                            },
                        ) { index ->
                            val value = when (index) {
                                0 -> ChatBackgroundSpec.SCALE_COVER
                                1 -> ChatBackgroundSpec.SCALE_CONTAIN
                                else -> ChatBackgroundSpec.SCALE_TILE
                            }
                            updateSpec { it.copy(scale = value) }
                        }
                        LabeledSlider(
                            label = stringResource(R.string.bg_zoom, spec.zoom),
                            stateText = stringResource(R.string.bg_slider_state, spec.zoom),
                            value = spec.zoom.toFloat(),
                            min = 100f,
                            max = 160f,
                            steps = 11,
                        ) {
                            updateSpec { s -> s.copy(zoom = it) }
                        }
                        AnchorGrid(selected = spec.anchor) { anchor ->
                            updateSpec { it.copy(anchor = anchor) }
                        }
                    }

                    ChatBackgroundSpec.SOURCE_SOLID -> {
                        ColorPalette(selected = spec.solidHex) { hex ->
                            updateSpec { it.copy(solidHex = hex) }
                        }
                    }

                    ChatBackgroundSpec.SOURCE_GRADIENT -> {
                        GradientPresets(current = spec) { json ->
                            updateSpec { it.copy(gradientJson = json) }
                        }
                    }

                    ChatBackgroundSpec.SOURCE_TEXTURE -> {
                        ChipRow(
                            listOf(
                                ChatBackgroundSpec.TEXTURE_LINEN to stringResource(R.string.bg_texture_linen),
                                ChatBackgroundSpec.TEXTURE_PAPER to stringResource(R.string.bg_texture_paper),
                                ChatBackgroundSpec.TEXTURE_GRID to stringResource(R.string.bg_texture_grid),
                                ChatBackgroundSpec.TEXTURE_WAVES to stringResource(R.string.bg_texture_waves),
                            ),
                            selectedIndex = ChatBackgroundSpec.TEXTURES.indexOf(spec.texture),
                        ) { index ->
                            val value = ChatBackgroundSpec.TEXTURES[index]
                            updateSpec { it.copy(texture = value) }
                        }
                    }

                    else -> Text(
                        stringResource(R.string.bg_theme_note),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // 效果
            SectionCard(stringResource(R.string.bg_effect_title)) {
                Text(stringResource(R.string.bg_blur, spec.blurDp), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                ChipRow(listOf(0, 6, 12, 18, 24, 32).map { it to "${it}dp" }, selectedIndex = listOf(0, 6, 12, 18, 24, 32).indexOf(spec.blurDp)) { index ->
                    updateSpec { it.copy(blurDp = listOf(0, 6, 12, 18, 24, 32)[index]) }
                }
                LabeledSlider(
                    label = stringResource(R.string.bg_brightness, spec.brightness),
                    stateText = stringResource(R.string.bg_slider_state, spec.brightness),
                    value = spec.brightness.toFloat(),
                    min = -40f,
                    max = 20f,
                    steps = 5,
                ) {
                    updateSpec { s -> s.copy(brightness = it) }
                }
                LabeledSlider(
                    label = stringResource(R.string.bg_saturation, spec.saturation),
                    stateText = stringResource(R.string.bg_slider_state, spec.saturation),
                    value = spec.saturation.toFloat(),
                    min = 0f,
                    max = 120f,
                    steps = 11,
                ) {
                    updateSpec { s -> s.copy(saturation = it) }
                }
            }

            // 遮罩
            SectionCard(stringResource(R.string.bg_scrim_title)) {
                ChipRow(
                    listOf(
                        ChatBackgroundSpec.SCRIM_AUTO to stringResource(R.string.bg_scrim_auto),
                        ChatBackgroundSpec.SCRIM_LIGHT to stringResource(R.string.bg_scrim_light),
                        ChatBackgroundSpec.SCRIM_DARK to stringResource(R.string.bg_scrim_dark),
                        ChatBackgroundSpec.SCRIM_CUSTOM to stringResource(R.string.bg_scrim_custom),
                    ),
                    selectedIndex = listOf(
                        ChatBackgroundSpec.SCRIM_AUTO,
                        ChatBackgroundSpec.SCRIM_LIGHT,
                        ChatBackgroundSpec.SCRIM_DARK,
                        ChatBackgroundSpec.SCRIM_CUSTOM,
                    ).indexOf(spec.scrimTone),
                ) { index ->
                    val value = listOf(
                        ChatBackgroundSpec.SCRIM_AUTO,
                        ChatBackgroundSpec.SCRIM_LIGHT,
                        ChatBackgroundSpec.SCRIM_DARK,
                        ChatBackgroundSpec.SCRIM_CUSTOM,
                    )[index]
                    updateSpec { it.copy(scrimTone = value) }
                }
                if (spec.scrimTone == ChatBackgroundSpec.SCRIM_CUSTOM) {
                    OutlinedTextField(
                        value = spec.scrimHex.orEmpty(),
                        onValueChange = { text ->
                            if (text.length <= 9) updateSpec { it.copy(scrimHex = text.ifBlank { null }) }
                        },
                        singleLine = true,
                        label = { Text(stringResource(R.string.bg_scrim_hex_label)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                LabeledSlider(
                    label = stringResource(R.string.bg_scrim_alpha, spec.scrimAlpha),
                    stateText = stringResource(R.string.bg_slider_state, spec.scrimAlpha),
                    value = spec.scrimAlpha.toFloat(),
                    min = 0f,
                    max = 80f,
                    steps = 15,
                ) {
                    updateSpec { s -> s.copy(scrimAlpha = it) }
                }
                LabeledSlider(
                    label = stringResource(R.string.bg_vignette, spec.vignette),
                    stateText = stringResource(R.string.bg_slider_state, spec.vignette),
                    value = spec.vignette.toFloat(),
                    min = 0f,
                    max = 60f,
                    steps = 11,
                ) {
                    updateSpec { s -> s.copy(vignette = it) }
                }
            }

            // 气泡
            SectionCard(stringResource(R.string.bg_bubble_title)) {
                LabeledSlider(
                    label = stringResource(R.string.bg_bubble_alpha, spec.bubbleAlpha),
                    stateText = stringResource(R.string.bg_slider_state, spec.bubbleAlpha),
                    value = spec.bubbleAlpha.toFloat(),
                    min = 55f,
                    max = 100f,
                    steps = 8,
                ) { updateSpec { s -> s.copy(bubbleAlpha = it) } }
                ToggleRow(stringResource(R.string.bg_bubble_underlay), spec.bubbleUnderlay) { value ->
                    updateSpec { it.copy(bubbleUnderlay = value) }
                }
                ToggleRow(stringResource(R.string.bg_bubble_blur), spec.bubbleBlur) { value ->
                    updateSpec { it.copy(bubbleBlur = value) }
                }
                ToggleRow(stringResource(R.string.bg_legibility_auto), spec.legibilityAuto) { value ->
                    updateSpec { it.copy(legibilityAuto = value) }
                }
                Text(
                    stringResource(R.string.bg_legibility_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // 动态
            SectionCard(stringResource(R.string.bg_motion_title)) {
                Text(stringResource(R.string.bg_parallax), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                ChipRow(listOf(0, 15, 30).map { it to "$it%" }, selectedIndex = listOf(0, 15, 30).indexOf(spec.parallax)) { index ->
                    updateSpec { it.copy(parallax = listOf(0, 15, 30)[index]) }
                }
                Text(
                    stringResource(R.string.bg_parallax_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // 深色单独设置
            SectionCard(stringResource(R.string.bg_dark_title)) {
                ToggleRow(stringResource(R.string.bg_dark_separate), darkEnabled) { enabled ->
                    darkEnabled = enabled
                    if (enabled) {
                        if (lightSpec.darkJson.isNullOrBlank()) darkSpec = lightSpec.copy(darkJson = null)
                    } else {
                        editingDark = false
                    }
                }
                if (darkEnabled) {
                    ChipRow(
                        listOf(
                            false to stringResource(R.string.bg_edit_light),
                            true to stringResource(R.string.bg_edit_dark),
                        ),
                        selectedIndex = if (editingDark) 1 else 0,
                    ) { index ->
                        editingDark = index == 1
                    }
                }
            }

            // 生效范围
            SectionCard(stringResource(R.string.bg_scope_title)) {
                ChipRow(
                    listOf(
                        false to stringResource(R.string.bg_scope_her),
                        true to stringResource(R.string.bg_scope_all),
                    ),
                    selectedIndex = if (applyToAll) 1 else 0,
                ) { index ->
                    applyToAll = index == 1
                }
                Text(
                    stringResource(R.string.bg_storage_note, cacheMb),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(Tokens.SpacingXl))
        }
    }
}

@Composable
private fun PreviewContent(imageFile: java.io.File?) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(Tokens.SpacingMd),
        verticalArrangement = Arrangement.spacedBy(Tokens.SpacingSm),
    ) {
        PreviewBubble(stringResource(R.string.bg_preview_bubble_her_1), mine = false)
        PreviewBubble(stringResource(R.string.bg_preview_bubble_me), mine = true)
        PreviewBubble(
            stringResource(R.string.bg_preview_bubble_her_2),
            mine = false,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            val file = imageFile
            if (file != null) {
                FileImage(
                    file = file,
                    modifier = Modifier
                        .size(ThumbSize)
                        .clip(RoundedCornerShape(Tokens.RadiusSm)),
                )
                Spacer(Modifier.width(Tokens.SpacingSm))
            }
            Text(
                "😊",
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun PreviewBubble(text: String, mine: Boolean) {
    val visual = LocalBubbleVisual.current
    val colors = com.xiaoyu.app.ui.theme.LocalBubbleColors.current
    val bg = (if (mine) colors.myBackground else colors.herBackground).copy(alpha = visual.alpha)
    val fg = if (mine) colors.myContent else colors.herContent
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            color = bg,
            contentColor = fg,
            shape = RoundedCornerShape(Tokens.RadiusLg),
            modifier = Modifier.fillMaxWidth(0.82f),
        ) {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(
                    horizontal = Tokens.BubbleHorizontal,
                    vertical = Tokens.BubbleVertical,
                ),
            )
        }
    }
}

@Composable
private fun ContrastRow(result: LegibilityGuard.Result, onAutoFix: () -> Unit) {
    val passing = result.passed
    val color = if (passing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpacingXxs)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Tokens.SpacingMd)) {
            Text(
                stringResource(R.string.bg_contrast_body, result.bodyContrast),
                style = MaterialTheme.typography.labelMedium,
                color = if (result.bodyContrast >= LegibilityGuard.WCAG_AA_CONTRAST) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
            Text(
                stringResource(R.string.bg_contrast_aux, result.auxiliaryContrast),
                style = MaterialTheme.typography.labelMedium,
                color = if (result.auxiliaryContrast >= LegibilityGuard.WCAG_AUX_CONTRAST) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        }
        result.hint?.let { hint ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    hint,
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onAutoFix) { Text(stringResource(R.string.bg_auto_fix)) }
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(Tokens.RadiusLg),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Tokens.SpacingLg)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(Tokens.SpacingSm))
            Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpacingSm)) { content() }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ChipRow(options: List<Pair<Any, String>>, selectedIndex: Int? = null, onSelect: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Tokens.SpacingSm)) {
        options.forEachIndexed { index, (value, label) ->
            FilterChip(
                selected = selectedIndex == index,
                onClick = { onSelect(index) },
                label = { Text(label) },
                leadingIcon = if (selectedIndex == index) {
                    { Text("✓") }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    stateText: String,
    value: Float,
    min: Float,
    max: Float,
    steps: Int,
    onValue: (Int) -> Unit,
) {
    Column {
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Slider(
            value = value.coerceIn(min, max),
            onValueChange = { onValue(it.roundToStep()) },
            valueRange = min..max,
            steps = steps,
            modifier = Modifier.semantics {
                contentDescription = label
                stateDescription = stateText
            },
        )
    }
}

private fun Float.roundToStep(): Int = kotlin.math.round(this).toInt()

@Composable
private fun ToggleRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Tokens.SizeTouchTarget)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onToggle),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = null)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorPalette(selected: String, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Tokens.SpacingSm)) {
        SolidPalette.forEach { hex ->
            val color = LegibilityGuard.parseHex(hex)?.let { Color(it) } ?: Color.Transparent
            val active = selected.equals(hex, ignoreCase = true)
            val swatchDesc = stringResource(R.string.bg_swatch_desc, hex)
            Box(
                Modifier
                    .size(Tokens.SizeTouchTarget)
                    .semantics { contentDescription = swatchDesc }
                    .clickable { onSelect(hex) },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(Tokens.SpacingXxl)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            width = if (active) 3.dp else 1.dp,
                            color = if (active) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape,
                        ),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GradientPresets(current: ChatBackgroundSpec, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Tokens.SpacingSm)) {
        GradientPalette.forEach { (labelRes, from, to) ->
            val json = """{"type":"linear","from":"$from","to":"$to","angle":135}"""
            FilterChip(
                selected = current.gradientJson == json,
                onClick = { onSelect(json) },
                label = { Text(stringResource(labelRes)) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun AnchorGrid(selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Tokens.SpacingXs)) {
        Text(stringResource(R.string.bg_anchor_title), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        AnchorOptions.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.SpacingXs)) {
                row.forEach { (anchor, symbol, labelRes) ->
                    val anchorDesc = stringResource(R.string.bg_anchor_desc, stringResource(labelRes))
                    FilterChip(
                        selected = selected == anchor,
                        onClick = { onSelect(anchor) },
                        label = { Text(symbol) },
                        modifier = Modifier.semantics { contentDescription = anchorDesc },
                    )
                }
            }
        }
    }
}

private val PreviewHeight = 232.dp
private val ThumbSize = 48.dp

private val SolidPalette = listOf(
    "#FFFBFE", "#F5EFE6", "#E8F0EA", "#E6EEF6", "#FDECEF", "#1C1B1F",
)

private val GradientPalette = listOf(
    Triple(R.string.bg_gradient_mist, "#F6E7D8", "#DCE7F2"),
    Triple(R.string.bg_gradient_dusk, "#3B2F4A", "#8D6E8C"),
    Triple(R.string.bg_gradient_mint, "#DDF3E4", "#BFD9F2"),
    Triple(R.string.bg_gradient_peach, "#FFE1D6", "#FFC1B6"),
)

private val AnchorOptions = listOf(
    Triple("topStart", "↖", R.string.bg_anchor_top_start),
    Triple("topCenter", "↑", R.string.bg_anchor_top_center),
    Triple("topEnd", "↗", R.string.bg_anchor_top_end),
    Triple("centerStart", "←", R.string.bg_anchor_center_start),
    Triple("center", "·", R.string.bg_anchor_center),
    Triple("centerEnd", "→", R.string.bg_anchor_center_end),
    Triple("bottomStart", "↙", R.string.bg_anchor_bottom_start),
    Triple("bottomCenter", "↓", R.string.bg_anchor_bottom_center),
    Triple("bottomEnd", "↘", R.string.bg_anchor_bottom_end),
)
