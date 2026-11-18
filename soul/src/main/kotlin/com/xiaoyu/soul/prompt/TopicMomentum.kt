package com.xiaoyu.soul.prompt

import com.xiaoyu.soul.core.MessageRole
import com.xiaoyu.soul.core.SoulMessage

/**
 * 话题推进：判断最近几轮是不是陷进了「他问一句、她答一句」的答题模式，
 * 命中时给本轮追加一条行为提示，让她主动往前接话。
 *
 * 纯规则实现，不调模型、不写库，便于单测。
 */
object TopicMomentum {

    private val questionMarkers = listOf(
        "？", "?", "吗", "呢", "怎么", "为什么", "啥", "如何", "多少", "是不是", "能不能", "可不可以",
    )

    /** 粗略判断一句用户消息是不是在提问。 */
    fun isQuestion(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return false
        if (t.endsWith("？") || t.endsWith("?")) return true
        return questionMarkers.any { t.contains(it) }
    }

    /**
     * @return 需要追加的提示；不需要时返回 null。
     */
    fun nudge(history: List<SoulMessage>): String? {
        val turns = history.filter { it.role != MessageRole.SYSTEM }.takeLast(8)
        if (turns.size < 4) return null

        val userTurns = turns.filter { it.role == MessageRole.USER }
        val herTurns = turns.filter { it.role == MessageRole.ASSISTANT }
        if (userTurns.isEmpty() || herTurns.isEmpty()) return null

        val recentUser = userTurns.takeLast(3)
        val recentHer = herTurns.takeLast(3)
        val userAllQuestions = recentUser.size >= 2 && recentUser.all { isQuestion(it.content) }
        val herTooShort = recentHer.count { it.content.trim().length <= 40 } >= 2

        return when {
            userAllQuestions && herTooShort ->
                "[本轮怎么接] 他连着问了几件事，你都答得很短。这次答完主动往前接一句：说说你的看法或今天遇到的事，别只等他继续问。"
            userAllQuestions ->
                "[本轮怎么接] 他一直在提问。答完不要只停在他问的点上，顺手带一句你自己的近况或你好奇的事。"
            else -> null
        }
    }
}
