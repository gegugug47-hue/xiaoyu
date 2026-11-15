package com.xiaoyu.app.data.brain

import com.xiaoyu.soul.core.BrainChunk
import com.xiaoyu.soul.core.BrainRole
import com.xiaoyu.soul.core.SoulMessage
import kotlinx.coroutines.flow.Flow

/**
 * 大脑端口（执行书 2.2）：数据层只依赖该接口，具体厂商路由由 :brain 模块实现。
 * 这样 :data 不需要反向依赖 :brain，避免循环依赖。
 */
interface BrainGateway {
    fun chat(role: BrainRole, messages: List<SoulMessage>): Flow<BrainChunk>

    suspend fun completeJson(role: BrainRole, system: String?, user: String): String?

    suspend fun completeText(role: BrainRole, prompt: String): String?
}
