package com.xiaoyu.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/** 字号基线（执行书 6.1）：正文 16sp、次级 13sp、时间戳 11sp。 */
val BodySize = 16.sp
val SecondarySize = 13.sp
val TimestampSize = 11.sp

/**
 * 全局字号缩放档位：0.9 / 1.0 / 1.15 / 1.3（设置页可选）。
 * 只缩放 Typography，不做 density 放大，避免图标与间距一起被撑开。
 */
val FontScaleSteps = listOf(0.9f, 1.0f, 1.15f, 1.3f)

internal fun baseTypography(): Typography = Typography(
    displaySmall = TextStyle(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = BodySize, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = SecondarySize, lineHeight = 19.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontSize = TimestampSize, lineHeight = 14.sp),
)

/** 按档位缩放全部字号与行高，行高同步缩放保证中文不挤压。 */
fun scaledTypography(scale: Float): Typography {
    val factor = if (scale <= 0f) 1f else scale
    fun TextStyle.scaled(): TextStyle = copy(
        fontSize = fontSize * factor,
        lineHeight = if (lineHeight == TextUnit.Unspecified) TextUnit.Unspecified else lineHeight * factor,
    )
    val base = baseTypography()
    return Typography(
        displayLarge = base.displayLarge.scaled(),
        displayMedium = base.displayMedium.scaled(),
        displaySmall = base.displaySmall.scaled(),
        headlineLarge = base.headlineLarge.scaled(),
        headlineMedium = base.headlineMedium.scaled(),
        headlineSmall = base.headlineSmall.scaled(),
        titleLarge = base.titleLarge.scaled(),
        titleMedium = base.titleMedium.scaled(),
        titleSmall = base.titleSmall.scaled(),
        bodyLarge = base.bodyLarge.scaled(),
        bodyMedium = base.bodyMedium.scaled(),
        bodySmall = base.bodySmall.scaled(),
        labelLarge = base.labelLarge.scaled(),
        labelMedium = base.labelMedium.scaled(),
        labelSmall = base.labelSmall.scaled(),
    )
}
