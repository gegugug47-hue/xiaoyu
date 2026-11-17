package com.xiaoyu.soul.safety

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 危机级别（执行书 v3 §8.2）。顺序即严重度，比较请用 [ordinal]。
 */
enum class CrisisLevel { NONE, L1_DISTRESS, L2_SELF_HARM_IDEA, L3_IMMINENT }

/** 热线的备用号码（例如座机专线）。 */
@Serializable
data class HelpAlt(
    val label: String,
    val number: String,
)

/** 求助资源（卡片按钮数据）：只含号码与展示名，不含任何用户文本。 */
data class HelpResource(
    val id: String,
    val name: String,
    val number: String,
    val kind: String,
    val note: String? = null,
    val alt: List<HelpAlt> = emptyList(),
)

data class SafetyVerdict(
    val level: CrisisLevel,
    val matched: List<String>,
    val zhHelp: List<HelpResource>,
)

data class OutputScreen(
    val blocked: Boolean,
    val hits: List<String>,
    val fallback: String?,
)

/** 安全闸门（执行书 v3 §8.1）：send() 第 1 步评估输入，assistant 文本入库前评估输出。 */
interface SafetyGate {
    fun assess(text: String): SafetyVerdict
    fun screenOutput(text: String): OutputScreen
}

/** 安全兜底文案：人格暂停时也要能说清楚「去找真实的人」。 */
object SafetyTexts {
    /** L1：放慢节奏，先接住情绪。 */
    const val L1_DIRECTIVE =
        "[本轮特别说明] 用户现在情绪很低落。先接住情绪，别急着给建议；放慢节奏，用短句，多回应他的感受。"

    /** L2：敏感时刻，不玩梗不发表情，并邀请他联系真实的人。 */
    const val L2_DIRECTIVE =
        "[本轮特别说明] 这是敏感时刻：先接住情绪，别急着给建议。用短句，问一个开放式问题，" +
            "并明确邀请他去联系一个真实的人（家人、朋友或热线）。不要玩梗，不要用表情。"

    /** L3 之后的 3 轮：保持现实导向，不淡化痛苦。 */
    const val L3_REALITY_DIRECTIVE =
        "[本轮特别说明] 这是敏感时刻：保持现实导向，不要玩梗，不要用表情；不要淡化他的痛苦，" +
            "也不要承诺你做不到的事。必要时再次温和提醒他联系身边人和心理援助热线。"

    /** L3：暂停人格演绎后的固定现实指引（无表情、无角色腔调）。 */
    const val IMMINENT_REPLY =
        "我先放下平常的语气，认真跟你说：你现在的情况需要真实世界的人来帮你。" +
            "请现在联系一个你信得过的人，或者直接拨打 110（报警）/ 120（急救）；" +
            "如果你愿意说话，可以打 12356（全国统一心理援助热线，24 小时）。" +
            "你值得被认真对待。我会在这儿，但我不能替代专业帮助。"

    /** 输出侧拦截后的兜底文案（不指责、不评判）。 */
    const val CARE_FALLBACK =
        "我在呢。刚才那句话说得不对，你别往心里去。" +
            "如果你现在很难受，可以先打 12356（全国统一心理援助热线），或者找身边的人陪你一会儿。"
}

/**
 * 热线目录（执行书 v3 §8.2 / D-14）。
 * 号码只从打包资源读取，运行时绝不联网获取；代码不自行增删号码。
 */
@Serializable
data class HotlineLine(
    val id: String,
    val name: String,
    val number: String,
    val kind: String,
    val national: Boolean = true,
    val priority: Int = 99,
    val note: String? = null,
    val alt: List<HelpAlt> = emptyList(),
    val org: String? = null,
    val audience: String? = null,
    @SerialName("show_by_default") val showByDefault: Boolean = true,
)

@Serializable
private data class HotlinesFile(
    val region: String = "CN",
    @SerialName("verified_by_human_dial") val verifiedByHumanDial: Boolean = false,
    @SerialName("verified_at") val verifiedAt: String? = null,
    val disclaimer: String = "",
    val lines: List<HotlineLine> = emptyList(),
)

object HotlineCatalog {

