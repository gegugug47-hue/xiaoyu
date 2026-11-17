package com.xiaoyu.app.perf

import android.view.Choreographer

/**
 * 帧间隔统计（执行书 v3 §1.5 / C-03：60fps，p95 < 16.7ms）。
 *
 * 项目未引入 `androidx.metrics:metrics-performance`，这里用 [Choreographer] 自实现：
 * 每次 `doFrame` 记录与上一帧的间隔，滚动保留最近 [WINDOW] 帧，
 * 诊断页读取时计算 p50 / p95。开销只有一次纳秒减法，不参与布局与绘制。
 */
object FrameStats {

    private const val WINDOW = 300

    /** p50 / p95 均为毫秒；[samples] 不足 30 帧时调用方应提示"样本不足"。 */
    data class Snapshot(
        val samples: Int,
        val p50Ms: Double,
        val p95Ms: Double,
    )

    private val intervals = ArrayDeque<Long>(WINDOW)
    private var lastFrameNanos = 0L
    private var installed = false

    /** 在 Application 启动时调用一次即可，主线程执行。 */
    fun install() {
        if (installed) return
        installed = true
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    @Synchronized
    fun snapshot(): Snapshot? {
        if (intervals.size < 2) return null
        val sorted = intervals.toLongArray().apply { sort() }
        fun percentile(p: Double): Double {
            val index = ((sorted.size - 1) * p).toInt().coerceIn(0, sorted.size - 1)
            return sorted[index] / 1_000_000.0
        }
        return Snapshot(
            samples = sorted.size,
            p50Ms = percentile(0.50),
            p95Ms = percentile(0.95),
        )
    }

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            val previous = lastFrameNanos
            lastFrameNanos = frameTimeNanos
            if (previous > 0L) {
                val delta = frameTimeNanos - previous
                // 切后台回来时的巨大跳变不代表渲染性能，直接丢弃。
                if (delta in 1_000_000L..1_000_000_000L) {
                    synchronized(this@FrameStats) {
                        if (intervals.size >= WINDOW) intervals.removeFirst()
                        intervals.addLast(delta)
                    }
                }
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }
}
