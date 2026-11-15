package com.xiaoyu.soul.proactive

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.random.Random

enum class ProactiveKind { LIFE, AGREEMENT, MILESTONE, MEMORY }

data class ProactiveMaterial(val kind: ProactiveKind, val seed: String)

/**
 * 主动聊天调度（PRD 9.1）：
 * 早 7-9 / 午 12-14 / 晚 19-23 三个窗口内随机时刻，每天 1~3 条，好感度越高越多；
 * 免打扰时段内不推送。
 */
object ProactivePlanner {
    private val windows = listOf(7..9, 12..14, 19..23)

    fun dailyCount(favorability: Int, random: Random): Int {
        val level = com.xiaoyu.soul.mood.FavorabilityLevel.of(favorability)
        return when (level) {
            com.xiaoyu.soul.mood.FavorabilityLevel.STRANGER -> 1
            com.xiaoyu.soul.mood.FavorabilityLevel.FAMILIAR -> if (random.nextBoolean()) 1 else 2
            com.xiaoyu.soul.mood.FavorabilityLevel.INTIMATE ->
                if (random.nextDouble() < 0.5) 2 else 3
            com.xiaoyu.soul.mood.FavorabilityLevel.BELOVED ->
                if (random.nextDouble() < 0.4) 2 else 3
        }
    }

    fun inDoNotDisturb(hour: Int, dndStartHour: Int, dndEndHour: Int): Boolean {
        if (dndStartHour == dndEndHour) return false
        return if (dndStartHour < dndEndHour) {
            hour in dndStartHour until dndEndHour
        } else {
            hour >= dndStartHour || hour < dndEndHour
        }
    }

    fun planDay(
        date: LocalDate,
        count: Int,
        dndStartHour: Int,
        dndEndHour: Int,
        zone: ZoneId,
        random: Random,
    ): List<Instant> {
        val picks = mutableListOf<Instant>()
        val candidates = mutableListOf<Instant>()
        for (window in windows) {
            for (hour in window) {
                for (minute in listOf(7, 23, 41, 56)) {
                    val t = ZonedDateTime.of(date, LocalTime.of(hour, minute), zone).toInstant()
                    if (!inDoNotDisturb(hour, dndStartHour, dndEndHour)) candidates += t
                }
            }
        }
        candidates.shuffled(random).take(count.coerceIn(0, 3)).forEach { picks += it }
        return picks.sorted()
    }

    fun pickMaterial(
        freshLife: List<String>,
        agreements: List<String>,
        milestone: String?,
        oldMemories: List<String>,
        random: Random,
    ): ProactiveMaterial? {
        val lifeMaterial = freshLife.filter { it.isNotBlank() }
            .takeIf { it.isNotEmpty() }
            ?.let { ProactiveMaterial(ProactiveKind.LIFE, it[random.nextInt(it.size)]) }
        val agreementMaterial = agreements.filter { it.isNotBlank() }
            .takeIf { it.isNotEmpty() }
            ?.let { ProactiveMaterial(ProactiveKind.AGREEMENT, it[random.nextInt(it.size)]) }
        val milestoneMaterial = milestone?.takeIf { it.isNotBlank() }
            ?.let { ProactiveMaterial(ProactiveKind.MILESTONE, it) }
        val memoryMaterial = oldMemories.filter { it.isNotBlank() }
            .takeIf { it.isNotEmpty() }
            ?.let { ProactiveMaterial(ProactiveKind.MEMORY, it[random.nextInt(it.size)]) }

        // 优先生活碎片；按 75% 概率取当前优先级最高的一类，否则往下退一级
        val ordered = listOfNotNull(lifeMaterial, agreementMaterial, milestoneMaterial, memoryMaterial)
        if (ordered.isEmpty()) return null
        val roll = random.nextDouble()
        val index = when {
            roll < 0.75 -> 0
            roll < 0.9 -> 1.coerceAtMost(ordered.lastIndex)
            else -> ordered.lastIndex
        }
        return ordered[index]
    }
}