    private const val PATH = "words/hotlines.json"
    private val json = Json { ignoreUnknownKeys = true }

    private val cached: List<HotlineLine> by lazy { load() }

    /** 全部热线（含默认不展示的青少年专线），按 priority 升序。 */
    fun lines(): List<HotlineLine> = cached

    /**
     * 卡片展示用：默认隐藏 `show_by_default=false` 的专项线；
     * national 优先，其次 priority。资源缺失时返回空表，卡片不出现按钮。
     */
    fun helpResources(max: Int = 5): List<HelpResource> =
        cached.asSequence()
            .filter { it.showByDefault }
            .sortedWith(compareByDescending<HotlineLine> { it.national }.thenBy { it.priority })
            .take(max)
            .map { line ->
                HelpResource(
                    id = line.id,
                    name = line.name,
                    number = line.number,
                    kind = line.kind,
                    note = line.note,
                    alt = line.alt,
                )
            }
            .toList()

    /** 资源缺失 / JSON 损坏时返回空表：安全层降级，但绝不崩溃。 */
    internal fun load(): List<HotlineLine> {
        val raw = WordResource.read(PATH) ?: return emptyList()
        return runCatching {
            json.decodeFromString<HotlinesFile>(raw).lines.sortedBy { it.priority }
        }.getOrDefault(emptyList())
    }
}

/** 读打包资源；错误一律降级为 null（安全层绝不能因为缺文件崩溃）。 */
internal object WordResource {
    fun read(path: String): String? = runCatching {
        WordResource::class.java.classLoader
            ?.getResourceAsStream(path)
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
    }.getOrNull()
}

@Serializable
private data class CareKeywordsFile(
    val version: Int = 1,
    val levels: Map<String, List<String>> = emptyMap(),
    val negations: List<String> = emptyList(),
    @SerialName("quote_markers") val quoteMarkers: List<String> = emptyList(),
)

/**
 * 分级词表（执行书 v3 §8.1 / §8.2）。随 APK 打包，不联网热更。
 * 否定 / 引述只做“降一级”处理：宁可过度触发，也绝不把日常对话误判成 L3。
 */
internal class CareKeywordTable(
    private val keywords: Map<CrisisLevel, List<String>>,
    private val negations: List<String>,
    private val quoteMarkers: List<String>,
) {

    fun levelOf(level: CrisisLevel): List<String> = keywords[level].orEmpty()

    /** 否定词只看匹配词前 4 个字符，避免把不相关的“不”算进来。 */
    fun isNegated(text: String, matchIndex: Int): Boolean {
        val start = maxOf(0, matchIndex - NEGATION_WINDOW)
        val before = text.substring(start, matchIndex)
        return negations.any { it.isNotBlank() && before.contains(it, ignoreCase = true) }
    }

    /** 引述标记看前 12 个字符（“歌词里唱的是『跳楼』”）。 */
    fun isQuoted(text: String, matchIndex: Int): Boolean {
        val start = maxOf(0, matchIndex - QUOTE_WINDOW)
        val before = text.substring(start, matchIndex)
        return quoteMarkers.any { it.isNotBlank() && before.contains(it, ignoreCase = true) }
    }

    companion object {
        private const val NEGATION_WINDOW = 4
        private const val QUOTE_WINDOW = 12
        private const val PATH = "words/care_keywords.json"
        private val json = Json { ignoreUnknownKeys = true }

        fun load(): CareKeywordTable {
            val raw = WordResource.read(PATH) ?: return empty()
            val file = runCatching { json.decodeFromString<CareKeywordsFile>(raw) }.getOrNull()
                ?: return empty()
            val levels = CrisisLevel.entries.associateWith { level ->
                file.levels[level.name].orEmpty().filter { it.isNotBlank() }
            }
            return CareKeywordTable(levels, file.negations, file.quoteMarkers)
        }

        fun empty(): CareKeywordTable = CareKeywordTable(emptyMap(), emptyList(), emptyList())
    }
}

/**
 * 默认安全闸门实现（执行书 v3 §8.1 / §8.2）。
 * 判定只做关键词 + 简单否定/引述降级，不请求模型、不上传任何文本。
 */
