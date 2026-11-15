package com.xiaoyu.soul

import com.xiaoyu.soul.memory.MoodSignal
import com.xiaoyu.soul.mood.FavorabilityLevel
import com.xiaoyu.soul.mood.FavorabilityState
import com.xiaoyu.soul.mood.FavorabilitySystem
import com.xiaoyu.soul.mood.MoodState
import com.xiaoyu.soul.mood.MoodSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** M5-08：情绪系统与好感度系统的边界、分档与事件覆盖。 */
class MoodFavorabilityTest {

    @Test
    fun `能量每三十分钟恢复一点且受深夜上限约束`() {
        // 90 分钟 → 恢复 3 点
        assertEquals(13, MoodSystem.energyAt(10, 0L, 90 * 60_000L, hourOfDay = 14))
        // 时间倒流回到起点，不额外扣能量
        assertEquals(10, MoodSystem.energyAt(10, 1_000L, 0L, hourOfDay = 14))
        // 白天上限 100
        assertEquals(100, MoodSystem.energyAt(100, 0L, 10 * 60_000L, hourOfDay = 14))
        // 0~6 点上限被压到 55
        assertEquals(55, MoodSystem.energyAt(100, 0L, 10 * 60_000L, hourOfDay = 6))
        assertEquals(55, MoodSystem.energyAt(100, 0L, 10 * 60_000L, hourOfDay = 0))
        // 7 点不再算深夜
        assertEquals(100, MoodSystem.energyAt(100, 0L, 10 * 60_000L, hourOfDay = 7))
    }

    @Test
    fun `情绪信号叠加并夹在上下限内`() {
        val base = MoodState(mood = 90, energy = 10, updatedAt = 1L)
        val up = MoodSystem.applySignal(base, MoodSignal(moodDelta = 30, energyDelta = 20), now = 99L)
        assertEquals(100, up.mood)
        assertEquals(30, up.energy)
        assertEquals(99L, up.updatedAt)

        val down = MoodSystem.applySignal(base, MoodSignal(moodDelta = -300, energyDelta = -80), now = 99L)
        assertEquals(-100, down.mood)
        assertEquals(0, down.energy)
    }

    @Test
    fun `情绪词按区间分档`() {
        assertEquals("心情很低落", MoodSystem.moodWord(-100))
        assertEquals("心情很低落", MoodSystem.moodWord(-60))
        assertEquals("心情有点down", MoodSystem.moodWord(-59))
        assertEquals("心情有点down", MoodSystem.moodWord(-25))
        assertEquals("心情还算平静", MoodSystem.moodWord(-24))
        assertEquals("心情还算平静", MoodSystem.moodWord(9))
        assertEquals("心情不错", MoodSystem.moodWord(10))
        assertEquals("心情不错", MoodSystem.moodWord(39))
        assertEquals("心情挺好的", MoodSystem.moodWord(40))
        assertEquals("心情特别好", MoodSystem.moodWord(70))
    }

    @Test
    fun `精力词同时看时段与能量`() {
        assertEquals("有点困，说话懒懒的", MoodSystem.energyWord(90, hourOfDay = 3))
        assertEquals("有点困，说话懒懒的", MoodSystem.energyWord(24, hourOfDay = 14))
        assertEquals("有些疲惫", MoodSystem.energyWord(30, hourOfDay = 14))
        assertEquals("精神正常", MoodSystem.energyWord(60, hourOfDay = 14))
        assertEquals("精力充沛", MoodSystem.energyWord(80, hourOfDay = 14))
    }

    @Test
    fun `状态描述按时段前缀拼接`() {
        assertTrue(MoodSystem.describe(0, 60, hourOfDay = 2).startsWith("现在是深夜，"))
        assertTrue(MoodSystem.describe(0, 60, hourOfDay = 6).startsWith("现在是清晨，"))
        assertTrue(MoodSystem.describe(0, 60, hourOfDay = 10).startsWith("现在是上午，"))
        assertTrue(MoodSystem.describe(0, 60, hourOfDay = 13).startsWith("现在是中午，"))
        assertTrue(MoodSystem.describe(0, 60, hourOfDay = 15).startsWith("现在是下午，"))
        assertTrue(MoodSystem.describe(0, 60, hourOfDay = 20).startsWith("现在是晚上，"))
        assertTrue(MoodSystem.describe(0, 60, hourOfDay = 23).startsWith("已经很晚了，"))
        val sentence = MoodSystem.describe(0, 60, hourOfDay = 20)
        assertTrue(sentence.contains("心情还算平静"))
        assertTrue(sentence.endsWith("。"))
    }

