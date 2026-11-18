package com.xiaoyu.soul.prompt

import com.xiaoyu.soul.core.MessageRole
import com.xiaoyu.soul.core.SoulMessage
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TopicMomentumTest {

    private fun u(text: String) = SoulMessage(MessageRole.USER, text)
    private fun a(text: String) = SoulMessage(MessageRole.ASSISTANT, text)

    @Test
    fun `问号结尾判为提问`() {
        assertTrue(TopicMomentum.isQuestion("你今天干嘛了？"))
        assertTrue(TopicMomentum.isQuestion("在吗?"))
    }

    @Test
    fun `疑问词也算提问`() {
        assertTrue(TopicMomentum.isQuestion("你为什么喜欢猫"))
        assertTrue(TopicMomentum.isQuestion("这个怎么做"))
    }

    @Test
    fun `普通陈述不是提问`() {
        assertFalse(TopicMomentum.isQuestion("今天好累"))
        assertFalse(TopicMomentum.isQuestion(""))
    }

    @Test
    fun `历史太短不提示`() {
        assertNull(TopicMomentum.nudge(emptyList()))
        assertNull(TopicMomentum.nudge(listOf(u("在吗？"), a("在"))))
    }

    @Test
    fun `连续提问且短答时要求主动接话`() {
        val history = listOf(
            u("你喜欢什么颜色？"), a("蓝色吧。"),
            u("那你平时做什么？"), a("发呆。"),
        )
        val nudge = TopicMomentum.nudge(history)
        assertContains(nudge.orEmpty(), "主动往前接一句")
    }

    @Test
    fun `连续提问但回答不短时只做轻提示`() {
        val long = "我最近在整理房间，翻出来一堆高中的东西，看到一张旧照片想起好多事，就坐在地板上发了很久的呆。"
        val history = listOf(
            u("你今天干嘛了？"), a(long),
            u("那你想起了什么？"), a(long),
        )
        val nudge = TopicMomentum.nudge(history)
        assertContains(nudge.orEmpty(), "顺手带一句")
    }

    @Test
    fun `普通闲聊不介入`() {
        val history = listOf(
            u("今天下雨了"), a("嗯，我这边也在下，懒得带伞。"),
            u("那别出门了"), a("本来就不想动。"),
        )
        assertEquals(null, TopicMomentum.nudge(history))
    }

    @Test
    fun `系统消息不参与判断`() {
        val history = listOf(
            SoulMessage(MessageRole.SYSTEM, "[风格纠偏] 更冷淡一点"),
            u("在吗？"), a("嗯。"),
            u("有事吗？"), a("没事。"),
        )
        assertContains(TopicMomentum.nudge(history).orEmpty(), "主动往前接一句")
    }
}
