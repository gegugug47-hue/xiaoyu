package com.xiaoyu.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.xiaoyu.app.data.secure.AppLockManager
import com.xiaoyu.app.data.settings.AppLockMode
import com.xiaoyu.app.data.settings.SettingsRepository
import com.xiaoyu.app.ui.LockScreen
import com.xiaoyu.app.ui.XiaoyuRoot
import com.xiaoyu.app.ui.theme.XiaoyuTheme
import com.xiaoyu.app.widget.LockScreenEntry
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var appLock: AppLockManager

    @Inject
    lateinit var settingsRepository: SettingsRepository

    /** 点通知进来时的会话 id，作为可重复触发的状态暴露给 Compose；消费后置空。 */
    private val pendingSessionId = MutableStateFlow<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // 统一 Android 8~16 的窗口行为：内容延伸到系统栏下方，由 Compose 的 inset 决定留白。
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleLockScreenIntent(intent)
        handleSessionIntent(intent)
        // 冷启动就要上锁：先把设置读出来，再决定是否拦在锁屏页（执行书 5.6.3）
        lifecycleScope.launch { appLock.coldStart() }
        setContent {
            val settings by settingsRepository.state.collectAsStateWithLifecycle()
            XiaoyuTheme(
                themeMode = settings.themeMode,
                dynamicColor = settings.dynamicColor,
                preset = settings.themePreset,
                customSeed = settings.customThemeSeed,
                reduceMotion = settings.reduceMotion,
                fontScale = settings.fontScale,
                bubbleStyle = settings.bubbleStyle,
            ) {
                val locked by appLock.locked.collectAsStateWithLifecycle()
                Surface(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    if (locked) {
                        LockScreen(
                            biometricEnabled = appLock.mode() == AppLockMode.BIOMETRIC,
                            verifyPin = appLock::verifyPin,
                            onUnlocked = appLock::unlock,
                        )
                    } else {
                        val incomingSessionId by pendingSessionId.collectAsStateWithLifecycle()
                        XiaoyuRoot(
                            pendingSessionId = incomingSessionId,
                            onSessionConsumed = { pendingSessionId.value = null },
                        )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        appLock.onForeground()
    }

    override fun onStop() {
        appLock.onBackground()
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLockScreenIntent(intent)
        // App 存活时点通知走这里：把 sessionId 推进状态，Compose 侧即可重复响应
        handleSessionIntent(intent)
    }

    private fun handleSessionIntent(intent: Intent?) {
        val sessionId = intent?.getLongExtra("sessionId", -1L) ?: -1L
        if (sessionId > 0L) {
            // 先消费掉 extra，避免 Activity 重建时残留导致再次跳转
            intent?.removeExtra("sessionId")
            pendingSessionId.value = sessionId
        }
    }

    /**
     * M7-08：从系统锁屏快捷入口进来时，允许她把卡片留在锁屏上。
     * 普通桌面启动不会触碰这一设置，避免和应用锁互相打架。
     */
    private fun handleLockScreenIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(LockScreenEntry.EXTRA_FROM_LOCK_SCREEN, false) == true) {
            LockScreenEntry.enableLockScreenEntry(this)
        }
    }
}
