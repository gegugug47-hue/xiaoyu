package com.xiaoyu.app.data.settings

import com.xiaoyu.app.data.db.SettingDao
import com.xiaoyu.app.data.db.SettingEntity
import com.xiaoyu.app.data.secure.SecretStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val dao: SettingDao,
    private val secretStore: SecretStore,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val key = "app_settings"
    private val _state = MutableStateFlow(AppSettings())
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    @Volatile
    private var loaded = false

    suspend fun ensureLoaded() {
        if (loaded) return
        load()
    }

    suspend fun load() {
        val stored = dao.get(key)
        if (stored != null) {
            val plain = secretStore.decrypt(stored.valueEncrypted)
            if (plain != null) {
                _state.value = runCatching { json.decodeFromString<AppSettings>(plain) }
                    .getOrDefault(AppSettings())
            }
        } else {
            dao.put(SettingEntity(key, secretStore.encrypt(json.encodeToString(AppSettings.serializer(), _state.value))))
        }
        loaded = true
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_state.value)
        _state.value = next
        dao.put(SettingEntity(key, secretStore.encrypt(json.encodeToString(AppSettings.serializer(), next))))
    }

    fun current(): AppSettings = _state.value

    suspend fun recordUsage(
        isChat: Boolean,
        promptTokens: Int,
        completionTokens: Int,
        costYuan: Double,
        cachedTokens: Int = 0,
        error: Boolean = false,
    ) {
        val month = java.time.YearMonth.now().toString()
        val date = java.time.LocalDate.now().toString()
        val costMicro = kotlin.math.round(costYuan * 1_000_000).toLong()
        update { settings ->
            val base = if (settings.usage.month == month) settings.usage
            else UsageStats(month = month)
            val usage = if (isChat) {
                base.copy(
                    chatPromptTokens = base.chatPromptTokens + promptTokens,
                    chatCompletionTokens = base.chatCompletionTokens + completionTokens,
                    chatRequests = base.chatRequests + 1,
                    estimatedCost = base.estimatedCost + costYuan,
                    cachedTokens = base.cachedTokens + cachedTokens,
                )
            } else {
                base.copy(
                    extractPromptTokens = base.extractPromptTokens + promptTokens,
                    extractCompletionTokens = base.extractCompletionTokens + completionTokens,
                    extractRequests = base.extractRequests + 1,
                    estimatedCost = base.estimatedCost + costYuan,
                    cachedTokens = base.cachedTokens + cachedTokens,
                )
            }
            settings.copy(
                usage = usage,
                dailyUsage = upsertDaily(
                    settings.dailyUsage,
                    date = date,
                    promptTokens = promptTokens,
                    completionTokens = completionTokens,
                    cachedTokens = cachedTokens,
                    costMicro = costMicro,
                    isChat = isChat,
                    error = error,
                ),
            )
        }
    }

    /** 单日用量 upsert，只保留最近 35 天（执行书 4.6）。 */
    private fun upsertDaily(
        list: List<DailyUsage>,
        date: String,
        promptTokens: Int,
        completionTokens: Int,
        cachedTokens: Int,
        costMicro: Long,
        isChat: Boolean,
        error: Boolean,
    ): List<DailyUsage> {
        val existing = list.firstOrNull { it.date == date } ?: DailyUsage(date)
        val tokens = promptTokens.toLong() + completionTokens
        val updated = existing.copy(
            promptTokens = existing.promptTokens + promptTokens,
            completionTokens = existing.completionTokens + completionTokens,
            cachedTokens = existing.cachedTokens + cachedTokens,
            costMicro = existing.costMicro + costMicro,
            calls = existing.calls + 1,
            errors = existing.errors + if (error) 1 else 0,
            chatTokens = existing.chatTokens + if (isChat) tokens else 0,
            backgroundTokens = existing.backgroundTokens + if (isChat) 0 else tokens,
        )
        return (list.filterNot { it.date == date } + updated).sortedBy { it.date }.takeLast(35)
    }

    /**
     * 成本护栏状态（执行书 v3 §2.5）：三级动作，永不断聊天。
     * - L1_WARN：日 token 或月费 > 60%，仅提示
     * - L2_THROTTLE：> 100%，停可选后台（VISION），聊天 + 记忆提炼保留
     * - L3_STOP：月费 > 150%，停全部可选后台，聊天 + 记忆提炼仍保留
     */
    fun guardState(): CostGuardState {
        val s = _state.value
        val today = s.dailyUsage.lastOrNull { it.date == java.time.LocalDate.now().toString() }
        val todayTokens = today?.totalTokens ?: 0L
        val monthCostMicro = kotlin.math.round(s.usage.estimatedCost * 1_000_000).toLong()
        if (!s.degradeOnCapExceeded) {
            return CostGuardState(CostGuardLevel.NONE, null, todayTokens, monthCostMicro)
        }
        val dailyRatio = todayTokens.toDouble() / s.dailyTokenCap
        val monthRatio = monthCostMicro.toDouble() / s.monthlyCostCapMicro
        val monthYuan = "%.2f".format(monthCostMicro / 1_000_000.0)
        val capYuan = "%.2f".format(s.monthlyCostCapMicro / 1_000_000.0)
        val (level, reason) = when {
            monthRatio > 1.5 -> CostGuardLevel.L3_STOP to
                "本月费用 ¥$monthYuan 已超上限 ¥$capYuan 的 150%，可选后台已暂停，聊天与记忆不受影响"
            dailyRatio > 1.0 || monthRatio > 1.0 -> CostGuardLevel.L2_THROTTLE to
                "今日 token $todayTokens 或本月费用 ¥$monthYuan 已达上限 ¥$capYuan，" +
                    "表情打标/生图描述等可选后台已暂停，聊天与记忆不受影响"
            dailyRatio > 0.6 || monthRatio > 0.6 -> CostGuardLevel.L1_WARN to
                "用量已达上限的 60%（今日 $todayTokens token / 本月 ¥$monthYuan），请注意控制"
            else -> CostGuardLevel.NONE to null
        }
        return CostGuardState(level, reason, todayTokens, monthCostMicro)
    }
}
