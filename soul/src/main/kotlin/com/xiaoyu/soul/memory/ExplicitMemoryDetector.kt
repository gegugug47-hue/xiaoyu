package com.xiaoyu.soul.memory

/** 显式记忆指令的本地关键词初筛（PRD 5.2.1）：命中后再交给 EXTRACT 确认。 */
object ExplicitMemoryDetector {
    private val positive = listOf(
        "记住", "记下来", "记一下", "记好", "别忘了", "别忘记", "帮我记", "要记得", "你要记得",
    )

    /** “我记住了”“记住了吗”这类不算指令（避免误触发）。 */
    private val negations = listOf(
        "我记住了", "我记下来了", "我记下了", "记住了吗", "你记住了吗", "我记住啦",
    )

    fun isCandidate(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty() || t.length > 120) return false
        if (negations.any { t.contains(it) }) return false
        if (t.startsWith("哈哈")) return false
        return positive.any { t.contains(it) }
    }

    /** 去掉“记住”“帮我记一下”等前缀，留下真正要记的事实。 */
    fun normalize(text: String): String {
        var result = text.trim()
        for (word in positive.sortedByDescending { it.length }) {
            val idx = result.indexOf(word)
            if (idx >= 0) {
                result = result.removeRange(idx, idx + word.length)
            }
        }
        return result
            .trim()
            .trim('，', ',', '。', '.', '：', ':', ' ', '\n')
            .ifEmpty { text.trim() }
    }
}
