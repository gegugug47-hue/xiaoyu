package com.xiaoyu.app.data.settings

import kotlinx.serialization.Serializable

enum class ProviderKind(val label: String, val defaultBaseUrl: String, val defaultModel: String) {
    DEEPSEEK("DeepSeek", "https://api.deepseek.com", "deepseek-chat"),
    DASHSCOPE("阿里百炼", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen3.7-plus"),
    OLLAMA("家里电脑 Ollama", "http://192.168.1.100:11434/v1", "qwen3:8b"),
    CUSTOM("自定义 OpenAI 兼容端点", "", ""),
}

@Serializable
data class ProviderConfig(
    val kind: String = ProviderKind.DEEPSEEK.name,
    val baseUrl: String = ProviderKind.DEEPSEEK.defaultBaseUrl,
    val apiKey: String = "",
    val model: String = ProviderKind.DEEPSEEK.defaultModel,
) {
    val kindEnum: ProviderKind
        get() = runCatching { ProviderKind.valueOf(kind) }.getOrDefault(ProviderKind.CUSTOM)

    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && model.isNotBlank() &&
            (kindEnum == ProviderKind.OLLAMA || apiKey.isNotBlank())

    fun label(): String = kindEnum.label
}

/** CHAT / EXTRACT 双角色分别配置 + 主备降级链（PRD 2.4.2 / 2.4.3）。 */
@Serializable
data class BrainSettings(
    val chat: ProviderConfig = ProviderConfig(),
    val chatFallback: ProviderConfig? = null,
    val extract: ProviderConfig = ProviderConfig(
        kind = ProviderKind.DEEPSEEK.name,
        baseUrl = ProviderKind.DEEPSEEK.defaultBaseUrl,
        model = "deepseek-chat",
    ),
    val extractFallback: ProviderConfig? = null,
)

@Serializable
data class UsageStats(
    val month: String = "",
    val chatPromptTokens: Long = 0,
    val chatCompletionTokens: Long = 0,
    val extractPromptTokens: Long = 0,
    val extractCompletionTokens: Long = 0,
    val estimatedCost: Double = 0.0,
    val chatRequests: Int = 0,
    val extractRequests: Int = 0,
    val cachedTokens: Long = 0,
) {
    val totalTokens: Long
        get() = chatPromptTokens + chatCompletionTokens + extractPromptTokens + extractCompletionTokens
}

/** 单日用量（执行书 4.6）：折线图与今日卡片用，最多保留 35 天。 */
@Serializable
data class DailyUsage(
    val date: String,
    val promptTokens: Long = 0,
    val completionTokens: Long = 0,
    val cachedTokens: Long = 0,
    val costMicro: Long = 0,
    val calls: Int = 0,
    val errors: Int = 0,
    val chatTokens: Long = 0,
    val backgroundTokens: Long = 0,
) {
    val totalTokens: Long get() = promptTokens + completionTokens

    val costYuan: Double get() = costMicro / 1_000_000.0
}

/** 成本护栏状态（执行书 4.6）：triggers 为空表示未触发。 */
data class CostGuardState(
    val reason: String? = null,
    val todayTokens: Long = 0,
    val monthCostMicro: Long = 0,
) {
    val active: Boolean get() = reason != null
}


@Serializable
data class AppSettings(
    val onboarded: Boolean = false,
    val brain: BrainSettings = BrainSettings(),
    val immersiveEnabled: Boolean = false,
    val immersiveIntensity: Float = 1.0f,
    val dndStartHour: Int = 23,
    val dndEndHour: Int = 8,
    val proactiveEnabled: Boolean = true,
    /** 每天最多主动消息条数（1~3），由好感度决定，此处仅存当日计划日期 */
    val proactivePlannedDate: String? = null,
    val proactiveSentDate: String? = null,
    val proactiveSentCount: Int = 0,
    val usage: UsageStats = UsageStats(),
    /** 最近 35 天用量（执行书 4.6），用于今日卡片与折线图 */
    val dailyUsage: List<DailyUsage> = emptyList(),
    /** 单日 token 上限（40 万），超限后暂停后台自动生成 */
    val dailyTokenCap: Int = 400_000,
    /** 单月费用上限（3 元，单位 micro：1 元 = 1_000_000） */
    val monthlyCostCapMicro: Long = 3_000_000,
    /** 护栏开关；关闭表示「我知道我在花钱」 */
    val degradeOnCapExceeded: Boolean = true,
    /** 护栏通知去重（每天最多一次） */
    val capNotifiedDate: String? = null,
    val lastLifeDate: String? = null,
    val lastNightlyDate: String? = null,
    val lastBackupAt: Long = 0,
    val lastDiaryPlanDate: String? = null,
    val pendingExplicitFacts: List<String> = emptyList(),
    val pendingSessionExtractions: List<Long> = emptyList(),
    val pendingPromotions: Boolean = false,
    val notifyProactive: Boolean = true,
    /** 主动聊天是否已在聊天中自然分享过加密日记 */
    val secretSharedDiaryId: Long? = null,
    /** 分段发送：把一条长回复拆成多条短消息连发（执行书 3.2.4，默认开） */
    val segmentSending: Boolean = true,
    /** 刚写了日记，下一条回复自然提一句“要不要看” */
    val pendingDiaryMentionId: Long? = null,
    /** 引导页选定的身份 / 风格，新对话默认使用 */
    val chosenIdentityId: Long? = null,
    val chosenStyleId: Long? = null,
    /** 聊天形态：连续流（默认，对标微信单窗口）/ 会话列表（按话题归档） */
    val chatMode: String = "continuous",
    /** 连续流模式下，幕后会话切换处是否插入“—— 新的话题 ——”分隔线（默认开） */
    val showTopicDividers: Boolean = true,
    /** 聊天背景图文件名（存在应用私有目录 backgrounds/ 下，仅本机可见） */
    val chatBackground: String? = null,
    /** 背景模糊半径（dp），仅 Android 12+ 生效，低版本自动忽略 */
    val chatBackgroundBlur: Float = 6f,
    /** 背景暗化程度 0~0.8，用于保证气泡文字可读 */
    val chatBackgroundDim: Float = 0.35f,
    /** 家里电脑本地 AI 网关地址（DSHA Gateway，如 http://10.178.116.101:8300），为空则生图/语音都不可用 */
    val localAiUrl: String? = null,
    /** 网关 Token（Authorization: Bearer），存 Keystore 加密配置 */
    val localAiToken: String? = null,
    /** 语音音色预设名（GET /api/voices 返回的名字，如 huihui / 流萤） */
    val ttsVoice: String = "huihui",
    /** 生图画风预设名（GET /api/styles 返回的名字，如 realistic / anime） */
    val imageStyle: String = "realistic",
    /** 应用锁模式：OFF / PIN / BIOMETRIC（执行书 5.6.3，默认关） */
    val appLockMode: String = "OFF",
    /** 回到前台超过这个秒数就重新上锁（默认 60s） */
    val autoLockSeconds: Int = 60,
    /** 外观：主题模式 SYSTEM（默认）/ LIGHT / DARK（执行书 6.1） */
    val themeMode: String = "SYSTEM",
    /** Android 12+ 动态取色开关（默认关，使用品牌色） */
    val dynamicColor: Boolean = false,
    /** 全局字号缩放：0.9 / 1.0 / 1.15 / 1.3 */
    val fontScale: Float = 1.0f,
    /** 气泡主题：BRAND（品牌青灰）/ MINT（薄荷绿）/ WARM（暖橙奶油） */
    val bubbleStyle: String = "BRAND",
    /** 引导里填的「她怎么称呼你」（执行书 6.10），空串表示没填 */
    val userNickname: String = "",
    /** 你希望她怎么自称（例如「我」「小鱼」），空串用身份默认 */
    val userFormOfAddress: String = "",
    /** 首次开场白发送时间，>0 表示已经发过，避免重复（执行书 6.10 Step 6） */
    val greetedAt: Long = 0L,
    /**
     * 她回给你的 reaction（M3-05）：消息 id（字符串）→ 表情。
     * 单用户场景用 Settings JSON 保存，不改 messages 表结构。
     */
    val herReactions: Map<String, String> = emptyMap(),
    /** 最近一次成功推送通知的时间（后台存活自检用，0 表示还没推过） */
    val lastPushAt: Long = 0,
    /** 今日已排期的主动消息时刻（epoch millis），重启/升级后据此重排精确闹钟（执行书 8.2） */
    val proactiveSlots: List<Long> = emptyList(),
)

/** 应用锁模式（执行书 5.6.3）。PIN 的 hash 与盐存在独立文件里，不进备份。 */
enum class AppLockMode {
    OFF, PIN, BIOMETRIC;

    companion object {
        fun from(value: String?): AppLockMode = runCatching {
            valueOf(requireNotNull(value).uppercase())
        }.getOrDefault(OFF)
    }
}

enum class ChatMode {
    CONTINUOUS, SESSION_LIST;

    companion object {
        fun from(value: String?): ChatMode = runCatching {
            valueOf(requireNotNull(value).uppercase())
        }.getOrDefault(CONTINUOUS)
    }
}
