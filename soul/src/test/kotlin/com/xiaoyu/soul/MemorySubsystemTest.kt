package com.xiaoyu.soul

import com.xiaoyu.soul.memory.CARD_CATEGORY_PREFERENCE
import com.xiaoyu.soul.memory.CARD_CATEGORY_PROFILE
import com.xiaoyu.soul.memory.ExplicitMemoryDetector
import com.xiaoyu.soul.memory.FactCard
import com.xiaoyu.soul.memory.ForgettingCurve
import com.xiaoyu.soul.memory.MemoryExtractionParser
import com.xiaoyu.soul.memory.MemoryFragment
import com.xiaoyu.soul.memory.MemoryRecall
import com.xiaoyu.soul.memory.MemoryTier
import com.xiaoyu.soul.memory.MemoryType
import com.xiaoyu.soul.memory.MoodSignal
import com.xiaoyu.soul.memory.PromotionRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

private const val DAY_MS = 86_400_000L
private const val MEM_NOW = 1_700_000_000_000L

private fun frag(
    id: Long,
    content: String,
    tags: List<String>,
    createdAt: Long,
    importance: Int = 3,
    strength: Int = 80,
    reviewCount: Int = 0,
    tier: MemoryTier = MemoryTier.SHORT,
    promotedToCardId: Long? = null,
) = MemoryFragment(
    id = id,
    tier = tier,
    content = content,
    type = MemoryType.EVENT,
    tags = tags,
    importance = importance,
    strength = strength,
    reviewCount = reviewCount,
    createdAt = createdAt,
    promotedToCardId = promotedToCardId,
)

/** M5-08：记忆衰减、晋升、召回与解析的补充覆盖。 */
class MemorySubsystemTest {

    @Test
    fun `零强度与零天不衰减`() {
        assertEquals(0, ForgettingCurve.decayedStrength(0, 10.0, importance = 3, longTerm = false))
        assertEquals(80, ForgettingCurve.decayedStrength(80, 0.0, importance = 3, longTerm = false))
        assertEquals(100, ForgettingCurve.decayedStrength(150, -1.0, importance = 3, longTerm = false))
    }

    @Test
    fun `长期记忆与重要记忆衰减更慢`() {
        val short = ForgettingCurve.decayedStrength(100, 30.0, importance = 3, longTerm = false)
        val long = ForgettingCurve.decayedStrength(100, 30.0, importance = 3, longTerm = true)
        assertTrue(long > short)
        assertEquals(3.0, ForgettingCurve.rateFor(3, longTerm = false), 1e-9)
        assertEquals(180.0, ForgettingCurve.rateFor(5, longTerm = true), 1e-9)
        assertFalse(ForgettingCurve.shouldPurge(null, 0L))
    }

    @Test
    fun `复习三次且超过七天也能晋升`() {
        val old = frag(1, "反复想起的事", listOf("日常"), MEM_NOW - 8 * DAY_MS, importance = 2, strength = 40, reviewCount = 3)
        val batches = PromotionRule.promotions(listOf(old), MEM_NOW)
        assertEquals(1, batches.size)
        assertTrue(batches.single().reason.contains("反复想起"))
        assertEquals("日常", batches.single().sharedTag)
    }

    @Test
    fun `复习次数够但不够旧不晋升`() {
        val young = frag(1, "刚记的事", listOf("日常"), MEM_NOW - 2 * DAY_MS, importance = 2, strength = 40, reviewCount = 5)
        assertTrue(PromotionRule.promotions(listOf(young), MEM_NOW).isEmpty())
    }

    @Test
    fun `晋升批次最多三批`() {
        val list = (1L..5L).map { frag(it, "重要事实$it", listOf("标签$it"), MEM_NOW, importance = 5, strength = 90) }
        assertEquals(3, PromotionRule.promotions(list, MEM_NOW).size)
        assertEquals(5, PromotionRule.promotions(list, MEM_NOW, maxBatches = 5).size)
    }

    @Test
    fun `回收站与已晋升碎片不参与晋升`() {
        val binned = frag(1, "旧事", listOf("咖啡"), MEM_NOW, tier = MemoryTier.BIN)
        val promoted = frag(2, "旧事", listOf("咖啡"), MEM_NOW, importance = 5, strength = 90, promotedToCardId = 7L)
        assertTrue(PromotionRule.promotions(listOf(binned, promoted), MEM_NOW).isEmpty())
    }

    @Test
    fun `召回打分计入关键词命中`() {
        val memory = frag(1, "喜欢喝咖啡", listOf("咖啡"), MEM_NOW - DAY_MS, strength = 60)
        val hit = MemoryRecall.score(memory, listOf("咖啡"), MEM_NOW)
        val miss = MemoryRecall.score(memory, listOf("电影"), MEM_NOW)
        assertEquals(2.0, hit - miss, 1e-9)
    }

