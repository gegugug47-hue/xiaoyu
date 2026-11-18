package com.xiaoyu.soul.pacing

import kotlin.random.Random

/**
 * 回复节奏（PRD 9.2）：默认关闭。
 *
 * 真人聊天的停顿不是「等五分钟」，而是：看到消息 → 想一下 → 边打字边发。
 * 所以单次停顿控制在 0.6~9 秒：强度滑杆只做线性缩放，
 * 再慢也不会慢到让人以为她掉线了；早期 10 秒~5 分钟的区间已废弃。
 */
object ReplyPacer {
    /** 最快也要喘口气，避免秒回得像机器人。 */
    const val MIN_DELAY_MS = 600L
    /** 超过这个时长，「正在输入」就变成失联提示了。 */
    const val MAX_DELAY_MS = 9_000L

    fun delayMillis(
        intensity: Float,
        energy: Int,
        hourOfDay: Int,
        random: Random,
        userTextLength: Int = 20,
    ): Long {
        // 读你这句话要的时间：中文按每字 90ms 估，最长 3 秒
        val reading = (userTextLength.coerceIn(1, 400) * 90L).coerceAtMost(3_000L)
        // 组织语言的起步停顿 + 抖动
        val start = 400L + random.nextLong(0L, 900L)
        val base = reading + start
        // 深夜稍慢一点，但不惩罚到夸张
        val hourFactor = when (hourOfDay) {
            in 0..5 -> 1.5
            23 -> 1.2
            6, 7 -> 1.1
            else -> 1.0
        }
        val energyFactor = if (energy < 30) 1.15 else 1.0
        val scale = intensity.coerceIn(0.2f, 2.0f) * hourFactor * energyFactor
        return (base * scale).toLong().coerceIn(MIN_DELAY_MS, MAX_DELAY_MS)
    }

    /** 分段连发之间的停顿：像真人打完一句、停一下再补一句。 */
    fun segmentGapMillis(
        intensity: Float,
        hourOfDay: Int,
        random: Random,
    ): Long {
        val hourFactor = if (hourOfDay in 0..5) 1.4 else 1.0
        val scale = intensity.coerceIn(0.2f, 2.0f) * hourFactor
        val base = 700L + random.nextLong(0L, 1_100L)
        return (base * scale).toLong().coerceIn(300L, 2_500L)
    }

    fun shouldSaySleepy(hourOfDay: Int, energy: Int, random: Random): Boolean {
        if (hourOfDay !in 0..5 && hourOfDay != 23) return false
        return energy < 40 || random.nextDouble() < 0.5
    }

    /** 晚安直出池：判定「上一句是不是已经在道晚安」时也用它。 */
    private val SLEEPY_LINES = listOf(
        "唔…我有点困了，先睡啦，明天再跟你聊好不好",
        "困得眼睛都快睁不开了…明天见哦",
        "这条我先回你：困了，明天聊～",
    )

    /** 上一句就是池子里的晚安台词，说明这一晚已经道过别了。 */
    fun isSleepyLine(text: String): Boolean = SLEEPY_LINES.any { it == text.trim() }

    fun sleepyLine(random: Random, avoid: String = ""): String {
        // 同一晚道别两次已经是极限，至少别把同一句原样复读一遍
        val pool = SLEEPY_LINES.filter { it != avoid.trim() }.ifEmpty { SLEEPY_LINES }
        return pool[random.nextInt(pool.size)]
    }
}
