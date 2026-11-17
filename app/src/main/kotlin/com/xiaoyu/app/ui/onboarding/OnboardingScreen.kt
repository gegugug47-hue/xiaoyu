package com.xiaoyu.app.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaoyu.app.R
import com.xiaoyu.app.data.settings.BrainSettings
import com.xiaoyu.app.data.settings.ProviderConfig
import com.xiaoyu.app.data.settings.ProviderKind
import com.xiaoyu.app.ui.AppViewModel
import com.xiaoyu.app.ui.theme.LocalYuMotion
import com.xiaoyu.app.ui.theme.YuThemePreset
import kotlinx.coroutines.launch

/** 引导总步数：欢迎 / 起名 / 主题 / 模型 / 通知 / 完成。 */
private const val STEP_COUNT = 6

/**
 * 首次引导（执行书 v3 §7.12）。
 *
 * - 左右滑动 + 底部进度点，任意一步都可以「跳过」直接进入聊天；
 * - 跳过同样写 `onboardingDone`，只是不落人设/主题/模型；
 * - 文案全部走 `strings_onboarding.xml`（中英各一份）；
 * - 每页可滚动、按钮最小 48dp，`fontScale=2.0` 也不会被截断。
 */
@Composable
fun OnboardingScreen(viewModel: AppViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val identities by viewModel.identities.collectAsStateWithLifecycle()
    val styles by viewModel.styles.collectAsStateWithLifecycle()

    val pagerState = rememberPagerState(pageCount = { STEP_COUNT })
    val scope = rememberCoroutineScope()

    var herName by rememberSaveable { mutableStateOf("") }
    var nickname by rememberSaveable { mutableStateOf(settings.userNickname) }
    var presetName by rememberSaveable { mutableStateOf(YuThemePreset.from(settings.themePreset).name) }
    var kindName by rememberSaveable { mutableStateOf(settings.brain.chat.kindEnum.name) }
    var baseUrl by rememberSaveable { mutableStateOf(settings.brain.chat.baseUrl) }
    var model by rememberSaveable { mutableStateOf(settings.brain.chat.model) }
    var apiKey by rememberSaveable { mutableStateOf(settings.brain.chat.apiKey) }

    // 身份异步到达后再补默认名字，用户已经输入过就不覆盖。
    LaunchedEffect(identities) {
        if (herName.isBlank()) herName = identities.firstOrNull()?.name.orEmpty()
    }

    // 用户拒绝也不阻断引导，通知只是可选项。
    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    val goNext: () -> Unit = {
        if (pagerState.currentPage < STEP_COUNT - 1) {
            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
        }
    }
    val finish: () -> Unit = {
        val configured = baseUrl.isNotBlank() && model.isNotBlank() &&
            (ProviderKind.valueOf(kindName) == ProviderKind.OLLAMA || apiKey.isNotBlank())
        val brain: BrainSettings? = if (configured) {
            settings.brain.copy(
                chat = ProviderConfig(
                    kind = kindName,
                    baseUrl = baseUrl.trim(),
                    apiKey = apiKey.trim(),
                    model = model.trim(),
                ),
            )
        } else {
            null
        }
        viewModel.completeOnboarding(
            identityId = settings.chosenIdentityId ?: identities.firstOrNull()?.id,
            styleId = settings.chosenStyleId ?: styles.firstOrNull()?.id,
            herName = herName,
            nickname = nickname,
            formOfAddress = herName,
            themePreset = presetName,
            brain = brain,
        )
    }

    BackHandler(enabled = pagerState.currentPage > 0) {
        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .imePadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 12.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = { viewModel.skipOnboarding() },
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.ob_skip))
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { page ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
                    when (page) {
                        0 -> WelcomeStep()
                        1 -> NameStep(
                            herName = herName,
                            onHerName = { herName = it.take(12) },
                            nickname = nickname,
                            onNickname = { nickname = it.take(12) },
                        )
                        2 -> ThemeStep(
                            selected = presetName,
                            onSelect = { presetName = it.name },
                        )
                        3 -> ModelStep(
                            kindName = kindName,
                            onKind = { kind ->
                                kindName = kind.name
                                baseUrl = kind.defaultBaseUrl
                                model = kind.defaultModel
                            },
                            baseUrl = baseUrl,
                            onBaseUrl = { baseUrl = it },
                            model = model,
                            onModel = { model = it },
                            apiKey = apiKey,
                            onApiKey = { apiKey = it },
                        )
                        4 -> NotifyStep(
                            onRequest = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                goNext()
                            },
                            onLater = goNext,
                        )
                        else -> DoneStep()
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StepDots(
                current = pagerState.currentPage,
                count = STEP_COUNT,
            )
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (pagerState.currentPage > 0) {
                    TextButton(
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(stringResource(R.string.ob_back))
                    }
                }
                Button(
                    onClick = { if (pagerState.currentPage == STEP_COUNT - 1) finish() else goNext() },
                    modifier = Modifier.weight(1f).heightIn(min = 50.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(
                        stringResource(
                            if (pagerState.currentPage == STEP_COUNT - 1) R.string.ob_start
                            else R.string.ob_continue,
                        ),
                    )
                }
            }
            val stepDesc = stringResource(R.string.ob_step_desc, pagerState.currentPage + 1, STEP_COUNT)
            Text(
                text = stepDesc,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun StepDots(current: Int, count: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(count) { index ->
            val desc = stringResource(R.string.ob_dot_desc, index + 1)
            val active = index == current
            Box(
                modifier = Modifier
                    .height(8.dp)
                    .width(if (active) 22.dp else 8.dp)
                    .clip(CircleShape)
                    .background(
                        if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                    )
                    .semantics { contentDescription = desc },
            )
        }
    }
}

@Composable
private fun WelcomeStep() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FishWander(MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.ob_welcome_tagline),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.ob_welcome_desc),
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
            Bullet(stringResource(R.string.ob_welcome_bullet_memory))
            Bullet(stringResource(R.string.ob_welcome_bullet_mood))
            Bullet(stringResource(R.string.ob_welcome_bullet_proactive))
            Bullet(stringResource(R.string.ob_welcome_bullet_records))
        }
    }
}

