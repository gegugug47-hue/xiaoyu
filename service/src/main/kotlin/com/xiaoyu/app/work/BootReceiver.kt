package com.xiaoyu.app.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val TAG = "XiaoyuBoot"

/**
 * 开机 / 应用升级后恢复后台排期（执行书 8.2）：
 * 重排早安晚安闹钟、今日主动消息闹钟，并补一次每日规划。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val ep = EntryPointAccessors.fromApplication(appContext, WorkerEntryPoint::class.java)
                ep.notifier().ensureChannels()
                val settings = ep.settingsRepository().apply { ensureLoaded() }
                val scheduler = ep.workScheduler()
                scheduler.scheduleNextDailyAlarms()
                scheduler.rearmProactiveAlarms(settings.current().proactiveSlots)
                scheduler.ensureDailyPlan()
                Log.i(TAG, "重启后已恢复闹钟，共 ${settings.current().proactiveSlots.size} 个主动消息槽位")
            } catch (t: Throwable) {
                Log.w(TAG, "重启恢复失败：${t.message}")
            } finally {
                pending.finish()
            }
        }
    }
}
