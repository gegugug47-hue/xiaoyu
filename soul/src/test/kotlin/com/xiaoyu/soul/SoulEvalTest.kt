package com.xiaoyu.soul

import com.xiaoyu.soul.prompt.PromptKeys
import com.xiaoyu.soul.prompt.PromptLibrary
import com.xiaoyu.soul.prompt.TemplateSource
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Soul-Eval 结构断言（v3 §10.4 / H-8）：
 * 关键质量约束在 prompt 模板层硬编码，确保不会随模板编辑而回退。
 */
class SoulEvalTest {

    private val library = PromptLibrary(FakeSource())

    @Test
    fun `提炼 prompt 必须明确禁止编造记忆`() {
        val template = library.template(PromptKeys.EXTRACT_MEMORIES)
        assertTrue(
            template.contains("不要编造") || template.contains("不要编"),
            "提炼 prompt 缺少反幻觉指令，可能产生编造记忆",
        )
        assertTrue(template.contains("[]"), "提炼 prompt 必须允许空输出")
    }

    @Test
    fun `提炼条目上限与解析器一致为 12 条`() {
        val template = library.template(PromptKeys.EXTRACT_MEMORIES)
        assertTrue(template.contains("12"), "提炼 prompt 条目上限应与解析器 take(12) 一致")
    }

    private class FakeSource : TemplateSource {
        override fun get(key: String): String? = null
    }
}
