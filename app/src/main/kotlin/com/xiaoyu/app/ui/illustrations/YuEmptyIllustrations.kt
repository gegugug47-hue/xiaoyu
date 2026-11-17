package com.xiaoyu.app.ui.illustrations

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.xiaoyu.app.ui.theme.Tokens

/** 聊天、记忆、日记、关系共用的一组轻量线性插画。 */
enum class YuIllustrationKind { CHAT, MEMORY, DIARY, RELATION }

@Composable
fun YuEmptyIllustration(
    kind: YuIllustrationKind,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.tertiary
    val wash = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
    Canvas(modifier.size(Tokens.SizeIllustration).semantics { this.contentDescription = contentDescription }) {
        val unit = size.minDimension / 120f
        val stroke = Stroke(width = 2.4f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawCircle(wash, radius = 48f * unit, center = center)
        when (kind) {
            YuIllustrationKind.CHAT -> {
                drawRoundRect(primary, Offset(22f * unit, 31f * unit), Size(57f * unit, 39f * unit), CornerRadius(10f * unit), style = stroke)
                drawRoundRect(secondary, Offset(48f * unit, 53f * unit), Size(51f * unit, 34f * unit), CornerRadius(9f * unit), style = stroke)
                val tail = Path().apply {
                    moveTo(35f * unit, 68f * unit)
                    lineTo(31f * unit, 79f * unit)
                    lineTo(46f * unit, 69f * unit)
                }
                drawPath(tail, primary, style = stroke)
                listOf(39f, 51f, 63f).forEach { x ->
                    drawCircle(primary, radius = 2f * unit, center = Offset(x * unit, 50f * unit))
                }
            }
            YuIllustrationKind.MEMORY -> {
                drawRoundRect(secondary, Offset(37f * unit, 24f * unit), Size(49f * unit, 66f * unit), CornerRadius(8f * unit), style = stroke)
                drawRoundRect(primary, Offset(26f * unit, 34f * unit), Size(56f * unit, 62f * unit), CornerRadius(8f * unit), style = stroke)
                listOf(49f, 61f, 73f).forEach { y ->
                    drawLine(primary, Offset(39f * unit, y * unit), Offset(68f * unit, y * unit), strokeWidth = stroke.width, cap = StrokeCap.Round)
                }
                drawCircle(secondary, radius = 5f * unit, center = Offset(87f * unit, 84f * unit), style = stroke)
            }
            YuIllustrationKind.DIARY -> {
                val left = Path().apply {
                    moveTo(60f * unit, 35f * unit)
                    cubicTo(48f * unit, 27f * unit, 32f * unit, 28f * unit, 25f * unit, 34f * unit)
                    lineTo(25f * unit, 87f * unit)
                    cubicTo(36f * unit, 80f * unit, 49f * unit, 81f * unit, 60f * unit, 89f * unit)
                    close()
                }
                val right = Path().apply {
                    moveTo(60f * unit, 35f * unit)
                    cubicTo(72f * unit, 27f * unit, 88f * unit, 28f * unit, 95f * unit, 34f * unit)
                    lineTo(95f * unit, 87f * unit)
                    cubicTo(84f * unit, 80f * unit, 71f * unit, 81f * unit, 60f * unit, 89f * unit)
                    close()
                }
                drawPath(left, primary, style = stroke)
                drawPath(right, secondary, style = stroke)
                drawLine(primary, Offset(60f * unit, 35f * unit), Offset(60f * unit, 89f * unit), strokeWidth = stroke.width)
                listOf(47f, 57f, 67f).forEach { y ->
                    drawLine(primary, Offset(35f * unit, y * unit), Offset(50f * unit, y * unit), strokeWidth = stroke.width, cap = StrokeCap.Round)
                    drawLine(secondary, Offset(70f * unit, y * unit), Offset(85f * unit, y * unit), strokeWidth = stroke.width, cap = StrokeCap.Round)
                }
            }
            YuIllustrationKind.RELATION -> {
                drawCircle(primary, radius = 18f * unit, center = Offset(42f * unit, 53f * unit), style = stroke)
                drawCircle(secondary, radius = 18f * unit, center = Offset(79f * unit, 53f * unit), style = stroke)
                drawArc(primary, 0f, 180f, false, Offset(24f * unit, 64f * unit), Size(37f * unit, 29f * unit), style = stroke)
                drawArc(secondary, 0f, 180f, false, Offset(60f * unit, 64f * unit), Size(37f * unit, 29f * unit), style = stroke)
                val heart = Path().apply {
                    moveTo(60f * unit, 37f * unit)
                    cubicTo(52f * unit, 27f * unit, 41f * unit, 37f * unit, 60f * unit, 49f * unit)
                    cubicTo(79f * unit, 37f * unit, 68f * unit, 27f * unit, 60f * unit, 37f * unit)
                }
                drawPath(heart, primary, style = stroke)
            }
        }
    }
}
