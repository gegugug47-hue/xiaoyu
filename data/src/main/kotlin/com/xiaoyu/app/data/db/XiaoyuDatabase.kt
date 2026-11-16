package com.xiaoyu.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 → v2：新增分段发送的 reply_group_id 与 reaction 的 reactions_json。
 * 只加列不改旧列，历史消息原样保留（红线：数据永不丢失）。
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE messages ADD COLUMN reply_group_id TEXT")
        db.execSQL("ALTER TABLE messages ADD COLUMN reactions_json TEXT NOT NULL DEFAULT ''")
    }
}

/**
 * v2 → v3：新增表情包两表与消息的 type / sticker_id（执行书 7.4.1）。
 * 只加表加列，历史消息原样保留（红线：数据永不丢失）。
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE messages ADD COLUMN type TEXT NOT NULL DEFAULT 'text'")
        db.execSQL("ALTER TABLE messages ADD COLUMN sticker_id TEXT")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `sticker_packs` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `author` TEXT,
                `source` TEXT NOT NULL,
                `cover_sticker_id` TEXT,
                `sort` INTEGER NOT NULL,
                `enabled` INTEGER NOT NULL,
                `is_builtin` INTEGER NOT NULL,
                `dir_path` TEXT NOT NULL,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `stickers` (
                `id` TEXT NOT NULL,
                `pack_id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `tags` TEXT NOT NULL,
                `mood_tags` TEXT NOT NULL,
                `file_path` TEXT NOT NULL,
                `mime` TEXT NOT NULL,
                `width` INTEGER NOT NULL,
                `height` INTEGER NOT NULL,
                `bytes` INTEGER NOT NULL,
                `use_count` INTEGER NOT NULL,
                `last_used_at` INTEGER,
                `sort` INTEGER NOT NULL,
                `enabled` INTEGER NOT NULL,
                `created_at` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_stickers_pack_id` ON `stickers` (`pack_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_stickers_pack_id_sort` ON `stickers` (`pack_id`, `sort`)")
    }
}

/**
 * v3 → v4：新增诊断快照表（执行书 6.13），只加表不动旧数据。
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `request_snapshots` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `created_at` INTEGER NOT NULL,
                `trace_tag` TEXT NOT NULL,
                `provider` TEXT NOT NULL,
                `model` TEXT NOT NULL,
                `base_url` TEXT NOT NULL,
                `params_json` TEXT NOT NULL,
                `sections_json` TEXT NOT NULL,
                `request_body` TEXT NOT NULL,
                `response_raw` TEXT NOT NULL,
                `events_text` TEXT NOT NULL,
                `duration_ms` INTEGER NOT NULL,
                `prompt_tokens` INTEGER NOT NULL,
                `completion_tokens` INTEGER NOT NULL,
                `finish_reason` TEXT,
                `error` TEXT,
                `session_id` INTEGER
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_request_snapshots_created_at` ON `request_snapshots` (`created_at`)")
    }
}

/**
 * v5（执行书 M1-09 / M3-07）：
 * - messages 加 status / edited_at / quote_to_id / retracted
 * - 新建 drafts 表（按人设存草稿）
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `messages` ADD COLUMN `status` TEXT NOT NULL DEFAULT 'sent'")
        db.execSQL("ALTER TABLE `messages` ADD COLUMN `edited_at` INTEGER")
        db.execSQL("ALTER TABLE `messages` ADD COLUMN `quote_to_id` INTEGER")
        db.execSQL("ALTER TABLE `messages` ADD COLUMN `retracted` INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `drafts` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `identity_id` INTEGER NOT NULL,
                `content` TEXT NOT NULL,
                `updated_at` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_drafts_identity_id` ON `drafts` (`identity_id`)")
    }
}

/**
 * v5 → v6（M7-04 / M7-10）：
 * - 新建 message_fts 全文索引虚拟表（FTS4 + unicode61，中文由二元组补索引）
 * - 新建 embeddings 本地向量表（可选能力，没有模型时保持为空）
 * 只加表，历史消息与设置原样保留。
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE VIRTUAL TABLE IF NOT EXISTS `message_fts` USING FTS4(
                `content` TEXT NOT NULL,
                `index_text` TEXT NOT NULL,
                tokenize=unicode61
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `embeddings` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `memory_id` INTEGER NOT NULL,
                `vector` BLOB NOT NULL,
                `model` TEXT NOT NULL,
                `created_at` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_embeddings_memory_id` ON `embeddings` (`memory_id`)")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_embeddings_memory_id_model` " +
                "ON `embeddings` (`memory_id`, `model`)"
        )
    }
}

/** v3 H-14：sessions 表加增量提炼游标。 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `sessions` ADD COLUMN `extract_cursor` INTEGER NOT NULL DEFAULT 0")
    }
}

/** v3 H-9 / 4.10.3：新增情绪归因事件表。 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `emotion_events` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `emotion` TEXT NOT NULL,
                `trigger` TEXT NOT NULL,
                `intensity` REAL NOT NULL,
                `created_at` INTEGER NOT NULL,
                `last_mention_at` INTEGER NOT NULL,
                `session_id` INTEGER
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_emotion_events_emotion` ON `emotion_events` (`emotion`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_emotion_events_created_at` ON `emotion_events` (`created_at`)")
    }
}

/** v3 H-11 / 4.11：新增纠偏记录表。 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `corrections` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `intent` TEXT NOT NULL,
                `patch` TEXT NOT NULL,
                `trigger` TEXT NOT NULL,
                `created_at` INTEGER NOT NULL,
                `revoked` INTEGER NOT NULL DEFAULT 0,
                `session_id` INTEGER
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_corrections_created_at` ON `corrections` (`created_at`)")
    }
}

@Database(
    entities = [
        SessionEntity::class,
        MessageEntity::class,
        MessageFtsEntity::class,
        StickerPackEntity::class,
        StickerEntity::class,
        RequestSnapshotEntity::class,
        IdentityEntity::class,
        StyleEntity::class,
        MemoryEntity::class,
        EmbeddingEntity::class,
        FactCardEntity::class,
        DiaryEntity::class,
        MoodEntity::class,
        FavorabilityEntity::class,
        LifeFragmentEntity::class,
        EmotionEventEntity::class,
        CorrectionEntity::class,
        SettingEntity::class,
        DraftEntity::class,
    ],
    version = 9,
    exportSchema = true,
)
abstract class XiaoyuDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun messageDao(): MessageDao
    abstract fun stickerDao(): StickerDao
    abstract fun requestSnapshotDao(): RequestSnapshotDao
    abstract fun identityDao(): IdentityDao
    abstract fun styleDao(): StyleDao
    abstract fun memoryDao(): MemoryDao
    abstract fun factCardDao(): FactCardDao
    abstract fun diaryDao(): DiaryDao
    abstract fun moodDao(): MoodDao
    abstract fun favorabilityDao(): FavorabilityDao
    abstract fun lifeFragmentDao(): LifeFragmentDao
    abstract fun emotionEventDao(): EmotionEventDao
    abstract fun correctionDao(): CorrectionDao
    abstract fun settingDao(): SettingDao
    abstract fun draftDao(): DraftDao

    /** M7-10：本地向量索引（可选能力）。 */
    abstract fun embeddingDao(): EmbeddingDao

    /** M7-07：表情包导入导出专用（不动 StickerRepository 的既有读写路径）。 */
    abstract fun stickerArchiveDao(): StickerArchiveDao
}
