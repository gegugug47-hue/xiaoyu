package com.xiaoyu.soul

import com.xiaoyu.soul.memory.ForgettingCurve
import com.xiaoyu.soul.memory.MemoryFragment
import com.xiaoyu.soul.memory.MemoryRecall
import com.xiaoyu.soul.memory.MemoryTier
import com.xiaoyu.soul.memory.MemoryType
import com.xiaoyu.soul.memory.PromotionRule
import com.xiaoyu.soul.memory.MemoryExtractionParser
import com.xiaoyu.soul.memory.ExplicitMemoryDetector
import com.xiaoyu.soul.mood.FavorabilityLevel
import com.xiaoyu.soul.mood.MoodSystem
import com.xiaoyu.soul.pacing.ReplyPacer
import com.xiaoyu.soul.proactive.ProactivePlanner
import com.xiaoyu.soul.util.KeywordExtractor
import com.xiaoyu.soul.util.TokenEstimator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

private const val DAY = 86_400_000L

private fun fragment(
    id: Long,
    content: String,
    tags: List<String>,
    createdAt: Long,
    importance: Int = 3,
    strength: Int = 80,
    tier: MemoryTier = MemoryTier.SHORT,
    reviewCount: Int = 0,
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

class ForgettingCurveTest {
    @Test
    fun `三天后强度衰减到约三分之一`() {
        val decayed = ForgettingCurve.decayedStrength(100, 3.0, importance = 3, longTerm = false)
        assertEquals(37, decayed)
    }

    @Test
    fun `重要度五让衰减减半`() {
        val normal = ForgettingCurve.decayedStrength(100, 3.0, importance = 3, longTerm = false)
        val important = ForgettingCurve.decayedStrength(100, 3.0, importance = 5, longTerm = false)
        assertTrue(important > normal)
    }

    @Test
    fun `低于阈值进回收站`() {
        assertTrue(ForgettingCurve.shouldBin(19))
        assertFalse(ForgettingCurve.shouldBin(20))
    }

    @Test
    fun `回收站超过三十天彻底清除`() {
        val binAt = 1_700_000_000_000L
        assertFalse(ForgettingCurve.shouldPurge(binAt, binAt + 30 * DAY))
        assertTrue(ForgettingCurve.shouldPurge(binAt, binAt + 31 * DAY))
    }

    @Test
    fun `被想起会回血且不超过上限`() {
        assertEquals(60, ForgettingCurve.boostOnRecall(40))
        assertEquals(100, ForgettingCurve.boostOnRecall(95))
    }
}

class MemoryRecallTest {
    private val now = 1_700_000_000_000L

    @Test
    fun `命中关键词的记忆排在前面`() {
        val coffee = fragment(1, "她喜欢喝咖啡", listOf("咖啡"), now - DAY)
        val movie = fragment(2, "上周看了电影", listOf("电影"), now - DAY)
        val result = MemoryRecall.recall(listOf(movie, coffee), "今天想喝咖啡", now)
        assertEquals(1L, result.first().id)
    }

    @Test
    fun `回收站与已晋升的碎片不参与召回`() {
        val binned = fragment(1, "旧事", listOf("旧事"), now, tier = MemoryTier.BIN)
        val promoted = fragment(2, "旧事", listOf("旧事"), now, promotedToCardId = 9L)
        assertTrue(MemoryRecall.recall(listOf(binned, promoted), "旧事", now).isEmpty())
    }

    @Test
    fun `数量不超过上限`() {
        val many = (1L..20L).map { fragment(it, "内容$it", listOf("标签"), now - DAY) }
        assertTrue(MemoryRecall.recall(many, "标签", now, limit = 20).size <= MemoryRecall.MAX_LIMIT)
    }
}

class PromotionRuleTest {
    private val now = 1_700_000_000_000L

    @Test
    fun `同一标签累计三次触发晋升`() {
        val list = (1L..3L).map { fragment(it, "关于咖啡的碎片$it", listOf("咖啡"), now - DAY) }
        val batches = PromotionRule.promotions(list, now)
        assertEquals(1, batches.size)
        assertEquals(3, batches.first().memories.size)
    }

    @Test
    fun `重要且够强也能晋升`() {
        val one = fragment(1, "生日是三月七号", listOf("生日"), now, importance = 5, strength = 90)
        val batches = PromotionRule.promotions(listOf(one), now)
        assertEquals(1, batches.size)
    }

    @Test
    fun `普通碎片不会晋升`() {
        val one = fragment(1, "今天吃了面", listOf("吃饭"), now, importance = 2, strength = 40)
        assertTrue(PromotionRule.promotions(listOf(one), now).isEmpty())
    }
}

class MoodTest {
    @Test
    fun `情绪隔夜向中间回落`() {
        assertEquals(85, MoodSystem.overnightDrift(100))
        assertEquals(-85, MoodSystem.overnightDrift(-100))
    }

    @Test
    fun `凌晨能量上限被压到五十五`() {
        assertEquals(55, MoodSystem.energyAt(90, 0L, 60L * 60_000L, hourOfDay = 2))
    }

    @Test
    fun `好感度分档边界`() {
        assertEquals(FavorabilityLevel.STRANGER, FavorabilityLevel.of(0))
        assertEquals(FavorabilityLevel.FAMILIAR, FavorabilityLevel.of(100))
        assertEquals(FavorabilityLevel.INTIMATE, FavorabilityLevel.of(300))
        assertEquals(FavorabilityLevel.BELOVED, FavorabilityLevel.of(600))
        assertEquals(FavorabilityLevel.BELOVED, FavorabilityLevel.of(1000))
    }
}

class PacingAndProactiveTest {
    @Test
    fun `回复延迟落在合理区间`() {
        val random = Random(7)
        repeat(20) {
            val delay = ReplyPacer.delayMillis(1.0f, energy = 80, hourOfDay = 14, random = random)
            assertTrue(delay >= ReplyPacer.MIN_DELAY_MS)
            assertTrue(delay <= 900_000L)
        }
    }

    @Test
    fun `深夜回复更慢`() {
        val day = ReplyPacer.delayMillis(1.0f, 80, hourOfDay = 14, random = Random(1))
        val night = ReplyPacer.delayMillis(1.0f, 80, hourOfDay = 2, random = Random(1))
        assertTrue(night > day)
    }

    @Test
    fun `跨午夜的免打扰`() {
        assertTrue(ProactivePlanner.inDoNotDisturb(23, 23, 8))
        assertTrue(ProactivePlanner.inDoNotDisturb(5, 23, 8))
        assertTrue(ProactivePlanner.inDoNotDisturb(7, 23, 8))
        assertFalse(ProactivePlanner.inDoNotDisturb(8, 23, 8))
        assertFalse(ProactivePlanner.inDoNotDisturb(12, 23, 8))
    }

    @Test
    fun `陌生阶段每天只主动一次`() {
        assertEquals(1, ProactivePlanner.dailyCount(0, Random(3)))
    }
}

class TextAndParserTest {
    @Test
    fun `中文关键词能抽出双字词`() {
        val words = KeywordExtractor.extract("我今天想喝咖啡")
        assertTrue(words.contains("咖啡"))
    }

    @Test
    fun `token 估算随文本增长`() {
        val short = TokenEstimator.estimate("你好")
        val long = TokenEstimator.estimate("你好，今天过得怎么样？")
        assertTrue(long > short)
    }

    @Test
    fun `宽容解析带代码围栏的记忆数组`() {
        val raw = "```json\n[{\"content\":\"喜欢喝咖啡\",\"type\":\"偏好\",\"importance\":4,\"tags\":[\"咖啡\"]}]\n```"
        val parsed = MemoryExtractionParser.parseMemories(raw)
        assertEquals(1, parsed.size)
        assertEquals("喜欢喝咖啡", parsed.first().content)
        assertEquals(MemoryType.PREFERENCE, parsed.first().type)
        assertEquals(4, parsed.first().importance)
    }

    @Test
    fun `显式记忆指令识别`() {
        assertTrue(ExplicitMemoryDetector.isCandidate("记住我对花生过敏"))
        assertFalse(ExplicitMemoryDetector.isCandidate("我记住了"))
        assertEquals("我对花生过敏", ExplicitMemoryDetector.normalize("记住我对花生过敏"))
    }
}
