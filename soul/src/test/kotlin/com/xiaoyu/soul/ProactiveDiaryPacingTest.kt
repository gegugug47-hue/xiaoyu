package com.xiaoyu.soul

import com.xiaoyu.soul.diary.DiaryPlan
import com.xiaoyu.soul.diary.DiaryPlanner
import com.xiaoyu.soul.diary.DiarySignal
import com.xiaoyu.soul.pacing.ReplyPacer
import com.xiaoyu.soul.proactive.ProactiveKind
import com.xiaoyu.soul.proactive.ProactivePlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.random.Random

private val ZONE_SH: ZoneId = ZoneId.of("Asia/Shanghai")
private val PLAN_DATE: LocalDate = LocalDate.of(2026, 3, 7)
private val PLAN_NOW: Instant = Instant.parse("2026-03-07T13:00:00Z")

/** 在固定种子空间里找一个满足条件的计划，避免依赖某个具体种子的取值。 */
private fun findPlan(signal: DiarySignal, predicate: (DiaryPlan) -> Boolean): DiaryPlan? {
    for (seed in 0 until 500) {
        val plan = DiaryPlanner.plan(signal, PLAN_NOW, ZONE_SH, Random(seed))
        if (predicate(plan)) return plan
    }
    return null
}

/** 同上，但找不到时直接让测试失败。 */
private fun requirePlan(signal: DiarySignal, predicate: (DiaryPlan) -> Boolean): DiaryPlan =
    findPlan(signal, predicate) ?: throw AssertionError("没有找到符合条件的日记计划")

private fun localHour(plan: DiaryPlan): Int = ZonedDateTime.ofInstant(plan.scheduledAt, ZONE_SH).hour

private fun localDate(plan: DiaryPlan): LocalDate = ZonedDateTime.ofInstant(plan.scheduledAt, ZONE_SH).toLocalDate()

/** M5-08：主动聊天调度、日记调度与回复节奏。 */
class ProactiveDiaryPacingTest {

    @Test
    fun `主动聊天数量随好感度提升`() {
        // 陌生阶段固定 1 条
        repeat(20) { assertEquals(1, ProactivePlanner.dailyCount(0, Random(it))) }
        repeat(20) { assertEquals(1, ProactivePlanner.dailyCount(99, Random(it))) }

        val familiar = (0 until 200).map { ProactivePlanner.dailyCount(150, Random(it)) }.toSet()
        assertEquals(setOf(1, 2), familiar)
        val intimate = (0 until 200).map { ProactivePlanner.dailyCount(400, Random(it)) }.toSet()
        assertEquals(setOf(2, 3), intimate)
        val beloved = (0 until 200).map { ProactivePlanner.dailyCount(700, Random(it)) }.toSet()
        assertEquals(setOf(2, 3), beloved)
    }

    @Test
    fun `免打扰支持同小时与跨午夜`() {
        // 起止相同表示未开启免打扰
        assertFalse(ProactivePlanner.inDoNotDisturb(12, 12, 12))
        assertTrue(ProactivePlanner.inDoNotDisturb(13, 12, 14))
        assertFalse(ProactivePlanner.inDoNotDisturb(14, 12, 14))
        assertFalse(ProactivePlanner.inDoNotDisturb(11, 12, 14))
        // 跨午夜：23 点到次日 8 点
        assertFalse(ProactivePlanner.inDoNotDisturb(22, 23, 8))
        assertTrue(ProactivePlanner.inDoNotDisturb(0, 23, 8))
        assertFalse(ProactivePlanner.inDoNotDisturb(9, 23, 8))
    }

