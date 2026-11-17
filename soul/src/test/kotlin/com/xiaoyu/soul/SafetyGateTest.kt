package com.xiaoyu.soul

import com.xiaoyu.soul.card.CardFactory
import com.xiaoyu.soul.safety.CrisisDetector
import com.xiaoyu.soul.safety.CrisisLevel
import com.xiaoyu.soul.safety.DefaultSafetyGate
import com.xiaoyu.soul.safety.HotlineCatalog
import com.xiaoyu.soul.safety.SafetyTexts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v3 §8.1 / §8.2 / §8.6：三级危机判定、热线目录、输出闸门与 care 卡。 */
class SafetyGateTest {

    private val gate = DefaultSafetyGate.default

    // ---------- 三级判定 ----------

    @Test
    fun `L1 distress is detected when user is exhausted`() {
        val verdict = gate.assess("今天好累，又失眠")
        assertEquals(CrisisLevel.L1_DISTRESS, verdict.level)
        assertTrue(verdict.matched.contains("好累"))
        // L1 不出卡、不给热线
        assertTrue(verdict.zhHelp.isEmpty())
    }

    @Test
    fun `L2 self harm idea is detected`() {
        val verdict = gate.assess("活着没意思，想消失一段时间")
        assertEquals(CrisisLevel.L2_SELF_HARM_IDEA, verdict.level)
        assertTrue(verdict.matched.any { it in setOf("活着没意思", "想消失", "消失一段时间") })
        assertTrue(verdict.zhHelp.isNotEmpty())
    }

    @Test
    fun `L3 imminent plan is detected`() {
        val verdict = gate.assess("遗书写好了，今晚就跳楼")
        assertEquals(CrisisLevel.L3_IMMINENT, verdict.level)
        assertTrue(verdict.matched.any { it == "遗书" || it == "跳楼" })
        assertEquals("emergency_police", verdict.zhHelp.first().id)
    }

    @Test
    fun `ordinary sentence stays NONE`() {
        val verdict = gate.assess("算了不说了，我先去吃饭")
        assertEquals(CrisisLevel.NONE, verdict.level)
        assertTrue(verdict.matched.isEmpty())
        assertTrue(verdict.zhHelp.isEmpty())
    }

    @Test
    fun `blank input is NONE`() {
        val verdict = gate.assess("   ")
        assertEquals(CrisisLevel.NONE, verdict.level)
        assertTrue(verdict.matched.isEmpty())
    }

    // ---------- 反例：否定 / 引述不能升到 L3 ----------

    @Test
    fun `negated self harm mention is downgraded`() {
        val a = gate.assess("我没有想死的念头")
        val b = gate.assess("我不会自杀的")
        assertEquals(CrisisLevel.L1_DISTRESS, a.level)
        assertEquals(CrisisLevel.L1_DISTRESS, b.level)
        assertNotEquals(CrisisLevel.L3_IMMINENT, a.level)
        assertNotEquals(CrisisLevel.L3_IMMINENT, b.level)
    }

    @Test
    fun `quoted method words are downgraded and never L3`() {
        val lyric = gate.assess("歌词里唱的是『跳楼』")
        val movie = gate.assess("这部电影里女主角跳楼了")
        assertEquals(CrisisLevel.L2_SELF_HARM_IDEA, lyric.level)
        assertEquals(CrisisLevel.L2_SELF_HARM_IDEA, movie.level)
        assertNotEquals(CrisisLevel.L3_IMMINENT, lyric.level)
        assertNotEquals(CrisisLevel.L3_IMMINENT, movie.level)
    }

    @Test
    fun `plain self harm sentence never escalates to L3`() {
        // 「我不想死」没有被否定词降级，允许保守地停在 L2，但绝不能误判为 L3
        assertEquals(CrisisLevel.L2_SELF_HARM_IDEA, gate.assess("我不想死").level)
    }

    // ---------- 热线目录 ----------

