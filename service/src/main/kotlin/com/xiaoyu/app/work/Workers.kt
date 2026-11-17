package com.xiaoyu.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.xiaoyu.app.data.brain.BrainRouter
import com.xiaoyu.app.data.db.SessionDao
import com.xiaoyu.app.data.prompt.PromptAssetSource
import com.xiaoyu.app.data.repo.ChatRepository
import com.xiaoyu.app.data.repo.MemoryRepository
import com.xiaoyu.app.data.repo.PersonaRepository
import com.xiaoyu.app.data.repo.SoulStateRepository
import com.xiaoyu.app.data.repo.StickerRepository
import com.xiaoyu.app.data.repo.toSoul
import com.xiaoyu.app.data.settings.SettingsRepository
import com.xiaoyu.app.data.settings.AppLockMode
import com.xiaoyu.app.data.sticker.StickerImporter
import com.xiaoyu.app.data.sticker.StickerTagger
import com.xiaoyu.soul.core.BrainRole
import com.xiaoyu.soul.engine.SoulEngine
import com.xiaoyu.soul.mood.FavorabilityLevel
import com.xiaoyu.soul.mood.FavorabilitySystem
import com.xiaoyu.soul.mood.MoodSystem
import com.xiaoyu.soul.proactive.ProactivePlanner
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.random.Random

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WorkerEntryPoint {
    fun settingsRepository(): SettingsRepository
    fun personaRepository(): PersonaRepository
    fun memoryRepository(): MemoryRepository
    fun soulStateRepository(): SoulStateRepository
    fun chatRepository(): ChatRepository
    fun brainRouter(): BrainRouter
    fun soulEngine(): SoulEngine
    fun workScheduler(): WorkScheduler
    fun notifier(): Notifier
    fun promptAssetSource(): PromptAssetSource
    fun sessionDao(): SessionDao
    /** 表情导入 / 管理（执行书 M4-03 / M4-06）。 */
    fun stickerRepository(): StickerRepository
    fun stickerImporter(): StickerImporter
    fun stickerTagger(): StickerTagger
}

private fun Context.entryPoint(): WorkerEntryPoint =
    EntryPointAccessors.fromApplication(applicationContext, WorkerEntryPoint::class.java)

/** 会话结束后的记忆提炼（PRD 5.2），失败指数退避重试。 */
class ExtractionWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val sessionId = inputData.getLong(KEY_SESSION_ID, -1L)
        if (sessionId <= 0) return Result.failure()
        val ep = applicationContext.entryPoint()
        ep.settingsRepository().ensureLoaded()
        val session = ep.sessionDao().byId(sessionId) ?: return Result.success()
        val ok = ep.memoryRepository().extractSession(sessionId, session.title)
        return when {
            ok -> Result.success()
            runAttemptCount < 3 -> Result.retry()
            else -> Result.failure()
        }
    }

    companion object {
        const val KEY_SESSION_ID = "session_id"
    }
}

/** 显式记忆确认失败的后台补偿。 */
class ExplicitMemoryWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ep = applicationContext.entryPoint()
        ep.settingsRepository().ensureLoaded()
        val ok = ep.memoryRepository().processPendingExplicitFacts()
        return if (ok || runAttemptCount >= 3) Result.success() else Result.retry()
    }
}

/** 每天凌晨规划：夜间清算、生活碎片、主动聊天时间点、周备份。 */
class DailyPlanWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ep = applicationContext.entryPoint()
        val settings = ep.settingsRepository().apply { ensureLoaded() }
        val persona = ep.personaRepository().apply { seedIfEmpty() }
        val scheduler = ep.workScheduler()
        val state = ep.soulStateRepository()
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)

        if (persona.identities().isEmpty() || persona.styles().isEmpty()) return Result.success()

        var nightly = now.toLocalDate().atTime(23, 50).atZone(zone)
        if (!nightly.isAfter(now)) nightly = nightly.plusDays(1)
        scheduler.scheduleNightly(
            Duration.between(now, nightly).toMillis(),
            nightly.toLocalDate().toString(),
        )

        var life = now.toLocalDate().atTime(8, 5).atZone(zone)
        if (!life.isAfter(now)) life = life.plusDays(1)
        scheduler.scheduleLife(Duration.between(now, life).toMillis(), life.toLocalDate().toString())
        // 早安 / 晚安闹钟：精确闹钟为可选增强，无权限时 scheduleAlarm 内部退化为 ±10 分钟窗口
        scheduler.scheduleNextDailyAlarms()

        val today = now.toLocalDate()
        val s = settings.current()
        if (s.proactiveEnabled && s.proactivePlannedDate != today.toString()) {
            // v3 §7.13 / B-22：每日主动上限由设置决定（1~3），覆盖好感度默认值
            val count = ProactivePlanner.dailyCount(state.favorabilityValue(), Random.Default)
                .coerceAtMost(s.proactiveDailyLimit.coerceIn(ProactivePlanner.MIN_DAILY_CAP, ProactivePlanner.MAX_DAILY_CAP))
            val instants = ProactivePlanner.planDay(
                date = today,
                count = count,
                dndStartHour = s.dndStartHour,
                dndEndHour = s.dndEndHour,
                zone = zone,
                random = Random.Default,
            )
            val nowMillis = System.currentTimeMillis()
            val plannedSlots = mutableListOf<Long>()
            instants.forEach { instant ->
                val at = instant.toEpochMilli()
                if (at > nowMillis + 60_000) {
                    val slotKey = "$today-$at"
                    // v3 ADR-10 / H-5：WorkManager 为主轨（跨重启持久、不依赖精确闹钟权限）；
                    // 精确闹钟仅在系统授权时叠加，作为"准点推送"的可选增强。
                    scheduler.scheduleProactive(at - nowMillis, slotKey)
                    if (scheduler.canScheduleExactAlarms()) {
                        scheduler.scheduleProactiveAlarm(at, slotKey)
                    }
                    plannedSlots += at
                }
            }
            settings.update {
                it.copy(
                    proactivePlannedDate = today.toString(),
                    proactiveSentCount = 0,
                    proactiveSlots = plannedSlots,
                )
            }
        }

        scheduler.scheduleBackup()
        val next = today.plusDays(1).atTime(6, 10).atZone(zone)
        scheduler.scheduleNextDailyPlan(Duration.between(now, next).toMillis())
        ep.memoryRepository().processPendingExplicitFacts()
        return Result.success()
    }
}

