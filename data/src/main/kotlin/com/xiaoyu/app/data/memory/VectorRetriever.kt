package com.xiaoyu.app.data.memory

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * 本地向量检索（执行书 M7-10，可选能力，纯 Kotlin，无原生依赖）。
 *
 * 只负责数学与序列化：把 FloatArray 存成 BLOB、算余弦相似度、取 TopK。
 * 是否拥有 embedding 模型由上层决定；没有模型时调用方传入 null，本类不会被触发。
 */
object VectorRetriever {

    data class Record(val memoryId: Long, val vector: FloatArray)

    data class Scored(val memoryId: Long, val score: Float)

    /** 小端 Float32 序列化，与 SQLite BLOB 一一对应。 */
    fun encode(vector: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(vector.size * Float.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        vector.forEach { buffer.putFloat(it) }
        return buffer.array()
    }

    fun decode(bytes: ByteArray): FloatArray {
        if (bytes.isEmpty() || bytes.size % Float.SIZE_BYTES != 0) return FloatArray(0)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(bytes.size / Float.SIZE_BYTES) { buffer.float }
    }

    /** 余弦相似度；维度不一致或零向量返回 0。 */
    fun cosine(a: FloatArray, b: FloatArray): Float {
        if (a.isEmpty() || a.size != b.size) return 0f
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        if (normA <= 0.0 || normB <= 0.0) return 0f
        return (dot / (sqrt(normA) * sqrt(normB))).toFloat()
    }

    /** 返回相似度最高的 k 条，低于 minScore 的丢弃。 */
    fun topK(
        query: FloatArray,
        candidates: List<Record>,
        k: Int = 6,
        minScore: Float = 0.35f,
    ): List<Scored> {
        if (query.isEmpty() || candidates.isEmpty() || k <= 0) return emptyList()
        return candidates.asSequence()
            .mapNotNull { record ->
                val score = cosine(query, record.vector)
                if (score >= minScore) Scored(record.memoryId, score) else null
            }
            .sortedByDescending { it.score }
            .take(k)
            .toList()
    }
}
