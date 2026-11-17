package com.xiaoyu.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** 3 套气泡主题（执行书 6.1）：她的一侧固定白/深灰，我的气泡换色。 */
enum class BubbleStyle(val label: String) {
    BRAND("品牌青灰"),
    MINT("薄荷绿"),
    WARM("暖橙奶油"),
    ;

    companion object {
        fun from(value: String?): BubbleStyle = runCatching {
            valueOf(requireNotNull(value).uppercase())
        }.getOrDefault(BRAND)
    }
}

data class BubbleColors(
    val herBackground: Color,
    val herContent: Color,
    val myBackground: Color,
    val myContent: Color,
)

fun bubbleColors(style: BubbleStyle, dark: Boolean, scheme: ColorScheme? = null): BubbleColors {
    val her = scheme?.surfaceContainerHigh ?: if (dark) Color(0xFF2A2D32) else Color.White
    val herText = scheme?.onSurface ?: if (dark) Color(0xFFEDE5DE) else Color(0xFF1C2426)
    return when (style) {
        BubbleStyle.BRAND -> BubbleColors(
            herBackground = her,
            herContent = herText,
            myBackground = scheme?.primaryContainer ?: if (dark) Color(0xFF2C5B67) else BrandTeal,
            myContent = scheme?.onPrimaryContainer ?: if (dark) Color(0xFFDCEEF2) else Color.White,
        )
        BubbleStyle.MINT -> BubbleColors(
            herBackground = her,
            herContent = herText,
            myBackground = if (dark) Color(0xFF3F6B52) else Color(0xFF95EC69),
            myContent = if (dark) Color(0xFFE2F3E8) else Color(0xFF16280F),
        )
        BubbleStyle.WARM -> BubbleColors(
            herBackground = her,
            herContent = herText,
            myBackground = if (dark) Color(0xFF8A5A2E) else BrandWarm,
            myContent = if (dark) Color(0xFFFBE7D0) else Color(0xFF3A2410),
        )
    }
}

val LocalBubbleColors = staticCompositionLocalOf { bubbleColors(BubbleStyle.BRAND, false) }