/** 每晚清算：记忆衰减礼崩、晋升合并、情绪分析、今日日记。 */
class NightlyWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ep = applicationContext.entryPoint()
        ep.settingsRepository().ensureLoaded()
        val persona = ep.personaRepository()
        val identity = persona.identities().firstOrNull() ?: return Result.success()
        val style = persona.styles().firstOrNull() ?: return Result.success()
        ep.memoryRepository().runNightlySettlement()
        ep.soulStateRepository().runNightly(identity, style)
        ep.memoryRepository().processPendingExplicitFacts()
        return Result.success()
    }
}

class LifeWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ep = applicationContext.entryPoint()
        ep.settingsRepository().ensureLoaded()
        val persona = ep.personaRepository()
        val identity = persona.identities().firstOrNull() ?: return Result.success()
        val style = persona.styles().firstOrNull() ?: return Result.success()
        ep.soulStateRepository().generateLifeIfNeeded(identity, style)
        return Result.success()
    }
}

/** 主动聊天：窗口内随机时刻，取生活碎片/约定/里程碑/旧记忆之一（PRD 9.1）。 */
class ProactiveWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ep = applicationContext.entryPoint()
        val settings = ep.settingsRepository().apply { ensureLoaded() }
        val s = settings.current()
        if (!s.proactiveEnabled) return Result.success()
        // v3 §8.2：L2/L3 危机后 48 小时内，主动消息总闸关闭（连模型都不请求）
        if (ProactivePlanner.pausedBySafety(s.proactivePausedUntilMs)) return Result.success()

        val now = ZonedDateTime.now()
        val hour = now.hour
        if (ProactivePlanner.inDoNotDisturb(hour, s.dndStartHour, s.dndEndHour)) return Result.success()

        val today = now.toLocalDate()
        val sentToday = if (s.proactiveSentDate == today.toString()) s.proactiveSentCount else 0
        if (ProactivePlanner.dailyCapReached(sentToday, s.proactiveDailyLimit)) return Result.success()

        val persona = ep.personaRepository()
        val identity = persona.identities().firstOrNull() ?: return Result.success()
        val style = persona.styles().firstOrNull() ?: return Result.success()
        val state = ep.soulStateRepository()
        val memory = ep.memoryRepository()
        val soul = ep.soulEngine()

        val mood = state.currentMoodSnapshot()
        val cards = memory.cards()
        val agreements = cards
            .filter { it.category == com.xiaoyu.soul.memory.CARD_CATEGORY_AGREEMENT }
            .map { it.content }
        val milestone = state.recentFavorabilityEvents(20)
            .firstOrNull { abs(it.delta) >= 10 }
            ?.let { "你们最近的关系有了明显进展（${FavorabilityLevel.of(state.favorabilityValue()).label}）" }
        val oldMemories = memory.shortMemories(12)
        val life = state.recentLife(5).map { it.content }
        val material = ProactivePlanner.pickMaterial(
            freshLife = life,
            agreements = agreements,
            milestone = milestone,
            oldMemories = oldMemories,
            random = Random.Default,
        )
        val prompt = soul.proactivePrompt(
            identity = identity.toSoul(),
            style = style.toSoul(),
            moodDescription = soul.moodDescription(mood.mood, mood.energy, hour),
            material = material,
        )
        val text = ep.brainRouter().completeText(BrainRole.CHAT, prompt)?.trim()
        // 模型配置错误时避免无限重试烧额度：最多试 3 次，之后等下一个时间窗
        if (text.isNullOrBlank()) {
            return if (runAttemptCount < 3) Result.retry() else Result.success()
        }

        val sessionId = ep.chatRepository().ensureSessionForProactive()
        ep.chatRepository().saveProactiveMessage(sessionId, text)
        state.recordFavorability(FavorabilitySystem.onChat(0), "proactive")
        settings.update {
            it.copy(proactiveSentDate = today.toString(), proactiveSentCount = sentToday + 1)
        }
        if (s.notifyProactive) {
            val pushed = ep.notifier().notifyProactive(
                text = text,
                sessionId = sessionId,
                hideContent = !s.notifyShowContent || AppLockMode.from(s.appLockMode) != AppLockMode.OFF,
                userText = ep.chatRepository().lastUserMessageText(sessionId),
                sound = s.notifySound,
                vibrate = s.notifyVibrate,
                hideOnLockScreen = s.notifyHideOnLockScreen,
            )
            if (pushed) {
                // 后台存活自检用：记录最近一次成功推送时间
                settings.update { it.copy(lastPushAt = System.currentTimeMillis()) }
            }
        }
        return Result.success()
    }
}

/** 每周备份（PRD 11.3）：由 BackupWorker 实现，见 BackupWorker.kt。 */
