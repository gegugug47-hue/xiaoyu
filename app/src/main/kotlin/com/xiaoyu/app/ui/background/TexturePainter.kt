package com.xiaoyu.app.ui.background

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import com.xiaoyu.app.data.settings.ChatBackgroundSpec
import java.util.Random
import kotlin.math.sin

/**
 * 程序化纹理（执行书 v3 §14.6.1 / §14.7）：亚麻 / 宣纸 / 方格 / 水波。
 * 纯 Canvas 生成、确定性伪随机（固定 seed），不引素材、不依赖网络。
 */
object TexturePainter {

    private const val TILE_SIZE = 256

    /** 生成一张可平铺的纹理贴图；[fg] 用于线条，[bg] 用于底色。 */
    fun tile(
        type: String,
        fg: Color,
        bg: Color,
        seed: Int = 20261116,
        size: Int = TILE_SIZE,
    ): ImageBitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(bg.toArgb())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fg.toArgb()
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        val random = Random(seed.toLong())
        when (type) {
            ChatBackgroundSpec.TEXTURE_LINEN -> linen(canvas, paint, size, random)
            ChatBackgroundSpec.TEXTURE_GRID -> grid(canvas, paint, size, fg)
            ChatBackgroundSpec.TEXTURE_WAVES -> waves(canvas, paint, size, fg)
            else -> paper(canvas, paint, size, random, fg)
        }
        return bitmap.asImageBitmap()
    }

    private fun linen(canvas: Canvas, paint: Paint, size: Int, random: Random) {
        for (y in 0 until size step 3) {
            paint.alpha = 18 + random.nextInt(22)
            paint.strokeWidth = if (random.nextInt(4) == 0) 1.6f else 0.8f
            canvas.drawLine(0f, y.toFloat(), size.toFloat(), y.toFloat(), paint)
        }
        for (x in 0 until size step 3) {
            paint.alpha = 14 + random.nextInt(18)
            paint.strokeWidth = if (random.nextInt(4) == 0) 1.6f else 0.8f
            canvas.drawLine(x.toFloat(), 0f, x.toFloat(), size.toFloat(), paint)
        }
    }

    private fun paper(canvas: Canvas, paint: Paint, size: Int, random: Random, fg: Color) {
        paint.alpha = 22
        repeat(90) {
            val x = random.nextFloat() * size
            val y = random.nextFloat() * size
            val length = 3f + random.nextFloat() * 14f
            val angle = (random.nextFloat() - 0.5f) * 0.9f
            canvas.drawLine(
                x, y,
                x + length * kotlin.math.cos(angle), y + length * sin(angle),
                paint,
            )
        }
        val speck = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fg.toArgb()
            style = Paint.Style.FILL
        }
        repeat(120) {
            speck.alpha = 8 + random.nextInt(18)
            canvas.drawCircle(
                random.nextFloat() * size,
                random.nextFloat() * size,
                0.6f + random.nextFloat() * 1.2f,
                speck,
            )
        }
    }

    private fun grid(canvas: Canvas, paint: Paint, size: Int, fg: Color) {
        val step = 32
        paint.alpha = 30
        paint.strokeWidth = 1f
        var position = 0
        while (position <= size) {
            canvas.drawLine(position.toFloat(), 0f, position.toFloat(), size.toFloat(), paint)
            canvas.drawLine(0f, position.toFloat(), size.toFloat(), position.toFloat(), paint)
            position += step
        }
        paint.alpha = 45
        paint.strokeWidth = 1.6f
        canvas.drawLine(0f, 0f, size.toFloat(), 0f, paint)
        canvas.drawLine(0f, 0f, 0f, size.toFloat(), paint)
        paint.color = fg.toArgb()
    }

    private fun waves(canvas: Canvas, paint: Paint, size: Int, fg: Color) {
        paint.alpha = 34
        paint.strokeWidth = 1.4f
        val path = Path()
        var row = 0
        while (row < size) {
            path.reset()
            val amplitude = 4f + (row % 5) * 1.6f
            var x = 0f
            path.moveTo(0f, row.toFloat())
            while (x <= size.toFloat()) {
                path.lineTo(x, row + (sin((x / 18f) + row * 0.3f) * amplitude).toFloat())
                x += 4f
            }
            canvas.drawPath(path, paint)
            row += 24
        }
        paint.color = fg.toArgb()
    }
}
