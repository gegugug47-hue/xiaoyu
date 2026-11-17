package com.xiaoyu.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/** 全局语义字号。应用内档位只缩放字体与行高，不放大布局密度。 */
data class YuType(
    val display: TextStyle = TextStyle(fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold),
    val title: TextStyle = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    val section: TextStyle = TextStyle(
        fontSize = 17.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.1.sp,
    ),
    val body: TextStyle = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    val chat: TextStyle = TextStyle(fontSize = 16.sp, lineHeight = 25.sp, fontWeight = FontWeight.Normal),
    val caption: TextStyle = TextStyle(
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.2.sp,
    ),
    val meta: TextStyle = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.2.sp,
    ),
    val emotion: TextStyle = TextStyle(fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium),
    val emoji: TextStyle = TextStyle(fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    val herDay: TextStyle = TextStyle(fontSize = 15.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    val diary: TextStyle = TextStyle(fontSize = 18.sp, lineHeight = 28.sp, fontWeight = FontWeight.Normal),
) {
    fun scaled(scale: Float): YuType {
        val factor = if (scale <= 0f) 1f else scale
        fun TextStyle.resize() = copy(
            fontSize = fontSize * factor,
            lineHeight = if (lineHeight == TextUnit.Unspecified) TextUnit.Unspecified else lineHeight * factor,
            letterSpacing = if (letterSpacing == TextUnit.Unspecified) TextUnit.Unspecified else letterSpacing * factor,
        )
        return copy(
            display = display.resize(),
            title = title.resize(),
            section = section.resize(),
            body = body.resize(),
            chat = chat.resize(),
            caption = caption.resize(),
            meta = meta.resize(),
            emotion = emotion.resize(),
            emoji = emoji.resize(),
            herDay = herDay.resize(),
            diary = diary.resize(),
        )
    }

    fun toMaterialTypography(): Typography = Typography(
        displayLarge = display,
        displayMedium = display,
        displaySmall = display,
        headlineLarge = title,
        headlineMedium = title,
        headlineSmall = section,
        titleLarge = title,
        titleMedium = section,
        titleSmall = herDay,
        bodyLarge = body,
        bodyMedium = chat,
        bodySmall = caption,
        labelLarge = caption,
        labelMedium = meta,
        labelSmall = meta,
    )

    companion object {
        val Default = YuType()
    }
}

val LocalYuType = staticCompositionLocalOf { YuType.Default }
