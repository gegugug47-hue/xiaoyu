package com.xiaoyu.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 自绘线性图标（执行书 v3 G-08 的收尾部分）。
 *
 * 只覆盖聊天高频路径上最影响观感的 8 个：发送 / 加号 / 表情 / 语音 / 图片 / 返回 / 更多 / 关闭。
 * 统一 24dp 视口、1.8dp 圆头线宽，颜色靠 `Icon(tint = ...)` 着色，因此不必按主题各画一份。
 * 其余图标仍用 Material Icons，不强行替换。
 */
object YuIcons {

    private val stroke = SolidColor(Color.Black)
    private const val W = 1.8f

    /** 发送：纸飞机，实心，指向右上。 */
    val Send: ImageVector = ImageVector.Builder(
        name = "YuSend",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(3f, 20f)
            lineTo(21f, 12f)
            lineTo(3f, 4f)
            lineTo(4.5f, 10.5f)
            lineTo(14f, 12f)
            lineTo(4.5f, 13.5f)
            close()
        }
    }.build()

    /** 加号：输入栏扩展动作。 */
    val Plus: ImageVector = ImageVector.Builder(
        name = "YuPlus",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = stroke,
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
        ) {
            moveTo(12f, 5f)
            lineTo(12f, 19f)
            moveTo(5f, 12f)
            lineTo(19f, 12f)
        }
    }.build()

    /** 表情：笑脸。 */
    val Emoji: ImageVector = ImageVector.Builder(
        name = "YuEmoji",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = stroke,
            strokeLineWidth = W,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(12f, 3f)
            arcTo(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 12f, y1 = 21f)
            arcTo(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 12f, y1 = 3f)
            moveTo(9f, 9.5f)
            lineTo(9f, 10.4f)
            moveTo(15f, 9.5f)
            lineTo(15f, 10.4f)
            moveTo(8f, 14f)
            quadTo(12f, 17.5f, 16f, 14f)
        }
    }.build()

    /** 语音：麦克风。 */
    val Voice: ImageVector = ImageVector.Builder(
        name = "YuVoice",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = stroke,
            strokeLineWidth = W,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(12f, 3f)
            curveTo(10.4f, 3f, 9f, 4.4f, 9f, 6f)
            lineTo(9f, 12f)
            curveTo(9f, 13.6f, 10.4f, 15f, 12f, 15f)
            curveTo(13.6f, 15f, 15f, 13.6f, 15f, 12f)
            lineTo(15f, 6f)
            curveTo(15f, 4.4f, 13.6f, 3f, 12f, 3f)
            close()
            moveTo(5.5f, 11f)
            curveTo(5.5f, 15.5f, 8.4f, 18f, 12f, 18f)
            curveTo(15.6f, 18f, 18.5f, 15.5f, 18.5f, 11f)
            moveTo(12f, 18f)
            lineTo(12f, 21f)
            moveTo(8.5f, 21f)
            lineTo(15.5f, 21f)
        }
    }.build()

    /** 图片：相框 + 山形。 */
    val Image: ImageVector = ImageVector.Builder(
        name = "YuImage",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = stroke,
            strokeLineWidth = W,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(4f, 5f)
            lineTo(20f, 5f)
            lineTo(20f, 19f)
            lineTo(4f, 19f)
            close()
            moveTo(4f, 16f)
            lineTo(9f, 11f)
            lineTo(13f, 15f)
            lineTo(16f, 12f)
            lineTo(20f, 16f)
        }
        path(fill = SolidColor(Color.Black)) {
            moveTo(8f, 7.4f)
            arcTo(1.4f, 1.4f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 8f, y1 = 10.2f)
            arcTo(1.4f, 1.4f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 8f, y1 = 7.4f)
            close()
        }
    }.build()

    /** 返回：左向箭头。 */
    val Back: ImageVector = ImageVector.Builder(
        name = "YuBack",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = stroke,
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(15f, 5f)
            lineTo(8f, 12f)
            lineTo(15f, 19f)
        }
    }.build()

    /** 更多：三个圆点。 */
    val More: ImageVector = ImageVector.Builder(
        name = "YuMore",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(6f, 10.6f)
            arcTo(1.4f, 1.4f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 6f, y1 = 13.4f)
            arcTo(1.4f, 1.4f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 6f, y1 = 10.6f)
            close()
            moveTo(12f, 10.6f)
            arcTo(1.4f, 1.4f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 12f, y1 = 13.4f)
            arcTo(1.4f, 1.4f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 12f, y1 = 10.6f)
            close()
            moveTo(18f, 10.6f)
            arcTo(1.4f, 1.4f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 18f, y1 = 13.4f)
            arcTo(1.4f, 1.4f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 18f, y1 = 10.6f)
            close()
        }
    }.build()

    /** 关闭：叉。 */
    val Close: ImageVector = ImageVector.Builder(
        name = "YuClose",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = stroke,
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
        ) {
            moveTo(6f, 6f)
            lineTo(18f, 18f)
            moveTo(18f, 6f)
            lineTo(6f, 18f)
        }
    }.build()
}
