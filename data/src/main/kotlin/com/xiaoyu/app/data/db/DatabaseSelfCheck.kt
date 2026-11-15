package com.xiaoyu.app.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 启动期数据库自检（执行书 M5-05 / 附录 B `DB_CORRUPT`）。
 *
 * 在 Room 打开数据库之前执行 `PRAGMA quick_check`：
 * 若数据库损坏，先把原文件改名保留（绝不直接删除），再让 Room 重建空库，
 * 同时落一条可读提示，引导用户从备份恢复。
 */
object DatabaseSelfCheck {
    private const val TAG = "DatabaseSelfCheck"
    private const val DB_NAME = "xiaoyu.db"
    private const val NOTICE_NAME = "db_recovery_notice.txt"
    private const val QUARANTINE_PREFIX = "xiaoyu-corrupt-"

    data class Report(
        val healthy: Boolean,
        /** 损坏库改名后的文件，未损坏时为 null。 */
        val quarantined: File?,
        /** 给用户看的恢复引导文案。 */
        val message: String?,
    )

    /** 在 Application.onCreate 中、Room 首次打开前调用。 */
    fun run(context: Context): Report {
        val dbFile = context.getDatabasePath(DB_NAME)
        if (!dbFile.exists() || dbFile.length() <= 0L) return Report(true, null, null)
        val problem = inspect(dbFile) ?: return Report(true, null, null)

        val quarantined = quarantine(dbFile)
        val message = if (quarantined != null) {
            "数据库文件损坏（$problem）。原文件已保留为 ${quarantined.name}，已重建空库。" +
                "可从备份恢复：设置 → 数据管理 → 恢复。"
        } else {
            "数据库文件损坏（$problem），且无法改名保留。请从备份恢复：设置 → 数据管理 → 恢复。"
        }
        runCatching { File(context.filesDir, NOTICE_NAME).writeText(message) }
        Log.e(TAG, message)
        return Report(false, quarantined, message)
    }

    /** 读取上次自检留下的恢复提示；没有异常时返回 null。 */
    fun pendingNotice(context: Context): String? = runCatching {
        File(context.filesDir, NOTICE_NAME).takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() }
    }.getOrNull()

    /** 用户确认已处理后清除提示。 */
    fun clearNotice(context: Context) {
        runCatching { File(context.filesDir, NOTICE_NAME).delete() }
    }

    /** 返回问题描述；数据库健康返回 null。 */
    private fun inspect(file: File): String? = runCatching {
        // 用读写模式打开，让 SQLite 能完成 WAL 恢复；只读模式在 -shm 缺失时会误报。
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { database ->
            val result = database.rawQuery("PRAGMA quick_check", null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
            if (result == null || !result.equals("ok", ignoreCase = true)) "quick_check=$result" else null
        }
    }.getOrElse { "无法打开：${it.message ?: it.javaClass.simpleName}" }

    /** 把损坏库与 WAL/SHM 一起改名保留，返回改名后的主文件。 */
    private fun quarantine(dbFile: File): File? {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.CHINA).format(Date())
        val target = File(dbFile.parentFile, "$QUARANTINE_PREFIX$stamp.db")
        val moved = runCatching { dbFile.renameTo(target) }.getOrDefault(false)
        if (!moved) {
            Log.e(TAG, "损坏数据库改名失败：${dbFile.absolutePath}")
            return null
        }
        listOf("-wal", "-shm").forEach { suffix ->
            runCatching {
                val sidecar = File(dbFile.path + suffix)
                if (sidecar.exists()) sidecar.renameTo(File(target.path + suffix))
            }
        }
        Log.w(TAG, "损坏数据库已保留：${target.absolutePath}")
        return target
    }
}
