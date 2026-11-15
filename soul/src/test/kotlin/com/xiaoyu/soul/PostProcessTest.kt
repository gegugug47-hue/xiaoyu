package com.xiaoyu.soul

import com.xiaoyu.soul.postprocess.PostEvent
import com.xiaoyu.soul.postprocess.PostProcessPipeline
import com.xiaoyu.soul.postprocess.SegmentEvent
import com.xiaoyu.soul.postprocess.Segmenter
import com.xiaoyu.soul.postprocess.XyTokenKind
import com.xiaoyu.soul.postprocess.XyTokenParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PostProcessTest {

    @Test
    fun `token 被切断时暂扣，补齐后识别`() {
        val parser = XyTokenParser()
        assertTrue(parser.feed("今天想你了[xy:si").none { it is com.xiaoyu.soul.postprocess.XyParsePart.Token })
        val parts = parser.feed("g=joy]")
        val token = parts.filterIsInstance<com.xiaoyu.soul.postprocess.XyParsePart.Token>().single().token
        assertEquals(XyTokenKind.SIGNAL, token.kind)
        assertEquals("joy", token.value)
    }

    @Test
    fun `普通方括号不会被吞`() {
        val parser = XyTokenParser()
        val text = parser.feed("价格 [100] 元").filterIsInstance<com.xiaoyu.soul.postprocess.XyParsePart.Text>()
            .joinToString("") { it.text }
        assertEquals("价格 [100] 元", text)
        assertTrue(parser.flush().isEmpty())
    }

    @Test
    fun `用户消息里的 token 被转义`() {
        assertEquals("［xy:s=1］", XyTokenParser.escapeUserText("[xy:s=1]"))
    }

    @Test
    fun `分段器按分隔符拆气泡`() {
        val segmenter = Segmenter(maxSegments = 4)
        val events = segmenter.feed("你好|||在吗")
        assertEquals("你好", (events[0] as SegmentEvent.Done).text)
        assertEquals("在吗", (events[1] as SegmentEvent.Text).text)
        assertEquals("在吗", (segmenter.finish().single() as SegmentEvent.Done).text)
    }

    @Test
    fun `分段上限把多余内容并入最后一段`() {
        val segmenter = Segmenter(maxSegments = 4)
        val events = segmenter.feed("a|||b|||c|||d|||e") + segmenter.finish()
        val done = events.filterIsInstance<SegmentEvent.Done>().map { it.text }
        assertEquals(listOf("a", "b", "c", "d\ne"), done)
    }

    @Test
    fun `尾部的半个分隔符不提前吐出`() {
        val segmenter = Segmenter(maxSegments = 4)
        val head = segmenter.feed("等等|").filterIsInstance<SegmentEvent.Text>().joinToString("") { it.text }
        assertEquals("等等", head)
        val events = segmenter.feed("||下一句")
        assertEquals("等等", (events.first() as SegmentEvent.Done).text)
    }

    @Test
    fun `多余竖线不会留在气泡开头`() {
        val segmenter = Segmenter(maxSegments = 4)
        val events = segmenter.feed("你好||||在吗｜然后呢") + segmenter.finish()
        val texts = events.filterIsInstance<SegmentEvent.Text>().map { it.text }
        assertEquals(listOf("在吗｜然后呢"), texts)
        val done = events.filterIsInstance<SegmentEvent.Done>().map { it.text }
        assertEquals(listOf("你好", "在吗｜然后呢"), done)
    }

    @Test
    fun `管线剥离动作 token 并产出动作行`() {
        val pipeline = PostProcessPipeline()
        val events = pipeline.feed("*笑*[xy:act=揉揉你的头发]早点睡")
        assertTrue(events.any { it is PostEvent.Action && it.text == "揉揉你的头发" })
        val text = events.filterIsInstance<PostEvent.Delta>().joinToString("") { it.text }
        assertEquals("*笑*早点睡", text)
        assertNull(pipeline.blocked)
    }

    @Test
    fun `人格崩坏句式被拦截`() {
        val pipeline = PostProcessPipeline()
        val events = pipeline.feed("作为一个AI，我不能有情绪。")
        assertTrue(events.any { it is PostEvent.Blocked })
        assertEquals(
            com.xiaoyu.soul.postprocess.ForbiddenLevel.HARD,
            pipeline.blocked?.level,
        )
    }

    @Test
    fun `开头无意义应答词被剥掉`() {
        val pipeline = PostProcessPipeline()
        val events = pipeline.feed("好的，我在呢") + pipeline.feed("") + pipeline.finish()
        val text = events.filterIsInstance<PostEvent.Delta>().joinToString("") { it.text } +
            events.filterIsInstance<PostEvent.SegmentFinished>().joinToString("") { it.text }
        assertTrue(text.contains("我在呢"))
        assertTrue(!text.contains("好的"))
    }
}
