package com.xiaoyu.soul

import com.xiaoyu.soul.postprocess.ForbiddenLevel
import com.xiaoyu.soul.postprocess.ForbiddenPhraseGuard
import com.xiaoyu.soul.postprocess.PostEvent
import com.xiaoyu.soul.postprocess.PostProcessPipeline
import com.xiaoyu.soul.postprocess.SegmentEvent
import com.xiaoyu.soul.postprocess.Segmenter
import com.xiaoyu.soul.postprocess.XyParsePart
import com.xiaoyu.soul.postprocess.XyTokenKind
import com.xiaoyu.soul.postprocess.XyTokenParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** M5-08：分段器、token 解析、禁词守卫与后处理管线的边界分支。 */
class SegmentPipelineTest {

    @Test
    fun `空输入不产出事件`() {
        val segmenter = Segmenter()
        assertTrue(segmenter.feed("").isEmpty())
        assertTrue(segmenter.finish().isEmpty())
    }

    @Test
    fun `全角与空格分隔符同样生效`() {
        val events = Segmenter().feed("你好 | | | 在吗")
        assertEquals("你好", events.filterIsInstance<SegmentEvent.Done>().single().text)
        assertEquals("在吗", events.filterIsInstance<SegmentEvent.Text>().last().text)
    }

    @Test
    fun `只有一段时限时全部并入单条`() {
        val segmenter = Segmenter(maxSegments = 1)
        val done = (segmenter.feed("早|||午|||晚") + segmenter.finish())
            .filterIsInstance<SegmentEvent.Done>()
            .map { it.text }
        assertEquals(listOf("早\n午\n晚"), done)
    }

    @Test
    fun `尾部的半个分隔符被暂扣不提前吐出`() {
        val events = Segmenter().feed("等等 |")
        assertTrue(events.none { it is SegmentEvent.Done })
        assertEquals("等等", events.filterIsInstance<SegmentEvent.Text>().joinToString("") { it.text })
    }

    @Test
    fun `暂扣超过上限时按普通文本吐出`() {
        val parser = XyTokenParser(maxHoldChars = 4)
        val text = parser.feed("[xy:s").filterIsInstance<XyParsePart.Text>().joinToString("") { it.text }
        assertEquals("[xy:s", text)
    }

    @Test
    fun `纯文本直接透传`() {
        val parser = XyTokenParser()
        assertTrue(parser.flush().isEmpty())
        assertTrue(parser.feed("").isEmpty())
        val parts = parser.feed("你好")
        assertEquals("你好", (parts.single() as XyParsePart.Text).text)
        assertTrue(parser.flush().isEmpty())
    }

    @Test
    fun `memo 与 br token 解析`() {
        val tokens = XyTokenParser().feed("[xy:memo][xy:br]")
            .filterIsInstance<XyParsePart.Token>()
            .map { it.token }
        assertEquals(listOf(XyTokenKind.MEMO, XyTokenKind.BREAK), tokens.map { it.kind })
        assertNull(tokens[1].value)
    }

    @Test
    fun `无值 token 的值解析为空`() {
        val sticker = XyTokenParser().feed("[xy:s]").filterIsInstance<XyParsePart.Token>().single().token
        assertEquals(XyTokenKind.STICKER, sticker.kind)
        assertNull(sticker.value)
        val signal = XyTokenParser().feed("[xy:sig= ]").filterIsInstance<XyParsePart.Token>().single().token
        assertEquals(XyTokenKind.SIGNAL, signal.kind)
        assertNull(signal.value)
    }

    @Test
    fun `用户文本转义只处理 xy token`() {
        assertEquals("价格［xy:s=1］", XyTokenParser.escapeUserText("价格[xy:s=1]"))
        assertEquals("价格[100]元", XyTokenParser.escapeUserText("价格[100]元"))
        assertEquals("没有方括号", XyTokenParser.escapeUserText("没有方括号"))
    }

    @Test
    fun `禁词守卫按严重程度分档`() {
        val guard = ForbiddenPhraseGuard()
        assertNull(guard.check("   "))
        assertEquals(ForbiddenLevel.HARD, guard.check("我作为一名AI，没有情绪")?.level)
        assertEquals(ForbiddenLevel.HARD, guard.check("As an AI, I cannot")?.level)
        assertEquals(ForbiddenLevel.SAFETY, guard.check("我无法协助你完成这个")?.level)
        assertEquals(ForbiddenLevel.SOFT, guard.check("总结一下今天的聊天")?.level)
        assertNull(guard.check("今天风很大"))
    }

    @Test
    fun `break token 触发分段`() {
        val events = PostProcessPipeline().feed("第一句[xy:br]第二句")
        assertEquals("第一句", events.filterIsInstance<PostEvent.SegmentFinished>().single().text)
        assertEquals(listOf("第一句", "第二句"), events.filterIsInstance<PostEvent.Delta>().map { it.text })
    }

    @Test
    fun `表情与 memo token 各自产出事件`() {
        val events = PostProcessPipeline().feed("开心[xy:s=2][xy:memo]")
        assertEquals("2", events.filterIsInstance<PostEvent.Sticker>().single().stickerId)
        assertEquals(1, events.filterIsInstance<PostEvent.Memo>().size)
        assertEquals("开心", events.filterIsInstance<PostEvent.Delta>().joinToString("") { it.text })
    }

    @Test
    fun `信号值可以为空且空动作不产出事件`() {
        val events = PostProcessPipeline().feed("[xy:sig][xy:act= ]")
        assertTrue(events.any { it is PostEvent.Signal && it.emotion == null })
        assertTrue(events.none { it is PostEvent.Action })
    }

    @Test
    fun `安全拒绝会拦截整条回复且之后不再输出`() {
        val pipeline = PostProcessPipeline()
        val events = pipeline.feed("我无法协助这件事")
        assertEquals(ForbiddenLevel.SAFETY, pipeline.blocked?.level)
        assertTrue(events.any { it is PostEvent.Blocked })
        assertTrue(pipeline.feed("再说一句").isEmpty())
        assertTrue(pipeline.finish().isEmpty())
    }

    @Test
    fun `软性问题只提示一次`() {
        val pipeline = PostProcessPipeline()
        assertEquals(1, pipeline.feed("总结一下").filterIsInstance<PostEvent.SoftWarning>().size)
        assertTrue(pipeline.feed("总结一下").filterIsInstance<PostEvent.SoftWarning>().isEmpty())
        assertEquals("总结一下总结一下", pipeline.visibleText())
    }

    @Test
    fun `关闭开头填充词剥离后保留原句`() {
        val pipeline = PostProcessPipeline(stripFillers = false)
        val events = pipeline.feed("好的，我在") + pipeline.finish()
        assertEquals("好的，我在", events.filterIsInstance<PostEvent.Delta>().joinToString("") { it.text })
    }

    @Test
    fun `连续重复的非 ASCII 字符压缩到六个码元`() {
        val events = PostProcessPipeline().feed("好" + "啊".repeat(8))
        assertEquals("好" + "啊".repeat(6), events.filterIsInstance<PostEvent.Delta>().joinToString("") { it.text })
    }

    @Test
    fun `结束后吐出暂扣的半截 token`() {
        val pipeline = PostProcessPipeline()
        val events = pipeline.feed("你好[xy:si") + pipeline.finish()
        assertEquals("你好[xy:si", events.filterIsInstance<PostEvent.Delta>().joinToString("") { it.text })
        assertEquals("你好[xy:si", pipeline.visibleText())
    }
}
