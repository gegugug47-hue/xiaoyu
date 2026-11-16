package com.xiaoyu.soul

import com.xiaoyu.soul.persona.PersonaIdentity
import com.xiaoyu.soul.persona.PersonaStyle
import com.xiaoyu.soul.prompt.PromptAssembler
import com.xiaoyu.soul.prompt.PromptContext
import com.xiaoyu.soul.prompt.PromptLibrary
import com.xiaoyu.soul.prompt.StickerCatalogEntry
import com.xiaoyu.soul.sticker.StickerCooldown
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 表情目录注入与冷却（执行书 7.4.2 / 7.4.7）。 */
class StickerPromptTest {

    private fun context(stickers: List<StickerCatalogEntry>, mood: Int = 0) = PromptContext(
        identity = PersonaIdentity(1, "小鱼", "我是小鱼"),
        style = PersonaStyle(1, "日常", "说话简短"),
        moodValue = mood,
        energy = 60,
        hourOfDay = 21,
        favorability = 50,
        factCards = emptyList(),
        lifeFragments = emptyList(),
        memories = emptyList(),
        history = emptyList(),
        stickerCatalog = stickers,
    )

    private fun sticker(index: Int, name: String, tags: List<String>) =
        StickerCatalogEntry(index = index, id = "builtin-fish-$index", name = name, tags = tags)

    @Test
    fun `表情目录按语义名渲染并带标签`() {
        val assembly = PromptAssembler(PromptLibrary()).assemble(
            context(listOf(sticker(1, "开心到转圈", listOf("开心", "兴奋")))),
        )
        val block = assembly.blocks.firstOrNull { it.key == "stickers" }
        assertNotNull(block)
        // v3 H-6：用语义名而非数字编号
        assertTrue(block.content.contains("开心到转圈 [开心,兴奋]"))
        assertTrue(block.content.contains("[xy:s=名字]"))
        assertFalse(block.content.contains("{{"))
    }

    @Test
    fun `没有表情时整段省略`() {
        val assembly = PromptAssembler(PromptLibrary()).assemble(context(emptyList()))
        assertNull(assembly.blocks.firstOrNull { it.key == "stickers" })
    }

    @Test
    fun `心情很差时追加收敛提示`() {
        val assembly = PromptAssembler(PromptLibrary()).assemble(
            context(listOf(sticker(1, "开心到转圈", emptyList())), mood = -50),
        )
        assertTrue(assembly.blocks.any { it.content.contains("心情不太好") })
    }

    @Test
    fun `表情冷却四轮后才能再发`() {
        val cooldown = StickerCooldown(4)
        assertTrue(cooldown.canSend())
        cooldown.markSent()
        assertFalse(cooldown.canSend())
        repeat(3) { cooldown.onTurnStarted() }
        assertFalse(cooldown.canSend())
        cooldown.onTurnStarted()
        assertTrue(cooldown.canSend())
    }
}
