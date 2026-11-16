package com.xiaoyu.app.ui

import android.graphics.Bitmap
import kotlin.math.pow

/**
 * WCAG 对比度守卫（执行书 v3 H-24 / 14.6）。
 * 根据背景图的平均亮度，计算保证文字可读所需的最小暗化蒙层透明度。
 *
 * 目标：文字与背景的对比度 ≥ WCAG AA（4.5:1，正文）。
 * 不直接读像素的逐点对比度（成本太高），而是对整张图采样平均亮度后换算。
 */
object LegibilityGuard {

    /** WCAG AA 正文最低对比度。 */
    const val WCAG_AA_CONTRAST = 4.5

    /** 浅色主题下，正文字色的相对亮度（近似 #1C1B1F 深色文字）。 */
    private const val LIGHT_THEME_TEXT_LUM = 0.03

    /** 深色主题下，正文字色的相对亮度（近似 #F4EFF4 浅色文字）。 */
    private const val DARK_THEME_TEXT_LUM = 0.83

    /**
     * 计算保证对比度所需的最小暗化蒙层透明度。
     *
     * @param bitmap 背景图（已解码）
     * @param darkTheme 是否深色主题
     * @return 最小 dim alpha（0~1），调用方取 max(dim, 此值)
     */
    fun minDimForContrast(bitmap: Bitmap?, darkTheme: Boolean): Float {
        if (bitmap == null) return 0f
        val bgLum = averageLuminance(bitmap)
        val textLum = if (darkTheme) DARK_THEME_TEXT_LUM else LIGHT_THEME_TEXT_LUM

        // 暗化后的背景亮度 = bgLum * (1 - dim)
        // 对比度 = (max(textLum, bgLum') + 0.05) / (min(textLum, bgLum') + 0.05)
        // 要求对比度 >= 4.5，反解 dim
        val lighter = maxOf(textLum, bgLum)
        val darker = minOf(textLum, bgLum)
        val currentContrast = (lighter + 0.05) / (darker + 0.05)
        if (currentContrast >= WCAG_AA_CONTRAST) return 0f

        // 需要把背景亮度压到 safeBgLum
        val safeBgLum = if (textLum > bgLum) {
            // 文字比背景亮（深色主题文字在亮背景上，其实不常见），把背景压暗
            (textLum + 0.05) / WCAG_AA_CONTRAST - 0.05
        } else {
            // 文字比背景暗（浅色主题文字在亮背景上），需要背景足够亮，暗化反而更差
            // 这种情况下 dim 不能解决问题，返回 0 交由主题色兜底
            return 0f
        }

        // bgLum * (1 - dim) <= safeBgLum  =>  dim >= 1 - safeBgLum / bgLum
        return if (bgLum > 0.001f) {
            (1f - safeBgLum.toFloat() / bgLum.toFloat()).coerceIn(0f, 0.85f)
        } else 0f
    }

    /**
     * 采样位图平均相对亮度（0~1）。步长采样，避免 O(w*h) 遍历大卡。
     */
    private fun averageLuminance(bitmap: Bitmap): Double {
        val w = bitmap.width
        val h = bitmap.height
        val stepX = (w / 48).coerceAtLeast(1)
        val stepY = (h / 48).coerceAtLeast(1)
        var sum = 0.0
        var count = 0
        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val pixel = bitmap.getPixel(x, y)
                val r = ((pixel shr 16) and 0xFF) / 255.0
                val g = ((pixel shr 8) and 0xFF) / 255.0
                val b = (pixel and 0xFF) / 255.0
                sum += relativeLuminance(r, g, b)
                count++
                x += stepX
            }
            y += stepY
        }
        return if (count == 0) 0.0 else sum / count
    }

    /** sRGB 相对亮度（WCAG 定义）。 */
    private fun relativeLuminance(r: Double, g: Double, b: Double): Double {
        val rs = if (r <= 0.03928) r / 12.92 else ((r + 0.055) / 1.055).pow(2.4)
        val gs = if (g <= 0.03928) g / 12.92 else ((g + 0.055) / 1.055).pow(2.4)
        val bs = if (b <= 0.03928) b / 12.92 else ((b + 0.055) / 1.055).pow(2.4)
        return 0.2126 * rs + 0.7152 * gs + 0.0722 * bs
    }
}
