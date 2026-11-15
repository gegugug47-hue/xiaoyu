package com.xiaoyu.soul.engine

import com.xiaoyu.soul.core.MessageRole
import com.xiaoyu.soul.core.SoulMessage
import com.xiaoyu.soul.memory.FactCard
import com.xiaoyu.soul.memory.MemoryFragment
import com.xiaoyu.soul.memory.PromotionRule
import com.xiaoyu.soul.mood.MoodSystem
import com.xiaoyu.soul.persona.PersonaIdentity
import com.xiaoyu.soul.persona.PersonaStyle
import com.xiaoyu.soul.prompt.PromptAssembler
import com.xiaoyu.soul.prompt.PromptContext
import com.xiaoyu.soul.prompt.PromptKeys
import com.xiaoyu.soul.prompt.PromptLibrary
import com.xiaoyu.soul.proactive.ProactiveMaterial
import java.time.LocalDate

/**
 * 灵魂引擎门面：只做“想让她说什么、带着什么状态说”，不碰网络与 UI。
 * 大脑（模型）由 App 侧 BrainProvider 注入。
 */
class SoulEngine(
    val library: PromptLibrary = PromptLibrary(),
) {
    private val assembler = PromptAssembler(library)

    fun assemble(context: PromptContext) = assembler.assemble(context)

    fun transcript(messages: List<SoulMessage>): String = messages.joinToString("\n") {
        when (it.role) {
            MessageRole.USER -> "用户：${it.content}"
            MessageRole.ASSISTANT -> "她：${it.content}"
            MessageRole.SYSTEM -> ""
        }
    }.trim()

    fun extractMemoriesPrompt(sessionTitle: String, messages: List<SoulMessage>): String =
        library.render(
            PromptKeys.EXTRACT_MEMORIES,
            mapOf("title" to sessionTitle, "transcript" to transcript(messages)),
        )

    fun promoteMemoriesPrompt(batch: PromotionRule.PromotionBatch, cards: List<FactCard>): String =
        library.render(
            PromptKeys.PROMOTE_MEMORIES,
            mapOf(
                "memories" to batch.memories.joinToString("\n") {
                    "- [${it.type.label}/重要度${it.importance}] ${it.content} #${it.tags.joinToString(",")}"
                },
                "cards" to cards.joinToString("\n") { "- [${it.category}] ${it.content}" }.ifBlank { "（无）" },
            ),
        )

    fun diaryPrompt(
        identity: PersonaIdentity,
        style: PersonaStyle,
        date: LocalDate,
        memories: List<String>,
        moodDescription: String,
        favorabilityDescription: String,
        life: List<String>,
        longForm: Boolean,
        backfill: Boolean,
    ): String = library.render(
        PromptKeys.WRITE_DIARY,
        mapOf(
            "persona" to "${identity.name}，${style.name}口吻。${style.contentMarkdown.trim()}",
            "date" to date.toString(),
            "memories" to memories.joinToString("；").ifBlank { "（今天没什么特别的事）" },
            "mood" to moodDescription,
            "favorability" to favorabilityDescription,
            "life" to life.joinToString("；").ifBlank { "（普通的一天）" },
            "length" to if (longForm) "写长一点，300~500 字" else "短一点，100~200 字",
            "backfill_note" to if (backfill) "这是补写的日记，开头可以自然带出“前天忘了记…”" else "",
        ),
    )

    fun lifePrompt(identity: PersonaIdentity, style: PersonaStyle, moodDescription: String): String =
        library.render(
            PromptKeys.GENERATE_LIFE,
            mapOf(
                "persona" to "${identity.name}。${identity.contentMarkdown.trim()}\n说话风格：${style.contentMarkdown.trim()}",
                "mood" to moodDescription,
            ),
        )

    fun proactivePrompt(
        identity: PersonaIdentity,
        style: PersonaStyle,
        moodDescription: String,
        material: ProactiveMaterial?,
    ): String = library.render(
        PromptKeys.PROACTIVE_MESSAGE,
        mapOf(
            "persona" to "${identity.name}。${style.contentMarkdown.trim()}",
            "mood" to moodDescription,
            "material" to (material?.seed ?: "没什么特别的，就是突然想起他"),
        ),
    )

    fun explicitMemoryCheckPrompt(text: String): String =
        library.render(PromptKeys.EXPLICIT_MEMORY_CHECK, mapOf("text" to text))

    fun explicitMemoryAck(fact: String): String =
        library.render(PromptKeys.EXPLICIT_MEMORY_ACK, mapOf("fact" to fact))

    fun moodAnalysisPrompt(messages: List<SoulMessage>): String =
        library.render(PromptKeys.ANALYZE_MOOD, mapOf("transcript" to transcript(messages)))

    fun moodDescription(mood: Int, energy: Int, hourOfDay: Int): String =
        MoodSystem.describe(mood, energy, hourOfDay)
}
