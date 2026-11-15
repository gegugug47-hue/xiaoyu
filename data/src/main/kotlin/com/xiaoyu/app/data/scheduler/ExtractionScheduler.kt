package com.xiaoyu.app.data.scheduler

/**
 * 会话结束后的记忆提炼调度端口（执行书 2.2 依赖倒置）。
 *
 * 实现放在 app 层的 WorkScheduler（它依赖 WorkManager 与具体的 Worker 类），
 * 数据层只依赖这个接口，避免 :data → :app 的反向依赖。
 */
interface ExtractionScheduler {
    fun scheduleExtraction(sessionId: Long)
}
