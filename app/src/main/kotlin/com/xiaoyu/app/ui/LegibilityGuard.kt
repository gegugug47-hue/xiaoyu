package com.xiaoyu.app.ui

import android.graphics.Bitmap
import android.graphics.Color
import com.xiaoyu.app.data.settings.ChatBackgroundSpec
import kotlin.math.max
import kotlin.math.pow

/**
 * 可读性守卫（执行书 v3 §14.6.4）。
 *
 * - 旧能力保留：[minDimForContrast] 仍按整图平均亮度反解暗化下限；
 * - 新能力：[evaluate] 计算「合成后的气泡有效背景色 vs 文字色」的 WCAG 对比度
 *   （正文 4.5:1、辅助 3:1），并按五步顺序兜底：
 *   ① bubbleAlpha→92 ② scrimAlpha+10（≤80）③ 深色底浅色字加 0.5dp shadow
 *   ④ 仍不合格返回可关闭提示文案 ⑤ 绝不强制换回默认图。
 */
object LegibilityGuard {

    /** WCAG AA 正文最低对比度。 */
    const val WCAG_AA_CONTRAST = 4.5f

    /** WCAG AA 辅助文字最低对比度。 */
    const val WCAG_AUX_CONTRAST = 3.0f

    /** 守卫兜底时气泡不透明度的最低值（§14.6.4 第①步）。 */
    const val GUARD_BUBBLE_ALPHA = 92

    /** 守卫第④步提示文案（逐字，设置页与聊天页共用）。 */
    const val HINT_TEXT = "当前背景让文字难以阅读，点这里一键调成可读档"

    /** 浅色主题下，正文字色的相对亮度（近似 #1C1B1F 深色文字）。 */
    private const val LIGHT_THEME_TEXT_LUM = 0.03

    /** 深色主题下，正文字色的相对亮度（近似 #F4EFF4 浅色文字）。 */
    private const val DARK_THEME_TEXT_LUM = 0.83

    /** 气泡底色亮度（浅色主题近白 / 深色主题近黑）。 */
    private const val LIGHT_BUBBLE_LUM = 0.97
    private const val DARK_BUBBLE_LUM = 0.02

    /** 守卫结果：有效参数 + 对比度读数 + 是否需要提示。 */
    data class Result(
        val bodyContrast: Float,
        val auxiliaryContrast: Float,
        val passed: Boolean,
        val bubbleAlpha: Int,
        val scrimAlpha: Int,
        val textShadow: Boolean,
        val hint: String?,
    )

    /**
     * 计算保证对比度所需的最小暗化蒙层透明度（旧版接口，调用方保持兼容）。
     */
    fun minDimForContrast(bitmap: Bitmap?, darkTheme: Boolean): Float {
        if (bitmap == null) return 0f
        val bgLum = averageLuminance(bitmap)
        val textLum = if (darkTheme) DARK_THEME_TEXT_LUM else LIGHT_THEME_TEXT_LUM

        // 暗化后的背景亮度 = bgLum * (1 - dim)
        val lighter = maxOf(textLum, bgLum)
        val darker = minOf(textLum, bgLum)
        val currentContrast = (lighter + 0.05) / (darker + 0.05)
        if (currentContrast >= WCAG_AA_CONTRAST) return 0f

        val safeBgLum = if (textLum > bgLum) {
            (textLum + 0.05) / WCAG_AA_CONTRAST - 0.05
        } else {
            // 文字比背景暗（浅色主题文字在亮背景上），暗化反而更差，交由主题色兜底
            return 0f
        }

        return if (bgLum > 0.001f) {
            (1f - safeBgLum.toFloat() / bgLum.toFloat()).coerceIn(0f, 0.85f)
        } else 0f
    }

    /**
     * §14.6.4 五步守卫。
     *
     * @param spec 当前参数（取 bubbleAlpha / scrimTone / scrimHex / scrimAlpha / legibilityAuto）
     * @param backgroundLuminance 素材层平均相对亮度（0~1，见 [averageLuminance] / [luminanceOf]）
     * @param darkTheme 是否深色主题
     */
    fun evaluate(
        spec: ChatBackgroundSpec,
        backgroundLuminance: Double,
        darkTheme: Boolean,
    ): Result {
        var bubbleAlpha = spec.bubbleAlpha
        var scrimAlpha = spec.scrimAlpha
        val scrimLum = scrimLuminance(spec)
        var body = bodyContrast(bubbleAlpha, scrimAlpha, backgroundLuminance, scrimLum, darkTheme)
        var auxiliary = auxiliaryContrast(bubbleAlpha, scrimAlpha, backgroundLuminance, scrimLum, darkTheme)

        if (spec.legibilityAuto && (body < WCAG_AA_CONTRAST || auxiliary < WCAG_AUX_CONTRAST)) {
            // ① 气泡不透明度提到 92%
            bubbleAlpha = max(bubbleAlpha, GUARD_BUBBLE_ALPHA)
            body = bodyContrast(bubbleAlpha, scrimAlpha, backgroundLuminance, scrimLum, darkTheme)
            auxiliary = auxiliaryContrast(bubbleAlpha, scrimAlpha, backgroundLuminance, scrimLum, darkTheme)

            // ② 遮罩浓度 +10（上限 80）
            if (body < WCAG_AA_CONTRAST || auxiliary < WCAG_AUX_CONTRAST) {
                scrimAlpha = minOf(scrimAlpha + 10, 80)
                body = bodyContrast(bubbleAlpha, scrimAlpha, backgroundLuminance, scrimLum, darkTheme)
                auxiliary = auxiliaryContrast(bubbleAlpha, scrimAlpha, backgroundLuminance, scrimLum, darkTheme)
            }
        }

        val stillFailing = body < WCAG_AA_CONTRAST || auxiliary < WCAG_AUX_CONTRAST
        // ③ 深色底 + 浅色字：加 0.5dp shadow（不改变比值，但显著改善观感）
        val textShadow = stillFailing && darkTheme
        // ④ 仍不合格：返回可关闭提示；⑤ 绝不改回默认图
        val hint = if (stillFailing) HINT_TEXT else null

        return Result(
            bodyContrast = body,
            auxiliaryContrast = auxiliary,
            passed = !stillFailing,
            bubbleAlpha = bubbleAlpha,
            scrimAlpha = scrimAlpha,
            textShadow = textShadow,
            hint = hint,
        )
    }

