package com.xiaoyu.app.ui

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaoyu.app.R
import com.xiaoyu.app.data.repo.MoodPoint
import com.xiaoyu.app.data.repo.RelationMilestone
import com.xiaoyu.app.data.repo.RelationSummary
import com.xiaoyu.app.ui.components.YuEmptyView
import com.xiaoyu.app.ui.illustrations.YuIllustrationKind
import com.xiaoyu.app.ui.theme.LocalYuMotion
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayFormat = DateTimeFormatter.ofPattern("MM/dd", Locale.CHINA)
private val fullDateFormat = DateTimeFormatter.ofPattern("yyyy.MM.dd", Locale.CHINA)

/** 关系页（执行书 6.5）：档位弧 / 统计卡片 / 心情曲线 / 今日心情 / 纪念日。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelationScreen(
    viewModel: AppViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onOpenDiary: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenPersona: () -> Unit,
) {
    val summary by viewModel.relation.collectAsStateWithLifecycle()
    val memories by viewModel.shortMemories.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refreshRelation() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.relation_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.relation_back)) }
                },
            )
        },
    ) { padding ->
        val data = summary
        if (data == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.relation_loading),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            RelationHeader(data)
            if (data.messageCount == 0 && data.diaryCount == 0 && data.milestones.isEmpty() && memories.isEmpty()) {
                YuEmptyView(
                    kind = YuIllustrationKind.RELATION,
                    message = stringResource(R.string.relation_empty),
                )
            } else {
                RelationStats(data)
                MoodChartCard(data.moodPoints)
                TodayMoodCard(data.moodLine)
                MilestoneTimeline(data.milestones)
                MemoryGraphCard(memories)
            }
            EntryRow(onOpenDiary = onOpenDiary, onOpenMemory = onOpenMemory, onOpenPersona = onOpenPersona)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun RelationHeader(data: RelationSummary) {
    val primary = MaterialTheme.colorScheme.primary
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .background(
                        Brush.linearGradient(
                            listOf(primary, MaterialTheme.colorScheme.tertiary, primary.copy(alpha = 0.75f)),
                        ),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    data.identityName.take(1),
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(data.identityName, style = MaterialTheme.typography.titleLarge)
            Text(
                "「${stringResource(favorabilityLabelRes(data.level))}」",
                style = MaterialTheme.typography.labelLarge,
                color = primary,
            )
            Spacer(Modifier.height(12.dp))
            LevelArc(progress = data.levelProgress)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.relation_known_days, data.knownDays),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 柔和进度弧：只有弧线，不显示数值（D3 关闭时）。 */
@Composable
private fun LevelArc(progress: Float) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val primary = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(56.dp)) {
        val stroke = 9.dp.toPx()
        val inset = stroke / 2
        val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, (size.height - stroke) * 2f - stroke)
        drawArc(
            color = track,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        if (progress > 0f) {
            drawArc(
                color = primary,
                startAngle = 180f,
                sweepAngle = 180f * progress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
private fun RelationStats(data: RelationSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(stringResource(R.string.relation_stat_known), "${data.knownDays}", stringResource(R.string.relation_unit_days), Modifier.weight(1f))
            StatCard(stringResource(R.string.relation_stat_streak), "${data.streakDays}", stringResource(R.string.relation_unit_days), Modifier.weight(1f), flame = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(stringResource(R.string.relation_stat_messages), "${data.messageCount}", stringResource(R.string.relation_unit_sentences), Modifier.weight(1f))
            StatCard(stringResource(R.string.relation_stat_diaries), "${data.diaryCount}", stringResource(R.string.relation_unit_diaries), Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
    flame: Boolean = false,
) {
    val statDescription = stringResource(R.string.relation_stat_a11y, label, value, unit)
    Card(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = statDescription },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold))
                Text(
                    unit,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 3.dp, bottom = 3.dp),
                )
                if (flame) {
                    Spacer(Modifier.width(4.dp))
                    FlameDot(Modifier.padding(bottom = 4.dp))
                }
            }
        }
    }
}