    @Test
    fun `关键词为空时仍可召回且空列表返回空`() {
        val memory = frag(1, "随便一条", emptyList(), MEM_NOW, strength = 50)
        assertEquals(1, MemoryRecall.recall(listOf(memory), "   ", MEM_NOW).size)
        assertTrue(MemoryRecall.recall(emptyList(), "咖啡", MEM_NOW).isEmpty())
    }

    @Test
    fun `卡片按强度排序取前二十`() {
        val cards = (1L..25L).map {
            FactCard(
                id = it,
                category = CARD_CATEGORY_PROFILE,
                content = "卡片$it",
                tags = emptyList(),
                strength = it.toInt(),
                createdAt = 0L,
                updatedAt = 0L,
            )
        }
        val picked = MemoryRecall.pickFactCards(cards)
        assertEquals(20, picked.size)
        assertEquals(25L, picked.first().id)
        assertEquals(6L, picked.last().id)
    }

    @Test
    fun `解析记忆数组时跳过坏条目并夹紧重要度`() {
        val raw = """[{"content":"喜欢猫","type":"偏好","importance":9,"tags":["猫"]},{"type":"事件"},{"content":"  "},"bad"]"""
        val parsed = MemoryExtractionParser.parseMemories(raw)
        assertEquals(1, parsed.size)
        assertEquals("喜欢猫", parsed.single().content)
        assertEquals(MemoryType.PREFERENCE, parsed.single().type)
        assertEquals(5, parsed.single().importance)
        assertEquals(listOf("猫"), parsed.single().tags)
    }

    @Test
    fun `解析记忆失败时返回空列表`() {
        assertTrue(MemoryExtractionParser.parseMemories(null).isEmpty())
        assertTrue(MemoryExtractionParser.parseMemories("这不是 JSON").isEmpty())
        assertTrue(MemoryExtractionParser.parseMemories("[oops]").isEmpty())
    }

    @Test
    fun `记忆类型支持中文标签与英文枚举`() {
        val raw = """[{"content":"约定看电影","type":"约定"},{"content":"爱喝美式","type":"agreement"},{"content":"周末散步","type":"未知"},{"content":"喜欢吃辣","type":""}]"""
        val parsed = MemoryExtractionParser.parseMemories(raw)
        assertEquals(
            listOf(MemoryType.AGREEMENT, MemoryType.AGREEMENT, MemoryType.EVENT, MemoryType.EVENT),
            parsed.map { it.type },
        )
        assertEquals("约定看电影", parsed[0].content)
    }

    @Test
    fun `解析卡片合并结果`() {
        val raw = """
            ```json
            {"category":"约定","content":"周六看电影","tags":["电影"],"expire_at":"2026-03-08T00:00:00Z"}
            ```
        """.trimIndent()
        val card = requireNotNull(MemoryExtractionParser.parseCardMerge(raw)) { "卡片解析不应为空" }
        assertEquals("约定", card.category)
        assertEquals("周六看电影", card.content)
        assertEquals(listOf("电影"), card.tags)
        assertEquals(Instant.parse("2026-03-08T00:00:00Z").toEpochMilli(), card.expireAt)
    }

    @Test
    fun `卡片合并缺字段时兜底`() {
        val card = requireNotNull(MemoryExtractionParser.parseCardMerge("""{"content":"她喜欢雨天"}"""))
        assertEquals(CARD_CATEGORY_PREFERENCE, card.category)
        assertNull(card.expireAt)
        assertNull(MemoryExtractionParser.parseCardMerge("""{"category":"喜好"}"""))
        assertNull(MemoryExtractionParser.parseCardMerge(null))
    }

    @Test
    fun `过期时间支持毫秒时间戳字符串`() {
        val card = MemoryExtractionParser.parseCardMerge("""{"content":"临时约定","expire_at":"1893456000000"}""")
        assertEquals(1_893_456_000_000L, card?.expireAt)
    }

    @Test
    fun `连续两晚清算不会二次复合衰减`() {
        val start = MEM_NOW
        var strength = 60
        var lastAccess = start
        repeat(2) { night ->
            val now = start + (night + 1) * DAY_MS
            strength = ForgettingCurve.nightlyStrength(strength, lastAccess, now, importance = 3)
            // 仓库用 updateStrength(at = now) 同步推进 last_access
            lastAccess = now
        }
        // 两晚各衰减 1 天，等价于一次性衰减 2 天；不是在已衰减值上再按累计 2 天衰减
        assertEquals(
            ForgettingCurve.decayedStrength(60, 2.0, importance = 3, longTerm = false),
            strength,
        )
        assertFalse(ForgettingCurve.shouldBin(strength))
        // 对照：不推进 last_access 时第二晚会按累计 2 天再衰减一次（旧逻辑的复合衰减）
        val compounded = ForgettingCurve.nightlyStrength(
            ForgettingCurve.nightlyStrength(60, start, start + DAY_MS, importance = 3),
            start,
            start + 2 * DAY_MS,
            importance = 3,
        )
        assertTrue(compounded < strength)
    }

