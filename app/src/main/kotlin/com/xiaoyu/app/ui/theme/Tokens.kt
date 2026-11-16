package com.xiaoyu.app.ui.theme

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 设计系统 Tokens（执行书 v3 H-24 / 14.2）：全局间距、圆角、海拔、动效时长的唯一数值真源。
 *
 * 约定：UI 代码中所有 `.dp` / `.sp` 数值应优先引用本对象的命名常量，
 * 避免散落的硬编码魔法数。新增间距档位时在此处扩展，不要就地写数字。
 *
 * 命名规则：
 * - Spacing：Xxs(2) / Xs(4) / Sm(8) / Md(12) / Lg(16) / Xl(24) / Xxl(32)
 * - Radius：Xs(4) / Sm(8) / Md(12) / Lg(16) / Pill(999)
 * - Elevation：Flat(0) / Card(1) / Floating(3) / Modal(6)
 */
object Tokens {

    // ---- 间距 ----
    val SpacingXxs = 2.dp
    val SpacingXs = 4.dp
    val SpacingSm = 8.dp
    val SpacingMd = 12.dp
    val SpacingLg = 16.dp
    val SpacingXl = 24.dp
    val SpacingXxl = 32.dp

    // ---- 圆角 ----
    val RadiusXs = 4.dp
    val RadiusSm = 8.dp
    val RadiusMd = 12.dp
    val RadiusLg = 16.dp
    val RadiusPill = 999.dp

    // ---- 海拔（阴影） ----
    val ElevationFlat = 0.dp
    val ElevationCard = 1.dp
    val ElevationFloating = 3.dp
    val ElevationModal = 6.dp

    // ---- 动效时长（毫秒） ----
    const val MotionFast = 120
    const val MotionNormal = 180
    const val MotionSlow = 280

    // ---- 字号补充（Material 默认之外的微调档位） ----
    val FontCaption = 11.sp
    val FontFootnote = 13.sp

    // ---- 气泡内边距（聊天气泡的水平/垂直内边距） ----
    val BubbleHorizontal = 14.dp
    val BubbleVertical = 8.dp

    // ---- 顶栏 ----
    val TopBarHeight = 64.dp

    // ---- 输入栏 ----
    val InputBarHeight = 46.dp
    val InputBarHorizontal = 16.dp
}
