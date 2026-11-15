package com.xiaoyu.app.data.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * 备份加密（执行书 5.6 / M5-01）：PBKDF2 + AES-GCM，
 * 错误口令必须解密失败，正确口令必须还原明文，且每次加密产物不同（随机盐+IV）。
 */
class BackupCryptoTest {

    private val plaintext = "小渔的聊天记录备份 2026-11-15 🐟".toByteArray(Charsets.UTF_8)
    private val password = "正确的口令123"

    @Test
    fun roundtrip_restores_plaintext_with_correct_password() {
        val encrypted = BackupCrypto.encrypt(plaintext, password)
        val decrypted = BackupCrypto.decrypt(encrypted, password)
        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun wrong_password_returns_null() {
        val encrypted = BackupCrypto.encrypt(plaintext, password)
        assertNull(BackupCrypto.decrypt(encrypted, "错误的口令"))
    }

    @Test
    fun each_encryption_produces_different_output() {
        val a = BackupCrypto.encrypt(plaintext, password)
        val b = BackupCrypto.encrypt(plaintext, password)
        assertNotEquals(a.toList(), b.toList())
        // 两个产物都能用同一口令还原
        assertArrayEquals(plaintext, BackupCrypto.decrypt(a, password))
        assertArrayEquals(plaintext, BackupCrypto.decrypt(b, password))
    }

    @Test
    fun empty_password_still_roundtrips() {
        val encrypted = BackupCrypto.encrypt(plaintext, "")
        assertArrayEquals(plaintext, BackupCrypto.decrypt(encrypted, ""))
        assertNull(BackupCrypto.decrypt(encrypted, "x"))
    }

    @Test
    fun large_payload_roundtrips() {
        val big = Random.nextBytes(1024 * 256)
        val encrypted = BackupCrypto.encrypt(big, password)
        assertArrayEquals(big, BackupCrypto.decrypt(encrypted, password))
    }

    @Test
    fun tampered_ciphertext_returns_null() {
        val encrypted = BackupCrypto.encrypt(plaintext, password)
        val tampered = encrypted.copyOf()
        tampered[tampered.size - 1] = (tampered[tampered.size - 1] + 1).toByte()
        assertNull(BackupCrypto.decrypt(tampered, password))
    }

    @Test
    fun magic_header_is_present() {
        val encrypted = BackupCrypto.encrypt(plaintext, password)
        assertTrue(encrypted.copyOfRange(0, BackupCrypto.MAGIC.length) contentEquals BackupCrypto.MAGIC.toByteArray(Charsets.UTF_8))
        assertEquals("XYBK", BackupCrypto.MAGIC)
    }
}