    @Test
    fun `每日排程落在三个窗口内且不超过三条`() {
        val picks = ProactivePlanner.planDay(PLAN_DATE, 3, 1, 6, ZONE_SH, Random(11))
        assertEquals(3, picks.size)
        assertEquals(picks.sorted(), picks)
        val allowed = setOf(7, 8, 9, 12, 13, 14, 19, 20, 21, 22, 23)
        picks.forEach { instant ->
            val time = ZonedDateTime.ofInstant(instant, ZONE_SH)
            assertEquals(PLAN_DATE, time.toLocalDate())
            assertTrue(time.hour in allowed)
        }
        assertTrue(ProactivePlanner.planDay(PLAN_DATE, 0, 1, 6, ZONE_SH, Random(1)).isEmpty())
        assertEquals(3, ProactivePlanner.planDay(PLAN_DATE, 99, 1, 6, ZONE_SH, Random(1)).size)
    }

    @Test
    fun `免打扰窗口内的候选时刻被剔除`() {
        val picks = ProactivePlanner.planDay(PLAN_DATE, 3, 7, 10, ZONE_SH, Random(5))
        assertEquals(3, picks.size)
        picks.forEach { assertTrue(ZonedDateTime.ofInstant(it, ZONE_SH).hour !in 7..9) }
    }

    @Test
    fun `主动素材按优先级取用`() {
        assertEquals(
            ProactiveKind.LIFE,
            ProactivePlanner.pickMaterial(listOf("刚煮了泡面"), emptyList(), null, emptyList(), Random(1))?.kind,
        )
        assertEquals(
            ProactiveKind.AGREEMENT,
            ProactivePlanner.pickMaterial(emptyList(), listOf("答应周末看电影"), null, emptyList(), Random(1))?.kind,
        )
        assertEquals(
            ProactiveKind.MILESTONE,
            ProactivePlanner.pickMaterial(emptyList(), emptyList(), "认识一百天", emptyList(), Random(1))?.kind,
        )
        assertEquals(
            ProactiveKind.MEMORY,
            ProactivePlanner.pickMaterial(emptyList(), emptyList(), null, listOf("他很怕打雷"), Random(1))?.kind,
        )
        assertNull(ProactivePlanner.pickMaterial(listOf("  "), emptyList(), null, listOf(""), Random(1)))
        assertNull(ProactivePlanner.pickMaterial(emptyList(), emptyList(), null, emptyList(), Random(1)))
    }

    @Test
    fun `素材齐全时按概率降级`() {
        val kinds = (0 until 200).mapNotNull { seed ->
            ProactivePlanner.pickMaterial(listOf("生活"), listOf("约定"), "里程碑", listOf("旧事"), Random(seed))?.kind
        }.toSet()
        assertEquals(setOf(ProactiveKind.LIFE, ProactiveKind.AGREEMENT, ProactiveKind.MEMORY), kinds)
    }

    @Test
    fun `没有触发条件时不写日记`() {
        val plan = DiaryPlanner.plan(DiarySignal(PLAN_DATE, 0, 10, false), PLAN_NOW, ZONE_SH, Random(0))
        assertFalse(plan.shouldWrite)
        assertTrue(plan.triggers.isEmpty())
        assertEquals(PLAN_NOW, plan.scheduledAt)
        assertEquals(PLAN_DATE, plan.eventDate)
        assertFalse(plan.backfill)
        assertFalse(plan.secret)
        assertFalse(plan.longForm)
    }

    @Test
    fun `三个触发条件可同时命中`() {
        val plan = DiaryPlanner.plan(DiarySignal(PLAN_DATE, 2, 45, true), PLAN_NOW, ZONE_SH, Random(0))
        assertEquals(listOf("new_memory", "mood_swing", "favorability"), plan.triggers)
    }

    @Test
    fun `百分之三十概率跳过写日记`() {
        val plan = requirePlan(DiarySignal(PLAN_DATE, 1, 0, false)) { !it.shouldWrite }
        assertEquals(listOf("new_memory"), plan.triggers)
        assertFalse(plan.backfill)
        assertEquals(PLAN_NOW, plan.scheduledAt)
    }

    @Test
    fun `常规日记写在当晚九点后`() {
        val plan = requirePlan(DiarySignal(PLAN_DATE, 1, 0, false)) {
            it.shouldWrite && !it.backfill && localHour(it) >= 21
        }
        assertEquals(PLAN_DATE, localDate(plan))
        assertTrue(localHour(plan) in 21..23)
    }

