package com.xiaoyu.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 自定义聊天背景 DAO（执行书 v3 §14.6.2 / G-11）。
 * 一身份一行，`identity_id` 唯一；读写都只碰本表，不动旧 settings 键。
 */
@Dao
interface ChatBackgroundDao {

    @Query("SELECT * FROM chat_backgrounds WHERE identity_id = :identityId LIMIT 1")
    suspend fun byIdentity(identityId: Long): ChatBackgroundEntity?

    @Query("SELECT * FROM chat_backgrounds WHERE identity_id = :identityId LIMIT 1")
    fun observe(identityId: Long): Flow<ChatBackgroundEntity?>

    @Query("SELECT * FROM chat_backgrounds ORDER BY identity_id ASC")
    suspend fun all(): List<ChatBackgroundEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ChatBackgroundEntity): Long

    @Query("DELETE FROM chat_backgrounds WHERE identity_id = :identityId")
    suspend fun delete(identityId: Long)

    /** 当前所有身份引用的原图 sha1，用于存储配额淘汰时保留在用的图片。 */
    @Query("SELECT DISTINCT image_sha1 FROM chat_backgrounds WHERE image_sha1 IS NOT NULL")
    suspend fun usedImages(): List<String>
}
