package com.xiaoyu.soul.diary

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.random.Random

data class DiarySignal(
    val date: LocalDate,
    val newMemoryCount: Int,
    val moodDelta: Int,
    val favorabilityChanged: Boolean,
)

/**
 * 日记触发与“活人佐料”（PRD 8.2）：
 * 三选一命中 → 30% 概率跳过 → 10% 补写、10% 凌晨毛刺、20% 加密、篇幅随情绪。
 */
data class DiaryPlan(
    val shouldWrite: Boolean,
    val eventDate: LocalDate,
    val scheduledAt: Instant,
    val backfill: Boolean,
    val secret: Boolean,
    val longForm: Boolean,
    val triggers: List<String>,
)

object DiaryPlanner {
    const val SKIP_PROBABILITY = 0.30
    const val BACKFILL_PROBABILITY = 0.10
    const val LATE_NIGHT_PROBABILITY = 0.10
    const val SECRET_PROBABILITY = 0.20
    const val MOOD_SWING_THRESHOLD = 30
    const val LONG_FORM_MOOD_SWING = 50

    fun plan(
        signal: DiarySignal,
        now: Instant,
        zone: ZoneId,
        random: Random,
    ): DiaryPlan {
        val triggers = buildList {
            if (signal.newMemoryCount > 0) add("new_memory")
            if (abs(signal.moodDelta) >= MOOD_SWING_THRESHOLD) add("mood_swing")
            if (signal.favorabilityChanged) add("favorability")
        }
        if (triggers.isEmpty()) {
            return DiaryPlan(false, signal.date, now, false, false, false, emptyList())
        }
        if (random.nextDouble() < SKIP_PROBABILITY) {
            return DiaryPlan(false, signal.date, now, false, false, false, triggers)
        }

        val backfill = random.nextDouble() < BACKFILL_PROBABILITY
        val secret = random.nextDouble() < SECRET_PROBABILITY
        val longForm = abs(signal.moodDelta) >= LONG_FORM_MOOD_SWING

        val scheduled = if (backfill) {
            val daysLater = 1 + random.nextInt(2)
            val day = signal.date.plusDays(daysLater.toLong())
            val time = LocalTime.of(21, 0).plusSeconds(random.nextInt(0, 3 * 3600).toLong())
            ZonedDateTime.of(day, time, zone).toInstant()
        } else if (random.nextDouble() < LATE_NIGHT_PROBABILITY) {
            val day = signal.date.plusDays(1)
            val time = LocalTime.of(0, 0).plusSeconds(random.nextInt(0, 2 * 3600).toLong())
            ZonedDateTime.of(day, time, zone).toInstant()
        } else {
            val time = LocalTime.of(21, 0).plusSeconds(random.nextInt(0, 3 * 3600).toLong())
            ZonedDateTime.of(signal.date, time, zone).toInstant()
        }

        return DiaryPlan(
            shouldWrite = true,
            eventDate = signal.date,
            scheduledAt = scheduled,
            backfill = backfill,
            secret = secret,
            longForm = longForm,
            triggers = triggers,
        )
    }

    fun moodIcon(mood: Int): String = when {
        mood <= -40 -> "🌧"
        mood < -10 -> "☁️"
        mood < 15 -> "🌤"
        mood < 50 -> "☀️"
        else -> "🌈"
    }
}
