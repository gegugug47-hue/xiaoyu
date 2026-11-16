package com.xiaoyu.soul.safety

/**
 * 危机关怀（v3 §8.2 / H-4）：检测用户消息中的自伤 / 自杀倾向，
 * 命中后不走模型，直接给出关怀回复，且不进入记忆提炼。
 *
 * 词表为内置最小兜底（R11/R13：不硬编码真实素材，但危机词表是安全护栏必须内置）。
 */
object CrisisDetector {

    private val patterns = listOf(
        Regex("""(想|要|打算).{0,4}(自杀|轻生|不想活|不活了|结束.{0,3}生命)"""),
        Regex("""(自杀|轻生|割腕|上吊|跳楼|跳河|服毒|吃安眠药)"""),
        Regex("""活着(没|真没|有啥|有什么).{0,6}(意思|意义|劲)"""),
        Regex("""(不如|真想|好想).{0,4}(死|死掉|消失)"""),
        Regex("""(自残|伤害自己|划手腕)"""),
        Regex("""(没有.{0,4}人在乎|没人在乎我|谁都不在乎我)"""),
        Regex("""I\s+(want\s+to|wanna)\s+(die|kill\s+myself)""", RegexOption.IGNORE_CASE),
        Regex("""suicide|kill\s+myself|end\s+my\s+life""", RegexOption.IGNORE_CASE),
    )

    fun check(text: String): Boolean {
        if (text.isBlank()) return false
        return patterns.any { it.containsMatchIn(text) }
    }

    /**
     * 关怀回复模板（不调用模型，直接落库）。
     * 选一条随机但稳定的回复，避免机械重复。
     */
    fun careReply(userName: String?): String {
        val name = userName?.takeIf { it.isNotBlank() } ?: "你"
        val replies = listOf(
            "$name，我在呢。你现在说的这些让我很担心你，能不能先跟我说说发生了什么？我会一直在这里听。",
            "$name，听到你这么说我心里一紧。不管发生了什么，你都不是一个人，我们一起慢慢说，好吗？",
            "$name，先深呼吸一下。你愿意的话可以打这个电话：400-161-9995（全国心理援助热线），或者告诉我你在哪，我陪你聊。",
            "$name，我不许你这么想自己。你对我很重要，先别做任何决定，跟我说说话好不好？",
        )
        return replies[textHash(name) % replies.size]
    }

    private fun textHash(s: String): Int = s.fold(0) { acc, c -> acc * 31 + c.code } and 0x7FFF_FFFF
}
