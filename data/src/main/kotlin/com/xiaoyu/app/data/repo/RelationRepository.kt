package com.xiaoyu.app.data.repo

import com.xiaoyu.app.data.db.DiaryDao
import com.xiaoyu.app.data.db.MessageDao
import com.xiaoyu.app.data.db.MoodDao
import com.xiaoyu.app.data.db.SessionDao
import com.xiaoyu.app.data.settings.SettingsRepository
import com.xiaoyu.soul.mood.FavorabilityLevel
import com.xiaoyu.soul.mood.MoodSystem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

data class MoodPoint(val date: LocalDate, val mood: Int, val energy: Int)

data class RelationMilestone(
    val title: String,
    val date: LocalDate,
    val detail: String,
    val upcoming: Boolean = false,
)

/**
 * 关系页数据（执行书 6.5）。数值只用于画弧/曲线，文字层不出现好感数字。
 */
data class RelationSummary(
    val identityName: String,
    val level: FavorabilityLevel,
    /** 当前档位内的进度 0f..1f，用于柔和进度弧。 */
    val levelProgress: Float,
    val knownDays: Int,
    val streakDays: Int,
    val messageCount: Int,
    val diaryCount: Int,
    val moodPoints: List<MoodPoint>,
    val milestones: List<RelationMilestone>,
    val moodLine: String,
)

@Singleton
class RelationRepository @Inject constructor(
    private val sessionDao: SessionDao,
    private val messageDao: MessageDao,
    private val diaryDao: DiaryDao,
    private val moodDao: MoodDao,
    private val stateRepo: SoulStateRepository,
    private val personaRepo: PersonaRepository,
    private val settings: SettingsRepository,
) {
    private val zone: ZoneId get() = ZoneId.systemDefault()

    @Volatile
    private var cachedMoodLine: Pair<String, String>? = null

    suspend fun summary(): RelationSummary {
        val today = LocalDate.now(zone)
        val fav = stateRepo.favorabilityRow()
        val level = FavorabilityLevel.of(fav.currentValue)
        val knownAt = messageDao.earliestVisibleAt()
            ?: sessionDao.earliestCreatedAt()
            ?: System.currentTimeMillis()
        val knownDate = Instant.ofEpochMilli(knownAt).atZone(zone).toLocalDate()
        val knownDays = ChronoUnit.DAYS.between(knownDate, today).toInt().coerceAtLeast(0) + 1
        val chosen = settings.current().chosenIdentityId
        val identities = personaRepo.identities()
        val name = identities.firstOrNull { it.id == chosen }?.name
            ?: identities.firstOrNull()?.name
            ?: "小鱼"
        val diaryAt = diaryDao.earliestCreatedAt()

        val moodPoints = moodDao.recent(30).reversed().map {
            MoodPoint(LocalDate.parse(it.date), it.moodValue, it.energy)
        }

        return RelationSummary(
            identityName = name,
            level = level,
            levelProgress = levelProgress(fav.currentValue, level),
            knownDays = knownDays,
            streakDays = fav.streakDays,
            messageCount = messageDao.countAllVisible(),
            diaryCount = diaryDao.count(),
            moodPoints = moodPoints,
            milestones = milestones(knownDate, today, fav.currentValue, level, diaryAt),
            moodLine = moodLine(today),
        )
    }

    private fun levelProgress(value: Int, level: FavorabilityLevel): Float {
        val span = (level.max - level.min).coerceAtLeast(1)
        return ((value - level.min).toFloat() / span).coerceIn(0f, 1f)
    }

    private suspend fun milestones(
        knownDate: LocalDate,
        today: LocalDate,
        current: Int,
        level: FavorabilityLevel,
        firstDiaryAt: Long?,
    ): List<RelationMilestone> {
        val items = mutableListOf<RelationMilestone>()
        items += RelationMilestone(
            title = "我们认识的那天",
            date = knownDate,
            detail = "从这天起，她开始记得你。",
        )
        levelUpDate(current, level)?.let { date ->
            items += RelationMilestone(
                title = "她把你放进了「${level.label}」",
                date = date,
                detail = "关系悄悄往前走了一步。",
            )
        }
        val hundred = knownDate.plusDays(99)
        items += if (hundred.isAfter(today)) {
            RelationMilestone(
                title = "认识满 100 天",
                date = hundred,
                detail = "还有 ${ChronoUnit.DAYS.between(today, hundred)} 天。",
                upcoming = true,
            )
        } else {
            RelationMilestone(
                title = "认识满 100 天",
                date = hundred,
                detail = "已经一起走过 ${ChronoUnit.DAYS.between(hundred, today)} 天了。",
            )
        }
        firstDiaryAt?.let {
            items += RelationMilestone(
                title = "她写的第一篇日记",
                date = Instant.ofEpochMilli(it).atZone(zone).toLocalDate(),
                detail = "有些话她没直接说，写下来了。",
            )
        }
        return items.sortedBy { it.date }
    }

    /** 从好感事件里找最近一次跨进当前档位的时间；历史窗口不够时返回 null。 */
    private suspend fun levelUpDate(current: Int, level: FavorabilityLevel): LocalDate? {
        if (level == FavorabilityLevel.STRANGER) return null
        val events = stateRepo.recentFavorabilityEvents(200) // 最新在前
        var value = current
        for (event in events) {
            val before = value - event.delta
            if (before < level.min && value >= level.min) {
                return Instant.ofEpochMilli(event.t).atZone(zone).toLocalDate()
            }
            value = before
        }
        return null
    }

    /** 「她今天的心情」：mood_label + 当日生活碎片，缓存当日（6.5）。 */
    private suspend fun moodLine(today: LocalDate): String {
        val key = today.toString()
        cachedMoodLine?.let { (date, line) -> if (date == key) return line }
        val mood = stateRepo.currentMoodSnapshot()
        val fragment = stateRepo.recentLife(3).firstOrNull()?.content
        val line = if (fragment.isNullOrBlank()) {
            "今天${MoodSystem.moodWord(mood.mood)}。"
        } else {
            "今天${MoodSystem.moodWord(mood.mood)}，$fragment"
        }
        cachedMoodLine = key to line
        return line
    }
}
