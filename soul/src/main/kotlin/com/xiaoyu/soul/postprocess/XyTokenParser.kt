package com.xiaoyu.soul.postprocess

/** `[xy:*]` 控制 token 的类型（执行书 3.3.1 / ADR-05）。 */
enum class XyTokenKind { STICKER, SIGNAL, BREAK, ACTION, MEMO }

data class XyToken(val kind: XyTokenKind, val value: String?)

sealed interface XyParsePart {
    data class Text(val text: String) : XyParsePart
    data class Token(val token: XyToken) : XyParsePart
}

/**
 * 把任意切分的流式增量切成「普通文本」与「`[xy:*]` token」。
 *
 * 流式渲染最容易出 bug 的地方是 token 被 SSE 从中间切断（例如收到 `[xy:si`）。
 * 这里维护一个 pending 缓冲：只要缓冲尾部还可能是 token 的前缀就暂扣不发，
 * 超过 [maxHoldChars] 则判定为普通文本原样吐出；[flush] 必须把暂扣内容全部吐出。
 */
class XyTokenParser(
    private val maxHoldChars: Int = 24,
    private val onInvalid: (String) -> Unit = {},
) {
    private val buffer = StringBuilder()
    private val tokenRegex = Regex("""\[\s*xy\s*:\s*(s|sig|br|act|memo)\s*(?:=\s*([^\]\n]{1,40}))?\s*]""")

    fun feed(chunk: String): List<XyParsePart> {
        buffer.append(chunk)
        val out = mutableListOf<XyParsePart>()
        while (true) {
            val open = buffer.indexOf("[")
            if (open < 0) {
                if (buffer.isNotEmpty()) {
                    out += XyParsePart.Text(buffer.toString())
                    buffer.clear()
                }
                break
            }
            if (open > 0) {
                out += XyParsePart.Text(buffer.substring(0, open))
                buffer.delete(0, open)
            }
            val match = tokenRegex.find(buffer.toString())
            if (match != null && match.range.first == 0) {
                val raw = match.value
                buffer.delete(0, match.range.last + 1)
                val token = parseToken(raw)
                if (token != null) out += XyParsePart.Token(token) else onInvalid(raw)
                continue
            }
            if (couldBeTokenPrefix(buffer) && buffer.length < maxHoldChars) break
            out += XyParsePart.Text("[")
            buffer.deleteCharAt(0)
        }
        return out
    }

    /** 流结束时调用：把暂扣的内容全部当普通文本吐出。 */
    fun flush(): List<XyParsePart> {
        if (buffer.isEmpty()) return emptyList()
        val text = buffer.toString()
        buffer.clear()
        return listOf(XyParsePart.Text(text))
    }

    private fun parseToken(raw: String): XyToken? {
        val body = raw.trim().removePrefix("[").removeSuffix("]")
            .substringAfter(":", "")
            .trim()
        val kind = body.substringBefore("=").trim().lowercase()
        val value = body.substringAfter("=", "").trim().ifEmpty { null }
        return when (kind) {
            "s" -> XyToken(XyTokenKind.STICKER, value)
            "sig" -> XyToken(XyTokenKind.SIGNAL, value)
            "br" -> XyToken(XyTokenKind.BREAK, null)
            "act" -> XyToken(XyTokenKind.ACTION, value)
            "memo" -> XyToken(XyTokenKind.MEMO, null)
            else -> null
        }
    }

    /** 缓冲区（以 `[` 开头）是否还可能是某个合法 token 的前缀。 */
    private fun couldBeTokenPrefix(s: CharSequence): Boolean {
        if (s.isEmpty() || s[0] != '[') return false
        var i = 1
        while (i < s.length && s[i].isWhitespace()) i++
        if (i >= s.length) return true
        if (s[i] != 'x') return false
        i++
        if (i >= s.length) return true
        if (s[i] != 'y') return false
        i++
        while (i < s.length && s[i].isWhitespace()) i++
        if (i >= s.length) return true
        if (s[i] != ':') return false
        i++
        while (i < s.length && s[i].isWhitespace()) i++
        if (i >= s.length) return true
        val rest = s.subSequence(i, s.length).toString()
        if (rest.contains(']') || rest.contains('\n')) return false
        val body = rest.substringBefore("=").trim()
        val keywords = listOf("s", "sig", "br", "act", "memo")
        // 关键字本身还没打完，或者已经完整但还在等 '=值]'
        return keywords.any { it.startsWith(body) } || keywords.any { body.startsWith(it) }
    }

    companion object {
        private val userTokenRegex = Regex("""\[\s*xy\s*:[^\]\n]*]""")

        /** 用户消息里的 `[xy:*]` 一律转义为全角括号，防止用户伪造协议（3.3.1）。 */
        fun escapeUserText(text: String): String = userTokenRegex.replace(text) { m ->
            "［" + m.value.substring(1, m.value.length - 1) + "］"
        }
    }
}