    /** 合成气泡有效背景色与正文色的对比度。 */
    fun bodyContrast(
        bubbleAlphaPct: Int,
        scrimAlphaPct: Int,
        backgroundLuminance: Double,
        scrimLuminance: Double,
        darkTheme: Boolean,
    ): Float {
        val bubbleLum = compositeBubbleLuminance(
            bubbleAlphaPct, scrimAlphaPct, backgroundLuminance, scrimLuminance, darkTheme,
        )
        val textLum = if (darkTheme) DARK_THEME_TEXT_LUM else LIGHT_THEME_TEXT_LUM
        return contrastRatio(textLum, bubbleLum).toFloat()
    }

    /** 辅助文字（约 70% 不透明度）与气泡有效背景色的对比度。 */
    fun auxiliaryContrast(
        bubbleAlphaPct: Int,
        scrimAlphaPct: Int,
        backgroundLuminance: Double,
        scrimLuminance: Double,
        darkTheme: Boolean,
    ): Float {
        val bubbleLum = compositeBubbleLuminance(
            bubbleAlphaPct, scrimAlphaPct, backgroundLuminance, scrimLuminance, darkTheme,
        )
        val textLum = if (darkTheme) DARK_THEME_TEXT_LUM else LIGHT_THEME_TEXT_LUM
        val auxLum = textLum * 0.7 + bubbleLum * 0.3
        return contrastRatio(auxLum, bubbleLum).toFloat()
    }

    private fun compositeBubbleLuminance(
        bubbleAlphaPct: Int,
        scrimAlphaPct: Int,
        backgroundLuminance: Double,
        scrimLuminance: Double,
        darkTheme: Boolean,
    ): Double {
        val scrimAlpha = scrimAlphaPct.coerceIn(0, 100) / 100.0
        val scrimmed = backgroundLuminance * (1 - scrimAlpha) + scrimLuminance * scrimAlpha
        val bubbleBase = if (darkTheme) DARK_BUBBLE_LUM else LIGHT_BUBBLE_LUM
        val bubbleAlpha = bubbleAlphaPct.coerceIn(0, 100) / 100.0
        return bubbleBase * bubbleAlpha + scrimmed * (1 - bubbleAlpha)
    }

    private fun contrastRatio(lumA: Double, lumB: Double): Double {
        val lighter = max(lumA, lumB)
        val darker = minOf(lumA, lumB)
        return (lighter + 0.05) / (darker + 0.05)
    }

    /** 遮罩色亮度：auto / dark = 黑，light = 白，custom 取 HEX。 */
    private fun scrimLuminance(spec: ChatBackgroundSpec): Double = when (spec.scrimTone) {
        ChatBackgroundSpec.SCRIM_LIGHT -> 1.0
        ChatBackgroundSpec.SCRIM_CUSTOM -> parseHex(spec.scrimHex)?.let { luminanceOf(it) } ?: 0.0
        else -> 0.0
    }

    /** 解析 `#RRGGBB` / `#AARRGGBB`；失败返回 null。 */
    fun parseHex(hex: String?): Int? {
        if (hex.isNullOrBlank()) return null
        return runCatching { Color.parseColor(hex.trim()) }.getOrNull()
    }

    /** ARGB 颜色的相对亮度（WCAG）。 */
    fun luminanceOf(argb: Int): Double = relativeLuminance(
        Color.red(argb) / 255.0,
        Color.green(argb) / 255.0,
        Color.blue(argb) / 255.0,
    )

    /** 采样位图平均相对亮度（0~1）。步长采样，避免 O(w*h) 遍历大卡。 */
    fun averageLuminance(bitmap: Bitmap?): Double {
        if (bitmap == null) return 0.5
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
        return if (count == 0) 0.5 else sum / count
    }

    /** sRGB 相对亮度（WCAG 定义）。 */
    fun relativeLuminance(r: Double, g: Double, b: Double): Double {
        val rs = if (r <= 0.03928) r / 12.92 else ((r + 0.055) / 1.055).pow(2.4)
        val gs = if (g <= 0.03928) g / 12.92 else ((g + 0.055) / 1.055).pow(2.4)
        val bs = if (b <= 0.03928) b / 12.92 else ((b + 0.055) / 1.055).pow(2.4)
        return 0.2126 * rs + 0.7152 * gs + 0.0722 * bs
    }
}
