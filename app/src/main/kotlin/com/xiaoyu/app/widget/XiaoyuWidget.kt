package com.xiaoyu.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.xiaoyu.app.data.repo.ChatRepository
import com.xiaoyu.app.data.repo.SoulStateRepository
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * 桌面小组件（执行书 M7-08）：展示她最近一句话 + 当前好感度，点击回到聊天。
 *
 * 依赖 `androidx.glance:glance-appwidget`，并在 AndroidManifest 注册
 * [XiaoyuWidgetReceiver]（详见交付说明，本轮不修改中央 Manifest）。
 * 数据通过 Hilt EntryPoint 读取，避免小组件进程里再做一套数据库初始化。
 */
class XiaoyuWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = loadState(context)
        provideContent {
            GlanceTheme {
                WidgetContent(state)
            }
        }
    }

    private suspend fun loadState(context: Context): WidgetState {
        return runCatching {
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                WidgetEntryPoint::class.java,
            )
            val line = entryPoint.chatRepository().latestAssistantLine(maxChars = 60)
            val favorability = entryPoint.soulStateRepository().favorabilityValue()
            WidgetState(line = line.orEmpty(), favorability = favorability)
        }.getOrDefault(WidgetState(line = "", favorability = 0))
    }
}

data class WidgetState(val line: String, val favorability: Int)

@dagger.hilt.EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun chatRepository(): ChatRepository
    fun soulStateRepository(): SoulStateRepository
}

@Composable
private fun WidgetContent(state: WidgetState) {
    val openIntent = Intent().apply {
        setClassName("com.xiaoyu.app", "com.xiaoyu.app.MainActivity")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Color(0xFFFDF7F3)))
            .clickable(actionStartActivity(openIntent))
            .padding(16.dp),
    ) {
        Text(
            text = "小渔",
            style = TextStyle(
                color = ColorProvider(Color(0xFFE2856B)),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
        Spacer(modifier = GlanceModifier.padding(top = 6.dp))
        Text(
            text = state.line.ifBlank { "她正趴在窗边发呆，点我聊两句～" },
            maxLines = 3,
            style = TextStyle(
                color = ColorProvider(Color(0xFF3D3A38)),
                fontSize = 15.sp,
            ),
        )
        Spacer(modifier = GlanceModifier.padding(top = 10.dp))
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            Text(
                text = "好感度",
                style = TextStyle(color = ColorProvider(Color(0xFF9A928C)), fontSize = 12.sp),
            )
            Spacer(modifier = GlanceModifier.padding(start = 6.dp))
            Text(
                text = "${state.favorability}",
                style = TextStyle(
                    color = ColorProvider(Color(0xFFE2856B)),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
    }
}

/** Manifest 里注册此 Receiver（exported=true，带 APPWIDGET_UPDATE intent-filter）。 */
class XiaoyuWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = XiaoyuWidget()
}
