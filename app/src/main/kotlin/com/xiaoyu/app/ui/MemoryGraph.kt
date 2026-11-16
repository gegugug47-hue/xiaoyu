package com.xiaoyu.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.xiaoyu.app.data.db.MemoryEntity
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** 记忆图谱卡片：标题 + Canvas 图谱。 */
@Composable
fun MemoryGraphCard(
    memories: List<MemoryEntity>,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("记忆图谱", style = MaterialTheme.typography.titleSmall)
            Text(
                "颜色越深 = 记忆越强，节点越大 = 越重要",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (memories.isEmpty()) {
                Text(
                    "还没有记忆，多聊聊就会慢慢长出来～",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                MemoryGraph(memories = memories)
            }
        }
    }
}

/**
 * 记忆图谱（执行书 v3 H-12 / 7.5）：把短期记忆画成一张关系网。
 * - 节点位置：按 strength 排序后螺旋排布，强的居中、弱的外圈
 * - 节点颜色：strength 越高，主色饱和度越高（越深）
 * - 节点半径：importance 越高越大
 * - 连线：相近节点连淡线，表示语义关联（启发式，不做真实语义计算）
 */
@Composable
fun MemoryGraph(
    memories: List<MemoryEntity>,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurfaceVariant

    Canvas(modifier.fillMaxWidth().height(220.dp)) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val maxR = min(w, h) / 2f - 24f

        // 按 strength 降序，强的排前面（靠近中心）
        val sorted = memories.sortedByDescending { it.strength }.take(40)
        if (sorted.isEmpty()) return@Canvas

        val positions = mutableListOf<Offset>()
        sorted.forEachIndexed { i, mem ->
            // 螺旋布局：角度随 i 增长，半径随 i 增长
            val angle = i * 2.399963f // 黄金角
            val radius = 18f + (i.toFloat() / sorted.size) * (maxR - 18f)
            val x = cx + radius * cos(angle)
            val y = cy + radius * sin(angle)
            positions += Offset(x, y)
        }

        // 先画连线（相近节点）
        positions.forEachIndexed { i, p1 ->
            for (j in i + 1 until positions.size) {
                val p2 = positions[j]
                val dist = (p1 - p2).getDistance()
                if (dist < maxR * 0.45f) {
                    drawLine(
                        color = onSurface.copy(alpha = 0.12f),
                        start = p1,
                        end = p2,
                        strokeWidth = 1.2f,
                    )
                }
            }
        }

        // 再画节点
        sorted.forEachIndexed { i, mem ->
            val p = positions[i]
            // strength 0~100 映射到饱和度：越强颜色越深越实
            val strengthRatio = mem.strength.coerceIn(0, 100) / 100f
            val nodeColor = primary.copy(alpha = 0.35f + strengthRatio * 0.55f)
            // importance 1~5 映射节点半径
            val nodeR = 4f + (mem.importance.coerceIn(1, 5) / 5f) * 7f
            drawCircle(
                color = nodeColor,
                radius = nodeR,
                center = p,
            )
            drawCircle(
                color = primary.copy(alpha = strengthRatio * 0.9f),
                radius = nodeR,
                center = p,
                style = Stroke(width = 1.5f),
            )
        }
    }
}
