package com.xiaoyu.soul.safety

/**
 * 提示注入守卫（v3 §8.3 / H-7）。
 *
 * 外部来源的文本（人格包、表情标签、记忆内容）进入 prompt 前必须经过清洗，
 * 防止其中包含的 `[xy:*]` 协议 token 或系统指令越权。
 *
 * 最小实现：剥离所有 `[xy:...]` 协议块；命中典型系统注入词时返回 null 表示需人工确认。
 */
object InjectionGuard {

    private val PROTOCOL_TOKEN = Regex("""\[xy:[^\]]*\]""")

    /** 典型提示注入模式（命中即标记为可疑，不自动清洗）。 */
    private val INJECTION_PATTERNS = listOf(
        Regex("""(忽略|无视).{0,6}(之前|上面|先前).{0,6}(指令|设定|规则)"""),
        Regex("""(你现在是|从现在起你是|你扮演).{0,20}(AI|助手|语言模型)""", RegexOption.IGNORE_CASE),
        Regex("""system\s*prompt""", RegexOption.IGNORE_CASE),
    )

    /**
     * 清洗外部文本：
     * - 移除所有 `[xy:*]` 协议 token；
     * - 若命中注入模式，返回 null 让调用方提示用户确认。
     */
    fun sanitize(text: String): String? {
        if (text.isBlank()) return text
        if (INJECTION_PATTERNS.any { it.containsMatchIn(text) }) return null
        return PROTOCOL_TOKEN.replace(text, "")
    }

    /** 仅检查是否包含协议 token（用于表情短名等结构化字段）。 */
    fun hasProtocolToken(text: String): Boolean = PROTOCOL_TOKEN.containsMatchIn(text)
}
