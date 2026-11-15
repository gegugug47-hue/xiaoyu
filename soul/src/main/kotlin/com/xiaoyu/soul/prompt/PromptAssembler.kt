package com.xiaoyu.soul.prompt

import com.xiaoyu.soul.core.SoulMessage
import com.xiaoyu.soul.memory.FactCard
import com.xiaoyu.soul.memory.MemoryFragment
import com.xiaoyu.soul.mood.FavorabilityLevel
import com.xiaoyu.soul.mood.FavorabilitySystem
import com.xiaoyu.soul.mood.MoodSystem
import com.xiaoyu.soul.persona.PersonaIdentity
import com.xiaoyu.soul.persona.PersonaStyle
import com.xiaoyu.soul.util.TokenEstimator

/**
 * 表情目录条目（执行书 7.4.2）：序号用于 `[xy:s=编号]`，与注入时的顺序一一对应。
 */
data class StickerCatalogEntry(
    val index: Int,
    val id: String,
    val name: String,
    val tags: List<String> = emptyList(),
    val moodTags: List<String> = emptyList(),
)

data class PromptContext(
    val identity: PersonaIdentity,
    val style: PersonaStyle,
    val moodValue: Int,
    val energy: Int,
    val hourOfDay: Int,
    val favorability: Int,
    val factCards: List<FactCard>,
    val lifeFragments: List<String>,
    val memories: List<MemoryFragment>,
    val history: List<SoulMessage>,
    val directive: String? = null,
    /** L3 表情目录；为空时整段省略，省 token。 */
    val stickerCatalog: List<StickerCatalogEntry> = emptyList(),
)

data class PromptBlock(
    val index: Int,
    val key: String,
    val label: String,
    val content: String,
    val volatile: Boolean,
)

data class PromptAssembly(
    val blocks: List<PromptBlock>,
    val messages: List<SoulMessage>,
    val injectedTokens: Int,
)

/**
 * 按 PRD 4.1 的固定顺序装配上下文：
 * ①身份 ②长期卡片 ③风格 ④状态 ⑤生活碎片 ⑥短期记忆（+本轮特别指令） ⑦最近对话。
 * 顺序即缓存策略：稳定内容前置，易变内容后置；注入记忆控制在约 800 token 内。
 */
class PromptAssembler(
    private val library: PromptLibrary,
    private val maxVolatileTokens: Int = 800,
) {
    fun assemble(context: PromptContext): PromptAssembly {
        val identityBlock = library.render(
            PromptKeys.PERSONA_IDENTITY,
            mapOf(
                "name" to context.identity.name,
                "identity" to context.identity.contentMarkdown.trim(),
            ),
        )
        val styleBlock = library.render(
            PromptKeys.PERSONA_STYLE,
            mapOf(
                "name" to context.identity.name,
                "style_name" to context.style.name,
                "style" to context.style.contentMarkdown.trim(),
            ),
        )
        val moodBlock = buildString {
            append(
                library.render(
                    PromptKeys.MOOD_STATE,
                    mapOf(
                        "mood" to MoodSystem.describe(context.moodValue, context.energy, context.hourOfDay),
                        "favorability" to FavorabilitySystem.describe(FavorabilityLevel.of(context.favorability)),
                    ),
                )
            )
            // 心情很差时不隐藏欢快表情（避免“无表情可用”），只用一句状态提示自然收敛（7.4.7）
            if (context.stickerCatalog.isNotEmpty() && context.moodValue <= -40) {
                append("\n你现在心情不太好，不太会用欢快的表情。")
            }
        }
        val stickerBlock = if (context.stickerCatalog.isEmpty()) "" else library.render(
            PromptKeys.STICKERS,
            mapOf(
                "stickers" to context.stickerCatalog.joinToString("\n") { entry ->
                    val tags = entry.tags.take(2).joinToString(",")
                    if (tags.isBlank()) "${entry.index} ${entry.name}"
                    else "${entry.index} ${entry.name} [$tags]"
                },
            ),
        )

        var cards = context.factCards.take(20)
        var life = context.lifeFragments.take(3)
        var memories = context.memories.take(8)

        fun cardText(list: List<FactCard>): String = list.joinToString("\n") { "- ${it.content}" }
        fun lifeText(list: List<String>): String = list.joinToString("\n") { "- $it" }
        fun memoryText(list: List<MemoryFragment>): String = list.joinToString("\n") { "- ${it.content}" }

        fun volatileTokens(): Int = TokenEstimator.estimate(
            cardText(cards) + lifeText(life) + memoryText(memories) +
                (context.directive ?: "")
        )

        // 超预算：先砍记忆碎片，再砍生活碎片，最后砍卡片（长期卡片优先级最高）
        while (volatileTokens() > maxVolatileTokens && memories.isNotEmpty()) {
            memories = memories.dropLast(1)
        }
        while (volatileTokens() > maxVolatileTokens && life.isNotEmpty()) {
            life = life.dropLast(1)
        }
        while (volatileTokens() > maxVolatileTokens && cards.size > 1) {
            cards = cards.dropLast(1)
        }

        val factBlock = if (cards.isEmpty()) "" else library.render(
            PromptKeys.FACT_CARDS,
            mapOf("cards" to cardText(cards)),
        )
        val lifeBlock = if (life.isEmpty()) "" else library.render(
            PromptKeys.LIFE_FRAGMENTS,
            mapOf("fragments" to lifeText(life)),
        )
        val memoryBlock = buildString {
            if (memories.isNotEmpty()) {
                append(
                    library.render(
                        PromptKeys.MEMORY_FRAGMENTS,
                        mapOf("memories" to memoryText(memories)),
                    )
                )
            }
            val directive = context.directive?.takeIf { it.isNotBlank() }
            if (directive != null) {
                if (isNotEmpty()) append("\n\n")
                append(directive)
            }
        }

        val blocks = buildList {
            add(PromptBlock(1, "identity", "身份层", identityBlock, false))
            if (factBlock.isNotBlank()) add(PromptBlock(2, "facts", "长期记忆卡片", factBlock, false))
            add(PromptBlock(3, "style", "风格层", styleBlock, false))
            // L3 表情目录：稳定内容，放在易变的状态段之前（利于前缀缓存）
            if (stickerBlock.isNotBlank()) add(PromptBlock(4, "stickers", "表情目录", stickerBlock, false))
            add(PromptBlock(5, "mood", "情绪与好感度", moodBlock, true))
            if (lifeBlock.isNotBlank()) add(PromptBlock(6, "life", "生活碎片", lifeBlock, true))
            if (memoryBlock.isNotBlank()) add(PromptBlock(7, "memory", "短期记忆", memoryBlock, true))
        }

        val messages = buildList {
            blocks.forEach { add(SoulMessage(com.xiaoyu.soul.core.MessageRole.SYSTEM, it.content)) }
            addAll(context.history)
        }

        val injected = TokenEstimator.estimate(
            cardText(cards) + lifeText(life) + memoryText(memories) + (context.directive ?: "")
        )
        return PromptAssembly(blocks, messages, injected)
    }
}
