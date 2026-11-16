package com.xiaoyu.soul.mood

/**
 * 在线状态推导（执行书 v3 1.3#35）：由精力 + 时段 + 生活碎片共同决定。
 * 不直接暴露数值，只输出状态枚举 + 一句状态描述，供顶栏展示。
 */
enum class PresenceStatus(
    val label: String,
    val dotColor: String,
) {
    /** 在线：精力充足且在白天活跃时段 */
    ONLINE("在线", "4CAF50"),

    /** 忙碌/疲惫：精力偏低但还醒着 */
    BUSY("忙碌", "FF9800"),

    /** 睡觉：深夜或精力耗尽 */
    SLEEPING("睡觉", "9E9E9E"),

    /** 离开：长时段无交互或精力极低 */
    AWAY("离开", "BDBDBD"),
    ;
}

data class PresenceResult(
    val status: PresenceStatus,
    val description: String,
)

/** 生活碎片摘要（可选）：有未完成事项时会让状态偏向 BUSY。 */
data class LifeSnapshot(
    val pendingTasks: Int = 0,
    val resting: Boolean = false,
)

object PresenceEngine {

    /**
     * 推导当前在线状态。
     *
     * @param energy 当前精力（0~100）
     * @param hourOfDay 当前小时（0~23）
     * @param minutesSinceInteraction 距离上次互动的分钟数（可选，>120 偏向 AWAY）
     * @param life 生活碎片摘要（可选）
     */
    fun derive(
        energy: Int,
        hourOfDay: Int,
        minutesSinceInteraction: Long = 0L,
        life: LifeSnapshot = LifeSnapshot(),
    ): PresenceResult {
        // 1. 睡觉时段：0~7 点，或精力耗尽（< 20）
        val isSleepHours = hourOfDay in 0..7
        val isExhausted = energy < 20
        if (isSleepHours || isExhausted || life.resting) {
            return PresenceResult(
                status = PresenceStatus.SLEEPING,
                description = sleepDescription(hourOfDay, energy),
            )
        }

        // 2. 离开：白天但超过 2 小时没互动，或精力极低
        if (minutesSinceInteraction > 120 || energy < 30) {
            return PresenceResult(
                status = PresenceStatus.AWAY,
                description = "暂时不在，一会儿回来",
            )
        }

        // 3. 忙碌：精力偏低（30~50）或有未完成生活事项
        if (energy in 30..50 || life.pendingTasks > 0) {
            return PresenceResult(
                status = PresenceStatus.BUSY,
                description = busyDescription(energy, life.pendingTasks),
            )
        }

        // 4. 在线：精力充足（> 50）且在活跃时段
        return PresenceResult(
            status = PresenceStatus.ONLINE,
            description = onlineDescription(hourOfDay, energy),
        )
    }

    private fun sleepDescription(hourOfDay: Int, energy: Int): String = when {
        hourOfDay in 0..5 -> "已经睡了，小声点哦"
        hourOfDay == 6 || hourOfDay == 7 -> "还没起呢，再眯一会儿"
        energy < 20 -> "累得睡着了，别吵醒我"
        else -> "在休息"
    }

    private fun busyDescription(energy: Int, pendingTasks: Int): String = when {
        pendingTasks >= 3 -> "手头事有点多，可能回得慢"
        energy < 40 -> "有点累，回复会慢一点"
        else -> "在忙，但看到消息会回"
    }

    private fun onlineDescription(hourOfDay: Int, energy: Int): String = when {
        hourOfDay in 8..11 -> "早上好，今天精神不错"
        hourOfDay in 12..13 -> "午休时间，一起聊会儿？"
        hourOfDay in 14..17 -> "下午好，正闲着呢"
        hourOfDay in 18..22 -> "晚上好，随时可以聊"
        energy >= 80 -> "现在精力满满，想聊什么都行"
        else -> "在的，说吧"
    }
}
