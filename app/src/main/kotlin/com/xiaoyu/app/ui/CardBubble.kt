package com.xiaoyu.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xiaoyu.soul.card.CardFactory
import com.xiaoyu.soul.card.CardFactory.CardKind

/**
 * 卡片消息气泡（执行书 v3 ADR-15 / 7.4.5）。
 * 解析 MessageEntity.content（JSON）并按卡片类别渲染标题 + 内容行 + 页脚。
 * 解析失败时回退为纯文本展示，不丢消息。
 */
@Composable
fun CardBubble(
    rawContent: String,
    modifier: Modifier = Modifier,
) {
    val payload = CardFactory.parse(rawContent)
    if (payload == null) {
        Text(rawContent, style = MaterialTheme.typography.bodyMedium)
        return
    }
    val accent = when (runCatching { CardKind.valueOf(payload.kind) }.getOrDefault(CardKind.DIARY)) {
        CardKind.DIARY -> MaterialTheme.colorScheme.tertiary
        CardKind.AGREEMENT -> MaterialTheme.colorScheme.primary
        CardKind.HER_DAY -> MaterialTheme.colorScheme.secondary
        CardKind.MEMORY_RECALL -> MaterialTheme.colorScheme.primary
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                payload.title,
                style = MaterialTheme.typography.titleSmall,
                color = accent,
                fontWeight = FontWeight.SemiBold,
            )
            payload.lines.forEach { line ->
                Text(
                    line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            payload.footer?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
