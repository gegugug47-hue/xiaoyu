package com.xiaoyu.soul.postprocess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * 分隔符暂扣边界：达到段数上限、以及竖线后的空白跨 chunk 时，
 * 正文都不能漏出用于分隔的「|」。
 */
class SegmenterSeparatorHoldTest {

    @Test
    fun `达到段数上限后跨 chunk 的分隔符不会漏出竖线`() {
        val segmenter = Segmenter(maxSegments = 2)
        val events = segmenter.feed("第一段|||第二段|") +
            segmenter.feed("  |第三段") + segmenter.finish()
        val done = events.filterIsInstance<SegmentEvent.Done>().map { it.text }
        val streamed = events.filterIsInstance<SegmentEvent.Text>().joinToString("") { it.text }
        assertEquals(listOf("第一段", "第二段\n第三段"), done)
        assertEquals("第二段\n第三段", streamed)
        assertFalse(streamed.contains('|'))
    }

    @Test
    fun `竖线后的长空白跨 chunk 时仍整段暂扣`() {
        val segmenter = Segmenter()
        val events = segmenter.feed("甲|") +
            segmenter.feed(" ".repeat(12)) +
            segmenter.feed(" |乙") + segmenter.finish()
        val done = events.filterIsInstance<SegmentEvent.Done>().map { it.text }
        val streamed = events.filterIsInstance<SegmentEvent.Text>().joinToString("") { it.text }
        assertEquals(listOf("甲", "乙"), done)
        assertEquals("甲乙", streamed)
        assertFalse(streamed.contains('|'))
    }
}