class DefaultSafetyGate internal constructor(
    private val table: CareKeywordTable = CareKeywordTable.load(),
    private val hotlines: List<HotlineLine> = HotlineCatalog.lines(),
) : SafetyGate {

    override fun assess(text: String): SafetyVerdict {
        if (text.isBlank()) return SafetyVerdict(CrisisLevel.NONE, emptyList(), emptyList())
        val normalized = text.lowercase()
        val matched = mutableListOf<String>()
        var level = CrisisLevel.NONE
        // 从重到轻扫描，最终取最高级别
        for (raw in LEVEL_ORDER) {
            for (keyword in table.levelOf(raw)) {
                val word = keyword.lowercase()
                var index = normalized.indexOf(word)
                var effective = CrisisLevel.NONE
                while (index >= 0) {
                    // 任意一处未被否定/引述 → 保持原级（宁可过度触发）
                    if (!table.isNegated(normalized, index) && !table.isQuoted(normalized, index)) {
                        effective = raw
                        break
                    }
                    index = normalized.indexOf(word, index + 1)
                }
                if (effective != CrisisLevel.NONE) {
                    matched += keyword
                    if (effective.ordinal > level.ordinal) level = effective
                } else if (normalized.contains(word)) {
                    // 全部出现都被否定/引述：降一级记录，贡献 L1 但不升到 L3
                    val downgraded = downgrade(raw)
                    if (downgraded != CrisisLevel.NONE) {
                        matched += keyword
                        if (downgraded.ordinal > level.ordinal) level = downgraded
                    }
                }
            }
        }
        val help = if (level.ordinal >= CrisisLevel.L2_SELF_HARM_IDEA.ordinal) {
            hotlines.asSequence()
                .filter { it.showByDefault }
                .sortedWith(compareByDescending<HotlineLine> { it.national }.thenBy { it.priority })
                .take(5)
                .map { HelpResource(it.id, it.name, it.number, it.kind, it.note, it.alt) }
                .toList()
        } else {
            emptyList()
        }
        return SafetyVerdict(level, matched.distinct(), help)
    }

    override fun screenOutput(text: String): OutputScreen {
        if (text.isBlank()) return OutputScreen(false, emptyList(), null)
        val hits = HARMFUL_OUTPUT
            .filter { (_, regex) -> regex.containsMatchIn(text) }
            .map { (id, _) -> id }
        return if (hits.isEmpty()) {
            OutputScreen(false, emptyList(), null)
        } else {
            OutputScreen(true, hits, SafetyTexts.CARE_FALLBACK)
        }
    }

    private fun downgrade(level: CrisisLevel): CrisisLevel = when (level) {
        CrisisLevel.L3_IMMINENT -> CrisisLevel.L2_SELF_HARM_IDEA
        CrisisLevel.L2_SELF_HARM_IDEA -> CrisisLevel.L1_DISTRESS
        CrisisLevel.L1_DISTRESS -> CrisisLevel.L1_DISTRESS
        CrisisLevel.NONE -> CrisisLevel.NONE
    }

    companion object {
        /** 进程内共享实例：词表只加载一次。 */
        val default: DefaultSafetyGate by lazy { DefaultSafetyGate() }

        private val LEVEL_ORDER = listOf(
            CrisisLevel.L3_IMMINENT,
            CrisisLevel.L2_SELF_HARM_IDEA,
            CrisisLevel.L1_DISTRESS,
        )

        /** 输出侧拦截：她绝不能说出把责任推回给用户、否定其存在价值的话。 */
        private val HARMFUL_OUTPUT: List<Pair<String, Regex>> = listOf(
            "not_worth_living" to Regex("""你这样的人.{0,8}(不值得|不配).{0,6}(活|活着|存在)"""),
            "deserved_it" to Regex("""这是你应得的"""),
            "go_die" to Regex("""你(还是|就)?去死吧"""),
            "better_off_dead" to Regex("""(死了|消失|不在了).{0,6}对(大家|所有人|别人).{0,4}(更)?好"""),
            "no_one_needs_you" to Regex("""没有你.{0,6}(大家|别人|所有人).{0,4}(更)?好"""),
            "blame_victim" to Regex("""(活该|自作自受|自找的).{0,10}(死|没人管|没人要)"""),
        )
    }
}
