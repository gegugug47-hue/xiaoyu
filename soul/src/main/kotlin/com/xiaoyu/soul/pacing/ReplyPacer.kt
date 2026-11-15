package com.xiaoyu.soul.pacing

import kotlin.random.Random

/**
 * 回复节奏（PRD 9.2）：默认关闭；开启后 10 秒 ~ 5 分钟随机延迟，
 * 深夜更长；深夜且低能量时直接“困了”。多条消息合并回复由聊天层负责。
 */
object ReplyPacer {
    const val MIN_DELAY_MS = 10_000L
    const val MAX_DELAY_MS = 300_000L

    fun delayMillis(
        intensity: Float,
        energy: Int,
        hourOfDay: Int,
        random: Random,
    ): Long {
        val factor = when (hourOfDay) {
            in 0..5 -> 1.9
            23 -> 1.4
            6, 7 -> 1.2
            else -> 1.0
        }
        val energyFactor = if (energy < 30) 1.3 else 1.0
        val scale = intensity.coerceIn(0.2f, 2.0f) * factor * energyFactor
        val base = MIN_DELAY_MS + random.nextDouble() * (MAX_DELAY_MS - MIN_DELAY_MS)
        return (base * scale).toLong().coerceIn(MIN_DELAY_MS, 900_000L)
    }

    fun shouldSaySleepy(hourOfDay: Int, energy: Int, random: Random): Boolean {
        if (hourOfDay !in 0..5 && hourOfDay != 23) return false
        return energy < 40 || random.nextDouble() < 0.5
    }

    fun sleepyLine(random: Random): String {
        val lines = listOf(
            "唔…我有点困了，先睡啦，明天再跟你聊好不好",
            "困得眼睛都快睁不开了…明天见哦",
            "这条我先回你：困了，明天聊～",
        )
        return lines[random.nextInt(lines.size)]
    }
}
