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

        // 已到上限时分隔符换行；两种分支都统一暂扣可能跨 chunk 的分隔符后缀
        drainPending(replaceSeparators = completed >= limit)?.let { out += it }
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

    /** 吐出安全前缀，尾部若仍是分隔符前缀则原样留在 pending。 */
    private fun drainPending(replaceSeparators: Boolean): SegmentEvent.Text? {
        val hold = holdLength()
        if (pending.length <= hold) return null
        val raw = pending.substring(0, pending.length - hold)
        pending.delete(0, raw.length)
        val visible = if (replaceSeparators) separator.replace(raw, "\n") else raw
        current.append(visible)
        return SegmentEvent.Text(visible)
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
        while (i > 0 && (pending[i - 1] == '|' || pending[i - 1] == '｜' || pending[i - 1].isWhitespace())) {
            if (pending[i - 1] == '|' || pending[i - 1] == '｜') hasPipe = true
            i--
        }
        if (!hasPipe) return 0
        // 空白也可能跨很多 chunk，不能按固定长度截断后把前面的竖线漏出去
        return pending.length - i
    }
}
