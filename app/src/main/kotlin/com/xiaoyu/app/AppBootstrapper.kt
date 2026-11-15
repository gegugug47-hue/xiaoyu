package com.xiaoyu.app

import com.xiaoyu.app.data.prompt.PromptAssetSource
import com.xiaoyu.app.data.repo.ChatRepository
import com.xiaoyu.app.data.repo.MemoryRepository
import com.xiaoyu.app.data.repo.PersonaRepository
import com.xiaoyu.app.data.settings.SettingsRepository
import com.xiaoyu.app.work.Notifier
import com.xiaoyu.app.work.WorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** 启动自检与补偿：设置加载、提示词播种、人设播种、漏掉的提炼/清算排期（PRD 4.2 / 12）。 */
@Singleton
class AppBootstrapper @Inject constructor(
    private val settings: SettingsRepository,
    private val prompts: PromptAssetSource,
    private val persona: PersonaRepository,
    private val chat: ChatRepository,
    private val memory: MemoryRepository,
    private val notifier: Notifier,
    private val scheduler: WorkScheduler,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start() {
        notifier.ensureChannel()
        scope.launch {
            runCatching {
                settings.ensureLoaded()
                prompts.seedIfNeeded()
                persona.seedIfEmpty()
                chat.finalizeStaleSessions()
                chat.reschedulePendingExtractions()
                scheduler.ensureDailyPlan()
                if (settings.current().pendingExplicitFacts.isNotEmpty()) {
                    scheduler.scheduleExplicitRetry()
                }
                memory.processPendingExplicitFacts()
            }
        }
    }
}
