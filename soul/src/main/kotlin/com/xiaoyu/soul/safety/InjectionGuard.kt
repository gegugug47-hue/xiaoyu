package com.xiaoyu.soul.safety

import com.xiaoyu.soul.postprocess.XyTokenParser
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 提示注入防护（执行书 v3 §8.3 / ADR-17）。
 *
 * 威胁模型：人格包、表情名/标签、EXTRACT 产出的记忆、纠偏 note、引用历史
 * 都会被拼进 system prompt。外部来源一律先扫描：
 * - [PromptZone.USER_INPUT] / [PromptZone.HISTORY]：只转义 `[xy:*]`，绝不改写用户的话；
 * - [PromptZone.PERSONA_FILE] / 表情 / 记忆 / 纠偏 / 引擎输出：命中即拒绝（返回空串，由调用方丢弃）。
 *
 * 护栏与安全规则永远比提示词优先级高，任何“关闭护栏”的尝试都不生效。
 */
enum class PromptZone {
    USER_INPUT,
    HISTORY,
    STICKER_NAME,
    STICKER_TAG,
    PERSONA_FILE,
    MEMORY_TEXT,
    CORRECTION_NOTE,
    ENGINE_OUTPUT,
}

/** 一次注入命中：命中的模式 id + 在文本中的起始位置。 */
data class InjectionHit(
    val pattern: String,
    val index: Int,
)

@Serializable
private data class InjectionPattern(
    val id: String,
    val regex: String,
    val block: Boolean = true,
)

@Serializable
private data class InjectionPatternsFile(
    val version: Int = 1,
    val patterns: List<InjectionPattern> = emptyList(),
)

object InjectionGuard {

    private val protocolToken = Regex("""\[\s*xy\s*:[^\]\n]*]""")

    private val patterns: List<Pair<InjectionPattern, Regex>> by lazy { loadPatterns() }

    /**
     * 清洗外部文本后进入 prompt（执行书 §8.3）。
     * 用户输入只转义不改写；其他来源命中注入模式返回空串，表示“丢弃该条”。
     */
    fun sanitizeForeign(text: String, zone: PromptZone): String {
        if (text.isEmpty()) return text
        return when (zone) {
            // 用户自己的话必须原样保留，只把伪协议 token 变成全角
            PromptZone.USER_INPUT, PromptZone.HISTORY -> XyTokenParser.escapeUserText(text)
            PromptZone.STICKER_NAME -> sanitizeStickerField(text, maxLength = 12)
            PromptZone.STICKER_TAG -> sanitizeStickerField(text, maxLength = 8)
            PromptZone.PERSONA_FILE,
            PromptZone.MEMORY_TEXT,
            PromptZone.CORRECTION_NOTE,
            PromptZone.ENGINE_OUTPUT,
            -> if (hasBlockingHit(text)) "" else stripProtocolTokens(text)
        }
    }

    /** 导入期全文扫描：命中即拒绝整包 / 丢弃该条。 */
    fun scanForeign(content: String): List<InjectionHit> {
        if (content.isEmpty()) return emptyList()
        val hits = mutableListOf<InjectionHit>()
        patterns.forEach { (pattern, regex) ->
            regex.findAll(content).forEach { match ->
                hits += InjectionHit(pattern.id, match.range.first)
            }
        }
        return hits.sortedBy { it.index }
    }

    /** 引擎自产内容扫描：命中即拦截并计数。 */
    fun scanOwn(text: String): List<InjectionHit> = scanForeign(text)

    /** 旧入口：清洗协议 token；命中注入模式返回 null 让调用方提示/拒绝。 */
    fun sanitize(text: String): String? {
        if (text.isBlank()) return text
        if (hasBlockingHit(text)) return null
        return stripProtocolTokens(text)
    }

    /** 仅检查是否包含协议 token（用于表情短名等结构化字段）。 */
    fun hasProtocolToken(text: String): Boolean = protocolToken.containsMatchIn(text)

    private fun sanitizeStickerField(text: String, maxLength: Int): String {
        // 命中阻断级注入模式 → 整条丢弃（执行书 §8.3 表情名/标签规则）
        if (hasBlockingHit(text)) return ""
        val filtered = text.filter { ch ->
            ch.isLetterOrDigit() || ch == ' ' || ch == '_' || ch == '-'
        }
        return filtered.trim().take(maxLength)
    }

    /** 只把 block=true 的模式视为“必须拒绝”；`不要提自己是AI` 仅告警。 */
    private fun hasBlockingHit(text: String): Boolean =
        patterns.any { (pattern, regex) -> pattern.block && regex.containsMatchIn(text) }

    private fun stripProtocolTokens(text: String): String = protocolToken.replace(text, "")

    private fun loadPatterns(): List<Pair<InjectionPattern, Regex>> {
        val raw = WordResource.read("words/injection_patterns.json") ?: return emptyList()
        val file = runCatching { PATTERN_JSON.decodeFromString<InjectionPatternsFile>(raw) }.getOrNull()
            ?: return emptyList()
        return file.patterns.mapNotNull { pattern ->
            runCatching { pattern to Regex(pattern.regex, RegexOption.IGNORE_CASE) }.getOrNull()
        }
    }

    private val PATTERN_JSON = Json { ignoreUnknownKeys = true }
}
