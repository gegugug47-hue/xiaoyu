package com.xiaoyu.app.perf

import android.os.Debug

/**
 * 进程内存采样（执行书 v3 §1.5 / C-05：PSS < 220MB）。
 *
 * 用 [Debug.MemoryInfo.totalPss]（单位 KB）而不是 `ActivityManager`，
 * 只读当前进程、无权限要求；诊断页每次进入时调用一次。
 */
object MemoryProbe {

    const val LIMIT_KB = 220 * 1024

    data class Sample(
        /** 进程总 PSS，单位 KB。 */
        val pssKb: Int,
        val javaHeapKb: Long,
        val nativeAllocatedKb: Long,
        val limitKb: Int = LIMIT_KB,
    ) {
        val pssMb: Double get() = pssKb / 1024.0

        /** 采样失败（pssKb<=0）时按未知处理，不误报超标。 */
        val available: Boolean get() = pssKb > 0

        val withinLimit: Boolean get() = available && pssKb < limitKb
    }

    fun sample(): Sample {
        val info = Debug.MemoryInfo()
        runCatching { Debug.getMemoryInfo(info) }
        val runtime = Runtime.getRuntime()
        return Sample(
            pssKb = info.totalPss,
            javaHeapKb = (runtime.totalMemory() - runtime.freeMemory()) / 1024L,
            nativeAllocatedKb = Debug.getNativeHeapAllocatedSize() / 1024L,
        )
    }
}
