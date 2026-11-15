package com.xiaoyu.app.data.usage

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 外置单价表（执行书 4.6 / F19）：`assets/cost_table.json` 用户可改，开源社区可维护。
 * 单位：元 / 百万 token；未知模型按 0 计，调用方标注「未定价」。
 */
@Singleton
class CostTable @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    data class Entry(
        val input: Double,
        val output: Double,
        val cachedInput: Double,
    )

    private val prices: Map<String, Entry> by lazy { load() }

    /** 估算一次调用的费用（元）。Ollama / 示例模式单价为 0。 */
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

    fun isPriced(model: String): Boolean = prices.containsKey(model.trim().lowercase())

    private fun load(): Map<String, Entry> = runCatching {
        val text = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
        val json = JSONObject(text)
        buildMap {
            json.keys().forEach { key ->
                val obj = json.getJSONObject(key)
                put(
                    key.trim().lowercase(),
                    Entry(
                        input = obj.optDouble("input", 0.0),
                        output = obj.optDouble("output", 0.0),
                        cachedInput = obj.optDouble("cached_input", obj.optDouble("input", 0.0)),
                    ),
                )
            }
        }
    }.getOrDefault(emptyMap())

    companion object {
        const val ASSET_NAME = "cost_table.json"
    }
}
