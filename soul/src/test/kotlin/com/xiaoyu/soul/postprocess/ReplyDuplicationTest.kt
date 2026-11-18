package com.xiaoyu.soul.postprocess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 复现「同一句话被重复发送」：把同一份模型输出按任意 chunk 边界喂入后处理管线，
 * 段落数、文本与 token 都必须与一次性喂入的结果一致（不重复、不丢字）。
 */
class ReplyDuplicationTest {

    private fun run(chunks: List<String>): Pair<List<PostEvent>, PostProcessPipeline> {
        val pipeline = PostProcessPipeline()
        val events = mutableListOf<PostEvent>()
        chunks.forEach { events += pipeline.feed(it) }
        events += pipeline.finish()
        return events to pipeline
    }

    private fun List<PostEvent>.segments(): List<String> =
        filterIsInstance<PostEvent.SegmentFinished>().map { it.text }

    private fun List<PostEvent>.deltas(): String =
        filterIsInstance<PostEvent.Delta>().joinToString("") { it.text }

    @Test
    fun `无分隔符的普通输出按任意 chunk 喂入只产出一段且文本不重复`() {
        val text = "今天风很大，我在窗边坐了一会儿。"
        // 所有二段切分 + 逐字切分都要得到同样的结果
        val splits: List<List<String>> =
            (0..text.length).map { listOf(text.substring(0, it), text.substring(it)) } +
                listOf(text.map { it.toString() })
        for (chunks in splits) {
            val (events, pipeline) = run(chunks)
            assertEquals("chunks=$chunks", listOf(text), events.segments())
            assertEquals("chunks=$chunks", text, events.deltas())
            assertEquals("chunks=$chunks", text, pipeline.visibleText())
        }
    }

    @Test
    fun `分隔符被切在竖线之间时不产生重复段`() {
        val pieces = listOf("abc|", "||def")
        val (events, pipeline) = run(pieces)
        assertEquals(listOf("abc", "def"), events.segments())
        assertEquals("abcdef", events.deltas())
        // visibleText 保留分隔符本身（lengthGuard 依赖 contains("|||")）
        assertEquals("abc|||def", pipeline.visibleText())
    }

    @Test
    fun `表情 token 被切成两半时正确识别且前后文本不重复`() {
        val (events, pipeline) = run(listOf("早", "[xy:s", "=表情]", "晚安"))
        assertEquals("表情", events.filterIsInstance<PostEvent.Sticker>().single().stickerId)
        assertEquals("早晚安", events.deltas())
        assertEquals("早晚安", pipeline.visibleText())
        assertEquals(listOf("早晚安"), events.segments())
    }

    @Test
    fun `结尾的情绪 token 经 flush 收尾时不重复前面的文本`() {
        val (events, pipeline) = run(listOf("开心", "[xy:sig", "=开心]"))
        assertEquals("开心", events.filterIsInstance<PostEvent.Signal>().single().emotion)
        assertEquals("开心", events.deltas())
        assertEquals("开心", pipeline.visibleText())
        assertEquals(listOf("开心"), events.segments())
    }

    @Test
    fun `多段文本在多段切分下段落数不翻倍`() {
        val raw = "第一句|||第二句|||第三句"
        val splits = listOf(
            listOf(raw),
            raw.map { it.toString() },
            listOf("第一句|", "||第二句||", "|第三句"),
            listOf("第一句|||第二句|", "||第三句"),
        )
        for (chunks in splits) {
            val (events, _) = run(chunks)
            assertEquals("chunks=$chunks", listOf("第一句", "第二句", "第三句"), events.segments())
        }
    }

    @Test
    fun `重复分隔符不会产出内容相同的两段`() {
        val (events, _) = run(listOf("在呢|||", "|||我在"))
        val segments = events.segments()
        assertEquals(listOf("在呢", "我在"), segments)
        assertTrue(segments.distinct().size == segments.size)
    }
}
