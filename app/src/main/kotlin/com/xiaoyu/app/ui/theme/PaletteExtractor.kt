package com.xiaoyu.app.ui.theme

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color

/**
 * 轻量色板提取器（批次 3 / G-03 差距补齐）。
 *
 * 从 Bitmap 采样像素，经 HSV 聚类后返回主导色列表与 seed。
 * 不依赖 AndroidX Palette / material-color-utilities，保持 app 模块无额外依赖。
 */
object PaletteExtractor {

    /** 主导色结果。 */
    data class Palette(
        val seed: Color,
        val primaries: List<Color>,
        val vibrant: Color?,
        val darkMuted: Color?,
        val lightMuted: Color?,
    )

    /** 从 Bitmap 提取色板；bitmap 会被缩放后采样，不修改原图。 */
    fun extract(bitmap: Bitmap, maxColors: Int = 5): Palette {
        val scaled = if (bitmap.width > 96 || bitmap.height > 96) {
            Bitmap.createScaledBitmap(
                bitmap,
                minOf(96, bitmap.width),
                minOf(96, bitmap.height),
                true,
            )
        } else bitmap

        val pixels = IntArray(scaled.width * scaled.height)
        scaled.getPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)

        val hsvBuckets = mutableMapOf<Int, MutableList<Int>>()
        for (pixel in pixels) {
            val r = (pixel ushr 16) and 0xFF
            val g = (pixel ushr 8) and 0xFF
            val b = pixel and 0xFF
            val maxV = r.coerceAtLeast(g).coerceAtLeast(b)
            val minV = r.coerceAtMost(g).coerceAtMost(b)
            val delta = maxV - minV
            val sat = if (maxV == 0) 0f else delta.toFloat() / maxV
            // 低饱和或过暗/过亮跳过，避免白色/黑色主导
            if (sat < 0.12f || maxV < 28 || maxV > 235) continue
            val bucket = ((hueOf(r, g, b) / 30f).toInt() * 30) % 360
            hsvBuckets.getOrPut(bucket) { mutableListOf() }.add(pixel)
        }

        val sorted = hsvBuckets.entries.sortedByDescending { it.value.size }
        val primaries = sorted.take(maxColors).map { (_, pix) -> averageColor(pix) }

        val seed = primaries.firstOrNull() ?: Color(0xFF4A90D9)

        val vibrant = primaries.filter { avgBrightness(it) > 0.45f && avgBrightness(it) < 0.85f }
            .maxByOrNull { avgSaturation(it) }

        val darkMuted = primaries.filter { avgBrightness(it) < 0.45f }
            .maxByOrNull { avgSaturation(it) }

        val lightMuted = primaries.filter { avgBrightness(it) > 0.65f && avgSaturation(it) < 0.5f }
            .firstOrNull()

        if (scaled !== bitmap) scaled.recycle()

        return Palette(
            seed = seed,
            primaries = primaries.ifEmpty { listOf(seed) },
            vibrant = vibrant,
            darkMuted = darkMuted,
            lightMuted = lightMuted,
        )
    }

    private fun hueOf(r: Int, g: Int, b: Int): Float {
        val rf = r / 255f
        val gf = g / 255f
        val bf = b / 255f
        val maxV = rf.coerceAtLeast(gf).coerceAtLeast(bf)
        val minV = rf.coerceAtMost(gf).coerceAtMost(bf)
        val delta = maxV - minV
        if (delta == 0f) return 0f
        val hue: Float = when (maxV) {
            rf -> 60f * (((gf - bf) / delta) % 6f)
            gf -> 60f * (((bf - rf) / delta) + 2f)
            else -> 60f * (((rf - gf) / delta) + 4f)
        }
        return if (hue < 0f) hue + 360f else hue
    }

    private fun averageColor(pixels: List<Int>): Color {
        var r = 0
        var g = 0
        var b = 0
        for (p in pixels) {
            r += (p ushr 16) and 0xFF
            g += (p ushr 8) and 0xFF
            b += p and 0xFF
        }
        val n = pixels.size.coerceAtLeast(1)
        return Color(r / n / 255f, g / n / 255f, b / n / 255f, 1f)
    }

    private fun avgBrightness(c: Color): Float = c.red * 0.299f + c.green * 0.587f + c.blue * 0.114f
    private fun avgSaturation(c: Color): Float {
        val maxV = c.red.coerceAtLeast(c.green).coerceAtLeast(c.blue)
        val minV = c.red.coerceAtMost(c.green).coerceAtMost(c.blue)
        return if (maxV == 0f) 0f else (maxV - minV) / maxV
    }

    /** 给定 Bitmap 路径，提取并转为主题 seed HEX。 */
    fun extractSeedHex(bitmap: Bitmap): String = colorToHex(extract(bitmap).seed)
}