    @Test
    fun `补写日记安排在一到两天后`() {
        val plan = requirePlan(DiarySignal(PLAN_DATE, 1, 0, false)) { it.shouldWrite && it.backfill }
        val date = localDate(plan)
        assertTrue(date == PLAN_DATE.plusDays(1) || date == PLAN_DATE.plusDays(2))
        assertTrue(localHour(plan) in 21..23)
    }

    @Test
    fun `凌晨毛刺会把日记挪到次日零点后`() {
        val plan = requirePlan(DiarySignal(PLAN_DATE, 1, 0, false)) {
            it.shouldWrite && !it.backfill && localHour(it) < 2
        }
        assertEquals(PLAN_DATE.plusDays(1), localDate(plan))
        assertTrue(localHour(plan) in 0..1)
    }

    @Test
    fun `加密日记按两成概率出现`() {
        val plan = requirePlan(DiarySignal(PLAN_DATE, 1, 0, false)) { it.shouldWrite && it.secret }
        assertTrue(plan.secret)
    }

    @Test
    fun `情绪波动超过五十写长日记`() {
        val plan = requirePlan(DiarySignal(PLAN_DATE, 0, -60, false)) { it.shouldWrite }
        assertTrue(plan.longForm)
        assertTrue(plan.triggers.contains("mood_swing"))
    }

    @Test
    fun `日记心情图标按档位切换`() {
        assertEquals("🌧", DiaryPlanner.moodIcon(-40))
        assertEquals("☁️", DiaryPlanner.moodIcon(-11))
        assertEquals("🌤", DiaryPlanner.moodIcon(-10))
        assertEquals("🌤", DiaryPlanner.moodIcon(14))
        assertEquals("☀️", DiaryPlanner.moodIcon(15))
        assertEquals("🌈", DiaryPlanner.moodIcon(50))
    }

    @Test
    fun `回复延迟夹在六百毫秒到九秒之间`() {
        var hitMin = false
        var hitMax = false
        for (seed in 0 until 500) {
            val low = ReplyPacer.delayMillis(0.2f, energy = 80, hourOfDay = 14, random = Random(seed))
            assertTrue(low >= ReplyPacer.MIN_DELAY_MS)
            if (low == ReplyPacer.MIN_DELAY_MS) hitMin = true

            val high = ReplyPacer.delayMillis(2.0f, energy = 20, hourOfDay = 3, random = Random(seed))
            assertTrue(high <= ReplyPacer.MAX_DELAY_MS)
            if (high == ReplyPacer.MAX_DELAY_MS) hitMax = true
        }
        assertTrue(hitMin)
        assertTrue(hitMax)
    }

    @Test
    fun `深夜低能量才说自己困了`() {
        assertTrue(ReplyPacer.shouldSaySleepy(2, energy = 30, random = Random(0)))
        assertTrue(ReplyPacer.shouldSaySleepy(23, energy = 30, random = Random(0)))
        assertFalse(ReplyPacer.shouldSaySleepy(14, energy = 20, random = Random(0)))
        assertTrue((0 until 200).any { ReplyPacer.shouldSaySleepy(2, energy = 80, random = Random(it)) })
        assertTrue((0 until 200).any { !ReplyPacer.shouldSaySleepy(2, energy = 80, random = Random(it)) })
        assertTrue(ReplyPacer.sleepyLine(Random(3)).isNotEmpty())
    }

    @Test
    fun `晚安台词不会连续复读同一句`() {
        val first = ReplyPacer.sleepyLine(Random(3))
        assertTrue(ReplyPacer.isSleepyLine(first))
        // 无论抽到哪个种子，避让上一句后都不会再拿到同一句
        (0 until 50).forEach { seed ->
            assertNotEquals(first, ReplyPacer.sleepyLine(Random(seed), avoid = first))
        }
        // 非池内文本不算已道过晚安
        assertFalse(ReplyPacer.isSleepyLine("晚安呀"))
    }
}
