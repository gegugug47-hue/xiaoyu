package com.xiaoyu.soul.postprocess

enum class ForbiddenLevel {
    /** 人格崩坏：拦截整条回复并重生成 1 次。 */
    HARD,

    /** AI 腔：只记诊断事件。 */
    SOFT,

    /** 模型拒答：软化重试，仍失败则给友好提示。 */
    SAFETY,
}

data class ForbiddenHit(val level: ForbiddenLevel, val phrase: String)

/**
 * 禁词守卫（执行书 3.3.2）。
 *
 * 词表在这里内置一份最小兜底版本，保证 assets 加载失败时也不会崩；
 * 后续接入 `assets/prompts/forbidden_phrases.json` 时用构造参数覆盖即可。
 */
class ForbiddenPhraseGuard(
    hardPatterns: List<Regex> = DEFAULT_HARD,
    softPatterns: List<Regex> = DEFAULT_SOFT,
    safetyPatterns: List<Regex> = DEFAULT_SAFETY,
) {
    private val hard = hardPatterns
    private val soft = softPatterns
    private val safety = safetyPatterns

    /** 命中最严重的一档：HARD > SAFETY > SOFT。 */
    fun check(text: String): ForbiddenHit? {
        if (text.isBlank()) return null
        hard.firstOrNull { it.containsMatchIn(text) }?.let {
            return ForbiddenHit(ForbiddenLevel.HARD, it.pattern)
        }
        safety.firstOrNull { it.containsMatchIn(text) }?.let {
            return ForbiddenHit(ForbiddenLevel.SAFETY, it.pattern)
        }
        soft.firstOrNull { it.containsMatchIn(text) }?.let {
            return ForbiddenHit(ForbiddenLevel.SOFT, it.pattern)
        }
        return null
    }

    companion object {
        private val DEFAULT_HARD = listOf(
            Regex("""作为(一个|一名)?\s*AI""", RegexOption.IGNORE_CASE),
            Regex("""作为(一个)?\s*(语言|大)模型""", RegexOption.IGNORE_CASE),
            Regex("""我(只是|不过是|仅仅是)(一个)?\s*(程序|模型|AI|人工智能)""", RegexOption.IGNORE_CASE),
            Regex("""作为(你的)?\s*助手""", RegexOption.IGNORE_CASE),
            Regex("""As an AI""", RegexOption.IGNORE_CASE),
            Regex("""language model""", RegexOption.IGNORE_CASE),
        )

        private val DEFAULT_SOFT = listOf(
            Regex("""首先[\s\S]{0,80}其次"""),
            Regex("""总结一下"""),
            Regex("""希望这能帮到你"""),
            Regex("""如果你还有其他问题"""),
            Regex("""很高兴为你"""),
        )

        private val DEFAULT_SAFETY = listOf(
            Regex("""我不能(提供|讨论|协助)"""),
            Regex("""这超出了我的"""),
            Regex("""我无法协助"""),
        )
    }
}
