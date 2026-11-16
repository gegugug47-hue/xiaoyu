package com.xiaoyu.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    @ColumnInfo(name = "identity_id") val identityId: Long,
    @ColumnInfo(name = "style_id") val styleId: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "last_message_at") val lastMessageAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long? = null,
    /** pending / done / failed（PRD 4.2 / 5.2） */
    @ColumnInfo(name = "extracted_state") val extractedState: String = "pending",
    @ColumnInfo(name = "extract_attempts") val extractAttempts: Int = 0,
    /** v3 §4.7.1 / H-14：增量提炼游标，记录上次提炼到的消息时间戳，0 表示从未提炼 */
    @ColumnInfo(name = "extract_cursor") val extractCursor: Long = 0,
    val archived: Boolean = false,
)

/**
 * 聊天记录只增不改不批量删（PRD 红线 2）：hidden 为软删除，DAO 不提供批量 delete。
 */
@Entity(
    tableName = "messages",
    indices = [Index("session_id"), Index(value = ["session_id", "created_at"])],
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "session_id") val sessionId: Long,
    /** user / assistant / system_notice */
    val role: String,
    val content: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    val hidden: Boolean = false,
    @ColumnInfo(name = "image_uri") val imageUri: String? = null,
    @ColumnInfo(name = "voice_uri") val voiceUri: String? = null,
    /** 分段发送时同一轮回复的多个气泡共享该 id（执行书 3.2.4）。 */
    @ColumnInfo(name = "reply_group_id") val replyGroupId: String? = null,
    /** 用户给的 reaction，逗号分隔，单用户场景每类最多 1 个（执行书 6.3.7）。 */
    @ColumnInfo(name = "reactions_json") val reactionsJson: String = "",
    /** 消息类型：text / sticker（执行书 7.4.4；sticker 的 content 为空串）。 */
    val type: String = "text",
    @ColumnInfo(name = "sticker_id") val stickerId: String? = null,
    /** 发送状态：sent / sending / failed（执行书 M1-09）。默认 sent，兼容历史数据。 */
    val status: String = "sent",
    /** 编辑重发时间戳（执行书 M3-07）；null 表示未编辑过。 */
    @ColumnInfo(name = "edited_at") val editedAt: Long? = null,
    /** 引用回复的目标消息 id（执行书 M1-09 引用回复）。 */
    @ColumnInfo(name = "quote_to_id") val quoteToId: Long? = null,
    /** 是否被撤回（执行书 M3-07）；撤回后正文不再展示，只留"你撤回了一条消息"占位。 */
    val retracted: Boolean = false,
)

/**
 * 消息全文索引（执行书 6.8 / M7-04）。
 * 中文没有内置分词，写入时把正文额外展开成「单字 + 二元组」放进 index_text，
 * 查询时同样按二元组 OR 组合；FTS4 负责倒排与快速匹配。
 * rowid 直接复用消息 id，查询时与 messages.id 等值连接，删除/覆盖天然幂等。
 * 注意：FTS 实体除主键外只允许 String 字段，所以这里不冗余 session_id。
 */
@Fts4(tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "message_fts")
data class MessageFtsEntity(
    @PrimaryKey
    @ColumnInfo(name = "rowid") val rowId: Long,
    val content: String,
    @ColumnInfo(name = "index_text") val indexText: String,
)

