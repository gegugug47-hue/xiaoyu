package com.xiaoyu.app.data.backup

import java.security.SecureRandom
import java.security.spec.KeySpec
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 备份加密（执行书 M5-01）：口令 → PBKDF2 派生 AES 密钥 → AES/GCM/NoPadding。
 *
 * 产物格式（便于未来版本迁移）：
 * ```
 * MAGIC(4) | VERSION(1) | SALT(16) | IV(12) | CIPHERTEXT+TAG
 * ```
 * 解密失败（口令错误 / 被篡改）返回 null，调用方据此提示"口令不对"。
 * 全部使用标准 JDK API，便于在 JVM 单测里直接验证。
 */
object BackupCrypto {
    const val MAGIC = "XYBK"

    /** 当前容器格式版本；与下面的 VERSION 必须保持一致（M5-02 导入时用于识别更新版本）。 */
    const val FORMAT_VERSION: Int = 1
    private const val VERSION: Byte = 1
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val GCM_TAG_BITS = 128
    private const val PBKDF2_ITERATIONS = 200_000
    private const val KEY_LEN_BITS = 256
    private const val CIPHER = "AES/GCM/NoPadding"
    private const val KDF = "PBKDF2WithHmacSHA256"

    private val magicBytes = MAGIC.toByteArray(Charsets.UTF_8)

    fun encrypt(plaintext: ByteArray, password: String): ByteArray {
        val salt = ByteArray(SALT_LEN).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance(CIPHER)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext)
        return ByteArray(magicBytes.size + 1 + SALT_LEN + IV_LEN + ciphertext.size).apply {
            var pos = 0
            magicBytes.copyInto(this, pos); pos += magicBytes.size
            this[pos++] = VERSION
            salt.copyInto(this, pos); pos += SALT_LEN
            iv.copyInto(this, pos); pos += IV_LEN
            ciphertext.copyInto(this, pos)
        }
    }

    fun decrypt(data: ByteArray, password: String): ByteArray? = runCatching {
        require(data.size >= magicBytes.size + 1 + SALT_LEN + IV_LEN + 16) { "too short" }
        var pos = 0
        require(data.copyOfRange(pos, pos + magicBytes.size).contentEquals(magicBytes)) { "bad magic" }
        pos += magicBytes.size
        val version = data[pos++]; require(version == VERSION) { "bad version" }
        val salt = data.copyOfRange(pos, pos + SALT_LEN); pos += SALT_LEN
        val iv = data.copyOfRange(pos, pos + IV_LEN); pos += IV_LEN
        val ciphertext = data.copyOfRange(pos, data.size)
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance(CIPHER)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.doFinal(ciphertext)
    }.getOrNull()

    /** 文件头信息：是否加密、容器格式版本（明文 zip 的版本记为 0）。 */
    data class HeaderInfo(val encrypted: Boolean, val version: Int)

    /**
     * 只读文件头，不解密也不消耗口令（M5-02）。
     * 返回 null 表示长度不足，连魔数都读不出来。
     */
    fun peekHeader(data: ByteArray): HeaderInfo? {
        if (data.size < magicBytes.size) return null
        val encrypted = data.copyOfRange(0, magicBytes.size).contentEquals(magicBytes)
        if (!encrypted) return HeaderInfo(false, 0)
        if (data.size < magicBytes.size + 1) return HeaderInfo(true, 0)
        return HeaderInfo(true, data[magicBytes.size].toInt() and 0xFF)
    }

    private fun deriveKey(password: String, salt: ByteArray): ByteArray {
        val spec: KeySpec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LEN_BITS)
        return SecretKeyFactory.getInstance(KDF).generateSecret(spec).encoded
    }
}
