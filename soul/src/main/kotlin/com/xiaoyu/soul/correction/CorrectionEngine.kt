package com.xiaoyu.soul.correction

/**
 * 纠偏引擎（执行书 v3 H-11 / 4.11）。
 *
 * 当用户说「不像她」「你不是这样的」之类的话时，生成一条风格补丁指令，
 * 临时注入到 prompt 中约束她的语气。补丁可被用户主动撤销。
 *
 * 纯 JVM，无 Android 依赖。
 */
object CorrectionEngine {

    /** 触发纠偏的关键词（用户表达「不像她」）。 */
    private val triggers = listOf(
        "不像你", "不像她", "你不是这样的", "你变了", "这不像你",
        "你怎么这样", "你不是这种人", "好陌生", "这不是你",
    )

    /** 纠偏意图分类。 */
    enum class Intent {
        /** 语气不对：太冷淡 / 太热情 / 太敷衍 */
        TONE,
        /** 性格不对：不像她平时的样子 */
        PERSONALITY,
        /** 通用纠偏 */
        GENERAL,
    }

    data class Correction(
        val intent: Intent,
        /** 注入 prompt 的风格补丁语。 */
        val patch: String,
        /** 用户原话里的触发词，用于展示。 */
        val trigger: String,
    )

    /**
     * 检测用户文本是否在纠偏，命中返回纠偏指令，否则 null。
     */
    fun detect(text: String): Correction? {
        if (text.isBlank()) return null
        val hit = triggers.firstOrNull { text.contains(it, ignoreCase = true) } ?: return null
        val intent = classify(text)
        return Correction(
            intent = intent,
            patch = patchFor(intent),
            trigger = hit,
        )
    }

    private fun classify(text: String): Intent = when {
        text.contains("冷淡") || text.contains("敷衍") || text.contains("热情") || text.contains("语气") -> Intent.TONE
        text.contains("性格") || text.contains("陌生") || text.contains("变了") -> Intent.PERSONALITY
        else -> Intent.GENERAL
    }

    private fun patchFor(intent: Intent): String = when (intent) {
        Intent.TONE -> "注意：用户觉得你刚才语气不对，请回到你平时自然的语气，不要太冷淡也不要过度热情。"
        Intent.PERSONALITY -> "注意：用户觉得你不像平时的你，请回到你一贯的性格和说话方式。"
        Intent.GENERAL -> "注意：用户觉得你刚才的回应不像你，请调整回你平时的风格。"
    }

    /** 生成纠偏条的展示文案（供 UI 用）。 */
    fun bannerText(trigger: String): String = "已记录「$trigger」纠偏，她会调整语气，你也可以撤销。"
}
