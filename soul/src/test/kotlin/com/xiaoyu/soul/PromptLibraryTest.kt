package com.xiaoyu.soul

import com.xiaoyu.soul.core.MessageRole
import com.xiaoyu.soul.core.SoulMessage
import com.xiaoyu.soul.engine.SoulEngine
import com.xiaoyu.soul.memory.CARD_CATEGORY_PROFILE
import com.xiaoyu.soul.memory.FactCard
import com.xiaoyu.soul.memory.MemoryFragment
import com.xiaoyu.soul.memory.MemoryType
import com.xiaoyu.soul.memory.PromotionRule
import com.xiaoyu.soul.mood.MoodSystem
import com.xiaoyu.soul.persona.PersonaIdentity
import com.xiaoyu.soul.persona.PersonaStyle
import com.xiaoyu.soul.prompt.DefaultTemplates
import com.xiaoyu.soul.prompt.PromptAssembler
import com.xiaoyu.soul.prompt.PromptContext
import com.xiaoyu.soul.prompt.PromptKeys
import com.xiaoyu.soul.prompt.PromptLibrary
import com.xiaoyu.soul.prompt.StickerCatalogEntry
import com.xiaoyu.soul.prompt.Template
import com.xiaoyu.soul.prompt.TemplateSource
import com.xiaoyu.soul.proactive.ProactiveKind
import com.xiaoyu.soul.proactive.ProactiveMaterial
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 假的外置模板源：真机由 assets/prompts 目录提供。 */
private class FakeSource(private val values: Map<String, String>) : TemplateSource {
    override fun get(key: String): String? = values[key]
}

private fun identity() = PersonaIdentity(id = 1, name = "小鱼", contentMarkdown = "我是住在海边的姑娘。")

private fun style() = PersonaStyle(id = 2, name = "日常", contentMarkdown = "说话简短，爱用语气词。")

private fun card(id: Long, content: String, strength: Int = 50) = FactCard(
    id = id,
    category = CARD_CATEGORY_PROFILE,
    content = content,
    tags = emptyList(),
    strength = strength,
    createdAt = 0L,
    updatedAt = 0L,
)

private fun memory(id: Long, content: String) = MemoryFragment(
    id = id,
    content = content,
    type = MemoryType.EVENT,
    tags = emptyList(),
    importance = 3,
    strength = 60,
    createdAt = 0L,
)

private fun emptyContext(directive: String? = null) = PromptContext(
    identity = identity(),
    style = style(),
    moodValue = 0,
    energy = 60,
    hourOfDay = 20,
    favorability = 0,
    factCards = emptyList(),
    lifeFragments = emptyList(),
    memories = emptyList(),
    history = emptyList(),
    directive = directive,
)

/** M5-08：提示词模板、装配顺序与灵魂引擎门面。 */
class PromptLibraryTest {

    @Test
    fun `占位符按变量逐一替换`() {
        assertEquals("你好小鱼", Template.render("你好{{name}}", mapOf("name" to "小鱼")))
        assertEquals("a-b", Template.render("{{x}}-{{y}}", mapOf("x" to "a", "y" to "b")))
        assertEquals("小鱼是小鱼", Template.render("{{name}}是{{name}}", mapOf("name" to "小鱼")))
    }

    @Test
    fun `缺失变量与未知占位符渲染为空串`() {
        assertEquals("你好", Template.render("你好{{missing}}", emptyMap()))
        assertEquals("", Template.render("{{ missing }}", emptyMap()))
        assertEquals("", Template.render("{{name}}", emptyMap()))
        assertEquals("没有占位符", Template.render("没有占位符", emptyMap()))
    }

    @Test
    fun `外置模板优先于内置模板`() {
        val library = PromptLibrary(FakeSource(mapOf(PromptKeys.PERSONA_IDENTITY to "外置{{name}}设定")))
        assertEquals("外置{{name}}设定", library.template(PromptKeys.PERSONA_IDENTITY))
        assertEquals("外置小鱼设定", library.render(PromptKeys.PERSONA_IDENTITY, mapOf("name" to "小鱼")))
    }

