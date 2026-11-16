package com.xiaoyu.app.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.xiaoyu.app.data.scheduler.ExtractionScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : ExtractionScheduler {
    private fun wm(): WorkManager = WorkManager.getInstance(context)

    override fun scheduleExtraction(sessionId: Long) {
        val request = OneTimeWorkRequestBuilder<ExtractionWorker>()
            .setInputData(workDataOf(ExtractionWorker.KEY_SESSION_ID to sessionId))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
            .build()
        wm().enqueueUniqueWork("extract-$sessionId", ExistingWorkPolicy.KEEP, request)
    }

    fun scheduleExplicitRetry() {
        val request = OneTimeWorkRequestBuilder<ExplicitMemoryWorker>()
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()
        wm().enqueueUniqueWork("explicit_retry", ExistingWorkPolicy.REPLACE, request)
    }

    fun ensureDailyPlan() {
        val request = OneTimeWorkRequestBuilder<DailyPlanWorker>().build()
        wm().enqueueUniqueWork("daily_plan_seed", ExistingWorkPolicy.KEEP, request)
    }

    fun scheduleNextDailyPlan(delayMillis: Long) {
        val request = OneTimeWorkRequestBuilder<DailyPlanWorker>()
            .setInitialDelay(delayMillis.coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .build()
        wm().enqueueUniqueWork("daily_plan_next", ExistingWorkPolicy.REPLACE, request)
    }

    fun scheduleNightly(delayMillis: Long, dayKey: String) {
        val request = OneTimeWorkRequestBuilder<NightlyWorker>()
            .setInitialDelay(delayMillis.coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 20, TimeUnit.MINUTES)
            .build()
        wm().enqueueUniqueWork("nightly-$dayKey", ExistingWorkPolicy.KEEP, request)
    }

    fun scheduleLife(delayMillis: Long, dayKey: String) {
        val request = OneTimeWorkRequestBuilder<LifeWorker>()
            .setInitialDelay(delayMillis.coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .build()
        wm().enqueueUniqueWork("life-$dayKey", ExistingWorkPolicy.KEEP, request)
    }

    fun scheduleProactive(delayMillis: Long, slotKey: String) {
        val request = OneTimeWorkRequestBuilder<ProactiveWorker>()
            .setInitialDelay(delayMillis.coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 15, TimeUnit.MINUTES)
            .build()
        wm().enqueueUniqueWork("proactive-$slotKey", ExistingWorkPolicy.KEEP, request)
    }

    fun scheduleBackup() {
        // 执行书 8.1：每周一次，尽量在充电且电量不低时执行，避免刷机前没备份还耗电。
        val constraints = Constraints.Builder()
            .setRequiresCharging(true)
            .setRequiresBatteryNotLow(true)
            .build()
        val request = PeriodicWorkRequestBuilder<BackupWorker>(7, TimeUnit.DAYS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()
        wm().enqueueUniquePeriodicWork("weekly_backup", ExistingPeriodicWorkPolicy.KEEP, request)
    }

    // ---------------- 精确闹钟（v3 ADR-10：WorkManager 为主轨，精确闹钟为可选增强） ----------------

    /** 是否具备精确闹钟权限；Android 12+ 未授权时只能退化调度。 */
    fun canScheduleExactAlarms(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() ?: false
    }

    /**
     * 预约一次闹钟：有精确权限时用 setExactAndAllowWhileIdle（可在 Doze 下唤醒）；
     * 无权限或抛 SecurityException 时退化为 setWindow(±10 分钟) 并打日志。
     * 仅作为 WorkManager 主轨之上的准点增强，核心体验不依赖它。
     */
    fun scheduleAlarm(kind: String, triggerAtMillis: Long, slotKey: String? = null): Boolean {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return false
        val at = triggerAtMillis.coerceAtLeast(System.currentTimeMillis() + 1_000)
        val pending = PendingIntent.getBroadcast(
            context,
            requestCodeFor(kind, slotKey),
            alarmIntent(kind, slotKey),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (canScheduleExactAlarms()) {
            val exact = runCatching {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
                true
            }.getOrElse { e ->
                Log.w(TAG, "精确闹钟设置失败，退化为窗口调度：$kind ${e.message}")
                false
            }
            if (exact) return true
        } else {
            Log.w(TAG, "未获得精确闹钟权限，退化为 ±10 分钟窗口调度：$kind")
        }
        runCatching {
            manager.setWindow(AlarmManager.RTC_WAKEUP, at, WINDOW_MILLIS, pending)
        }.onFailure { Log.w(TAG, "窗口闹钟设置失败：$kind ${it.message}") }
        return false
    }

    /** 预约下一次早安 / 晚安闹钟（每次触发后立即重排，始终只保留下一次）。 */
    fun scheduleNextDailyAlarms() {
        val now = ZonedDateTime.now()
        scheduleAlarm(
            ALARM_MORNING,
            nextOccurrence(now, MORNING_HOUR, MORNING_MINUTE).toInstant().toEpochMilli(),
        )
        scheduleAlarm(
            ALARM_NIGHT,
            nextOccurrence(now, NIGHT_HOUR, NIGHT_MINUTE).toInstant().toEpochMilli(),
        )
    }

    /** 主动消息精确闹钟；slotKey 与 WorkManager 兜底任务同名，互相顶替避免重复推送。 */
    fun scheduleProactiveAlarm(triggerAtMillis: Long, slotKey: String) {
        scheduleAlarm(ALARM_PROACTIVE, triggerAtMillis, slotKey)
    }

    /** 开机 / 升级后重排今天的主动消息精确闹钟（WorkManager 任务自身持久，无需重排）。 */
    fun rearmProactiveAlarms(slots: List<Long>) {
        if (!canScheduleExactAlarms()) return
        val now = System.currentTimeMillis()
        slots.filter { it > now + 60_000 }.forEach { at ->
            val day = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate()
            scheduleProactiveAlarm(at, "$day-$at")
        }
    }

    /** 闹钟触发后立即入队；REPLACE 顶掉同槽位的延迟兜底任务，避免重复推送。 */
    fun enqueueProactiveNow(slotKey: String) {
        val request = OneTimeWorkRequestBuilder<ProactiveWorker>()
            .setBackoffCriteria(BackoffPolicy.LINEAR, 15, TimeUnit.MINUTES)
            .build()
        wm().enqueueUniqueWork("proactive-$slotKey", ExistingWorkPolicy.REPLACE, request)
    }

    private fun nextOccurrence(now: ZonedDateTime, hour: Int, minute: Int): ZonedDateTime {
        var at = now.toLocalDate().atTime(hour, minute).atZone(now.zone)
        if (!at.isAfter(now.plusSeconds(60))) at = at.plusDays(1)
        return at
    }

    private fun alarmIntent(kind: String, slotKey: String?): Intent =
        Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_ALARM
            putExtra(EXTRA_ALARM_KIND, kind)
            putExtra(EXTRA_SLOT_KEY, slotKey)
        }

    private fun requestCodeFor(kind: String, slotKey: String?): Int = when (kind) {
        ALARM_MORNING -> REQUEST_MORNING
        ALARM_NIGHT -> REQUEST_NIGHT
        else -> REQUEST_PROACTIVE_BASE + ((slotKey ?: "").hashCode() and 0x0FFF)
    }

    companion object {
        private const val TAG = "XiaoyuAlarm"

        /** 闹钟种类：早安 / 晚安 / 主动消息。 */
        const val ALARM_MORNING = "morning"
        const val ALARM_NIGHT = "night"
        const val ALARM_PROACTIVE = "proactive"

        const val ACTION_ALARM = "com.xiaoyu.app.action.ALARM"
        const val EXTRA_ALARM_KIND = "alarm_kind"
        const val EXTRA_SLOT_KEY = "slot_key"

        /** 无精确闹钟权限时的窗口浮动（±10 分钟）。 */
        private const val WINDOW_MILLIS = 10 * 60 * 1000L
        private const val MORNING_HOUR = 8
        private const val MORNING_MINUTE = 5
        private const val NIGHT_HOUR = 23
        private const val NIGHT_MINUTE = 50
        private const val REQUEST_MORNING = 1001
        private const val REQUEST_NIGHT = 1002
        private const val REQUEST_PROACTIVE_BASE = 2000
    }
}
