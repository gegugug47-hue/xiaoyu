package com.xiaoyu.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * 设计系统预览页（M0-04 验收项）：色板 / 字阶 / 圆角 / 3 套气泡 / 常用组件。
 * 在 Android Studio 的 Compose Preview 中可直接查看深浅两套效果。
 */
@Composable
private fun DesignSystemShowcase(style: BubbleStyle) {
    val bubbles = bubbleColors(style, LocalXiaoyuDark.current)
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("小鱼设计系统", style = MaterialTheme.typography.titleLarge)
            Text("正文 16sp：今天也要好好吃饭。", style = MaterialTheme.typography.bodyLarge)
            Text("次级 13sp：她想起你的时候", style = MaterialTheme.typography.bodySmall)
            Text("时间戳 11sp · 14:32", style = MaterialTheme.typography.labelSmall)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PaletteDot(MaterialTheme.colorScheme.primary, "主色")
                PaletteDot(MaterialTheme.colorScheme.tertiary, "强调")
                PaletteDot(SemanticSuccess, "成功")
                PaletteDot(SemanticWarning, "警告")
                PaletteDot(SemanticError, "错误")
            }

            BubblePreview("你今天怎么这么晚才来", bubbles.herBackground, bubbles.herContent)
            BubblePreview("在忙啊，刚下班", bubbles.myBackground, bubbles.myContent)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {}) { Text("主按钮") }
                OutlinedTextField(value = "", onValueChange = {}, label = { Text("输入框") })
            }
            Text("气泡主题：${style.label}", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun PaletteDot(color: Color, label: String) {
    Column {
        Box(Modifier.size(36.dp).background(color, RoundedCornerShape(10.dp)))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun BubblePreview(text: String, background: Color, content: Color) {
    Box(
        Modifier
            .background(background, RoundedCornerShape(BubbleCorner, BubbleCornerTight, BubbleCorner, BubbleCorner)),
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            color = content,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Preview(name = "浅色 · 品牌青灰", showBackground = true)
@Composable
private fun DesignSystemLightPreview() {
    XiaoyuTheme(darkTheme = false, bubbleStyle = BubbleStyle.BRAND.name) {
        DesignSystemShowcase(BubbleStyle.BRAND)
    }
}

@Preview(name = "深色 · 薄荷绿", showBackground = true)
@Composable
private fun DesignSystemDarkPreview() {
    XiaoyuTheme(darkTheme = true, bubbleStyle = BubbleStyle.MINT.name) {
        DesignSystemShowcase(BubbleStyle.MINT)
    }
}

@Preview(name = "浅色 · 暖橙", showBackground = true)
@Composable
private fun DesignSystemWarmPreview() {
    XiaoyuTheme(darkTheme = false, bubbleStyle = BubbleStyle.WARM.name) {
        DesignSystemShowcase(BubbleStyle.WARM)
    }
}
