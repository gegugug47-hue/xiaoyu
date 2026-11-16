package com.xiaoyu.app.data.usage

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 外置单价表（执行书 4.6 / F19；v3 §2.4 补充币种与有效期）：
 * `assets/cost_table.json` 用户可改，开源社区可维护。
 * 单位：[currency] / 百万 token；未知模型按 0 计并标记「未定价」。
 */
@Singleton
class CostTable @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    data class Entry(
        val input: Double,
        val output: Double,
        val cachedInput: Double,
        val unpriced: Boolean = false,
    )

    val currency: String by lazy { loadMeta().first }
    val checkedAt: String by lazy { loadMeta().second }

    private val prices: Map<String, Entry> by lazy { loadPrices() }

    /** 估算一次调用的费用（[currency] 单位）。Ollama / 示例模式单价为 0。 */
    fun estimate(
        model: String,
        promptTokens: Int,
        completionTokens: Int,
        cachedTokens: Int = 0,
    ): Double {
        val entry = prices[model.trim().lowercase()] ?: return 0.0
        val cached = cachedTokens.coerceIn(0, promptTokens)
        val fresh = promptTokens - cached
        return fresh / 1_000_000.0 * entry.input +
            cached / 1_000_000.0 * entry.cachedInput +
            completionTokens / 1_000_000.0 * entry.output
    }

    fun isPriced(model: String): Boolean {
        val entry = prices[model.trim().lowercase()] ?: return false
        return !entry.unpriced
    }

    private fun loadMeta(): Pair<String, String> = runCatching {
        val json = JSONObject(readAsset())
        json.optString("currency", "CNY") to json.optString("checked_at", "")
    }.getOrDefault("CNY" to "")

    private fun loadPrices(): Map<String, Entry> = runCatching {
        val json = JSONObject(readAsset())
        val models = json.optJSONObject("models") ?: return@runCatching emptyMap()
        buildMap {
            models.keys().forEach { key ->
                val obj = models.getJSONObject(key)
                put(
                    key.trim().lowercase(),
                    Entry(
                        input = obj.optDouble("input", 0.0),
                        output = obj.optDouble("output", 0.0),
                        cachedInput = obj.optDouble("cached_input", obj.optDouble("input", 0.0)),
                        unpriced = obj.optBoolean("unpriced", false),
                    ),
                )
            }
        }
    }.getOrDefault(emptyMap())

    private fun readAsset(): String =
        context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }

    companion object {
        const val ASSET_NAME = "cost_table.json"
    }
}
