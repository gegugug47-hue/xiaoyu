package com.xiaoyu.soul.postprocess

sealed interface SegmentEvent {
    /** 当前气泡的增量文本。 */
    data class Text(val text: String) : SegmentEvent

    /** 当前气泡结束，text 是该段完整文本。 */
    data class Done(val text: String) : SegmentEvent
}

/**
 * 分段发送（执行书 3.2.4）。
 *
 * - 分隔符 `|||`，容忍 `||` 与 `| | |` 变体；
 * - 最多 [maxSegments] 段，超出部分并入最后一段（分隔符改为换行）；
 * - 流式友好：当前段实时吐文本，遇到分隔符就结束当前段、开下一段。
 */
class Segmenter(private val maxSegments: Int = 4) {
    /** 两个及以上的竖线（含全角、中间夹空格）都算分隔符，一次吃完。 */
    private val separator = Regex("""(?:[|｜]\s*){2,}""")
    private val pending = StringBuilder()
    private val current = StringBuilder()
    private var completed = 0

    fun feed(chunk: String): List<SegmentEvent> {
        if (chunk.isEmpty()) return emptyList()
        val out = mutableListOf<SegmentEvent>()
        pending.append(chunk)
        val limit = maxSegments.coerceAtLeast(1) - 1

        var match = if (completed < limit) separator.find(pending) else null
        while (match != null && completed < limit) {
            val before = pending.substring(0, match.range.first)
            pending.delete(0, match.range.last + 1)
            current.append(before)
            completeSegment()?.let { out += it }
            match = separator.find(pending)
        }

        // 已到上限：剩余内容全部并入最后一段，分隔符换行
        if (completed >= limit) {
            val rest = pending.toString()
            pending.clear()
            if (rest.isNotEmpty()) {
                val safe = separator.replace(rest, "\n")
                current.append(safe)
                out += SegmentEvent.Text(safe)
            }
            return out
        }

        // 暂扣尾部可能是半个分隔符的字符，避免把 "|" 先吐出去又拆段
        val hold = holdLength()
        if (pending.length > hold) {
            val visible = pending.substring(0, pending.length - hold)
            pending.delete(0, visible.length)
            current.append(visible)
            out += SegmentEvent.Text(visible)
        }
        return out
    }

    fun finish(): List<SegmentEvent> {
        val out = mutableListOf<SegmentEvent>()
        val rest = pending.toString()
        pending.clear()
        if (rest.isNotEmpty()) {
            val safe = if (completed >= maxSegments.coerceAtLeast(1) - 1) separator.replace(rest, "\n") else rest
            current.append(safe)
            out += SegmentEvent.Text(safe)
        }
        completeSegment()?.let { out += it }
        return out
    }

    private fun completeSegment(): SegmentEvent? {
        // 模型偶尔会多打一两个竖线（或全角竖线），不能让它留在气泡开头
        val text = current.toString().trim().trim('|', '｜').trim()
        current.clear()
        if (text.isEmpty()) return null
        completed += 1
        return SegmentEvent.Done(text)
    }

    private fun holdLength(): Int {
        var i = pending.length
        var hasPipe = false
        while (i > 0 && (pending[i - 1] == '|' || pending[i - 1] == '｜' || pending[i - 1] == ' ')) {
            if (pending[i - 1] == '|' || pending[i - 1] == '｜') hasPipe = true
            i--
        }
        if (!hasPipe) return 0
        return minOf(pending.length - i, 5)
    }
}
