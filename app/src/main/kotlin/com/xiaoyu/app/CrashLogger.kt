package com.xiaoyu.app

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 本地崩溃日志（执行书 M5-05 / ADR-09：不接 Sentry/Firebase，R10 隐私红线）。
 *
 * 日志落在 `filesDir/crash/`，只保留最近 10 个；安装时链式调用系统原有 Handler，
 * 确保记完日志后仍按系统默认流程终止进程（不吞崩溃）。
 */
object CrashLogger {
    private const val TAG = "CrashLogger"
    private const val MAX_FILES = 10
    private const val DIR_NAME = "crash"

    @Volatile
    private var installed = false

    /** 在 Application.onCreate 最早处调用一次。 */
    fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { write(app, thread, throwable) }
                .onFailure { Log.e(TAG, "写入崩溃日志失败", it) }
            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
                kotlin.system.exitProcess(10)
            }
        }
    }

    fun logDir(context: Context): File = File(context.filesDir, DIR_NAME).apply { mkdirs() }

    /** 崩溃日志列表，按时间新的在前。 */
    fun list(context: Context): List<File> =
        logDir(context).listFiles { file -> file.isFile && file.extension == "log" }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    fun latest(context: Context): File? = list(context).firstOrNull()

    fun read(context: Context, file: File): String? = runCatching { file.readText() }.getOrNull()

    /** 清空全部崩溃日志（设置 → 诊断 → 清空诊断数据）。 */
    fun clear(context: Context) {
        list(context).forEach { runCatching { it.delete() } }
    }

    private fun write(context: Context, thread: Thread, throwable: Throwable) {
        val dir = logDir(context)
        val now = Date()
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.CHINA).format(now)
        val target = File(dir, "crash-$stamp.log")
        target.writeText(buildReport(context, thread, throwable, now))
        list(context).drop(MAX_FILES).forEach { runCatching { it.delete() } }
        Log.e(TAG, "崩溃日志已写入：${target.absolutePath}", throwable)
    }

    private fun buildReport(context: Context, thread: Thread, throwable: Throwable, now: Date): String {
        val stack = StringWriter().also { writer ->
            throwable.printStackTrace(PrintWriter(writer))
        }.toString()
        return buildString {
            appendLine("时间：${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(now)}")
            appendLine("应用：${versionName(context)}")
            appendLine("设备：${Build.MANUFACTURER} ${Build.MODEL}，Android ${Build.VERSION.RELEASE}（API ${Build.VERSION.SDK_INT}）")
            appendLine("线程：${thread.name}（id=${thread.id}）")
            appendLine()
            appendLine(stack)
        }
    }

    @Suppress("DEPRECATION")
    private fun versionName(context: Context): String = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()
        "${info.versionName}（$code）"
    }.getOrDefault("unknown")
}
