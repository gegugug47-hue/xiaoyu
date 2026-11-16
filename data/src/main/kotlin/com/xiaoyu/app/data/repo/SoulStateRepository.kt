package com.xiaoyu.app.data.repo

import com.xiaoyu.app.data.brain.BrainGateway
import com.xiaoyu.app.data.db.DiaryDao
import com.xiaoyu.app.data.db.DiaryEntity
import com.xiaoyu.app.data.db.FavorabilityDao
import com.xiaoyu.app.data.db.FavorabilityEntity
import com.xiaoyu.app.data.db.IdentityEntity
import com.xiaoyu.app.data.db.LifeFragmentDao
import com.xiaoyu.app.data.db.LifeFragmentEntity
import com.xiaoyu.app.data.db.MessageDao
import com.xiaoyu.app.data.db.MoodDao
import com.xiaoyu.app.data.db.MoodEntity
import com.xiaoyu.app.data.db.StyleEntity
import com.xiaoyu.app.data.settings.SettingsRepository
import com.xiaoyu.soul.core.BrainRole
import com.xiaoyu.soul.diary.DiaryPlanner
import com.xiaoyu.soul.diary.DiarySignal
import com.xiaoyu.soul.engine.SoulEngine
import com.xiaoyu.soul.life.LifeSimulator
import com.xiaoyu.soul.memory.MemoryExtractionParser
import com.xiaoyu.soul.mood.FavorabilityLevel
import com.xiaoyu.soul.mood.FavorabilitySystem
import com.xiaoyu.soul.mood.MoodState
import com.xiaoyu.soul.mood.MoodSystem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.random.Random

@Serializable
data class FavorabilityEvent(val t: Long, val delta: Int, val reason: String)

data class MoodSnapshot(val mood: Int, val energy: Int)

/**
 * 情绪 / 好感度 / 生活碎片 / 日记 / 每晚清算（PRD 6、8）。
 * 所有数值都只在内部流转，UI 只展示文字与图标（红线 6-1）。
 */
