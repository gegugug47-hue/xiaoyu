package com.xiaoyu.soul.postprocess

/**
 * 回复长度自适应反馈（v3 §4.4）：不硬截断，而是记录连续超长次数，
 * 累计 3 次后在下一轮 L1 追加约束，让模型自己收敛。
 *
 * @param maxChars 单条回复的理想上限（含分段符的长回复不算超长）。
 */
class LengthGuard(private val maxChars: Int = 180) {
    private var consecutiveOverLength = 0

    /** 记录本轮回复长度；[hasSeparator] 表示是否含 `|||` 分段符。 */
    fun record(length: Int, hasSeparator: Boolean) {
        if (length > maxChars && !hasSeparator) {
            consecutiveOverLength += 1
        } else {
            consecutiveOverLength = 0
        }
    }

    /** 连续 3 次超长时返回约束语，否则 null。 */
    fun directive(): String? = if (consecutiveOverLength >= 3) {
        "注意：你最近几条回复都太长了。每条消息控制在 $maxChars 字以内，像微信聊天一样短一点。"
    } else null

    fun reset() { consecutiveOverLength = 0 }
}
