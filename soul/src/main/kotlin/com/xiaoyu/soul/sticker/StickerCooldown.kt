package com.xiaoyu.soul.sticker

/**
 * AI 发表情冷却（执行书 7.4.7 / `stickerCooldownTurns`=4）。
 *
 * 两次表情之间至少间隔 [cooldownTurns] 轮对话；连续两轮都想发表情时，
 * 第二轮的 `[xy:s=N]` 会被丢弃，避免“每句话都甩表情”的机械感。
 * 纯 JVM，可单元测试。
 */
class StickerCooldown(private val cooldownTurns: Int = 4) {

    private var turnsSinceLast = cooldownTurns

    /** 每轮回复开始时调用，推进轮次。 */
    fun onTurnStarted() {
        if (turnsSinceLast < cooldownTurns) turnsSinceLast += 1
    }

    /** 本轮是否允许发表情。 */
    fun canSend(): Boolean = turnsSinceLast >= cooldownTurns

    /** 真的发出去后调用，重置冷却。 */
    fun markSent() {
        turnsSinceLast = 0
    }
}
