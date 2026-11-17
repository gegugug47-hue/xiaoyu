package com.xiaoyu.app.data.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
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

    @Test
    fun background_backup_contains_originals_but_not_cache() {
        val filesDir = Files.createTempDirectory("xy-bg-backup").toFile()
        try {
            val bgDir = File(filesDir, "bg").apply { mkdirs() }
            val name = "${"a".repeat(40)}.jpg"
            val expected = byteArrayOf(1, 2, 3, 4)
            File(bgDir, name).writeBytes(expected)
            File(bgDir, "not-a-sha1.jpg").writeBytes(byteArrayOf(9))
            File(bgDir, "cache").apply { mkdirs() }
                .let { File(it, "$name.webp").writeBytes(byteArrayOf(8)) }

            val bytes = ByteArrayOutputStream()
            ZipOutputStream(bytes).use { BackgroundBackup.addOriginals(filesDir, it) }
            val entries = linkedMapOf<String, ByteArray>()
            ZipInputStream(bytes.toByteArray().inputStream()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    entries[entry.name] = zip.readBytes()
                    entry = zip.nextEntry
                }
            }

            assertEquals(setOf("media/bg/$name"), entries.keys)
            assertArrayEquals(expected, entries["media/bg/$name"])
        } finally {
            filesDir.deleteRecursively()
        }
    }

    @Test
    fun background_restore_writes_original_and_preserves_cache() {
        val filesDir = Files.createTempDirectory("xy-bg-restore").toFile()
        try {
            val name = "${"b".repeat(40)}.jpg"
            val expected = byteArrayOf(5, 6, 7)
            val cache = File(filesDir, "bg/cache/existing.webp").apply {
                parentFile?.mkdirs()
                writeBytes(byteArrayOf(4))
            }

            BackgroundBackup.restore(filesDir, mapOf(name to expected))

            assertArrayEquals(expected, File(filesDir, "bg/$name").readBytes())
            assertArrayEquals(byteArrayOf(4), cache.readBytes())
        } finally {
            filesDir.deleteRecursively()
        }
    }

    @Test
    fun background_entry_name_rejects_cache_and_non_sha1_files() {
        val name = "${"c".repeat(40)}.jpg"
        assertEquals(name, BackgroundBackup.fileName("media/bg/$name"))
        assertNull(BackgroundBackup.fileName("media/bg/cache/$name.webp"))
        assertNull(BackgroundBackup.fileName("media/bg/not-a-sha1.jpg"))
        assertNull(BackgroundBackup.fileName("media/bg/../$name"))
    }
}
