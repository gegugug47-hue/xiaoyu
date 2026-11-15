package com.xiaoyu.app.ui

import android.Manifest
import android.app.AlarmManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.xiaoyu.app.data.device.RomBrand
import com.xiaoyu.app.data.device.RomCompat

/** 后台存活自检结果（执行书 8.2）。 */
data class BackgroundSelfCheckStatus(
    val notificationReady: Boolean,
    val exactAlarmReady: Boolean,
    val batteryReady: Boolean,
    val autoStartPanelFound: Boolean,
    val brand: RomBrand,
)

/** 读取四项自检状态，供卡片与需要诊断信息的页面复用。 */
fun readBackgroundSelfCheckStatus(context: Context): BackgroundSelfCheckStatus {
    val brand = RomCompat.brand()
    return BackgroundSelfCheckStatus(
        notificationReady = areNotificationsAllowed(context),
        exactAlarmReady = canScheduleExactAlarms(context),
        batteryReady = RomCompat.isIgnoringBatteryOptimizations(context),
        autoStartPanelFound = RomCompat.availableAutoStartIntents(context, brand).isNotEmpty(),
        brand = brand,
    )
}

/**
 * 后台存活自检卡片（执行书 8.2）：通知权限 / 精确闹钟 / 电池优化 / 自启动四项状态 + 一键跳转。
 * 独立 Composable，不依赖 ViewModel，可在设置页任意位置挂载。
 */
@Composable
fun BackgroundSelfCheckCard(modifier: Modifier = Modifier, lastPushAt: Long = 0L) {
    val context = LocalContext.current
    var status by remember { mutableStateOf(readBackgroundSelfCheckStatus(context)) }
    var guide by remember { mutableStateOf<String?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { status = readBackgroundSelfCheckStatus(context) }

    LifecycleResumeEffect(Unit) {
        // 从系统设置返回后自动刷新四项状态
        status = readBackgroundSelfCheckStatus(context)
        onPauseOrDispose { }
    }

    fun toast(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_LONG).show()
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "后台存活自检",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "她能不能准时找到你，就看下面四项。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            CheckRow(
                title = "通知权限",
                desc = "她发来消息时能提醒你",
                state = if (status.notificationReady) CheckState.OK else CheckState.WARN,
                actionLabel = "去开启",
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !status.notificationReady) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else if (!openAppNotificationSettings(context)) {
                    toast("请在系统设置 → 通知 里允许小鱼发送通知")
                }
            }

            CheckRow(
                title = "精确闹钟",
                desc = "早安、晚安和主动消息不被系统拖延",
                state = if (status.exactAlarmReady) CheckState.OK else CheckState.WARN,
                actionLabel = "去授权",
            ) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    toast("当前系统不需要精确闹钟授权")
                } else {
                    val intent = Intent(
                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:${context.packageName}"),
                    )
                    if (!tryStart(context, intent)) toast("请在系统设置 → 应用 → 特殊权限 里允许「闹钟和提醒」")
                }
            }

            CheckRow(
                title = "电池优化白名单",
                desc = "系统不会轻易清理她的后台任务",
                state = if (status.batteryReady) CheckState.OK else CheckState.WARN,
                actionLabel = "去设置",
            ) {
                guide = RomCompat.openBatteryOptimization(context, status.brand)
            }

            CheckRow(
                title = "自启动",
                desc = if (status.autoStartPanelFound) {
                    "无法自动检测，请确认已允许自启动"
                } else {
                    "没找到自启动设置页，请按指引手动设置"
                },
                state = if (status.autoStartPanelFound) CheckState.UNKNOWN else CheckState.WARN,
                actionLabel = "去设置",
            ) {
                guide = RomCompat.openAutoStart(context, status.brand)
            }

            guide?.let { text ->
                Text(
                    text,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "最近一次成功推送：${formatLastPush(lastPushAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = {
                    copyDiagnostics(context, status)
                    toast("诊断信息已复制")
                }) { Text("复制诊断信息") }
            }
        }
    }
}

private enum class CheckState(val icon: String) {
    OK("✅"),
    WARN("⚠️"),
    UNKNOWN("❓"),
}

@Composable
private fun CheckRow(
    title: String,
    desc: String,
    state: CheckState,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(state.icon, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                desc,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onAction) { Text(actionLabel) }
    }
}

private fun areNotificationsAllowed(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED
    ) {
        return false
    }
    return NotificationManagerCompat.from(context).areNotificationsEnabled()
}

private fun canScheduleExactAlarms(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    return context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() ?: false
}

private fun openAppNotificationSettings(context: Context): Boolean {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    return tryStart(context, intent)
}

private fun tryStart(context: Context, intent: Intent): Boolean = runCatching {
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
}.getOrDefault(false)

/** 最近一次推送时间：0 表示还没有成功推送过。 */
private fun formatLastPush(lastPushAt: Long): String {
    if (lastPushAt <= 0) return "还没有记录"
    val format = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.CHINA)
    return format.format(java.util.Date(lastPushAt))
}

private fun copyDiagnostics(context: Context, status: BackgroundSelfCheckStatus) {
    val text = buildString {
        appendLine("小鱼后台自检")
        appendLine("机型：${Build.MANUFACTURER} ${Build.MODEL}（Android ${Build.VERSION.RELEASE}）")
        appendLine("品牌：${status.brand.label}")
        appendLine("通知权限：${if (status.notificationReady) "已开启" else "未开启"}")
        appendLine("精确闹钟：${if (status.exactAlarmReady) "已授权" else "未授权（推送可能延迟）"}")
        appendLine("电池优化：${if (status.batteryReady) "已豁免" else "未豁免"}")
        appendLine("自启动设置页：${if (status.autoStartPanelFound) "已找到" else "未找到"}")
    }
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("小鱼后台自检", text))
}
