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
import java.time.LocalDate

private const val TAG = "XiaoyuAlarm"

/**
 * 精确闹钟触发点（执行书 8.2）：只做「立即入队」，耗时的生成交给 WorkManager。
 * goAsync() 内通过 EntryPoint 取 WorkScheduler，避免在广播里做重活。
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != WorkScheduler.ACTION_ALARM) return
        val kind = intent.getStringExtra(WorkScheduler.EXTRA_ALARM_KIND) ?: return
        val slotKey = intent.getStringExtra(WorkScheduler.EXTRA_SLOT_KEY)
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val ep = EntryPointAccessors.fromApplication(appContext, WorkerEntryPoint::class.java)
                ep.settingsRepository().ensureLoaded()
                val scheduler = ep.workScheduler()
                // 夜间闹钟携带预约时的目标日，即使跨午夜才送达也不分析错日期
                val dayKey = if (kind == WorkScheduler.ALARM_NIGHT) {
                    slotKey ?: LocalDate.now().toString()
                } else {
                    LocalDate.now().toString()
                }
                when (kind) {
                    WorkScheduler.ALARM_MORNING -> scheduler.scheduleLife(0, dayKey)
                    WorkScheduler.ALARM_NIGHT -> scheduler.scheduleNightly(0, dayKey)
                    WorkScheduler.ALARM_PROACTIVE -> scheduler.enqueueProactiveNow(slotKey ?: "alarm")
                    else -> Log.w(TAG, "未知闹钟类型：$kind")
                }
                // 每次触发后立即预约下一次，不要一次性预约全天
                scheduler.scheduleNextDailyAlarms()
            } catch (t: Throwable) {
                Log.w(TAG, "闹钟处理失败：${t.message}")
            } finally {
                pending.finish()
            }
        }
    }
}