/** 连续互动的小火苗，轻微呼吸动画。 */
@Composable
private fun FlameDot(modifier: Modifier = Modifier) {
    val scale = if (LocalYuMotion.current.reduced) {
        1f
    } else {
        val transition = rememberInfiniteTransition(label = "flame")
        val animatedScale by transition.animateFloat(
            initialValue = 0.78f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "flame-scale",
        )
        animatedScale
    }
    Canvas(modifier.size(14.dp)) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.02f)
            cubicTo(w * 0.92f * scale, h * 0.36f, w * 0.88f, h * 0.66f, w * 0.5f, h * 0.98f)
            cubicTo(w * 0.12f, h * 0.66f, w * 0.08f * scale, h * 0.36f, w * 0.5f, h * 0.02f)
            close()
        }
        drawPath(path, Color(0xFFFF8A3D).copy(alpha = 0.85f))
    }
}

@Composable
private fun MoodChartCard(points: List<MoodPoint>) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(stringResource(R.string.relation_mood_chart), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.relation_mood_chart_range),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            if (points.size < 2) {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.relation_mood_chart_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                MoodChart(points, Modifier.fillMaxWidth().height(150.dp))
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(points.first().date.format(dayFormat), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(points.last().date.format(dayFormat), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun MoodChart(points: List<MoodPoint>, modifier: Modifier = Modifier) {
    val moodColor = MaterialTheme.colorScheme.primary
    val energyColor = MaterialTheme.colorScheme.tertiary
    val grid = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val pad = 4.dp.toPx()
        drawLine(grid, Offset(0f, h / 2), Offset(w, h / 2), strokeWidth = 1.dp.toPx())

        val stepX = (w - pad * 2) / (points.size - 1)
        fun x(i: Int) = pad + stepX * i
        // mood：-100..100 映射到上半/下半，保留 8% 边距
        fun yMood(v: Int) = h / 2 - (v.coerceIn(-100, 100) / 100f) * (h / 2 - pad)
        // energy：0..100 映射到下半区，作为面积
        fun yEnergy(v: Int) = h - (v.coerceIn(0, 100) / 100f) * (h * 0.42f) - pad

        val energyPath = Path().apply {
            moveTo(x(0), h)
            points.forEachIndexed { index, point -> lineTo(x(index), yEnergy(point.energy)) }
            lineTo(x(points.lastIndex), h)
            close()
        }
        drawPath(energyPath, energyColor.copy(alpha = 0.16f))

        val moodPath = Path().apply {
            moveTo(x(0), yMood(points.first().mood))
            points.drop(1).forEachIndexed { index, point -> lineTo(x(index + 1), yMood(point.mood)) }
        }
        drawPath(
            moodPath,
            moodColor,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        drawCircle(moodColor, 3.5.dp.toPx(), Offset(x(points.lastIndex), yMood(points.last().mood)))
    }
}

@Composable
private fun TodayMoodCard(line: String) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(stringResource(R.string.relation_today_mood), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.height(6.dp))
            Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

@Composable
private fun MilestoneTimeline(items: List<RelationMilestone>) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(stringResource(R.string.relation_milestones), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            items.forEachIndexed { index, milestone ->
                val milestoneDesc = stringResource(
                    R.string.life_day_a11y_item,
                    milestone.date.format(fullDateFormat),
                    milestone.title,
                    milestone.detail,
                )
                Row(
                    modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = milestoneDesc },
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(16.dp)) {
                        Box(
                            Modifier
                                .size(10.dp)
                                .background(
                                    if (milestone.upcoming) MaterialTheme.colorScheme.outlineVariant
                                    else MaterialTheme.colorScheme.primary,
                                    CircleShape,
                                )
                        )
                        if (index != items.lastIndex) {
                            Box(
                                Modifier
                                    .width(2.dp)
                                    .height(52.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f).padding(bottom = 14.dp)) {
                        Text(
                            milestone.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (milestone.upcoming) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            milestone.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            milestone.date.format(fullDateFormat),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryRow(onOpenDiary: () -> Unit, onOpenMemory: () -> Unit, onOpenPersona: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = onOpenDiary, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
            Text(stringResource(R.string.relation_entry_diary), style = MaterialTheme.typography.labelLarge)
        }
        OutlinedButton(onClick = onOpenMemory, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
            Text(stringResource(R.string.relation_entry_memory), style = MaterialTheme.typography.labelLarge)
        }
        OutlinedButton(onClick = onOpenPersona, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
            Text(stringResource(R.string.relation_entry_persona), style = MaterialTheme.typography.labelLarge)
        }
    }
}