    @Test
    fun `情绪波动默认阈值为三十`() {
        assertTrue(MoodSystem.isMoodSwingSignificant(0, 30))
        assertTrue(MoodSystem.isMoodSwingSignificant(0, -30))
        assertFalse(MoodSystem.isMoodSwingSignificant(0, 29))
        assertTrue(MoodSystem.isMoodSwingSignificant(0, 20, threshold = 10))
        assertFalse(MoodSystem.isMoodSwingSignificant(50, 45, threshold = 10))
    }

    @Test
    fun `好感度档位边界与文案`() {
        assertEquals(FavorabilityLevel.STRANGER, FavorabilityLevel.of(-5))
        assertEquals(FavorabilityLevel.STRANGER, FavorabilityLevel.of(99))
        assertEquals(FavorabilityLevel.FAMILIAR, FavorabilityLevel.of(100))
        assertEquals(FavorabilityLevel.FAMILIAR, FavorabilityLevel.of(299))
        assertEquals(FavorabilityLevel.INTIMATE, FavorabilityLevel.of(300))
        assertEquals(FavorabilityLevel.INTIMATE, FavorabilityLevel.of(599))
        assertEquals(FavorabilityLevel.BELOVED, FavorabilityLevel.of(600))
        assertEquals(FavorabilityLevel.BELOVED, FavorabilityLevel.of(1000))
        assertEquals("陌生", FavorabilityLevel.STRANGER.label)
        assertEquals("熟悉", FavorabilityLevel.FAMILIAR.label)
        assertEquals("亲密", FavorabilityLevel.INTIMATE.label)
        assertEquals("挚爱", FavorabilityLevel.BELOVED.label)
        assertEquals(0, FavorabilityLevel.STRANGER.min)
        assertEquals(1000, FavorabilityLevel.BELOVED.max)
    }

    @Test
    fun `好感度事件加分各不相同且封顶一千`() {
        assertEquals(12, FavorabilitySystem.onChat(10))
        assertEquals(1000, FavorabilitySystem.onChat(999))
        assertEquals(110, FavorabilitySystem.onAgreementKept(100))
        assertEquals(1000, FavorabilitySystem.onAgreementKept(995))
        assertEquals(7, FavorabilitySystem.onDiaryRead(5))
        assertEquals(2, FavorabilitySystem.onDailyStreak(0, streakDays = 1))
        assertEquals(3, FavorabilitySystem.onDailyStreak(0, streakDays = 3))
        assertEquals(4, FavorabilitySystem.onDailyStreak(0, streakDays = 7))
        assertEquals(1000, FavorabilitySystem.onDailyStreak(999, streakDays = 7))
    }

    @Test
    fun `三天内不互动不衰减`() {
        assertEquals(150, FavorabilitySystem.inactivityDecay(150, idleDays = 0))
        assertEquals(150, FavorabilitySystem.inactivityDecay(150, idleDays = 3))
    }

    @Test
    fun `衰减不会跌破当前档位下限`() {
        // 100 已是熟悉档下限，衰减后仍停在 100
        assertEquals(100, FavorabilitySystem.inactivityDecay(100, idleDays = 4))
        assertEquals(103, FavorabilitySystem.inactivityDecay(105, idleDays = 4))
        assertEquals(648, FavorabilitySystem.inactivityDecay(650, idleDays = 10))
        assertEquals(0, FavorabilitySystem.inactivityDecay(0, idleDays = 30))
    }

    @Test
    fun `好感度状态自带等级与态度文案`() {
        assertEquals(FavorabilityLevel.INTIMATE, FavorabilityState(value = 400).level)
        assertEquals(FavorabilityLevel.STRANGER, FavorabilityState().level)
        assertTrue(FavorabilitySystem.describe(FavorabilityLevel.STRANGER).contains("距离感"))
        assertTrue(FavorabilitySystem.describe(FavorabilityLevel.BELOVED).contains("最重要的人"))
        assertEquals("[]", FavorabilityState().historyJson)
    }
}
