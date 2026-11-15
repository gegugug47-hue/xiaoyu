package com.xiaoyu.soul.life

data class LifeFragmentDraft(
    val content: String,
    val type: String = "生活",
)

/** 生活碎片只用于聊天取材与主动聊天，绝不写入 memories（红线 15-6）。 */
object LifeSimulator {
    const val EXPIRE_DAYS = 7L
    const val DAILY_MIN = 2
    const val DAILY_MAX = 5

    fun parse(raw: String?): List<LifeFragmentDraft> =
        com.xiaoyu.soul.memory.MemoryExtractionParser.parseLifeFragments(raw)
            .map { LifeFragmentDraft(it) }
            .take(DAILY_MAX)

    fun dailyCount(random: kotlin.random.Random): Int =
        random.nextInt(DAILY_MIN, DAILY_MAX + 1)

    fun injectable(fragments: List<String>, limit: Int = 3): List<String> =
        fragments.filter { it.isNotBlank() }.take(limit)
}
