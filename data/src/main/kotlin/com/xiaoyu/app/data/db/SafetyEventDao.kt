package com.xiaoyu.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/**
 * 安全事件 DAO（v3 §8.1 / F-05）。
 * 只读写级别、命中词与动作，不存用户原文。
 */
@Dao
interface SafetyEventDao {

    @Insert
    suspend fun insert(event: SafetyEventEntity): Long

    /** 最近的同身份、同级别事件，用于「L3 之后 3 轮保持现实导向」。 */
    @Query(
        """
        SELECT * FROM safety_events
        WHERE identity_id = :identityId AND level = :level
        ORDER BY created_at_ms DESC, id DESC LIMIT 1
        """
    )
    suspend fun latest(identityId: Long, level: String): SafetyEventEntity?

    @Query("SELECT COUNT(*) FROM safety_events WHERE identity_id = :identityId AND level = :level")
    suspend fun count(identityId: Long, level: String): Int

    @Query("SELECT * FROM safety_events ORDER BY created_at_ms DESC, id DESC LIMIT :limit")
    suspend fun latestAll(limit: Int = 50): List<SafetyEventEntity>
}
