package com.xiaoyu.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * 表情包归档专用 DAO（M7-07）。
 * 单独放一张 DAO，避免改 StickerRepository 的既有契约；
 * 只做“整包”级别的读取与追加，不提供任何删除表情的入口。
 */
@Dao
interface StickerArchiveDao {

    @Query("SELECT * FROM sticker_packs ORDER BY sort ASC, id ASC")
    suspend fun packs(): List<StickerPackEntity>

    @Query("SELECT * FROM sticker_packs WHERE id = :id")
    suspend fun pack(id: String): StickerPackEntity?

    @Query("SELECT * FROM sticker_packs WHERE name = :name LIMIT 1")
    suspend fun packByName(name: String): StickerPackEntity?

    @Query("SELECT * FROM stickers WHERE pack_id = :packId ORDER BY sort ASC, id ASC")
    suspend fun stickers(packId: String): List<StickerEntity>

    @Query("SELECT MAX(sort) FROM sticker_packs")
    suspend fun maxPackSort(): Int?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPack(pack: StickerPackEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertStickers(stickers: List<StickerEntity>)
}
