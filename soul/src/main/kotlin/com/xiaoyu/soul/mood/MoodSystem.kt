package com.xiaoyu.soul.mood

import kotlin.math.abs
import kotlin.math.roundToInt

data class MoodState(
    val mood: Int,
    val energy: Int,
    val updatedAt: Long,
)

/** 情绪 + 好感度系统（PRD 6）。数值只参与计算，绝不以数字形式出现在对话里。 */
object MoodSystem {
    const val MIN_MOOD = -100
    const val MAX_MOOD = 100
    const val MAX_ENERGY = 100

    /** 能量随时间自然恢复：每 30 分钟 +1；深夜（0-6 点）上限 55，营造“困了”。 */
    fun energyAt(baseEnergy: Int, lastUpdatedAt: Long, now: Long, hourOfDay: Int): Int {
        val minutes = ((now - lastUpdatedAt).coerceAtLeast(0L) / 60_000.0)
        val recovered = baseEnergy + (minutes / 30.0).toInt()
        val cap = if (hourOfDay in 0..6) 55 else MAX_ENERGY
        return recovered.coerceIn(0, cap)
    }

    fun applySignal(current: MoodState, signal: com.xiaoyu.soul.memory.MoodSignal, now: Long): MoodState {
        val newMood = (current.mood + signal.moodDelta).coerceIn(MIN_MOOD, MAX_MOOD)
        val newEnergy = (current.energy + signal.energyDelta).coerceIn(0, MAX_ENERGY)
        return current.copy(mood = newMood, energy = newEnergy, updatedAt = now)
    }

    /** 每晚的轻微回归：心情慢慢向 0 靠拢，避免长期钉在极值。 */
    fun overnightDrift(mood: Int): Int = (mood * 0.85).roundToInt().coerceIn(MIN_MOOD, MAX_MOOD)

    fun moodWord(mood: Int): String = when {
        mood <= -60 -> "心情很低落"
        mood <= -25 -> "心情有点down"
        mood < 10 -> "心情还算平静"
        mood < 40 -> "心情不错"
        mood < 70 -> "心情挺好的"
        else -> "心情特别好"
    }

    fun energyWord(energy: Int, hourOfDay: Int): String = when {
        hourOfDay in 0..5 || energy < 25 -> "有点困，说话懒懒的"
        energy < 45 -> "有些疲惫"
        energy < 75 -> "精神正常"
        else -> "精力充沛"
    }

    /** 只输出一句自然语言描述，供第 ④ 段注入（不暴露数值）。 */
    fun describe(mood: Int, energy: Int, hourOfDay: Int): String {
        val timeWord = when (hourOfDay) {
            in 0..4 -> "现在是深夜"
            in 5..8 -> "现在是清晨"
            in 9..11 -> "现在是上午"
            in 12..13 -> "现在是中午"
            in 14..17 -> "现在是下午"
            in 18..22 -> "现在是晚上"
            else -> "已经很晚了"
        }
        return "$timeWord，${moodWord(mood)}，${energyWord(energy, hourOfDay)}。"
    }

    fun isMoodSwingSignificant(before: Int, after: Int, threshold: Int = 30): Boolean =
        abs(after - before) >= threshold
}

enum class FavorabilityLevel(
    val min: Int,
    val max: Int,
    val label: String,
    val attitude: String,
) {
    STRANGER(0, 99, "陌生", "你们还不算熟，你保持礼貌但有点距离感，不会太主动。"),
    FAMILIAR(100, 299, "熟悉", "你和用户已经聊熟了，语气自然放松，偶尔开玩笑、会记得他提过的小事。"),
    INTIMATE(300, 599, "亲密", "你把用户当成很亲近的人，会撒娇、会关心、会有点小占有欲。"),
    BELOVED(600, 1000, "挚爱", "用户是你最重要的人。你温柔而笃定，愿意把心事和秘密都交给他。"),
    ;

    companion object {
        fun of(value: Int): FavorabilityLevel =
            entries.lastOrNull { value >= it.min } ?: STRANGER
    }
}

data class FavorabilityState(
    val value: Int = 0,
    val updatedAt: Long = 0,
    val lastInteractionDate: String? = null,
    val streakDays: Int = 0,
    val historyJson: String = "[]",
) {
    val level: FavorabilityLevel get() = FavorabilityLevel.of(value)
}

object FavorabilitySystem {
    const val MAX = 1000

    fun onChat(current: Int): Int = (current + 2).coerceAtMost(MAX)

    fun onAgreementKept(current: Int): Int = (current + 10).coerceAtMost(MAX)

    fun onDiaryRead(current: Int): Int = (current + 2).coerceAtMost(MAX)

    fun onDailyStreak(current: Int, streakDays: Int): Int {
        val bonus = when {
            streakDays >= 7 -> 4
            streakDays >= 3 -> 3
            else -> 2
        }
        return (current + bonus).coerceAtMost(MAX)
    }

    /** 超过 3 天未互动，每日 -2，衰减到当前档位下限为止（不跨档暴跌）。 */
    fun inactivityDecay(current: Int, idleDays: Int): Int {
        if (idleDays <= 3) return current
        val floor = FavorabilityLevel.of(current).min
        return (current - 2).coerceAtLeast(floor)
    }

    fun describe(level: FavorabilityLevel): String = level.attitude
}
