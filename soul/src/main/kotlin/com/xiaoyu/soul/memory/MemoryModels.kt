package com.xiaoyu.soul.memory

enum class MemoryType(val label: String) {
    EVENT("事件"),
    PREFERENCE("偏好"),
    AGREEMENT("约定"),
    EMOTION("情绪"),
    RELATION("关系"),
    ;

    companion object {
        fun from(value: String?): MemoryType {
            val v = value?.trim().orEmpty()
            return entries.firstOrNull { it.label == v || it.name.equals(v, ignoreCase = true) } ?: EVENT
        }
    }
}

enum class MemoryTier { SHORT, BIN }

/** 记忆碎片（短期记忆 / 回收站），对应 memories 表（PRD 5.6）。 */
data class MemoryFragment(
    val id: Long = 0,
    val tier: MemoryTier = MemoryTier.SHORT,
    val content: String,
    val type: MemoryType,
    val tags: List<String>,
    val importance: Int,
    val strength: Int,
    val reviewCount: Int = 0,
    val createdAt: Long,
    val lastAccessAt: Long = createdAt,
    val sourceSessionId: Long? = null,
    val promotedToCardId: Long? = null,
    val binAt: Long? = null,
)

/** 长期事实卡片（fact_cards 表）。 */
data class FactCard(
    val id: Long = 0,
    val category: String,
    val content: String,
    val tags: List<String>,
    val strength: Int = 100,
    val expireAt: Long? = null,
    val source: String = FactSource.AI_EXTRACT,
    val createdAt: Long,
    val updatedAt: Long,
    val sourceMemoryIds: List<Long> = emptyList(),
)

object FactSource {
    const val AI_EXTRACT = "ai_extract"
    const val USER_COMMAND = "user_command"
}

const val CARD_CATEGORY_PROFILE = "个人信息"
const val CARD_CATEGORY_PREFERENCE = "喜好"
const val CARD_CATEGORY_EVENT = "重要事件"
const val CARD_CATEGORY_RELATION = "人际关系"
const val CARD_CATEGORY_AGREEMENT = "约定"

val CARD_CATEGORIES = listOf(
    CARD_CATEGORY_PROFILE,
    CARD_CATEGORY_PREFERENCE,
    CARD_CATEGORY_EVENT,
    CARD_CATEGORY_RELATION,
    CARD_CATEGORY_AGREEMENT,
)

data class ExtractedMemory(
    val content: String,
    val type: MemoryType,
    val importance: Int,
    val tags: List<String>,
)

data class ExtractedCard(
    val category: String,
    val content: String,
    val tags: List<String>,
    val expireAt: Long? = null,
)
