package com.xiaoyu.soul.memory

import kotlin.math.exp
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 遗忘曲线（PRD 5.4）。
 * strength_new = strength_old × e^(-Δdays / R)，importance=5 时 R 翻倍（衰减减半）。
 */
object ForgettingCurve {
    const val SHORT_TERM_R = 3.0
    const val LONG_TERM_R = 90.0
    const val IMPORTANT_MULTIPLIER = 2.0
    const val BIN_THRESHOLD = 20
    const val BIN_RETENTION_DAYS = 30L
    const val RECALL_BOOST = 20
    const val MAX_STRENGTH = 100

    fun rateFor(importance: Int, longTerm: Boolean): Double {
        val base = if (longTerm) LONG_TERM_R else SHORT_TERM_R
        return if (importance >= 5) base * IMPORTANT_MULTIPLIER else base
    }

    fun decayedStrength(
        current: Int,
        days: Double,
        importance: Int,
        longTerm: Boolean,
    ): Int {
        if (current <= 0) return 0
        if (days <= 0.0) return current.coerceIn(0, MAX_STRENGTH)
        val r = rateFor(importance, longTerm)
        val factor = exp(-days / r)
        return (current * factor).roundToInt().coerceIn(0, MAX_STRENGTH)
    }

    fun shouldBin(strength: Int): Boolean = strength < BIN_THRESHOLD

    fun shouldPurge(binAt: Long?, now: Long): Boolean {
        if (binAt == null) return false
        val days = (now - binAt) / 86_400_000.0
        return days > BIN_RETENTION_DAYS
    }

    fun boostOnRecall(strength: Int): Int = min(MAX_STRENGTH, strength + RECALL_BOOST)
}
