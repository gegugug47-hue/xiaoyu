package com.xiaoyu.app.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaoyu.app.data.settings.DailyUsage
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * 用量统计与成本护栏（执行书 4.6）：
 * 今日 / 本月 token 与估算费用、按角色分列、缓存命中率、近 7 天折线图，以及护栏开关与上限。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageScreen(
    viewModel: AppViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val guard by viewModel.costGuard.collectAsStateWithLifecycle()
    val today = remember(settings.dailyUsage) {
        settings.dailyUsage.lastOrNull { it.date == LocalDate.now().toString() } ?: DailyUsage(LocalDate.now().toString())
    }
    val last7 = remember(settings.dailyUsage) {
        (6 downTo 0).map { offset ->
            val date = LocalDate.now().minusDays(offset.toLong()).toString()
            settings.dailyUsage.lastOrNull { it.date == date } ?: DailyUsage(date)
        }
    }
    val cachedRate = if (settings.usage.totalTokens > 0) {
        settings.usage.cachedTokens.toDouble() / settings.usage.totalTokens
    } else {
        0.0
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("用量统计") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "返回") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (guard.active) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFFF3CD), RoundedCornerShape(14.dp))
                        .padding(14.dp),
                ) {
                    Text(
                        "成本护栏已触发：${guard.reason}。聊天不受影响，可在下方关闭护栏。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF6B5200),
                    )
                }
            }

            UsageCard("今日") {
                StatRow("token", "${today.totalTokens}")
                StatRow("调用次数", "${today.calls}")
                StatRow("估算费用", "¥${"%.3f".format(today.costYuan)}")
                StatRow("后台占用", "${today.backgroundTokens}")
            }

            UsageCard("本月") {
                StatRow("token", "${settings.usage.totalTokens}")
                StatRow("聊天 / 后台调用", "${settings.usage.chatRequests} / ${settings.usage.extractRequests}")
                StatRow("估算费用", "¥${"%.3f".format(settings.usage.estimatedCost)}")
                StatRow("缓存命中率", "${(cachedRate * 100).roundToInt()}%")
            }

            UsageCard("近 7 天 token") {
                UsageChart(
                    days = last7,
                    tint = MaterialTheme.colorScheme.primary,
                    gridColor = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    last7.forEach { day ->
                        Text(
                            day.date.takeLast(2),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            UsageCard("成本护栏") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("超限暂停后台生成", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "生活模拟 / 日记 / 主动聊天的模型调用会暂停，聊天仍可用。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = settings.degradeOnCapExceeded,
                        onCheckedChange = { enabled ->
                            viewModel.updateSettings { it.copy(degradeOnCapExceeded = enabled) }
                        },
                    )
                }
                var dailyCap by rememberSaveable(settings.dailyTokenCap) {
                    mutableStateOf(settings.dailyTokenCap.toString())
                }
                var monthlyCap by rememberSaveable(settings.monthlyCostCapMicro) {
                    mutableStateOf("%.2f".format(settings.monthlyCostCapMicro / 1_000_000.0))
                }
                OutlinedTextField(
                    value = dailyCap,
                    onValueChange = { dailyCap = it.filter(Char::isDigit).take(9) },
                    label = { Text("每日 token 上限") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = monthlyCap,
                    onValueChange = { input ->
                        monthlyCap = input.filter { it.isDigit() || it == '.' }.take(8)
                    },
                    label = { Text("每月费用上限（元）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.Button(
                        onClick = {
                            val cap = dailyCap.toIntOrNull() ?: settings.dailyTokenCap
                            val monthlyMicro = ((monthlyCap.toDoubleOrNull() ?: 3.0) * 1_000_000).toLong()
                            viewModel.updateSettings {
                                it.copy(
                                    dailyTokenCap = cap,
                                    monthlyCostCapMicro = monthlyMicro,
                                )
                            }
                        },
                    ) { Text("保存上限") }
                    Text(
                        "关闭护栏等于允许无上限花费，请谨慎。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                }
            }

            Text(
                "单价表来自 assets/cost_table.json，未知模型按 0 计并标注「未定价」。Ollama 与示例模式不计费。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun UsageCard(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        content()
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** 手绘折线图（不引图表库），空数据也能画出基线。 */
@Composable
private fun UsageChart(
    days: List<DailyUsage>,
    tint: Color,
    gridColor: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        if (days.isEmpty()) return@Canvas
        val maxValue = (days.maxOfOrNull { it.totalTokens } ?: 0L).coerceAtLeast(1L).toFloat()
        for (i in 0..2) {
            val y = size.height * i / 2f
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }
        val stepX = if (days.size > 1) size.width / (days.size - 1) else size.width
        val points = days.mapIndexed { index, day ->
            Offset(index * stepX, size.height - day.totalTokens / maxValue * size.height * 0.92f)
        }
        val path = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(path, tint, style = Stroke(width = 2.dp.toPx()))
        points.forEach { drawCircle(tint, radius = 3.dp.toPx(), center = it) }
    }
}
