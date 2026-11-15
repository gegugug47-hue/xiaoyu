package com.xiaoyu.app.data.secure

import android.content.Context
import android.util.Base64
import com.xiaoyu.app.data.settings.AppLockMode
import com.xiaoyu.app.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 应用锁（执行书 5.6.3）：PIN 用 PBKDF2 存 hash，密文写入 files/secure/app_lock.json。
 * 单独存文件而不是 settings 表，确保**备份包里不包含 PIN**（备份只打包 DB 与 prompts）。
 */
@Singleton
class AppLockManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val secretStore: SecretStore,
    private val settings: SettingsRepository,
) {
    private val pinFile = File(context.filesDir, "secure/app_lock.json")
    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    @Volatile
    private var backgroundAt = 0L

    fun mode(): AppLockMode = AppLockMode.from(settings.current().appLockMode)

    fun hasPin(): Boolean = readStored()?.hash?.isNotBlank() == true

    /** 设置 / 修改 PIN（4~6 位数字）。成功后停在上锁状态。 */
    suspend fun setPin(pin: String): Boolean {
        val clean = pin.filter { it.isDigit() }
        if (clean.length !in 4..6) return false
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val stored = JSONObject()
            .put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .put("hash", Base64.encodeToString(hash(clean, salt), Base64.NO_WRAP))
        return runCatching {
            pinFile.parentFile?.mkdirs()
            pinFile.writeText(secretStore.encrypt(stored.toString()))
        }.isSuccess
    }

    fun verifyPin(pin: String): Boolean = runCatching {
        val stored = readStored() ?: return false
        val salt = Base64.decode(stored.salt, Base64.NO_WRAP)
        val expected = Base64.decode(stored.hash, Base64.NO_WRAP)
        MessageDigest.isEqual(hash(pin.filter { it.isDigit() }, salt), expected)
    }.getOrDefault(false)

    suspend fun setMode(mode: AppLockMode) {
        settings.update { it.copy(appLockMode = mode.name) }
        _locked.value = mode != AppLockMode.OFF
    }

    suspend fun disable() {
        pinFile.delete()
        settings.update { it.copy(appLockMode = AppLockMode.OFF.name) }
        _locked.value = false
    }

    /** 冷启动：设置加载完成后按需上锁。 */
    suspend fun coldStart() {
        settings.ensureLoaded()
        _locked.value = mode() != AppLockMode.OFF
    }

    fun onBackground() {
        backgroundAt = System.currentTimeMillis()
    }

    fun onForeground() {
        if (mode() == AppLockMode.OFF) {
            _locked.value = false
            return
        }
        val seconds = settings.current().autoLockSeconds.coerceAtLeast(0)
        val now = System.currentTimeMillis()
        if (backgroundAt > 0 && now - backgroundAt >= seconds * 1000L) {
            _locked.value = true
        }
        backgroundAt = 0
    }

    fun lockNow() {
        if (mode() != AppLockMode.OFF) _locked.value = true
    }

    fun unlock() {
        _locked.value = false
    }

    private data class Stored(val salt: String, val hash: String)

    private fun readStored(): Stored? = runCatching {
        if (!pinFile.exists()) return null
        val plain = secretStore.decrypt(pinFile.readText()) ?: return null
        val json = JSONObject(plain)
        Stored(json.getString("salt"), json.getString("hash"))
    }.getOrNull()

    /** PBKDF2WithHmacSHA256，10 万次迭代，256 位输出。 */
    private fun hash(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 100_000, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
