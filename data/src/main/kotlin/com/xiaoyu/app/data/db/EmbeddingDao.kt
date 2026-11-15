package com.xiaoyu.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * 本地向量索引（M7-10）。只服务于“可选”的语义召回：
 * 没有 embedding 模型时不会有任何写入，所有查询自然返回空列表。
 */
@Dao
interface EmbeddingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(embedding: EmbeddingEntity): Long

    @Query("SELECT * FROM embeddings")
    suspend fun all(): List<EmbeddingEntity>

    @Query("SELECT * FROM embeddings WHERE model = :model")
    suspend fun byModel(model: String): List<EmbeddingEntity>

    @Query("SELECT * FROM embeddings WHERE memory_id = :memoryId")
    suspend fun byMemory(memoryId: Long): List<EmbeddingEntity>

    @Query("DELETE FROM embeddings WHERE memory_id = :memoryId")
    suspend fun deleteByMemory(memoryId: Long)

    @Query("DELETE FROM embeddings")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM embeddings")
    suspend fun count(): Int
}
