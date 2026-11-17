package com.xiaoyu.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * 四套可由用户选择的主题种子（执行书 §14 晨雾/昼白/暮青/夜航）；
 * 每套都提供浅色和深色完整 M3 角色。
 *
 * Android 12+ 默认启用 Material You（见 Theme.kt），
 * 当 dynamicColor 关闭且未指定 customSeed 时，回落到这里的预设。
 */
enum class YuThemePreset(val seedHex: String, val labelZh: String, val tagline: String) {
    MIST("#8FB6D9", "晨雾", "清晨薄雾的青灰蓝"),
    DAY("#4A90D9", "昼白", "晴空正午的正蓝"),
    DUSK("#5BA58C", "暮青", "落日余晖的青绿色"),
    NIGHT("#5DADE2", "夜航", "深夜海面的幽蓝"),
    ;

    val seed: Color get() = requireNotNull(parseHexColor(seedHex))
    val lightColors: ColorScheme by lazy(LazyThreadSafetyMode.NONE) { colorSchemeFromSeed(seed, dark = false) }
    val darkColors: ColorScheme by lazy(LazyThreadSafetyMode.NONE) { colorSchemeFromSeed(seed, dark = true) }

    companion object {
        fun from(value: String?): YuThemePreset = entries.firstOrNull { it.name.equals(value, true) } ?: DAY
    }
}

fun colorSchemeFromSeed(seed: Color, dark: Boolean): ColorScheme {
    val secondary = seed.mix(Color(0xFF7B8B96), 0.42f)
    val tertiary = seed.mix(Color(0xFFF2A65A), 0.58f)
    return if (dark) {
        darkColorScheme(
            primary = seed.mix(Color.White, 0.28f),
            onPrimary = seed.mix(Color.Black, 0.82f),
            primaryContainer = seed.mix(Color.Black, 0.52f),
            onPrimaryContainer = seed.mix(Color.White, 0.78f),
            secondary = secondary.mix(Color.White, 0.30f),
            onSecondary = secondary.mix(Color.Black, 0.82f),
            secondaryContainer = secondary.mix(Color.Black, 0.58f),
            onSecondaryContainer = secondary.mix(Color.White, 0.80f),
            tertiary = tertiary.mix(Color.White, 0.30f),
            onTertiary = tertiary.mix(Color.Black, 0.78f),
            tertiaryContainer = tertiary.mix(Color.Black, 0.58f),
            onTertiaryContainer = tertiary.mix(Color.White, 0.80f),
            background = seed.mix(Color(0xFF101418), 0.93f),
            onBackground = Color(0xFFE4E9ED),
            surface = seed.mix(Color(0xFF151A1E), 0.94f),
            onSurface = Color(0xFFE4E9ED),
            surfaceVariant = seed.mix(Color(0xFF293137), 0.82f),
            onSurfaceVariant = Color(0xFFB9C5CD),
            outline = seed.mix(Color(0xFF7E8B94), 0.70f),
            outlineVariant = seed.mix(Color(0xFF3A444B), 0.78f),
            surfaceContainerLowest = seed.mix(Color(0xFF0D1114), 0.95f),
            surfaceContainerLow = seed.mix(Color(0xFF192025), 0.94f),
            surfaceContainer = seed.mix(Color(0xFF1D2429), 0.93f),
            surfaceContainerHigh = seed.mix(Color(0xFF242C32), 0.91f),
            surfaceContainerHighest = seed.mix(Color(0xFF2D363D), 0.88f),
            error = Color(0xFFE58E82),
            onError = Color(0xFF3A0F0C),
            errorContainer = Color(0xFF5C2420),
            onErrorContainer = Color(0xFFFBDAD5),
        )
    } else {
        lightColorScheme(
            primary = seed.mix(Color.Black, 0.28f),
            onPrimary = Color.White,
            primaryContainer = seed.mix(Color.White, 0.72f),
            onPrimaryContainer = seed.mix(Color.Black, 0.72f),
            secondary = secondary.mix(Color.Black, 0.22f),
            onSecondary = Color.White,
            secondaryContainer = secondary.mix(Color.White, 0.78f),
            onSecondaryContainer = secondary.mix(Color.Black, 0.70f),
            tertiary = tertiary.mix(Color.Black, 0.20f),
            onTertiary = Color.White,
            tertiaryContainer = tertiary.mix(Color.White, 0.76f),
            onTertiaryContainer = tertiary.mix(Color.Black, 0.68f),
            background = seed.mix(Color(0xFFFCFDFE), 0.96f),
            onBackground = Color(0xFF192128),
            surface = Color.White,
            onSurface = Color(0xFF192128),
            surfaceVariant = seed.mix(Color(0xFFF0F3F5), 0.88f),
            onSurfaceVariant = Color(0xFF4E5D67),
            outline = seed.mix(Color(0xFF87959E), 0.72f),
            outlineVariant = seed.mix(Color(0xFFD8E0E5), 0.82f),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = seed.mix(Color(0xFFF8FAFB), 0.95f),
            surfaceContainer = seed.mix(Color(0xFFF3F6F8), 0.92f),
            surfaceContainerHigh = seed.mix(Color(0xFFEDF2F5), 0.88f),
            surfaceContainerHighest = seed.mix(Color(0xFFE5ECEF), 0.84f),
            error = SemanticError,
            onError = Color.White,
            errorContainer = Color(0xFFF9DEDC),
            onErrorContainer = Color(0xFF5C1210),
        )
    }
}

private fun Color.mix(target: Color, targetFraction: Float): Color {
    val amount = targetFraction.coerceIn(0f, 1f)
    return Color(
        red = red + (target.red - red) * amount,
        green = green + (target.green - green) * amount,
        blue = blue + (target.blue - blue) * amount,
        alpha = alpha + (target.alpha - alpha) * amount,
    )
}