@Composable
private fun NameStep(
    herName: String,
    onHerName: (String) -> Unit,
    nickname: String,
    onNickname: (String) -> Unit,
) {
    Column {
        StepTitle(stringResource(R.string.ob_name_title), stringResource(R.string.ob_name_desc))
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = herName,
            onValueChange = onHerName,
            label = { Text(stringResource(R.string.ob_her_name_label)) },
            placeholder = { Text(stringResource(R.string.ob_her_name_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = nickname,
            onValueChange = onNickname,
            label = { Text(stringResource(R.string.ob_call_you_label)) },
            placeholder = { Text(stringResource(R.string.ob_call_you_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.ob_call_you_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ThemeStep(selected: String, onSelect: (YuThemePreset) -> Unit) {
    Column {
        StepTitle(stringResource(R.string.ob_theme_title), stringResource(R.string.ob_theme_desc))
        Spacer(Modifier.height(16.dp))
        YuThemePreset.entries.forEach { preset ->
            val active = preset.name == selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (active) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface,
                    )
                    .clickable { onSelect(preset) }
                    .padding(horizontal = 16.dp)
                    .heightIn(min = 56.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(preset.seed),
                )
                Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
                    Text(
                        text = stringResource(themeLabel(preset)),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (active) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(themeDesc(preset)),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (active) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (active) {
                    Text(
                        text = "✓",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelStep(
    kindName: String,
    onKind: (ProviderKind) -> Unit,
    baseUrl: String,
    onBaseUrl: (String) -> Unit,
    model: String,
    onModel: (String) -> Unit,
    apiKey: String,
    onApiKey: (String) -> Unit,
) {
    Column {
        StepTitle(stringResource(R.string.ob_model_title), stringResource(R.string.ob_model_desc))
        Spacer(Modifier.height(16.dp))
        ProviderKind.entries.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { kind ->
                    FilterChip(
                        selected = kind.name == kindName,
                        onClick = { onKind(kind) },
                        label = { Text(kind.label) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(4.dp))
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = baseUrl,
            onValueChange = onBaseUrl,
            label = { Text(stringResource(R.string.ob_model_base_url)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = model,
            onValueChange = onModel,
            label = { Text(stringResource(R.string.ob_model_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKey,
            label = { Text(stringResource(R.string.ob_api_key)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.ob_model_optional),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NotifyStep(onRequest: () -> Unit, onLater: () -> Unit) {
    Column {
        StepTitle(stringResource(R.string.ob_notify_title), stringResource(R.string.ob_notify_desc))
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onRequest,
            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(stringResource(R.string.ob_notify_enable))
        }
        Spacer(Modifier.height(4.dp))
        TextButton(
            onClick = onLater,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text(stringResource(R.string.ob_notify_later))
        }
    }
}

@Composable
private fun DoneStep() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.ob_done_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.ob_done_desc),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.ob_done_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun StepTitle(title: String, desc: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = desc,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Bullet(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text("·", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun themeLabel(preset: YuThemePreset): Int = when (preset) {
    YuThemePreset.MIST -> R.string.ob_theme_mist
    YuThemePreset.DAY -> R.string.ob_theme_day
    YuThemePreset.DUSK -> R.string.ob_theme_dusk
    YuThemePreset.NIGHT -> R.string.ob_theme_night
}

private fun themeDesc(preset: YuThemePreset): Int = when (preset) {
    YuThemePreset.MIST -> R.string.ob_theme_mist_desc
    YuThemePreset.DAY -> R.string.ob_theme_day_desc
    YuThemePreset.DUSK -> R.string.ob_theme_dusk_desc
    YuThemePreset.NIGHT -> R.string.ob_theme_night_desc
}

/** 欢迎页那只来回游的小鱼（沿用执行书 6.10 的纯 Canvas 实现，不引额外资源）。 */
@Composable
private fun FishWander(tint: Color) {
    val reduced = LocalYuMotion.current.reduced
    val x: Float
    val y: Float
    if (reduced) {
        x = 0.5f
        y = 0.5f
    } else {
        val transition = rememberInfiniteTransition(label = "fish")
        val animatedX by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(5200, easing = LinearEasing), RepeatMode.Reverse),
            label = "x",
        )
        val animatedY by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2100, easing = LinearEasing), RepeatMode.Reverse),
            label = "y",
        )
        x = animatedX
        y = animatedY
    }
    Canvas(Modifier.fillMaxWidth().height(76.dp)) {
        val bodyWidth = 34.dp.toPx()
        val bodyHeight = 22.dp.toPx()
        val left = 8.dp.toPx() + x * (size.width - bodyWidth - 24.dp.toPx())
        val centerY = size.height / 2f + (y - 0.5f) * size.height * 0.28f
        val tail = Path().apply {
            moveTo(left + 2.dp.toPx(), centerY)
            lineTo(left - 12.dp.toPx(), centerY - 9.dp.toPx())
            lineTo(left - 12.dp.toPx(), centerY + 9.dp.toPx())
            close()
        }
        drawPath(tail, tint.copy(alpha = 0.75f))
        drawOval(
            color = tint,
            topLeft = Offset(left, centerY - bodyHeight / 2f),
            size = Size(bodyWidth, bodyHeight),
        )
        drawCircle(
            color = Color.White,
            radius = 2.4.dp.toPx(),
            center = Offset(left + bodyWidth - 8.dp.toPx(), centerY - 3.dp.toPx()),
        )
    }
}
