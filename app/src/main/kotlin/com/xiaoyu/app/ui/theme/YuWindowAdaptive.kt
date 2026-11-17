package com.xiaoyu.app.ui.theme

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 窗口姿态（批次 3 / G-05 差距补齐）。
 *
 * 手机：标准竖握，底部 1/3 区域为单手舒适区。
 * 折叠展开：等同平板电脑，全区域可及。
 * 折叠悬停：两半屏幕折叠成 V 字，上半作显示、下半作虚拟输入台。
 * 折叠闭合（BOOK）：单屏小区域，等同手机 compact。
 */
enum class WindowPosture { PHONE, FOLD_EXPANDED, FOLD_HALF_OPEN, FOLD_CLOSED }

/** 单手偏好：双手（默认）/ 左手单手 / 右手单手。 */
enum class Handedness { TWO_HANDED, LEFT_HAND, RIGHT_HAND }

/** 单手舒适区的垂直占比（从底部起算）。 */
private const val ONE_HAND_REACH_RATIO = 0.58f

/** 单手模式下底部安全区的附加内边距。 */
val OneHandExtraPadding = 36.dp

/** 单手模式下输入栏抬高的偏移量。 */
val OneHandInputBarLift = 28.dp

data class WindowAdaptiveState(
    val posture: WindowPosture = WindowPosture.PHONE,
    val handedness: Handedness = Handedness.TWO_HANDED,
    val widthDp: Dp = 360.dp,
    val heightDp: Dp = 640.dp,
    val oneHandReachHeight: Dp = 371.dp,
) {
    /** 是否需要底部安全区抬升。 */
    val requiresBottomLift: Boolean get() =
        handedness != Handedness.TWO_HANDED || posture == WindowPosture.FOLD_HALF_OPEN

    /** 单手模式下的水平内边距偏移：左手缩右边、右手缩左边。 */
    fun horizontalOffset(): Dp = when (handedness) {
        Handedness.LEFT_HAND -> 28.dp
        Handedness.RIGHT_HAND -> (-28).dp
        Handedness.TWO_HANDED -> 0.dp
    }
}

val LocalWindowAdaptive = compositionLocalOf { WindowAdaptiveState() }

@Composable
fun rememberWindowAdaptive(
    handedness: Handedness = Handedness.TWO_HANDED,
): WindowAdaptiveState {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val width = configuration.screenWidthDp.dp
    val height = configuration.screenHeightDp.dp

    // 折叠屏判定：Android 11+ 通过 configuration.smallestScreenWidthDp 和 flags
    val posture = remember(configuration) {
        detectPosture(configuration, width, height)
    }

    val reachHeight = with(density) {
        (height.value * ONE_HAND_REACH_RATIO).dp
    }

    return remember(width, height, posture, handedness) {
        WindowAdaptiveState(
            posture = posture,
            handedness = handedness,
            widthDp = width,
            heightDp = height,
            oneHandReachHeight = reachHeight,
        )
    }
}

private fun detectPosture(
    configuration: Configuration,
    width: Dp,
    height: Dp,
): WindowPosture {
    val sw = configuration.smallestScreenWidthDp
    // 折叠屏展开：最小边 ≥ 600dp 且屏幕比例接近正方形或横屏
    if (sw >= 600) {
        val aspect = height.value / width.value
        return if (aspect in 1.0f..1.6f) {
            WindowPosture.FOLD_EXPANDED
        } else {
            WindowPosture.PHONE
        }
    }
    // 折叠屏悬停：简化判定 —— 折叠成 V 字上半显示时高度显著大于宽度
    if (height.value > width.value * 1.8f &&
        configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    ) {
        return WindowPosture.FOLD_HALF_OPEN
    }
    return WindowPosture.PHONE
}

/** 便捷 Provider：在 Theme 里提供单手姿态信息。 */
@Composable
fun WindowAdaptiveProvider(
    handedness: Handedness = Handedness.TWO_HANDED,
    content: @Composable () -> Unit,
) {
    val state = rememberWindowAdaptive(handedness)
    CompositionLocalProvider(LocalWindowAdaptive provides state, content = content)
}