/** 表情包（执行书 5.1 / 7.4.1）。 */
@Entity(tableName = "sticker_packs")
data class StickerPackEntity(
    @PrimaryKey val id: String,
    val name: String,
    val author: String? = null,
    /** user | zip | telegram | builtin */
    val source: String,
    @ColumnInfo(name = "cover_sticker_id") val coverStickerId: String? = null,
    val sort: Int = 0,
    val enabled: Boolean = true,
    @ColumnInfo(name = "is_builtin") val isBuiltin: Boolean = false,
    @ColumnInfo(name = "dir_path") val dirPath: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** 单枚表情（执行书 5.1 / 7.4.1）。内置小鱼用代码绘制，filePath 为 `builtin://fish/<情绪>`。 */
@Entity(
    tableName = "stickers",
    indices = [Index("pack_id"), Index(value = ["pack_id", "sort"])],
)
data class StickerEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "pack_id") val packId: String,
    val name: String,
    /** CSV 语义标签，注入表情目录时取前 2 个。 */
    val tags: String = "",
    /** CSV 情绪枚举（3.6.2），用于 mood 联动。 */
    @ColumnInfo(name = "mood_tags") val moodTags: String = "",
    @ColumnInfo(name = "file_path") val filePath: String,
    /** image/webp | image/gif | image/png | image/jpeg | builtin/canvas */
    val mime: String = "image/webp",
    val width: Int = 0,
    val height: Int = 0,
    val bytes: Long = 0,
    @ColumnInfo(name = "use_count") val useCount: Int = 0,
    @ColumnInfo(name = "last_used_at") val lastUsedAt: Long? = null,
    val sort: Int = 0,
    val enabled: Boolean = true,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(tableName = "identities")
data class IdentityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "content_md") val contentMd: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "preset_key") val presetKey: String? = null,
)

@Entity(tableName = "styles")
data class StyleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "content_md") val contentMd: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "preset_key") val presetKey: String? = null,
)

@Entity(
    tableName = "memories",
    indices = [Index("tier"), Index("source_session_id")],
)
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** SHORT / BIN */
    val tier: String,
    val content: String,
    val type: String,
    val tags: String,
    val importance: Int,
    val strength: Int,
    @ColumnInfo(name = "review_count") val reviewCount: Int = 0,
    @ColumnInfo(name = "last_access") val lastAccess: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "source_session_id") val sourceSessionId: Long? = null,
    @ColumnInfo(name = "promoted_to_card_id") val promotedToCardId: Long? = null,
    @ColumnInfo(name = "bin_at") val binAt: Long? = null,
)

@Entity(tableName = "fact_cards")
data class FactCardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,
    val content: String,
    val tags: String,
    val strength: Int = 100,
    @ColumnInfo(name = "expire_at") val expireAt: Long? = null,
    val source: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "source_memory_ids") val sourceMemoryIds: String = "",
)

@Entity(
    tableName = "diaries",
    indices = [Index(value = ["event_date"], unique = true)],
)
data class DiaryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "content_md") val contentMd: String,
    @ColumnInfo(name = "mood_snapshot") val moodSnapshot: Int,
    @ColumnInfo(name = "favorability_snapshot") val favorabilitySnapshot: Int,
    @ColumnInfo(name = "trigger_type") val triggerType: String,
    @ColumnInfo(name = "is_secret") val isSecret: Boolean,
    val unlocked: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "event_date") val eventDate: String,
    @ColumnInfo(name = "read_at") val readAt: Long? = null,
)

@Entity(
    tableName = "mood_states",
    indices = [Index(value = ["date"], unique = true)],
)
data class MoodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    @ColumnInfo(name = "mood_value") val moodValue: Int,
    val energy: Int,
    @ColumnInfo(name = "favorability_snapshot") val favorabilitySnapshot: Int = 0,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(tableName = "favorability")
data class FavorabilityEntity(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(name = "current_value") val currentValue: Int,
    val level: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "history_json") val historyJson: String,
    @ColumnInfo(name = "last_interaction_date") val lastInteractionDate: String?,
    @ColumnInfo(name = "streak_days") val streakDays: Int,
)

@Entity(tableName = "life_fragments", indices = [Index("created_at")])
data class LifeFragmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val type: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    val used: Boolean = false,
    @ColumnInfo(name = "expire_at") val expireAt: Long,
)

/**
 * 情绪归因事件（执行书 v3 H-9 / 4.10.3）。
 * 记录触发某种情绪的关键词，强度随时间半衰期衰减，被再次提及时回升。
 * 用于在对话中自然回应「上次你说的那件事还难受吗」之类的共情。
 */
