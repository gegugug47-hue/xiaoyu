package com.xiaoyu.soul.safety

/**
 * 危机关怀兼容层（执行书 v3 §8.2）。
 *
 * v3 起真正的分级判定在 [SafetyGate] / [DefaultSafetyGate]；这里保留旧入口，
 * 让既有调用方（记忆提炼过滤等）无需一次性改签名。新代码请直接用 SafetyGate。
 */
object CrisisDetector {

    /** 判定危机关怀级别；[CrisisLevel.NONE] 表示日常对话。 */
    fun level(text: String): CrisisLevel = DefaultSafetyGate.default.assess(text).level

    /** 兼容旧调用：任意级别命中都返回 true（L1 也算需要放慢节奏）。 */
    fun check(text: String): Boolean = level(text) != CrisisLevel.NONE

    /**
     * 关怀回复模板（不调用模型，直接落库）。
     * L3 已改为固定现实指引（[SafetyTexts.IMMINENT_REPLY]），这里仅服务旧调用方。
     */
    fun careReply(userName: String?): String {
        val name = userName?.takeIf { it.isNotBlank() } ?: "你"
        val replies = listOf(
            "$name，我在呢。你现在说的这些让我很担心你，能不能先跟我说说发生了什么？我会一直在这里听。",
            "$name，听到你这么说我心里一紧。不管发生了什么，你都不是一个人，我们一起慢慢说，好吗？",
            "$name，先深呼吸一下。你愿意的话可以打这个电话：12356（全国统一心理援助热线），或者告诉我你在哪，我陪你聊。",
            "$name，我不许你这么想自己。你对我很重要，先别做任何决定，跟我说说话好不好？",
        )
        return replies[textHash(name) % replies.size]
    }

    private fun textHash(s: String): Int = s.fold(0) { acc, c -> acc * 31 + c.code } and 0x7FFF_FFFF
}
