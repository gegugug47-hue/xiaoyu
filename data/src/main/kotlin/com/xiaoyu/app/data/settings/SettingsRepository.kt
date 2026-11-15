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

    /** 成本护栏状态（执行书 4.6）：日 token 或月费用超限时返回原因。 */
    fun guardState(): CostGuardState {
        val s = _state.value
        val today = s.dailyUsage.lastOrNull { it.date == java.time.LocalDate.now().toString() }
        val todayTokens = today?.totalTokens ?: 0L
        val monthCostMicro = kotlin.math.round(s.usage.estimatedCost * 1_000_000).toLong()
        if (!s.degradeOnCapExceeded) {
            return CostGuardState(null, todayTokens, monthCostMicro)
        }
        val reason = when {
            todayTokens > s.dailyTokenCap ->
                "今日用量 ${todayTokens} token 已超过上限 ${s.dailyTokenCap}，后台自动生成已暂停"
            monthCostMicro > s.monthlyCostCapMicro ->
                "本月估算费用 ${"%.2f".format(monthCostMicro / 1_000_000.0)} 元已超过上限 " +
                    "${"%.2f".format(s.monthlyCostCapMicro / 1_000_000.0)} 元，后台自动生成已暂停"
            else -> null
        }
        return CostGuardState(reason, todayTokens, monthCostMicro)
    }
}
