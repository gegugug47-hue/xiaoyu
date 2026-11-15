package com.xiaoyu.soul.memory

/**
 * 短期 → 长期晋升条件（PRD 5.3），满足其一：
 * 1. 同一实体标签碎片累计 ≥3 条
 * 2. importance ≥ 4 且强度 ≥ 60
 * 3. 复习次数 ≥ 3 且存活超过 7 天
 */
object PromotionRule {
    const val TAG_THRESHOLD = 3
    const val IMPORTANT_STRENGTH = 60
    const val REVIEW_THRESHOLD = 3
    const val MIN_AGE_DAYS = 7L

    data class PromotionBatch(
        val memories: List<MemoryFragment>,
        val reason: String,
        val sharedTag: String?,
    )

    fun promotions(
        memories: List<MemoryFragment>,
        now: Long,
        maxBatches: Int = 3,
    ): List<PromotionBatch> {
        val eligible = memories.filter {
            it.tier == MemoryTier.SHORT && it.promotedToCardId == null
        }
        val batches = mutableListOf<PromotionBatch>()
        val used = mutableSetOf<Long>()

        // 条件 1：同标签累计 ≥3
        val byTag = eligible
            .flatMap { memory -> memory.tags.distinct().map { tag -> tag to memory } }
            .groupBy({ it.first }, { it.second })
        byTag.entries
            .sortedByDescending { it.value.size }
            .forEach { (tag, list) ->
                val unique = list.distinctBy { it.id }
                if (unique.size >= TAG_THRESHOLD) {
                    val fresh = unique.filter { it.id !in used }
                    if (fresh.isNotEmpty()) {
                        used += fresh.map { it.id }
                        batches += PromotionBatch(fresh, "同一标签「$tag」累计 ${unique.size} 次", tag)
                    }
                }
            }

        // 条件 2 / 3：重要且强，或反复复习的旧记忆
        eligible.filter { it.id !in used }.forEach { memory ->
            val ageDays = (now - memory.createdAt) / 86_400_000.0
            val strongEnough = memory.importance >= 4 && memory.strength >= IMPORTANT_STRENGTH
            val reviewed = memory.reviewCount >= REVIEW_THRESHOLD && ageDays >= MIN_AGE_DAYS
            if (strongEnough || reviewed) {
                used += memory.id
                batches += PromotionBatch(
                    listOf(memory),
                    if (strongEnough) "重要度高且记忆强度足够" else "反复想起且存活超过 7 天",
                    memory.tags.firstOrNull(),
                )
            }
        }
        return batches.take(maxBatches)
    }
}