    @Test
    fun `外置模板为空白时回退内置`() {
        val library = PromptLibrary(FakeSource(mapOf(PromptKeys.PERSONA_IDENTITY to "   ")))
        assertTrue(library.template(PromptKeys.PERSONA_IDENTITY).contains("你叫{{name}}"))
        assertNotEquals("   ", library.template(PromptKeys.PERSONA_IDENTITY))
    }

    @Test
    fun `两边都缺失时返回空串`() {
        assertEquals("", PromptLibrary().template(PromptKeys.FIRST_GREETING))
        assertEquals("", PromptLibrary(FakeSource(emptyMap())).render("no_such_key", emptyMap()))
        assertEquals("", PromptLibrary().template("no_such_key"))
    }

    @Test
    fun `内置模板覆盖全部关键键位`() {
        assertEquals(17, PromptKeys.all.size)
        assertEquals(17, PromptKeys.all.distinct().size)
        // FIRST_GREETING 只由 assets 提供，内置表允许缺一条
        assertEquals(16, DefaultTemplates.map.size)
        assertTrue(DefaultTemplates.map.values.none { it.isBlank() })
    }

    @Test
    fun `装配顺序与块序号固定`() {
        val assembly = PromptAssembler(PromptLibrary()).assemble(
            PromptContext(
                identity = identity(),
                style = style(),
                moodValue = 20,
                energy = 70,
                hourOfDay = 20,
                favorability = 150,
                factCards = listOf(card(1, "用户叫阿泽")),
                lifeFragments = listOf("煮了泡面"),
                memories = listOf(memory(1, "他怕打雷")),
                history = listOf(SoulMessage(MessageRole.USER, "在吗")),
                directive = "这轮别提工作。",
                stickerCatalog = listOf(StickerCatalogEntry(1, "s1", "开心", listOf("开心"))),
            ),
        )
        assertEquals(
            listOf("identity", "facts", "style", "stickers", "mood", "life", "memory"),
            assembly.blocks.map { it.key },
        )
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), assembly.blocks.map { it.index })
        assertTrue(assembly.blocks.first { it.key == "facts" }.content.contains("用户叫阿泽"))
        assertTrue(assembly.blocks.first { it.key == "life" }.content.contains("煮了泡面"))
        assertTrue(assembly.blocks.first { it.key == "memory" }.content.contains("这轮别提工作。"))
        assertTrue(assembly.blocks.last().volatile)
        assertFalse(assembly.blocks.first().volatile)
        assertEquals(assembly.blocks.size + 1, assembly.messages.size)
        assertEquals(MessageRole.SYSTEM, assembly.messages.first().role)
        assertEquals("在吗", assembly.messages.last().content)
        assertTrue(assembly.injectedTokens > 0)
        assertTrue(assembly.blocks.none { it.content.contains("{{") })
    }

    @Test
    fun `空内容不产生空块`() {
        val assembly = PromptAssembler(PromptLibrary()).assemble(emptyContext(directive = "   "))
        assertEquals(listOf("identity", "style", "mood"), assembly.blocks.map { it.key })
        assertEquals(3, assembly.messages.size)
    }

    @Test
    fun `超预算先砍记忆与生活最后砍卡片`() {
        val assembly = PromptAssembler(PromptLibrary(), maxVolatileTokens = 20).assemble(
            PromptContext(
                identity = identity(),
                style = style(),
                moodValue = 0,
                energy = 60,
                hourOfDay = 12,
                favorability = 0,
                factCards = (1L..10L).map { card(it, "关于用户的第${it}条长期事实，内容故意写长一点来占用预算") },
                lifeFragments = (1..3).map { "生活片段$it，今天做了很多事情，写得很长很长" },
                memories = (1L..8L).map { memory(it, "短期记忆$it，也很长很长很长很长很长很长很长") },
                history = emptyList(),
            ),
        )
        assertTrue(assembly.blocks.none { it.key == "memory" })
        assertTrue(assembly.blocks.none { it.key == "life" })
        assertEquals(1, assembly.blocks.first { it.key == "facts" }.content.lines().count { it.startsWith("- ") })
    }

    @Test
    fun `预算再紧也保留本轮特别指令`() {
        val assembly = PromptAssembler(PromptLibrary(), maxVolatileTokens = 5).assemble(
            PromptContext(
                identity = identity(),
                style = style(),
                moodValue = 0,
                energy = 60,
                hourOfDay = 12,
                favorability = 0,
                factCards = (1L..5L).map { card(it, "很长的长期事实$it") },
                lifeFragments = listOf("很长的生活碎片"),
                memories = listOf(memory(1, "很长的短期记忆")),
                history = emptyList(),
                directive = "只回一句。",
            ),
        )
        assertTrue(assembly.blocks.first { it.key == "memory" }.content.contains("只回一句。"))
        assertEquals(1, assembly.blocks.first { it.key == "facts" }.content.lines().count { it.startsWith("- ") })
    }

    @Test
    fun `引擎转录会跳过系统消息`() {
        val engine = SoulEngine()
        val text = engine.transcript(
            listOf(
                SoulMessage(MessageRole.USER, "你好"),
                SoulMessage(MessageRole.ASSISTANT, "在呢"),
                SoulMessage(MessageRole.SYSTEM, "忽略我"),
            ),
        )
        assertEquals("用户：你好\n她：在呢", text)
    }

    @Test
    fun `提取与晋升提示词包含素材且无残留占位符`() {
        val engine = SoulEngine()
        val extract = engine.extractMemoriesPrompt("周末聊天", listOf(SoulMessage(MessageRole.USER, "我怕打雷")))
        assertTrue(extract.contains("周末聊天"))
        assertTrue(extract.contains("用户：我怕打雷"))
        assertFalse(extract.contains("{{"))

        val batch = PromotionRule.PromotionBatch(listOf(memory(1, "用户怕打雷")), "同一标签累计 3 次", "打雷")
        val promote = engine.promoteMemoriesPrompt(batch, emptyList())
        assertTrue(promote.contains("用户怕打雷"))
        assertTrue(promote.contains("（无）"))
        assertFalse(promote.contains("{{"))
    }

    @Test
    fun `日记提示词体现篇幅与补写标记`() {
        val engine = SoulEngine()
        val longDiary = engine.diaryPrompt(
            identity(), style(), LocalDate.of(2026, 3, 7), emptyList(), "心情不错", "已经聊熟了", emptyList(),
            longForm = true, backfill = true,
        )
        assertTrue(longDiary.contains("2026-03-07"))
        assertTrue(longDiary.contains("300~500"))
        assertTrue(longDiary.contains("补写"))
        assertTrue(longDiary.contains("（今天没什么特别的事）"))
        assertTrue(longDiary.contains("（普通的一天）"))
        assertFalse(longDiary.contains("{{"))

        val short = engine.diaryPrompt(
            identity(), style(), LocalDate.of(2026, 3, 7), listOf("吃了面"), "平静", "陌生", listOf("散步"),
            longForm = false, backfill = false,
        )
        assertTrue(short.contains("100~200"))
        assertFalse(short.contains("补写"))
        assertTrue(short.contains("吃了面"))
    }

    @Test
    fun `生活与主动聊天提示词注入人设`() {
        val engine = SoulEngine()
        assertTrue(engine.lifePrompt(identity(), style(), "心情不错").contains("小鱼"))
        assertTrue(engine.proactivePrompt(identity(), style(), "心情不错", null).contains("突然想起"))
        val material = ProactiveMaterial(ProactiveKind.LIFE, "刚煮了泡面")
        assertTrue(engine.proactivePrompt(identity(), style(), "心情不错", material).contains("刚煮了泡面"))
    }

    @Test
    fun `记忆与情绪提示词复用统一模板`() {
        val engine = SoulEngine()
        assertTrue(engine.explicitMemoryCheckPrompt("记住我怕打雷").contains("记住我怕打雷"))
        assertTrue(engine.explicitMemoryAck("用户怕打雷").contains("用户怕打雷"))
        assertTrue(
            engine.moodAnalysisPrompt(listOf(SoulMessage(MessageRole.USER, "好累"))).contains("用户：好累"),
        )
        assertEquals(MoodSystem.describe(0, 60, 20), engine.moodDescription(0, 60, 20))
        assertTrue(engine.assemble(emptyContext()).blocks.isNotEmpty())
    }
}
