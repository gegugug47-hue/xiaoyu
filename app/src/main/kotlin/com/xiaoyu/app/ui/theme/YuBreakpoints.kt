package com.xiaoyu.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** compact 单栏；medium/expanded（含折叠屏展开）均为双栏。 */
enum class WindowSizeClass {
    COMPACT,
    MEDIUM,
    EXPANDED,
    ;

    val supportsDualPane: Boolean get() = this != COMPACT
    val supportsSettingsSecondaryPane: Boolean get() = this == EXPANDED
}

fun windowSizeClassFor(width: Dp): WindowSizeClass = when {
    width < 600.dp -> WindowSizeClass.COMPACT
    width < 840.dp -> WindowSizeClass.MEDIUM
    else -> WindowSizeClass.EXPANDED
}

@Composable
fun rememberWindowSizeClass(): WindowSizeClass {
    val widthPx = LocalWindowInfo.current.containerSize.width
    val density = LocalDensity.current
    val width = with(density) { widthPx.toDp() }
    return remember(width) { windowSizeClassFor(width) }
}
