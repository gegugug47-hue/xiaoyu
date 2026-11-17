package com.xiaoyu.app.perf

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.xiaoyu.app.BuildConfig
import java.io.File

/**
 * 消息表索引护栏（执行书 v3 §1.5 / C-01「50 万条不降级」的可测部分）。
 *
 * 只做两件确定的事：
 * 1. Debug 启动时断言 `messages` 的分页/排序关键索引存在；
 * 2. 用 `EXPLAIN QUERY PLAN` 验证分页 SQL 走索引。
 *
 * 目前**没有**做 50 万条真实数据的压测（见审计文档 C-01），这里不是性能结论，
 * 只是防止后续把索引改坏的护栏。
 */
object DbIndexProbe {

    private const val TAG = "PerfIndex"
    private const val DB_NAME = "xiaoyu.db"

    private val REQUIRED_INDEXES = listOf(
        "index_messages_session_id",
        "index_messages_session_id_created_at",
    )

    private const val PAGE_QUERY_PLAN =
        "EXPLAIN QUERY PLAN SELECT * FROM messages " +
            "WHERE session_id = 0 AND hidden = 0 " +
            "ORDER BY created_at ASC, id ASC LIMIT 30 OFFSET 0"

    /** 仅在 Debug 构建、且数据库文件已存在时执行；失败只记日志，不影响启动。 */
    fun verify(context: Context) {
        if (!BuildConfig.DEBUG) return
        val dbFile: File = context.getDatabasePath(DB_NAME)
        if (!dbFile.isFile) return
        Thread {
            runCatching {
                SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY)
                    .use { db ->
                        val names = mutableSetOf<String>()
                        db.rawQuery("PRAGMA index_list('messages')", null).use { cursor ->
                            val index = cursor.getColumnIndexOrThrow("name")
                            while (cursor.moveToNext()) names += cursor.getString(index)
                        }
                        val missing = REQUIRED_INDEXES.filterNot { it in names }
                        if (missing.isEmpty()) {
                            Log.d(TAG, "messages 索引齐全：$REQUIRED_INDEXES")
                        } else {
                            Log.e(TAG, "messages 缺少索引：$missing（分页可能全表扫描）")
                        }
                        val plan = StringBuilder()
                        db.rawQuery(PAGE_QUERY_PLAN, null).use { cursor ->
                            val detail = cursor.getColumnIndex("detail")
                            while (cursor.moveToNext()) {
                                if (detail >= 0) plan.append(cursor.getString(detail)).append(" | ")
                            }
                        }
                        if (!plan.contains("USING INDEX", ignoreCase = true)) {
                            Log.w(TAG, "分页查询未命中索引：$plan")
                        }
                    }
            }.onFailure { Log.w(TAG, "索引自检失败", it) }
        }.start()
    }
}
