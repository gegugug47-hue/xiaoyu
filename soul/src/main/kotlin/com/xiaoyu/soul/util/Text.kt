package com.xiaoyu.soul.util

import kotlin.math.max

/** 粗略 token 估算：中日韩字符按 1.5 字符/token，其余按 4 字符/token。 */
object TokenEstimator {
    fun estimate(text: String): Int {
        if (text.isEmpty()) return 0
        var cjk = 0
        var other = 0
        for (ch in text) {
            if (isCjk(ch)) cjk++ else other++
        }
        return ((cjk / 1.5) + (other / 4.0)).toInt().coerceAtLeast(1)
    }

    fun estimateAll(texts: Iterable<String>): Int = texts.sumOf { estimate(it) }

    private fun isCjk(ch: Char): Boolean {
        val code = ch.code
        return code in 0x3000..0x9FFF || code in 0xF900..0xFAFF || code in 0xFF00..0xFFEF
    }
}

/**
 * 关键词提取：中文取 2 字滑动 bigram（短句/长句都可用），英文取单词与数字。
 * 用于 V1 的记忆召回打分（PRD 5.5），不依赖分词库。
 */
object KeywordExtractor {
    private val stopwords = setOf(
        "我们", "你们", "他们", "这个", "那个", "什么", "怎么", "就是", "可以", "一个",
        "今天", "现在", "然后", "因为", "所以", "如果", "但是", "还是", "已经", "自己",
        "the", "and", "you", "that", "this", "with", "for", "are", "was", "what",
    )

    fun extract(text: String, max: Int = 16): List<String> {
        val out = LinkedHashSet<String>()
        val latin = Regex("[A-Za-z0-9_]{2,}")
        latin.findAll(text).forEach { m ->
            val w = m.value.lowercase()
            if (w !in stopwords && w.length >= 2) out += w
        }
        val cjkRun = Regex("[\\u4e00-\\u9fff]{2,}")
        for (match in cjkRun.findAll(text)) {
            if (out.size >= max) break
            val run = match.value
            for (i in 0 until run.length - 1) {
                val gram = run.substring(i, i + 2)
                if (gram !in stopwords) out += gram
                if (out.size >= max) break
            }
        }
        return out.take(max)
    }

    /** 标签命中：双向包含即算命中（标签通常比用户消息短）。 */
    fun hits(tags: List<String>, keywords: List<String>): Int {
        var count = 0
        for (tag in tags) {
            val t = tag.trim().lowercase()
            if (t.isEmpty()) continue
            if (keywords.any { k -> k.contains(t, ignoreCase = true) || t.contains(k, ignoreCase = true) }) count++
        }
        return count
    }

    fun containsAny(text: String, words: List<String>): Boolean =
        words.any { w -> w.isNotEmpty() && text.contains(w, ignoreCase = true) }

    fun truncate(text: String, maxChars: Int): String =
        if (text.length <= maxChars) text else text.substring(0, max(maxChars, 1)).trimEnd() + "…"
}
