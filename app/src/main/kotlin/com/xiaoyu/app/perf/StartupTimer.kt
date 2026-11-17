package com.xiaoyu.app.perf

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import com.xiaoyu.app.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 冷启动耗时（执行书 v3 §1.5 / C-02）：`Application.onCreate` → 首帧绘制。
 *
 * 首帧用 [Choreographer] 的下一帧回调近似，同时调用 [Activity.reportFullyDrawn]，
 * 便于 `logcat` / Play Vitals 交叉验证。结果追加写入 `files/perf/startup.log`，
 * 单文件最多 [MAX_LINES] 条（滚动覆盖最旧记录），Debug 下同时打 `Log.d`。
 */
object StartupTimer {

    private const val TAG = "PerfStartup"
    private const val DIR_NAME = "perf"
    private const val FILE_NAME = "startup.log"
    private const val MAX_LINES = 200

    /** 一次启动采样；[coldStartMs] 是 Application 创建到首帧的毫秒数。 */
    data class Sample(
        val atMs: Long,
        val coldStartMs: Long,
        val device: String,
        val sdk: Int,
    )

    @Volatile
    private var startUptimeMs = 0L

    @Volatile
    private var recorded = false

    @Volatile
    private var lastSample: Sample? = null

    /** 最近一次（本次进程）采样；还没完成首帧时返回 null。 */
    fun last(): Sample? = lastSample

    /** 在 `Application.onCreate` 最早期调用；重复调用只记第一次。 */
    fun install(app: Application) {
        if (startUptimeMs > 0L) return
        startUptimeMs = SystemClock.uptimeMillis()
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

            override fun onActivityStarted(activity: Activity) = Unit

            override fun onActivityResumed(activity: Activity) {
                if (recorded) return
                recorded = true
                // 下一帧回调触发时，首帧已经提交给合成器，近似「首帧绘制完成」。
                Choreographer.getInstance().postFrameCallback {
                    val elapsed = SystemClock.uptimeMillis() - startUptimeMs
                    runCatching { activity.reportFullyDrawn() }
                    record(app, elapsed)
                }
            }

            override fun onActivityPaused(activity: Activity) = Unit

            override fun onActivityStopped(activity: Activity) = Unit

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    /** 读取 `startup.log` 最近若干行（磁盘 IO，须在后台线程调用）。 */
    fun tail(limit: Int = 5): List<String> {
        val file = File(File(appFilesDir ?: return emptyList(), DIR_NAME), FILE_NAME)
        if (!file.isFile) return emptyList()
        return runCatching { file.readLines().takeLast(limit) }.getOrDefault(emptyList())
    }

    /** 由 Application 提供 filesDir，供诊断页导出报告时复用。 */
    @Volatile
    var appFilesDir: File? = null

    private fun record(app: Application, elapsedMs: Long) {
        val sample = Sample(
            atMs = System.currentTimeMillis(),
            coldStartMs = elapsedMs,
            device = android.os.Build.MODEL ?: "",
            sdk = android.os.Build.VERSION.SDK_INT,
        )
        lastSample = sample
        appFilesDir = app.filesDir
        // 写盘放到后台线程，避免 Debug 的 StrictMode 把这次埋点自己记成主线程 IO。
        Thread {
            runCatching {
                val dir = File(app.filesDir, DIR_NAME).apply { mkdirs() }
                val file = File(dir, FILE_NAME)
                val lines = (if (file.isFile) file.readLines() else emptyList()).toMutableList()
                lines += format(sample)
                while (lines.size > MAX_LINES) lines.removeAt(0)
                file.writeText(lines.joinToString("\n", postfix = "\n"))
            }.onFailure { Log.w(TAG, "写入 startup.log 失败", it) }
        }.start()
        if (BuildConfig.DEBUG) Log.d(TAG, "cold start ${elapsedMs}ms")
    }

    private fun format(sample: Sample): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date(sample.atMs))
        return "$stamp cold_start_ms=${sample.coldStartMs} sdk=${sample.sdk} device=${sample.device}"
    }
}
