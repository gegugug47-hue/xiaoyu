package com.xiaoyu.app.data.search

/**
 * 中文全文索引的「应用层 n-gram」实现（执行书 6.8 / M7-04）。
 *
 * 选择理由：SQLite FTS4 没有中文分词器，unicode61 会把一整串汉字当成一个 token，
 * 直接 MATCH 关键词几乎永远匹配不上。这里在**写入时**把汉字串展开成「单字 + 二元组」
 * 作为独立 token 存入 index_text，查询时同样把关键词拆成二元组再 OR 起来。
 * 这样「今天天气」可以被「今天 / 天天 / 天气」召回，代价只是索引体积约为正文的 2~3 倍，
 * 对本地聊天量级（十万条以内）完全可接受，而且不引入任何新依赖。
 */
object MessageSearchIndex {

    /** 单条消息最多索引这么多字符，防止超长文本把索引撑爆。 */
    private const val MAX_INDEX_CHARS = 4000

    /** 单次查询最多展开的 token 数，避免超长关键词生成巨型 MATCH 表达式。 */
    private const val MAX_QUERY_TOKENS = 24

    /** 生成索引文本：保留原文 + 汉字单字 + 汉字二元组。 */
    fun buildIndexText(content: String): String {
        if (content.isBlank()) return ""
        val text = content.take(MAX_INDEX_CHARS)
        val sb = StringBuilder(text.length * 3)
        sb.append(text)
        val run = StringBuilder()
        fun flush() {
            if (run.isEmpty()) return
            val chars = run.toString()
            for (ch in chars) {
                sb.append(' ').append(ch)
            }
            for (i in 0 until chars.length - 1) {
                sb.append(' ').append(chars[i]).append(chars[i + 1])
            }
            run.setLength(0)
        }
        for (ch in text) {
            if (isCjk(ch)) run.append(ch) else flush()
        }
        flush()
        return sb.toString()
    }

    /** 把用户输入的关键词拼成 FTS4 的 MATCH 表达式；无有效 token 时返回 null。 */
    fun buildMatchQuery(keyword: String): String? {
        val cleaned = keyword.trim().take(64)
        if (cleaned.isEmpty()) return null
        val tokens = LinkedHashSet<String>()
        val cjk = StringBuilder()
        val word = StringBuilder()

        fun flushCjk() {
            if (cjk.isEmpty()) return
            val run = cjk.toString()
            if (run.length == 1) {
                tokens += run
            } else {
                for (i in 0 until run.length - 1) tokens += run.substring(i, i + 2)
            }
            cjk.setLength(0)
        }

        fun flushWord() {
            if (word.isEmpty()) return
            tokens += word.toString()
            word.setLength(0)
        }

        for (ch in cleaned) {
            when {
                isCjk(ch) -> {
                    flushWord()
                    cjk.append(ch)
                }
                ch.isLetterOrDigit() -> {
                    flushCjk()
                    word.append(ch)
                }
                else -> {
                    flushCjk()
                    flushWord()
                }
            }
        }
        flushCjk()
        flushWord()
        if (tokens.isEmpty()) return null
        return tokens.take(MAX_QUERY_TOKENS)
            .joinToString(" OR ") { "\"${it.replace("\"", "")}\"" }
    }

    data class Snippet(val text: String, val highlights: List<IntRange>)

    /** 截取命中附近的片段，并给出相对片段起点的命中区间（供 UI 高亮）。 */
    fun snippet(content: String, keyword: String, radius: Int = 30): Snippet {
        val k = keyword.trim()
        if (k.isEmpty()) return Snippet(content.take(radius * 2), emptyList())
        val lower = content.lowercase()
        var hit = lower.indexOf(k.lowercase())
        if (hit < 0) {
            // 关键词里带空格/标点时，退化为取第一个可命中的二元组
            hit = matchTokens(k).firstNotNullOfOrNull { token -> lower.indexOf(token.lowercase()) } ?: -1
        }
        if (hit < 0) return Snippet(content.take(radius * 2), emptyList())
        val start = (hit - radius).coerceAtLeast(0)
        val end = (start + radius * 2).coerceAtMost(content.length)
        val text = content.substring(start, end)
        val ranges = mutableListOf<IntRange>()
        val needles = (matchTokens(k) + k.lowercase()).distinct().filter { it.isNotBlank() }
        for (needle in needles) {
            var from = 0
            while (ranges.size < 8) {
                val index = text.lowercase().indexOf(needle.lowercase(), from)
                if (index < 0) break
                ranges += index until (index + needle.length)
                from = index + needle.length
            }
        }
        return Snippet(text, ranges.sortedBy { it.first })
    }

    private fun matchTokens(keyword: String): List<String> {
        val tokens = mutableListOf<String>()
        val cjk = StringBuilder()
        fun flush() {
            if (cjk.isEmpty()) return
            val run = cjk.toString()
            if (run.length == 1) {
                tokens += run
            } else {
                for (i in 0 until run.length - 1) tokens += run.substring(i, i + 2)
            }
            cjk.setLength(0)
        }
        for (ch in keyword) {
            if (isCjk(ch)) cjk.append(ch) else flush()
        }
        flush()
        return tokens.ifEmpty { listOf(keyword.trim()) }
    }

    private fun isCjk(ch: Char): Boolean {
        val code = ch.code
        return code in 0x3400..0x4DBF ||
            code in 0x4E00..0x9FFF ||
            code in 0xF900..0xFAFF ||
            code in 0x3040..0x30FF
    }
}
