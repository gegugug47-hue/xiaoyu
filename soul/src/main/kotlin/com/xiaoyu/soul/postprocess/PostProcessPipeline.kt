package com.xiaoyu.soul.postprocess

sealed interface PostEvent {
    /** 当前气泡可显示的增量文本（已剥离 token / 禁词处理）。 */
    data class Delta(val text: String) : PostEvent

    /** 当前气泡结束，text 为该段完整文本（落库用）。 */
    data class SegmentFinished(val text: String) : PostEvent

    /** `[xy:s=编号]`：该位置应插入一条表情消息。 */
    data class Sticker(val stickerId: String?) : PostEvent

    /** `[xy:sig=情绪]`：喂给情绪系统。 */
    data class Signal(val emotion: String?) : PostEvent

    /** `[xy:act=动作]`：气泡内渲染的斜体动作行。 */
    data class Action(val text: String) : PostEvent

    /** `[xy:memo]`：模型认为值得长期记住。 */
    data object Memo : PostEvent

    /** HARD / SAFETY 命中：整条回复不可用，调用方应丢弃并重生成。 */
    data class Blocked(val level: ForbiddenLevel, val phrase: String) : PostEvent

    /** SOFT 命中：不拦截，只记诊断。 */
    data class SoftWarning(val phrase: String) : PostEvent
}

/**
 * 回复后处理管线（执行书 3.3）：滚动剥离 `[xy:*]` → 禁词守卫 → 分段。
 *
 * 用法：`feed(rawChunk)` 逐块喂入 SSE 增量，结束时 `finish()`。
 * 一旦 `blocked` 非空，调用方应丢弃本轮输出并重生成（最多 1 次）。
 */
class PostProcessPipeline(
    private val parser: XyTokenParser = XyTokenParser(),
    private val guard: ForbiddenPhraseGuard = ForbiddenPhraseGuard(),
    private val segmenter: Segmenter = Segmenter(),
    private val stripFillers: Boolean = true,
) {
    private val visible = StringBuilder()
    private var atSegmentStart = true
    private var lastSoft: String? = null

    var blocked: ForbiddenHit? = null
        private set

    /** 已产出的全部可见文本（不含 token）。 */
    fun visibleText(): String = visible.toString()

    fun feed(rawChunk: String): List<PostEvent> {
        if (rawChunk.isEmpty()) return emptyList()
        val out = mutableListOf<PostEvent>()
        for (part in parser.feed(rawChunk)) dispatch(part, out)
        return out
    }

    fun finish(): List<PostEvent> {
        val out = mutableListOf<PostEvent>()
        for (part in parser.flush()) dispatch(part, out)
        segmenter.finish().forEach { out += it.toPostEvent() }
        return out
    }

    private fun dispatch(part: XyParsePart, out: MutableList<PostEvent>) {
        when (part) {
            is XyParsePart.Text -> handleText(part.text, out)
            is XyParsePart.Token -> handleToken(part.token, out)
        }
    }

    private fun handleToken(token: XyToken, out: MutableList<PostEvent>) {
        if (blocked != null) return
        when (token.kind) {
            XyTokenKind.BREAK -> segmenter.feed("|||").forEach { out += it.toPostEvent() }
            XyTokenKind.STICKER -> out += PostEvent.Sticker(token.value)
            XyTokenKind.SIGNAL -> out += PostEvent.Signal(token.value)
            XyTokenKind.ACTION -> token.value?.takeIf { it.isNotBlank() }?.let { out += PostEvent.Action(it) }
            XyTokenKind.MEMO -> out += PostEvent.Memo
        }
    }

    private fun handleText(raw: String, out: MutableList<PostEvent>) {
        if (blocked != null || raw.isEmpty()) return
        val text = when {
            stripFillers && atSegmentStart -> stripLeadingFillers(raw)
            else -> raw
        }
        if (text.isEmpty()) return
        atSegmentStart = false
        visible.append(text)
        guard.check(visible.toString())?.let { hit ->
            when (hit.level) {
                ForbiddenLevel.HARD, ForbiddenLevel.SAFETY -> {
                    blocked = hit
                    out += PostEvent.Blocked(hit.level, hit.phrase)
                    return
                }
                ForbiddenLevel.SOFT -> if (lastSoft != hit.phrase) {
                    lastSoft = hit.phrase
                    out += PostEvent.SoftWarning(hit.phrase)
                }
            }
        }
        segmenter.feed(collapseRepeatedEmoji(text)).forEach { out += it.toPostEvent() }
    }

    private fun SegmentEvent.toPostEvent(): PostEvent = when (this) {
        is SegmentEvent.Text -> PostEvent.Delta(text)
        is SegmentEvent.Done -> {
            atSegmentStart = true
            PostEvent.SegmentFinished(text)
        }
    }

    private fun stripLeadingFillers(text: String): String {
        for (filler in LEADING_FILLERS) {
            if (text.startsWith(filler)) return text.removePrefix(filler).trimStart()
        }
        return text
    }

    /** 连续 3 个以上相同 emoji 压缩为 3 个（3.3.3）。 */
    private fun collapseRepeatedEmoji(text: String): String =
        REPEATED_EMOJI.replace(text) { it.value.take(3 * 2) }

    companion object {
        private val LEADING_FILLERS = listOf(
            "好的，", "好的。", "好的,", "好呀，", "当然可以，", "当然可以。",
            "当然，", "没问题，", "没问题。",
        )

        /** 同一个非 ASCII 表情/符号重复 4 次以上。 */
        private val REPEATED_EMOJI = Regex("""([^\x00-\x7F])\1{3,}""")
    }
}
