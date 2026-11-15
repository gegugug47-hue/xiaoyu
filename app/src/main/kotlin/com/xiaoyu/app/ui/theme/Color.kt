package com.xiaoyu.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * 品牌色（执行书 6.1）：主色青灰蓝 `#3E7C8C`、强调暖橙 `#F2A65A`。
 * 语义色只用于状态提示，不参与大面积铺色，避免破坏「深夜客厅」的安静感。
 */
val BrandTeal = Color(0xFF3E7C8C)
val BrandTealDeep = Color(0xFF2C5B67)
val BrandTealLight = Color(0xFF9CC7D2)
val BrandWarm = Color(0xFFF2A65A)
val BrandWarmLight = Color(0xFFF7C892)

val SemanticSuccess = Color(0xFF4CAF7D)
val SemanticWarning = Color(0xFFE8B33C)
val SemanticError = Color(0xFFD9534F)

/** 浅色主题：米白纸面 + 青灰蓝主色，长时间阅读不刺眼。 */
val LightColors = lightColorScheme(
    primary = BrandTeal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8E9ED),
    onPrimaryContainer = Color(0xFF12333B),
    secondary = Color(0xFF6B8A92),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3EEF0),
    onSecondaryContainer = Color(0xFF1F3A41),
    tertiary = BrandWarm,
    onTertiary = Color(0xFF3A2410),
    tertiaryContainer = Color(0xFFFBE7D0),
    onTertiaryContainer = Color(0xFF4A2D12),
    background = Color(0xFFF7F9F9),
    onBackground = Color(0xFF1C2426),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C2426),
    surfaceVariant = Color(0xFFECF1F2),
    onSurfaceVariant = Color(0xFF546267),
    outline = Color(0xFFB9C7CB),
    outlineVariant = Color(0xFFD8E2E5),
    error = SemanticError,
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF5C1210),
)

/** 深色主题：深青灰底 + 柔化主色，夜里看聊天记录不刺眼。 */
val DarkColors = darkColorScheme(
    primary = BrandTealLight,
    onPrimary = Color(0xFF0E2A31),
    primaryContainer = Color(0xFF23505C),
    onPrimaryContainer = Color(0xFFD3ECF2),
    secondary = Color(0xFF9BB8BE),
    onSecondary = Color(0xFF12262B),
    secondaryContainer = Color(0xFF2A3F45),
    onSecondaryContainer = Color(0xFFD5E5E9),
    tertiary = BrandWarmLight,
    onTertiary = Color(0xFF3A2410),
    tertiaryContainer = Color(0xFF5A3A1C),
    onTertiaryContainer = Color(0xFFFBE0C4),
    background = Color(0xFF12100F),
    onBackground = Color(0xFFEDE5DE),
    surface = Color(0xFF1A1D1F),
    onSurface = Color(0xFFEDE5DE),
    surfaceVariant = Color(0xFF262B2D),
    onSurfaceVariant = Color(0xFFC1CDD0),
    outline = Color(0xFF4A5257),
    outlineVariant = Color(0xFF333B3E),
    error = Color(0xFFE58E82),
    onError = Color(0xFF3A0F0C),
    errorContainer = Color(0xFF5C2420),
    onErrorContainer = Color(0xFFFBDAD5),
)
