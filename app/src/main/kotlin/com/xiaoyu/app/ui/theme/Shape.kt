package com.xiaoyu.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** 圆角友好：对话卡片大圆角，控件中圆角，标签小圆角（执行书 6.1）。 */
val XiaoyuShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** 气泡圆角基线：常规 18dp，靠头像一侧收窄到 4dp。 */
val BubbleCorner = 18.dp
val BubbleCornerTight = 4.dp
