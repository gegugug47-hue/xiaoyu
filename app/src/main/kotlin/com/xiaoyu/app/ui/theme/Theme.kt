package com.xiaoyu.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/** 主题模式（执行书 6.1）：浅色 / 深色 / 跟随系统（默认）。 */
enum class ThemeMode(val label: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色"),
    ;

    companion object {
        fun from(value: String?): ThemeMode = runCatching {
            valueOf(requireNotNull(value).uppercase())
        }.getOrDefault(SYSTEM)
    }
}

val LocalXiaoyuDark = staticCompositionLocalOf { false }

/**
 * 设计系统入口：Material 3 + 品牌色 + 3 套气泡 + 应用内字号缩放。
 * 动态取色只在 Android 12+ 且用户主动开启时生效（默认关，用品牌色）。
 */
@Composable
fun XiaoyuTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    themeMode: String? = null,
    dynamicColor: Boolean = false,
    fontScale: Float = 1f,
    bubbleStyle: String? = null,
    content: @Composable () -> Unit,
) {
    val dark = when (ThemeMode.from(themeMode)) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> darkTheme
    }
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    val typography = remember(fontScale) { scaledTypography(fontScale) }
    val bubbles = remember(bubbleStyle, dark) { bubbleColors(BubbleStyle.from(bubbleStyle), dark) }
    CompositionLocalProvider(
        LocalBubbleColors provides bubbles,
        LocalXiaoyuDark provides dark,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = XiaoyuShapes,
            content = content,
        )
    }
}