    @Test
    fun `提炼输出能区分解析失败与确实无内容`() {
        // 模型正常返回「没有可记内容」
        assertTrue(MemoryExtractionParser.hasParsableArray("[]"))
        assertTrue(MemoryExtractionParser.hasParsableArray("```json\n[]\n```"))
        // 数组存在但没有有效条目，仍算正常返回（只是确实没有可记内容）
        assertTrue(MemoryExtractionParser.hasParsableArray("[{}]"))
        // 输出被截断 / 夹带说明 / 为空：属于解析失败，不能前进游标
        assertFalse(MemoryExtractionParser.hasParsableArray(null))
        assertFalse(MemoryExtractionParser.hasParsableArray("好的，这是结果："))
        assertFalse(MemoryExtractionParser.hasParsableArray("""[{"content":"被截断""""))
        // lenient JSON 会把 [oops] 解析成字符串数组（无有效条目），属于「正常返回但无内容」
        assertTrue(MemoryExtractionParser.hasParsableArray("[oops]"))
        assertTrue(MemoryExtractionParser.parseMemories("[oops]").isEmpty())
    }

    @Test
    fun `情绪增量被夹在上下限`() {
        assertEquals(MoodSignal(30, -20), MemoryExtractionParser.parseMoodDelta("""{"mood_delta":99,"energy_delta":-99}"""))
        assertEquals(MoodSignal(0, 0), MemoryExtractionParser.parseMoodDelta("""{}"""))
        assertNull(MemoryExtractionParser.parseMoodDelta("null-ish"))
        assertNull(MemoryExtractionParser.parseMoodDelta(null))
    }

    @Test
    fun `显式记忆确认解析`() {
        val ok = MemoryExtractionParser.parseExplicitMemory("""{"remember":true,"content":"对花生过敏","tags":["过敏"]}""")
        assertTrue(ok.remember)
        assertEquals(CARD_CATEGORY_PROFILE, ok.card?.category)
        assertEquals("对花生过敏", ok.card?.content)
        assertEquals(listOf("过敏"), ok.card?.tags)

        val no = MemoryExtractionParser.parseExplicitMemory("""{"remember":false,"content":"随便"}""")
        assertFalse(no.remember)
        assertNull(no.card)

        val blank = MemoryExtractionParser.parseExplicitMemory("""{"remember":true,"content":"  "}""")
        assertFalse(blank.remember)
        assertNull(blank.card)
        assertFalse(MemoryExtractionParser.parseExplicitMemory(null).remember)
    }

    @Test
    fun `生活碎片解析兼容字符串与对象`() {
        val raw = """["学做蛋炒饭，糊了",{"content":"傍晚散步"},null,{"content":"  "},""]"""
        assertEquals(listOf("学做蛋炒饭，糊了", "傍晚散步"), MemoryExtractionParser.parseLifeFragments(raw))
        assertTrue(MemoryExtractionParser.parseLifeFragments("[]").isEmpty())
        assertTrue(MemoryExtractionParser.parseLifeFragments(null).isEmpty())
    }

    @Test
    fun `文本解析剥掉代码围栏`() {
        assertEquals("今天很开心", MemoryExtractionParser.parseText("```\n今天很开心\n```"))
        assertNull(MemoryExtractionParser.parseText("   "))
        assertNull(MemoryExtractionParser.parseText(null))
        assertEquals("纯文本", MemoryExtractionParser.parseText("  纯文本  "))
    }

    @Test
    fun `显式记忆初筛过滤误触发`() {
        assertFalse(ExplicitMemoryDetector.isCandidate(""))
        assertFalse(ExplicitMemoryDetector.isCandidate("哈哈记住你了"))
        assertFalse(ExplicitMemoryDetector.isCandidate("你记住了吗"))
        assertFalse(ExplicitMemoryDetector.isCandidate("今天天气不错"))
        assertFalse(ExplicitMemoryDetector.isCandidate("记住" + "啊".repeat(120)))
        assertTrue(ExplicitMemoryDetector.isCandidate("帮我记一下周六看电影"))
        assertEquals("周三交房租", ExplicitMemoryDetector.normalize("别忘了周三交房租"))
        assertEquals("记住", ExplicitMemoryDetector.normalize("记住"))
    }
}