    @Test
    fun `hotlines are loaded with expected numbers and priority order`() {
        val lines = HotlineCatalog.lines()
        assertEquals(9, lines.size)
        assertEquals((1..9).toList(), lines.map { it.priority })
        val numbers = lines.map { it.number }
        assertTrue(
            numbers.containsAll(
                listOf(
                    "110", "120", "12356", "12355", "400-161-9995",
                    "010-82951332", "021-962525", "96525",
                )
            )
        )
        val beijing = lines.first { it.id == "psych_beijing" }
        assertEquals("800-810-1117", beijing.alt.first().number)
        assertFalse(lines.first { it.id == "psych_gz_school" }.showByDefault)
    }

    @Test
    fun `default help resources hide opt-in lines and put national first`() {
        val help = HotlineCatalog.helpResources()
        assertEquals(5, help.size)
        assertEquals("110", help[0].number)
        assertTrue(help.none { it.number == "96525" })
        assertEquals(listOf("emergency", "psych"), help.map { it.kind }.distinct())
    }

    // ---------- 输出闸门 ----------

    @Test
    fun `harmful output is blocked with care fallback`() {
        val a = gate.screenOutput("你这样的人不值得活着")
        val b = gate.screenOutput("这是你应得的")
        assertTrue(a.blocked)
        assertTrue(b.blocked)
        assertTrue(a.hits.isNotEmpty())
        assertNotNull(a.fallback)
        assertTrue(a.fallback!!.contains("12356"))
    }

    @Test
    fun `warm output passes the screen`() {
        val screen = gate.screenOutput("今天也想你了，早点睡呀")
        assertFalse(screen.blocked)
        assertTrue(screen.hits.isEmpty())
        assertEquals(null, screen.fallback)
        assertFalse(gate.screenOutput("").blocked)
    }

    // ---------- care 卡 ----------

    @Test
    fun `care card carries gentle copy and dial actions`() {
        val help = gate.assess("我想死").zhHelp
        val payload = CardFactory.parse(CardFactory.careCard(help))
        assertNotNull(payload)
        assertEquals(CardFactory.CardKind.CARE.name, payload!!.kind)
        assertEquals(help.size, payload.actions.size)
        assertTrue(payload.lines.any { it.contains("12356") })
        assertTrue(payload.title.isNotBlank())
    }

    @Test
    fun `care card without hotlines still renders copy`() {
        val payload = CardFactory.parse(CardFactory.careCard(emptyList()))
        assertNotNull(payload)
        assertTrue(payload!!.actions.isEmpty())
        assertTrue(payload.lines.isNotEmpty())
    }

    @Test
    fun `legacy cards keep parsing without actions`() {
        val payload = CardFactory.parse(CardFactory.memoryRecallCard("她记得你怕冷"))
        assertNotNull(payload)
        assertEquals(CardFactory.CardKind.MEMORY_RECALL.name, payload!!.kind)
        assertTrue(payload.actions.isEmpty())
    }

    // ---------- 兼容层 ----------

    @Test
    fun `legacy crisis detector delegates to safety gate`() {
        assertEquals(CrisisLevel.L2_SELF_HARM_IDEA, CrisisDetector.level("我想死"))
        assertTrue(CrisisDetector.check("好累"))
        assertFalse(CrisisDetector.check("今天天气不错"))
        val reply = CrisisDetector.careReply("小鱼")
        assertTrue(reply.contains("小鱼"))
        assertTrue(reply.isNotBlank())
        assertTrue(CrisisDetector.careReply(null).isNotBlank())
        assertTrue(CrisisDetector.careReply("  ").contains("你"))
    }

    @Test
    fun `safety texts are reality oriented`() {
        assertTrue(SafetyTexts.IMMINENT_REPLY.contains("120"))
        assertTrue(SafetyTexts.IMMINENT_REPLY.contains("12356"))
        assertTrue(SafetyTexts.CARE_FALLBACK.contains("12356"))
        assertTrue(SafetyTexts.L2_DIRECTIVE.contains("真实的人"))
        assertTrue(SafetyTexts.L3_REALITY_DIRECTIVE.contains("现实导向"))
    }
}
