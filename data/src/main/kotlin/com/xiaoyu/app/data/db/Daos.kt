package com.xiaoyu.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Insert
    suspend fun insert(session: SessionEntity): Long

    @Update
    suspend fun update(session: SessionEntity)

    @Query("SELECT * FROM sessions WHERE archived = 0 ORDER BY last_message_at DESC")
    fun observeActive(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE archived = 1 ORDER BY last_message_at DESC")
    fun observeArchived(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun byId(id: Long): SessionEntity?

    @Query("SELECT * FROM sessions WHERE id = :id")
    fun observeById(id: Long): Flow<SessionEntity?>

    @Query("SELECT * FROM sessions WHERE archived = 0 ORDER BY last_message_at DESC LIMIT 1")
    suspend fun latestActive(): SessionEntity?

    @Query("SELECT * FROM sessions WHERE archived = 0 AND ended_at IS NOT NULL AND extracted_state != 'done'")
    suspend fun sessionsNeedingExtraction(): List<SessionEntity>

    @Query("SELECT * FROM sessions WHERE archived = 0 AND ended_at IS NULL")
    suspend fun openSessions(): List<SessionEntity>

    @Query("UPDATE sessions SET ended_at = :at WHERE id = :id AND ended_at IS NULL")
    suspend fun markEnded(id: Long, at: Long)

    @Query("UPDATE sessions SET extracted_state = :state, extract_attempts = extract_attempts + 1 WHERE id = :id")
    suspend fun setExtractState(id: Long, state: String)

    /** 提炼失败（网络/解析）：只累计失败次数，供调用方做重试上限判定。 */
    @Query("UPDATE sessions SET extracted_state = 'failed', extract_attempts = extract_attempts + 1 WHERE id = :id")
    suspend fun markExtractFailed(id: Long)

    /** 提炼完成：状态落定并清零失败计数。 */
    @Query("UPDATE sessions SET extracted_state = 'done', extract_attempts = 0 WHERE id = :id")
    suspend fun markExtractDone(id: Long)

    @Query("UPDATE sessions SET title = :title WHERE id = :id")
    suspend fun rename(id: Long, title: String)

    @Query("UPDATE sessions SET archived = 1 WHERE id = :id")
    suspend fun archive(id: Long)

    @Query("UPDATE sessions SET archived = 0, ended_at = NULL WHERE id = :id")
    suspend fun unarchive(id: Long)

    @Query("UPDATE sessions SET style_id = :styleId WHERE id = :id")
    suspend fun setStyle(id: Long, styleId: Long)

    /** 会话内切换人设：之后这个对话的回复都按新人设生成。 */
    @Query("UPDATE sessions SET identity_id = :identityId WHERE id = :id")
    suspend fun setIdentity(id: Long, identityId: Long)

    @Query("UPDATE sessions SET last_message_at = :at WHERE id = :id")
    suspend fun touch(id: Long, at: Long)

    /** v3 H-14：增量提炼游标前移到指定时间戳。 */
    @Query("UPDATE sessions SET extract_cursor = :cursor WHERE id = :id")
    suspend fun updateExtractCursor(id: Long, cursor: Long)

    @Query("UPDATE sessions SET archived = 0, ended_at = NULL, extracted_state = 'pending' WHERE id = :id")
    suspend fun reopen(id: Long)

    @Query("SELECT MIN(created_at) FROM sessions")
    suspend fun earliestCreatedAt(): Long?

    /** v3 §8.2：标记会话关闭原因（危机时只标记，不结束对话）。 */
    @Query("UPDATE sessions SET closed_by = :reason WHERE id = :id")
    suspend fun markClosedBy(id: Long, reason: String)
}

@Dao
interface MessageDao {
    @Insert
    suspend fun insert(message: MessageEntity): Long

    @Query("SELECT * FROM messages WHERE session_id = :sessionId AND hidden = 0 ORDER BY created_at ASC, id ASC")
    fun observeVisible(sessionId: Long): Flow<List<MessageEntity>>

    /** 连续流模式：跨所有会话按时间展示全部可见消息（PRD 4.2.1）。 */
    @Query("SELECT * FROM messages WHERE hidden = 0 ORDER BY created_at ASC, id ASC")
    fun observeAllVisible(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE session_id = :sessionId AND hidden = 0 ORDER BY created_at ASC, id ASC")
    suspend fun visibleForSession(sessionId: Long): List<MessageEntity>

    /**
     * 记忆提炼用：撤回的消息不再参与提炼。
     * 不能直接改 [visibleForSession]——聊天历史与导出仍要展示「你撤回了一条消息」占位。
     */
    @Query("SELECT * FROM messages WHERE session_id = :sessionId AND hidden = 0 AND retracted = 0 ORDER BY created_at ASC, id ASC")
    suspend fun visibleForExtraction(sessionId: Long): List<MessageEntity>

    /** 某条用户消息之后是否已经落下她的回复（只算可见消息）。 */
    @Query("SELECT COUNT(*) FROM messages WHERE session_id = :sessionId AND role = 'assistant' AND hidden = 0 AND created_at >= :since")
    suspend fun countAssistantSince(sessionId: Long, since: Long): Int

    /** v3 H-14：统计游标之后的可见消息数，用于增量提炼触发判定。 */
    @Query("SELECT COUNT(*) FROM messages WHERE session_id = :sessionId AND hidden = 0 AND retracted = 0 AND created_at > :cursor")
    suspend fun countSince(sessionId: Long, cursor: Long): Int

    @Query("SELECT * FROM messages WHERE session_id = :sessionId AND hidden = 0 AND retracted = 0 ORDER BY created_at DESC, id DESC LIMIT :limit")
    suspend fun latestForSession(sessionId: Long, limit: Int): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE session_id = :sessionId AND hidden = 0 ORDER BY created_at ASC, id ASC LIMIT :limit OFFSET :offset")
    suspend fun pageForSession(sessionId: Long, limit: Int, offset: Int): List<MessageEntity>

    @Query("UPDATE messages SET hidden = 1 WHERE id = :id")
    suspend fun hide(id: Long)

    @Query("UPDATE messages SET hidden = 1 WHERE id IN (:ids)")
    suspend fun hideBatch(ids: List<Long>)

    @Query("SELECT * FROM messages WHERE id IN (:ids) AND hidden = 0 ORDER BY created_at ASC, id ASC")
    suspend fun byIds(ids: List<Long>): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun byId(id: Long): MessageEntity?

    @Query("UPDATE messages SET reactions_json = :json WHERE id = :id")
    suspend fun setReactions(id: Long, json: String)

    @Query("SELECT COUNT(*) FROM messages WHERE session_id = :sessionId AND hidden = 0")
    suspend fun countVisible(sessionId: Long): Int

    @Query("SELECT * FROM messages WHERE hidden = 0 AND content LIKE '%' || :keyword || '%' ORDER BY created_at DESC LIMIT :limit")
    suspend fun search(keyword: String, limit: Int = 30): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE hidden = 0 AND retracted = 0 AND created_at >= :since ORDER BY created_at ASC LIMIT 300")
    suspend fun since(since: Long): List<MessageEntity>

    /** 所有带图片的可见消息（PRD US-018 图库）。 */
    @Query("SELECT * FROM messages WHERE hidden = 0 AND image_uri IS NOT NULL ORDER BY created_at DESC")
    fun observeImages(): Flow<List<MessageEntity>>

    @Query("SELECT COUNT(*) FROM messages WHERE hidden = 0")
    suspend fun countAllVisible(): Int

    @Query("SELECT MIN(created_at) FROM messages WHERE hidden = 0")
    suspend fun earliestVisibleAt(): Long?

    // ---- 消息状态机（M1-09）：sending / sent / failed ----
    @Query("UPDATE messages SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    // ---- 编辑重发（M3-07）：改正文 + 标记 edited_at ----
    @Query("UPDATE messages SET content = :content, edited_at = :editedAt, status = 'sent' WHERE id = :id")
    suspend fun updateContent(id: Long, content: String, editedAt: Long)

    // ---- 重发 / 编辑重发（M3-07）：复用的用户消息要重新排到时间线末尾 ----
    @Query("UPDATE messages SET created_at = :createdAt WHERE id = :id")
    suspend fun updateCreatedAt(id: Long, createdAt: Long)

    // ---- 撤回（M3-07）：只对自己的消息有效，标记后正文一并清空（软删，不物理删除） ----
    @Query("UPDATE messages SET retracted = 1, content = '' WHERE id = :id AND role = 'user'")
    suspend fun retract(id: Long)

    // ---- 重新生成的历史版本（M3-06）----
    /**
     * 同一版本家族（version_family_id）的全部轮次消息，含 hidden=1。
     * 兼容旧数据：早期重新生成复用 reply_group_id 作家族 id，因此也按它匹配。
     * 每一轮的分段共享 reply_group_id，调用方按「轮」聚合后再展示。
     */
    @Query(
        """
        SELECT * FROM messages
        WHERE (version_family_id = :family OR reply_group_id = :family) AND role = 'assistant'
        ORDER BY created_at ASC, id ASC
        """
    )
    suspend fun assistantVersions(family: String): List<MessageEntity>

    /** 给历史回复及其同轮分段补建版本族，供旧数据重新生成后切换版本。 */
    @Query(
        """
        UPDATE messages SET version_family_id = :familyId
        WHERE version_family_id IS NULL
          AND (id = :messageId OR (:replyGroupId IS NOT NULL AND reply_group_id = :replyGroupId))
        """
    )
    suspend fun assignVersionFamily(messageId: Long, replyGroupId: String?, familyId: String)

    /** 切换版本时先整族收起旧版本（只改 hidden，不删数据）。 */
    @Query("UPDATE messages SET hidden = 1 WHERE version_family_id = :family OR reply_group_id = :family")
    suspend fun hideFamily(family: String)

    /** 放亮某一轮的全部消息（含同轮的表情分段）。 */
    @Query("UPDATE messages SET hidden = 0 WHERE reply_group_id = :roundId")
    suspend fun unhideRound(roundId: String)

    /** 某一版本族里当前可见的消息（用于重新生成失败时恢复上一条有效回复）。 */
    @Query("SELECT * FROM messages WHERE (version_family_id = :family OR reply_group_id = :family) AND hidden = 0")
    suspend fun visibleInFamily(family: String): List<MessageEntity>

    /** 整轮收起（拦截重试 / 重新生成回滚用）。 */
    @Query("UPDATE messages SET hidden = 1 WHERE reply_group_id = :groupId")
    suspend fun hideGroup(groupId: String)

    @Query("UPDATE messages SET hidden = 0 WHERE id = :id")
    suspend fun unhide(id: Long)

    /** v3 §8.2：本会话是否已经出现过求助资源卡（会话内只弹一次）。 */
    @Query("SELECT COUNT(*) FROM messages WHERE session_id = :sessionId AND hidden = 0 AND type = 'card' AND content LIKE '%\"CARE\"%'")
    suspend fun countCareCards(sessionId: Long): Int

    /** v3 §8.2：某个时间点之后本会话的用户消息数（用于 L3 后 3 轮现实导向）。 */
    @Query("SELECT COUNT(*) FROM messages WHERE session_id = :sessionId AND hidden = 0 AND role = 'user' AND created_at >= :since")
    suspend fun countUserSince(sessionId: Long, since: Long): Int

    // ---- 全文索引（M7-04）：写入时维护，查询时按二元组 MATCH ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFts(index: MessageFtsEntity)

    @Query("DELETE FROM message_fts WHERE rowid = :messageId")
    suspend fun deleteFts(messageId: Long)

    /**
     * FTS 搜索：会话 / 类型 / 时间区间都可为空（为空表示不过滤）。
     * `query` 必须是已经拼好的 MATCH 表达式（二元组 OR 组合），见 MessageSearchIndex。
     */
    @Query(
        """
        SELECT m.* FROM messages m
        INNER JOIN message_fts ON message_fts.rowid = m.id
        WHERE message_fts MATCH :query AND m.hidden = 0 AND m.retracted = 0
          AND (:sessionId IS NULL OR m.session_id = :sessionId)
          AND (:type IS NULL OR m.type = :type)
          AND (:since IS NULL OR m.created_at >= :since)
          AND (:until IS NULL OR m.created_at <= :until)
        ORDER BY m.created_at DESC, m.id DESC
        LIMIT :limit
        """
    )
    suspend fun searchFts(
        query: String,
        sessionId: Long?,
        type: String?,
        since: Long?,
        until: Long?,
        limit: Int,
    ): List<MessageEntity>

    /** 尚未建索引的可见消息，供首次搜索时补齐历史（M7-04 Day-1 索引兜底）。 */
    @Query(
        """
        SELECT m.* FROM messages m
        LEFT JOIN message_fts ON message_fts.rowid = m.id
        WHERE m.hidden = 0 AND message_fts.rowid IS NULL
        ORDER BY m.id ASC
        LIMIT :limit
        """
    )
    suspend fun messagesWithoutIndex(limit: Int): List<MessageEntity>

    /** 小组件展示用：她最近一句说出口的话（M7-08）。 */
    @Query(
        """
        SELECT * FROM messages
        WHERE hidden = 0 AND retracted = 0 AND role = 'assistant' AND type = 'text' AND content != ''
        ORDER BY created_at DESC, id DESC LIMIT 1
        """
    )
    suspend fun latestAssistantMessage(): MessageEntity?

    /** 本会话她最近一句说出口的话：用于避免连续两次直出同一句晚安。 */
    @Query(
        """
        SELECT * FROM messages
        WHERE session_id = :sessionId AND hidden = 0 AND retracted = 0
          AND role = 'assistant' AND type = 'text' AND content != ''
        ORDER BY created_at DESC, id DESC LIMIT 1
        """
    )
    suspend fun latestAssistantForSession(sessionId: Long): MessageEntity?
}

@Dao
interface IdentityDao {
    @Insert
    suspend fun insert(identity: IdentityEntity): Long

    @Update
    suspend fun update(identity: IdentityEntity)

    @Query("SELECT * FROM identities ORDER BY created_at ASC")
    fun observeAll(): Flow<List<IdentityEntity>>

    @Query("SELECT * FROM identities ORDER BY created_at ASC")
    suspend fun all(): List<IdentityEntity>

    @Query("SELECT * FROM identities WHERE id = :id")
    suspend fun byId(id: Long): IdentityEntity?

    @Query("SELECT * FROM identities WHERE preset_key = :key LIMIT 1")
    suspend fun byPresetKey(key: String): IdentityEntity?

    @Query("DELETE FROM identities WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM identities")
    suspend fun count(): Int
}

@Dao
interface StyleDao {
    @Insert
    suspend fun insert(style: StyleEntity): Long

    @Update
    suspend fun update(style: StyleEntity)

    @Query("SELECT * FROM styles ORDER BY created_at ASC")
    fun observeAll(): Flow<List<StyleEntity>>

    @Query("SELECT * FROM styles ORDER BY created_at ASC")
    suspend fun all(): List<StyleEntity>

    @Query("SELECT * FROM styles WHERE id = :id")
    suspend fun byId(id: Long): StyleEntity?

    @Query("SELECT * FROM styles WHERE preset_key = :key LIMIT 1")
    suspend fun byPresetKey(key: String): StyleEntity?

    @Query("DELETE FROM styles WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM styles")
    suspend fun count(): Int
}

@Dao
interface MemoryDao {
    @Insert
    suspend fun insert(memory: MemoryEntity): Long

    @Insert
    suspend fun insertAll(memories: List<MemoryEntity>): List<Long>

    @Update
    suspend fun update(memory: MemoryEntity)

    @Query("SELECT * FROM memories WHERE tier = 'SHORT' AND promoted_to_card_id IS NULL ORDER BY created_at DESC")
    suspend fun shortTerm(): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE tier = 'SHORT' AND promoted_to_card_id IS NULL ORDER BY created_at DESC")
    fun observeShortTerm(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE tier = 'BIN' ORDER BY bin_at DESC")
    fun observeBin(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE tier = 'BIN' ORDER BY bin_at DESC")
    suspend fun bin(): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE tier = 'SHORT' AND promoted_to_card_id IS NULL AND tags LIKE '%' || :tag || '%'")
    suspend fun byTagLike(tag: String): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE content = :content LIMIT 1")
    suspend fun findByContent(content: String): MemoryEntity?

    @Query("SELECT * FROM memories WHERE id = :id")
    suspend fun byId(id: Long): MemoryEntity?

    @Query("SELECT * FROM memories WHERE id IN (:ids)")
    suspend fun byIds(ids: List<Long>): List<MemoryEntity>

    @Query("UPDATE memories SET strength = :strength, review_count = :reviewCount, last_access = :at WHERE id = :id")
    suspend fun updateStrength(id: Long, strength: Int, reviewCount: Int, at: Long)

    @Query("UPDATE memories SET strength = :strength WHERE id = :id")
    suspend fun setStrength(id: Long, strength: Int)

    @Query("UPDATE memories SET tier = 'BIN', bin_at = :at WHERE id = :id")
    suspend fun moveToBin(id: Long, at: Long)

    @Query("UPDATE memories SET tier = 'SHORT', bin_at = NULL, strength = :strength, last_access = :at WHERE id = :id")
    suspend fun restore(id: Long, strength: Int, at: Long)

    @Query("UPDATE memories SET promoted_to_card_id = :cardId WHERE id IN (:ids)")
    suspend fun markPromoted(ids: List<Long>, cardId: Long)

    @Query("DELETE FROM memories WHERE id IN (:ids)")
    suspend fun purge(ids: List<Long>)

    @Query("UPDATE memories SET content = :content, type = :type, tags = :tags, importance = :importance WHERE id = :id")
    suspend fun edit(id: Long, content: String, type: String, tags: String, importance: Int)

    @Query("SELECT COUNT(*) FROM memories WHERE tier = 'SHORT' AND created_at >= :since")
    suspend fun countCreatedSince(since: Long): Int

    @Query("SELECT * FROM memories WHERE created_at BETWEEN :start AND :end ORDER BY created_at ASC LIMIT 30")
    suspend fun createdBetween(start: Long, end: Long): List<MemoryEntity>
}

@Dao
interface FactCardDao {
    @Insert
    suspend fun insert(card: FactCardEntity): Long

    @Update
    suspend fun update(card: FactCardEntity)

    @Query("SELECT * FROM fact_cards ORDER BY updated_at DESC")
    suspend fun all(): List<FactCardEntity>

    @Query("SELECT * FROM fact_cards ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<FactCardEntity>>

    @Query("SELECT * FROM fact_cards WHERE id = :id")
    suspend fun byId(id: Long): FactCardEntity?

    @Query("SELECT * FROM fact_cards WHERE content = :content LIMIT 1")
    suspend fun findByContent(content: String): FactCardEntity?

    @Query("DELETE FROM fact_cards WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM fact_cards")
    suspend fun count(): Int
}

@Dao
interface DiaryDao {
    @Insert
    suspend fun insert(diary: DiaryEntity): Long

    @Update
    suspend fun update(diary: DiaryEntity)

    @Query("SELECT * FROM diaries ORDER BY event_date DESC")
    fun observeAll(): Flow<List<DiaryEntity>>

    @Query("SELECT * FROM diaries ORDER BY event_date DESC LIMIT 1")
    suspend fun latest(): DiaryEntity?

    @Query("SELECT * FROM diaries WHERE event_date = :date LIMIT 1")
    suspend fun byDate(date: String): DiaryEntity?

    @Query("SELECT * FROM diaries WHERE id = :id")
    suspend fun byId(id: Long): DiaryEntity?

    @Query("UPDATE diaries SET unlocked = 1 WHERE id = :id")
    suspend fun unlock(id: Long)

    @Query("UPDATE diaries SET read_at = :at WHERE id = :id")
    suspend fun markRead(id: Long, at: Long)

    @Query("SELECT * FROM diaries WHERE is_secret = 1 AND unlocked = 0 ORDER BY created_at DESC LIMIT 1")
    suspend fun latestLockedSecret(): DiaryEntity?

    @Query("SELECT COUNT(*) FROM diaries")
    suspend fun count(): Int

    @Query("SELECT MIN(created_at) FROM diaries")
    suspend fun earliestCreatedAt(): Long?
}

@Dao
interface MoodDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(mood: MoodEntity)

    @Query("SELECT * FROM mood_states WHERE date = :date")
    suspend fun byDate(date: String): MoodEntity?

    @Query("SELECT * FROM mood_states ORDER BY date DESC LIMIT 1")
    suspend fun latest(): MoodEntity?

    @Query("SELECT * FROM mood_states ORDER BY date DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<MoodEntity>

    @Query("SELECT * FROM mood_states ORDER BY date DESC LIMIT 1")
    fun observeLatest(): Flow<MoodEntity?>
}

@Dao
interface FavorabilityDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(favorability: FavorabilityEntity)

    @Query("SELECT * FROM favorability WHERE id = 1")
    suspend fun get(): FavorabilityEntity?

    @Query("SELECT * FROM favorability WHERE id = 1")
    fun observe(): Flow<FavorabilityEntity?>
}

@Dao
interface LifeFragmentDao {
    @Insert
    suspend fun insert(fragment: LifeFragmentEntity): Long

    @Insert
    suspend fun insertAll(fragments: List<LifeFragmentEntity>): List<Long>

    @Query("SELECT * FROM life_fragments WHERE used = 0 AND expire_at > :now ORDER BY created_at DESC")
    suspend fun fresh(now: Long): List<LifeFragmentEntity>

    @Query("SELECT * FROM life_fragments WHERE expire_at > :now ORDER BY used ASC, created_at DESC LIMIT :limit")
    suspend fun recent(now: Long, limit: Int): List<LifeFragmentEntity>

    /** v3 H-12 / 7.6：观察指定时间之后的生活碎片，供「她的一天」时间线实时更新。 */
    @Query("SELECT * FROM life_fragments WHERE created_at >= :since ORDER BY created_at DESC")
    fun observeSince(since: Long): kotlinx.coroutines.flow.Flow<List<LifeFragmentEntity>>

    @Query("SELECT * FROM life_fragments WHERE id = :id")
    suspend fun byId(id: Long): LifeFragmentEntity?

    @Query("UPDATE life_fragments SET used = 1 WHERE id = :id")
    suspend fun markUsed(id: Long)

    @Query("UPDATE life_fragments SET used = 1 WHERE id IN (:ids)")
    suspend fun markUsed(ids: List<Long>)

    @Query("DELETE FROM life_fragments WHERE expire_at <= :now")
    suspend fun purgeExpired(now: Long)
}

/** v3 H-9 / 4.10.3：情绪归因事件 DAO。 */
@Dao
interface EmotionEventDao {
    @Insert
    suspend fun insert(event: EmotionEventEntity): Long

    @Query("SELECT * FROM emotion_events ORDER BY created_at DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<EmotionEventEntity>

    @Query("SELECT * FROM emotion_events WHERE emotion = :emotion ORDER BY created_at DESC LIMIT :limit")
    suspend fun byEmotion(emotion: String, limit: Int): List<EmotionEventEntity>

    @Query("UPDATE emotion_events SET last_mention_at = :at WHERE id = :id")
    suspend fun touch(id: Long, at: Long)

    @Query("DELETE FROM emotion_events WHERE created_at < :before")
    suspend fun purgeOlderThan(before: Long)
}

/** v3 H-11 / 4.11：纠偏记录 DAO。 */
@Dao
interface CorrectionDao {
    @Insert
    suspend fun insert(correction: CorrectionEntity): Long

    @Query("SELECT * FROM corrections WHERE revoked = 0 ORDER BY created_at DESC LIMIT 1")
    suspend fun latestActive(): CorrectionEntity?

    /** v3：观察最近一条未撤销的纠偏，供 UI 展示纠偏条。 */
    @Query("SELECT * FROM corrections WHERE revoked = 0 ORDER BY created_at DESC LIMIT 1")
    fun observeLatestActive(): kotlinx.coroutines.flow.Flow<CorrectionEntity?>

    @Query("SELECT * FROM corrections ORDER BY created_at DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<CorrectionEntity>

    @Query("UPDATE corrections SET revoked = 1 WHERE id = :id")
    suspend fun revoke(id: Long)
}

@Dao
interface StickerDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPack(pack: StickerPackEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertStickers(stickers: List<StickerEntity>)

    @Query("SELECT * FROM sticker_packs ORDER BY sort ASC, id ASC")
    fun observePacks(): Flow<List<StickerPackEntity>>

    @Query("SELECT * FROM sticker_packs ORDER BY sort ASC, id ASC")
    suspend fun packs(): List<StickerPackEntity>

    @Query("SELECT COALESCE(MAX(sort), 0) FROM sticker_packs")
    suspend fun maxPackSort(): Int

    @Query("SELECT COALESCE(MAX(sort), 0) FROM stickers WHERE pack_id = :packId")
    suspend fun maxStickerSort(packId: String): Int

    @Query("SELECT * FROM sticker_packs WHERE id = :id")
    suspend fun packById(id: String): StickerPackEntity?

    @Query("SELECT * FROM stickers WHERE enabled = 1 ORDER BY pack_id ASC, sort ASC, id ASC")
    fun observeEnabled(): Flow<List<StickerEntity>>

    @Query("SELECT * FROM stickers WHERE id = :id")
    suspend fun byId(id: String): StickerEntity?

    @Query("SELECT * FROM stickers WHERE id IN (:ids)")
    suspend fun byIds(ids: List<String>): List<StickerEntity>

    /**
     * 表情目录（执行书 7.4.2）：最近使用优先 → 使用次数降序 → id 升序，
     * 保证相同数据下字节级稳定，利于前缀缓存。
     */
    @Query(
        """
        SELECT * FROM stickers WHERE enabled = 1
        ORDER BY CASE WHEN last_used_at IS NULL THEN 1 ELSE 0 END,
                 last_used_at DESC, use_count DESC, id ASC
        LIMIT :limit
        """
    )
    suspend fun catalog(limit: Int): List<StickerEntity>

    @Query("UPDATE stickers SET use_count = use_count + 1, last_used_at = :at WHERE id = :id")
    suspend fun recordUse(id: String, at: Long)

    @Query("SELECT COUNT(*) FROM stickers")
    suspend fun count(): Int

    /** 管理页需要看到停用的表情，不能复用只查 enabled 的目录查询。 */
    @Query("SELECT * FROM stickers ORDER BY pack_id ASC, sort ASC, id ASC")
    fun observeAll(): Flow<List<StickerEntity>>

    @Query("SELECT * FROM stickers WHERE pack_id = :packId ORDER BY sort ASC, id ASC")
    suspend fun byPack(packId: String): List<StickerEntity>

    @Query("SELECT COUNT(*) FROM stickers WHERE pack_id = :packId")
    suspend fun countByPack(packId: String): Int

    @Update
    suspend fun updateSticker(sticker: StickerEntity)

    @Update
    suspend fun updatePack(pack: StickerPackEntity)

    @Query("DELETE FROM stickers WHERE id = :id")
    suspend fun deleteSticker(id: String)

    @Query("DELETE FROM stickers WHERE pack_id = :packId")
    suspend fun deleteStickersByPack(packId: String)

    @Query("DELETE FROM sticker_packs WHERE id = :id")
    suspend fun deletePack(id: String)
}

@Dao
interface RequestSnapshotDao {
    @Insert
    suspend fun insert(snapshot: RequestSnapshotEntity): Long

    @Query("SELECT * FROM request_snapshots ORDER BY created_at DESC, id DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<RequestSnapshotEntity>

    @Query("SELECT * FROM request_snapshots ORDER BY created_at DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<RequestSnapshotEntity>>

    /** 重新生成（M3-06）：取本会话最近的聊天快照，用于复用当次请求体。 */
    @Query(
        """
        SELECT * FROM request_snapshots
        WHERE session_id = :sessionId AND trace_tag = 'chat'
        ORDER BY created_at DESC, id DESC LIMIT :limit
        """
    )
    suspend fun chatSnapshots(sessionId: Long, limit: Int): List<RequestSnapshotEntity>

    @Query("DELETE FROM request_snapshots")
    suspend fun clear()

    /** 只保留最近 [keep] 条（6.13：最近 50 次）。 */
    @Query(
        """
        DELETE FROM request_snapshots
        WHERE id NOT IN (
            SELECT id FROM request_snapshots ORDER BY created_at DESC, id DESC LIMIT :keep
        )
        """
    )
    suspend fun trim(keep: Int)
}

@Dao
interface SettingDao {
    @Query("SELECT * FROM settings WHERE key = :key")
    suspend fun get(key: String): SettingEntity?

    @Query("SELECT * FROM settings")
    suspend fun all(): List<SettingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(setting: SettingEntity)

    @Query("DELETE FROM settings WHERE key = :key")
    suspend fun delete(key: String)
}

/** 草稿箱（M1-09）：按人设身份保存，切回身份时恢复。 */
@Dao
interface DraftDao {
    @Query("SELECT * FROM drafts WHERE identity_id = :identityId LIMIT 1")
    suspend fun byIdentity(identityId: Long): DraftEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(draft: DraftEntity)

    @Query("DELETE FROM drafts WHERE identity_id = :identityId")
    suspend fun clear(identityId: Long)
}
