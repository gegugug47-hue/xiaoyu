package com.xiaoyu.soul.emotion

import kotlin.math.pow

/**
 * 情绪归因（执行书 v3 H-9 / 4.10.3）。
 *
 * 从用户文本中检测情绪触发词，归因到具体情绪类别；
 * 强度随时间按半衰期衰减，被再次提及时回升（提起回升）。
 *
 * 纯 JVM，无 Android 依赖，可在 :soul 单测。
 */
object EmotionAttributor {

    /** 半衰期（小时）：经过这么多小时强度衰减一半。 */
    private const val HALF_LIFE_HOURS = 36.0

    /** 强度下限：低于此值视为已消退，不再共情。 */
    private const val FLOOR_INTENSITY = 0.08f

    /** 提起回升系数：再次提及把强度拉回到该比例（相对初始强度）。 */
    private const val MENTION_BOOST_RATIO = 0.7f

    /** 情绪 → 触发词词典。 */
    private val triggers: Map<String, List<String>> = mapOf(
        "难过" to listOf("难过", "伤心", "难受", "不开心", "郁闷", "低落", "哭", "委屈", "心碎", "失望"),
        "开心" to listOf("开心", "高兴", "快乐", "兴奋", "棒", "太好了", "哈哈", "爽", "美滋滋", "雀跃"),
        "生气" to listOf("生气", "愤怒", "气死", "恼火", "火大", "讨厌", "烦", "可恶", "怒", "不爽"),
        "焦虑" to listOf("焦虑", "紧张", "担心", "害怕", "不安", "压力", "慌", "忐忑", "发愁", "纠结"),
        "感动" to listOf("感动", "暖心", "泪目", "破防", "谢谢", "感谢", "想哭", "温暖", "戳", "泪奔"),
        "疲惫" to listOf("累", "疲惫", "困", "没劲", "乏力", "吃不消", "撑不住", "精疲力尽", "倦", "乏"),
    )

    data class Detection(
        val emotion: String,
        val trigger: String,
        /** 初始强度 0~1，命中越多越强。 */
        val intensity: Float,
    )

    /**
     * 从文本中检测情绪触发词。返回首个命中（主情绪），无命中返回 null。
     */
    fun detect(text: String): Detection? {
        if (text.isBlank()) return null
        for ((emotion, words) in triggers) {
            val hits = words.filter { text.contains(it, ignoreCase = true) }
            if (hits.isNotEmpty()) {
                val intensity = (0.4f + hits.size * 0.15f).coerceAtMost(1f)
                return Detection(emotion, hits.first(), intensity)
            }
        }
        return null
    }

    /**
     * 计算某条情绪事件的当前强度（半衰期衰减 + 提起回升）。
     *
     * @param initialIntensity 初始强度
     * @param createdAt 创建时间（epoch millis）
     * @param lastMentionAt 最近提及时间（epoch millis）
     * @param now 当前时间（epoch millis）
     */
    fun currentIntensity(
        initialIntensity: Float,
        createdAt: Long,
        lastMentionAt: Long,
        now: Long,
    ): Float {
        // 提起回升：以最近提及时间为「重生点」，强度回到初始的 MENTION_BOOST_RATIO
        val effectiveStart = maxOf(createdAt, lastMentionAt)
        val baseIntensity = if (lastMentionAt > createdAt) {
            initialIntensity * MENTION_BOOST_RATIO
        } else {
            initialIntensity
        }
        val hoursSince = (now - effectiveStart).coerceAtLeast(0L) / 3_600_000.0
        val decay = 0.5.pow(hoursSince / HALF_LIFE_HOURS)
        return (baseIntensity * decay.toFloat()).coerceAtLeast(0f).coerceAtMost(1f)
    }

    /** 当前强度是否仍值得共情（高于下限）。 */
    fun isActive(
        initialIntensity: Float,
        createdAt: Long,
        lastMentionAt: Long,
        now: Long,
    ): Boolean = currentIntensity(initialIntensity, createdAt, lastMentionAt, now) >= FLOOR_INTENSITY

    /**
     * 判断文本是否提及了某条已有情绪事件的触发词（用于提起回升）。
     */
    fun mentions(trigger: String, text: String): Boolean =
        trigger.isNotBlank() && text.contains(trigger, ignoreCase = true)
}