@Entity(tableName = "emotion_events", indices = [Index("emotion"), Index("created_at")])
data class EmotionEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 情绪类别：开心 / 难过 / 生气 / 焦虑 / 感动 … */
    val emotion: String,
    /** 触发词或短语（用户原话里的关键词） */
    val trigger: String,
    /** 初始强度 0~1 */
    val intensity: Float,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    /** 最近一次被提及的时间，用于「提起回升」 */
    @ColumnInfo(name = "last_mention_at") val lastMentionAt: Long,
    @ColumnInfo(name = "session_id") val sessionId: Long? = null,
)

/**
 * 纠偏记录（执行书 v3 H-11 / 4.11）。
 * 用户说「不像她」时落一条风格补丁，可撤销（revoked=1）。
 * 仅最近一条未撤销的补丁会注入 prompt。
 */
@Entity(tableName = "corrections", indices = [Index("created_at")])
data class CorrectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val intent: String,
    val patch: String,
    val trigger: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "revoked") val revoked: Boolean = false,
    @ColumnInfo(name = "session_id") val sessionId: Long? = null,
)

/** settings 表：敏感值（API Key / 电脑地址）一律加密后落库（PRD 2.1-4）。 */
@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    @ColumnInfo(name = "value_encrypted") val valueEncrypted: String,
)

/**
 * 诊断快照（执行书 6.13）：最近 50 次模型请求的 Prompt 分段、原文与后处理事件。
 * 只在本地 SQLite 保留，不写日志、不上传（R10）。
 */
@Entity(tableName = "request_snapshots", indices = [Index("created_at")])
data class RequestSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    /** chat / extract / diary ... */
    @ColumnInfo(name = "trace_tag") val traceTag: String,
    val provider: String,
    val model: String,
    @ColumnInfo(name = "base_url") val baseUrl: String,
    /** provider/model/temperature/max_tokens 等，便于复现。 */
    @ColumnInfo(name = "params_json") val paramsJson: String,
    /** L0~L9 分段：[{index,key,label,volatile,tokens,content}]。 */
    @ColumnInfo(name = "sections_json") val sectionsJson: String,
    /** 完整请求体（含历史消息），仅本地用于生成 curl。 */
    @ColumnInfo(name = "request_body") val requestBody: String,
    /** 模型原始输出，保留未剥离的 [xy:*] token。 */
    @ColumnInfo(name = "response_raw") val responseRaw: String,
    /** 后处理摘要：分段数、拦截、重试、表情等。 */
    @ColumnInfo(name = "events_text") val eventsText: String,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    @ColumnInfo(name = "prompt_tokens") val promptTokens: Int,
    @ColumnInfo(name = "completion_tokens") val completionTokens: Int,
    @ColumnInfo(name = "finish_reason") val finishReason: String?,
    val error: String? = null,
    @ColumnInfo(name = "session_id") val sessionId: Long? = null,
)

/**
 * 草稿箱（执行书 M1-09）：按人设身份保存未发送的输入文本，
 * 切回该身份时自动恢复，避免写到一半切走就丢。
 */
@Entity(tableName = "drafts", indices = [Index("identity_id", unique = true)])
data class DraftEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "identity_id") val identityId: Long,
    val content: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/**
 * 本地向量索引（执行书 M7-10，可选能力）：
 * 只存记忆 id 与向量 BLOB，不存任何原文；没有 embedding 模型时整张表保持为空。
 * 同一记忆 + 同一模型只保留一条，便于换模型后覆盖重建。
 */
@Entity(
    tableName = "embeddings",
    indices = [Index("memory_id"), Index(value = ["memory_id", "model"], unique = true)],
)
data class EmbeddingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "memory_id") val memoryId: Long,
    /** FloatArray 小端序列化后的 BLOB，编码见 VectorRetriever。 */
    val vector: ByteArray,
    val model: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
