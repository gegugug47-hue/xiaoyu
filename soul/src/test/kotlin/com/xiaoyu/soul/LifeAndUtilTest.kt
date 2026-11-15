package com.xiaoyu.soul

import com.xiaoyu.soul.core.BrainException
import com.xiaoyu.soul.core.BrainRole
import com.xiaoyu.soul.core.TokenUsage
import com.xiaoyu.soul.life.LifeSimulator
import com.xiaoyu.soul.sticker.StickerCooldown
import com.xiaoyu.soul.util.KeywordExtractor
import com.xiaoyu.soul.util.TokenEstimator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** M5-08：生活碎片、token 估算、关键词与其余纯函数工具。 */
class LifeAndUtilTest {

    @Test
    fun `生活碎片解析并限制条数`() {
        val drafts = LifeSimulator.parse("""["学做蛋炒饭，糊了","傍晚散步"]""")
        assertEquals(2, drafts.size)
        assertEquals("学做蛋炒饭，糊了", drafts.first().content)
        assertEquals("生活", drafts.first().type)
        assertTrue(LifeSimulator.parse("bad").isEmpty())
        assertTrue(LifeSimulator.parse(null).isEmpty())

        val many = LifeSimulator.parse((1..7).joinToString(",", "[", "]") { "\"片段$it\"" })
        assertEquals(LifeSimulator.DAILY_MAX, many.size)
    }

    @Test
    fun `每日生活碎片数量在二到五之间`() {
        repeat(50) { seed ->
            val count = LifeSimulator.dailyCount(Random(seed))
            assertTrue(count in LifeSimulator.DAILY_MIN..LifeSimulator.DAILY_MAX)
        }
    }

    @Test
    fun `注入生活碎片会过滤空白`() {
        assertEquals(listOf("散步", "做饭"), LifeSimulator.injectable(listOf("", "散步", "  ", "做饭", "看书"), limit = 2))
    }

    @Test
    fun `空文本不产生 token`() {
        assertEquals(0, TokenEstimator.estimate(""))
        assertEquals(0, TokenEstimator.estimateAll(emptyList()))
        assertEquals(1, TokenEstimator.estimate("你好"))
        assertEquals(2, TokenEstimator.estimate("hello world"))
        assertEquals(3, TokenEstimator.estimateAll(listOf("你好", "hello world")))
    }

    @Test
    fun `停用词不会成为关键词`() {
        assertTrue(KeywordExtractor.extract("我们").isEmpty())
        assertEquals(listOf("coffee", "latte"), KeywordExtractor.extract("Coffee Latte"))
        assertTrue(KeywordExtractor.extract("咖啡奶茶蛋糕", max = 2).size <= 2)
    }

    @Test
    fun `标签命中与文本截断`() {
        assertEquals(1, KeywordExtractor.hits(listOf("咖啡", "   "), listOf("想喝咖啡")))
        assertEquals(0, KeywordExtractor.hits(listOf("电影"), listOf("咖啡")))
        assertTrue(KeywordExtractor.containsAny("今天好累", listOf("好累")))
        assertFalse(KeywordExtractor.containsAny("今天好累", listOf("开心", "")))
        assertEquals("hello…", KeywordExtractor.truncate("hello world", 5))
        assertEquals("hi", KeywordExtractor.truncate("hi", 5))
    }

    @Test
    fun `表情冷却可以自定义轮数`() {
        val cooldown = StickerCooldown(cooldownTurns = 1)
        assertTrue(cooldown.canSend())
        cooldown.markSent()
        assertFalse(cooldown.canSend())
        cooldown.onTurnStarted()
        assertTrue(cooldown.canSend())
    }

    @Test
    fun `token 用量合计与错误文案默认值`() {
        assertEquals(7, TokenUsage(3, 4).total)
        assertEquals(0, TokenUsage().total)
        val error = BrainException("boom")
        assertEquals("boom", error.userMessage)
        assertEquals("boom", error.message)
        assertNull(error.role)
        assertEquals("友好提示", BrainException("x", userMessage = "友好提示", role = BrainRole.CHAT).userMessage)
    }

    @Test
    fun `全局常量保持稳定`() {
        assertEquals("v1", SoulInfo.PROMPT_SCHEMA_VERSION)
        assertEquals("小鱼", SoulInfo.APP_SOUL_NAME)
    }
}