@Singleton
class SoulStateRepository @Inject constructor(
    private val moodDao: MoodDao,
    private val favorabilityDao: FavorabilityDao,
    private val diaryDao: DiaryDao,
    private val lifeDao: LifeFragmentDao,
    private val messageDao: MessageDao,
    private val memoryRepo: MemoryRepository,
    private val settings: SettingsRepository,
    private val router: BrainGateway,
    private val soul: SoulEngine,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val zone: ZoneId get() = ZoneId.systemDefault()

    // ---------------- 情绪 ----------------

    fun observeMood(): Flow<MoodEntity?> = moodDao.observeLatest()

    suspend fun ensureTodayMood(): MoodEntity {
        val today = LocalDate.now(zone).toString()
        val now = System.currentTimeMillis()
        val existing = moodDao.byDate(today)
        if (existing != null) return existing
        val fav = favorabilityRow()
        val row = MoodEntity(
            date = today,
            moodValue = 10,
            energy = 80,
            favorabilitySnapshot = fav.currentValue,
            updatedAt = now,
        )
        moodDao.upsert(row)
        return row
    }

    suspend fun currentMoodSnapshot(): MoodSnapshot {
        val row = ensureTodayMood()
        val now = System.currentTimeMillis()
        val hour = java.time.LocalTime.now(zone).hour
        val energy = MoodSystem.energyAt(row.energy, row.updatedAt, now, hour)
        return MoodSnapshot(row.moodValue, energy)
    }

    suspend fun applyMoodSignal(moodDelta: Int, energyDelta: Int) {
        if (moodDelta == 0 && energyDelta == 0) return
        val row = ensureTodayMood()
        val now = System.currentTimeMillis()
        val updated = row.copy(
            moodValue = (row.moodValue + moodDelta).coerceIn(MoodSystem.MIN_MOOD, MoodSystem.MAX_MOOD),
            energy = (row.energy + energyDelta).coerceIn(0, MoodSystem.MAX_ENERGY),
            favorabilitySnapshot = favorabilityRow().currentValue,
            updatedAt = now,
        )
        moodDao.upsert(updated)
    }

    /** 聊天本身会让她“耗一点精力”。 */
    suspend fun drainEnergy(amount: Int = 1) {
        val row = ensureTodayMood()
        moodDao.upsert(row.copy(energy = (row.energy - amount).coerceAtLeast(0), updatedAt = System.currentTimeMillis()))
    }

    // ---------------- 好感度 ----------------

    fun observeFavorability(): Flow<Int> = favorabilityDao.observe().map { it?.currentValue ?: 0 }

    suspend fun favorabilityRow(): FavorabilityEntity {
        val existing = favorabilityDao.get()
        if (existing != null) return existing
        val fresh = FavorabilityEntity(
            currentValue = 0,
            level = FavorabilityLevel.STRANGER.name,
            updatedAt = System.currentTimeMillis(),
            historyJson = "[]",
            lastInteractionDate = null,
            streakDays = 0,
        )
        favorabilityDao.upsert(fresh)
        return fresh
    }

    suspend fun favorabilityValue(): Int = favorabilityRow().currentValue

    suspend fun level(): FavorabilityLevel = FavorabilityLevel.of(favorabilityValue())

    /** 每个会话开始：连续天数额外加分 + 日常对话 +2（PRD 6.2）。 */
    suspend fun registerSessionStart() {
        val row = favorabilityRow()
        val today = LocalDate.now(zone)
        val todayKey = today.toString()
        val streak = when (row.lastInteractionDate) {
            todayKey -> row.streakDays
            today.minusDays(1).toString() -> row.streakDays + 1
            else -> 1
        }
        val isNewDay = row.lastInteractionDate != todayKey
        val delta = if (isNewDay) {
            FavorabilitySystem.onDailyStreak(row.currentValue, streak) - row.currentValue
        } else {
            2
        }
        val updated = row.copy(
            currentValue = (row.currentValue + delta).coerceAtMost(FavorabilitySystem.MAX),
            level = FavorabilityLevel.of(row.currentValue + delta).name,
            lastInteractionDate = todayKey,
            streakDays = streak,
            updatedAt = System.currentTimeMillis(),
        )
        favorabilityDao.upsert(updated)
        appendHistory(delta, if (isNewDay) "daily_chat" else "chat")
    }

    suspend fun recordFavorability(delta: Int, reason: String) {
        if (delta == 0) return
        val row = favorabilityRow()
        val value = (row.currentValue + delta).coerceIn(0, FavorabilitySystem.MAX)
        favorabilityDao.upsert(
            row.copy(
                currentValue = value,
                level = FavorabilityLevel.of(value).name,
                updatedAt = System.currentTimeMillis(),
            )
        )
        appendHistory(delta, reason)
    }

    private suspend fun appendHistory(delta: Int, reason: String) {
        val row = favorabilityRow()
        val events = runCatching {
            json.decodeFromString<List<FavorabilityEvent>>(row.historyJson)
        }.getOrDefault(emptyList()).toMutableList()
        events += FavorabilityEvent(System.currentTimeMillis(), delta, reason)
        val trimmed = events.takeLast(200)
        favorabilityDao.upsert(
            row.copy(historyJson = json.encodeToString(kotlinx.serialization.builtins.ListSerializer(FavorabilityEvent.serializer()), trimmed))
        )
    }

    suspend fun recentFavorabilityEvents(limit: Int = 20): List<FavorabilityEvent> {
        val raw = favorabilityRow().historyJson
        return runCatching {
            json.decodeFromString<List<FavorabilityEvent>>(raw)
        }.getOrDefault(emptyList()).takeLast(limit).reversed()
    }

    // ---------------- 生活碎片 ----------------

    suspend fun freshLifeForChat(limit: Int = 3): List<String> {
        val now = System.currentTimeMillis()
        val fresh = lifeDao.fresh(now).take(limit)
        if (fresh.isNotEmpty()) lifeDao.markUsed(fresh.map { it.id })
        return fresh.map { it.content }
    }

    suspend fun generateLifeIfNeeded(identity: IdentityEntity, style: StyleEntity) {
        val today = LocalDate.now(zone).toString()
        if (settings.current().lastLifeDate == today) return
        lifeDao.purgeExpired(System.currentTimeMillis())
        val mood = currentMoodSnapshot()
        val prompt = soul.lifePrompt(identity.toSoul(), style.toSoul(), soul.moodDescription(mood.mood, mood.energy, java.time.LocalTime.now(zone).hour))
        val drafts = LifeSimulator.parse(router.completeJson(BrainRole.EXTRACT, null, prompt))
        if (drafts.isEmpty()) return
        val now = System.currentTimeMillis()
        val expireAt = now + LifeSimulator.EXPIRE_DAYS * 86_400_000L
        for (draft in drafts) {
            lifeDao.insert(
                LifeFragmentEntity(
                    content = draft.content,
                    type = draft.type,
                    createdAt = now,
                    expireAt = expireAt,
                )
            )
        }
        settings.update { it.copy(lastLifeDate = today) }
    }

    suspend fun recentLife(limit: Int = 5): List<LifeFragmentEntity> =
        lifeDao.recent(System.currentTimeMillis(), limit)

    /** v3 H-12 / 7.6：观察指定时间之后的生活碎片流。 */
    fun observeLifeSince(since: Long): kotlinx.coroutines.flow.Flow<List<LifeFragmentEntity>> =
        lifeDao.observeSince(since)

    // ---------------- 日记 ----------------

    fun observeDiaries(): Flow<List<DiaryEntity>> = diaryDao.observeAll()

    suspend fun diary(id: Long): DiaryEntity? = diaryDao.byId(id)

    suspend fun markDiaryRead(id: Long) {
        val diary = diaryDao.byId(id) ?: return
        if (diary.readAt != null) return
        diaryDao.markRead(id, System.currentTimeMillis())
        recordFavorability(FavorabilitySystem.onDiaryRead(0), "diary_read")
    }

    suspend fun unlockDiary(id: Long) = diaryDao.unlock(id)

    suspend fun latestLockedSecret(): DiaryEntity? = diaryDao.latestLockedSecret()

    /**
     * 判断是否该写今天的日记并生成（PRD 8.2）。
     * 返回新日记 id；不满足触发条件或模型失败返回 null。
     */
    suspend fun generateDiary(date: LocalDate, identity: IdentityEntity, style: StyleEntity): Long? {
        if (diaryDao.byDate(date.toString()) != null) return null
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val moodRow = moodDao.byDate(date.toString()) ?: ensureTodayMood()
        val yesterdayMood = moodDao.byDate(date.minusDays(1).toString())?.moodValue ?: moodRow.moodValue
        val fav = favorabilityRow()
        val newMemories = memoryRepo.createdBetween(start, end)
        val signal = DiarySignal(
            date = date,
            newMemoryCount = newMemories.size,
            moodDelta = moodRow.moodValue - yesterdayMood,
            favorabilityChanged = abs(fav.currentValue - moodRow.favorabilitySnapshot) >= 10,
        )
        val plan = DiaryPlanner.plan(signal, Instant.now(), zone, Random.Default)
        settings.update { it.copy(lastDiaryPlanDate = date.toString()) }
        if (!plan.shouldWrite) return null

        val life = recentLife(3).map { it.content }
        val prompt = soul.diaryPrompt(
            identity = identity.toSoul(),
            style = style.toSoul(),
            date = date,
            memories = newMemories.map { it.content },
            moodDescription = soul.moodDescription(moodRow.moodValue, moodRow.energy, 22),
            favorabilityDescription = FavorabilitySystem.describe(FavorabilityLevel.of(fav.currentValue)),
            life = life,
            longForm = plan.longForm,
            backfill = plan.backfill,
        )
        val text = router.completeText(BrainRole.EXTRACT, prompt) ?: return null
        val id = diaryDao.insert(
            DiaryEntity(
                contentMd = text.trim(),
                moodSnapshot = moodRow.moodValue,
                favorabilitySnapshot = fav.currentValue,
                triggerType = plan.triggers.joinToString(","),
                isSecret = plan.secret,
                unlocked = !plan.secret,
                createdAt = plan.scheduledAt.toEpochMilli(),
                eventDate = date.toString(),
            )
        )
        if (!plan.secret) {
            settings.update { it.copy(pendingDiaryMentionId = id) }
        }
        return id
    }

    suspend fun secretShareable(): Boolean = favorabilityValue() >= FavorabilityLevel.BELOVED.min

    // ---------------- 每晚清算 ----------------

    /**
     * 每晚清算（PRD 5.8）：情绪分析 → 日记 → 生活碎片。记忆衰减/晋升由 MemoryRepository 负责。
     */
    suspend fun runNightly(identity: IdentityEntity, style: StyleEntity) {
        val today = LocalDate.now(zone)
        val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val messages = messageDao.since(start)
        if (messages.isNotEmpty()) {
            val prompt = soul.moodAnalysisPrompt(messages.map { it.toSoulMessage() })
            val signal = MemoryExtractionParser.parseMoodDelta(router.completeJson(BrainRole.EXTRACT, null, prompt))
            val row = ensureTodayMood()
            val now = System.currentTimeMillis()
            val newMood = if (signal != null) {
                (row.moodValue + signal.moodDelta).coerceIn(MoodSystem.MIN_MOOD, MoodSystem.MAX_MOOD)
            } else {
                row.moodValue
            }
            val newEnergy = if (signal != null) {
                (row.energy + signal.energyDelta).coerceIn(0, MoodSystem.MAX_ENERGY)
            } else {
                row.energy
            }
            moodDao.upsert(
                row.copy(
                    moodValue = MoodSystem.overnightDrift(newMood),
                    energy = newEnergy,
                    updatedAt = now,
                )
            )
        }
        generateLifeIfNeeded(identity, style)
        generateDiary(today, identity, style)
        settings.update { it.copy(lastNightlyDate = today.toString()) }
    }

    fun dayStartMillis(date: LocalDate = LocalDate.now(zone)): Long =
        ZonedDateTime.of(date.atStartOfDay(), zone).toInstant().toEpochMilli()
}
