package com.xiaoyu.soul.memory

import com.xiaoyu.soul.util.KeywordExtractor
import kotlin.math.exp
import kotlin.math.min

/**
 * 记忆召回打分（PRD 5.5）：
 * score = 关键词命中数 × 2 + strength/100 × 3 + 新鲜度（越近越高，最多 2 分）
 */
object MemoryRecall {
    const val DEFAULT_LIMIT = 6
    const val MAX_LIMIT = 8

    fun score(memory: MemoryFragment, keywords: List<String>, now: Long): Double {
        val hits = KeywordExtractor.hits(memory.tags, keywords)
        val strengthScore = memory.strength / 100.0 * 3.0
        val days = ((now - memory.createdAt) / 86_400_000.0).coerceAtLeast(0.0)
        val freshness = exp(-days / 14.0) * 2.0
        return hits * 2.0 + strengthScore + freshness
    }

    fun recall(
        fragments: List<MemoryFragment>,
        userText: String,
        now: Long,
        limit: Int = DEFAULT_LIMIT,
    ): List<MemoryFragment> {
        if (fragments.isEmpty()) return emptyList()
        val keywords = KeywordExtractor.extract(userText)
        val scored = fragments
            .filter { it.tier == MemoryTier.SHORT && it.promotedToCardId == null }
            .map { it to score(it, keywords, now) }
            .filter { (memory, s) ->
                keywords.isEmpty() || s > memory.strength / 100.0 * 3.0
            }
            .sortedByDescending { it.second }
        return scored.take(min(limit, MAX_LIMIT)).map { it.first }
    }

    fun pickFactCards(cards: List<FactCard>, limit: Int = 20): List<FactCard> =
        cards.sortedByDescending { it.strength }.take(limit)
}
