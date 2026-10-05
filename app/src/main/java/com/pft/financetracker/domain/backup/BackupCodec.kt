package com.pft.financetracker.domain.backup

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

sealed class BackupException(message: String) : Exception(message) {
    class NotABackup : BackupException("This file is not a FinTrack backup.")
    class CannotOpen : BackupException("Wrong passphrase, or the file is damaged.")
    class TooNew : BackupException("This backup is from a newer FinTrack. Update the app first.")
}

/**
 * Seals a backup with a passphrase only the person knows: AES-256-GCM, with the key stretched from the passphrase by
 * PBKDF2-HMAC-SHA256 and a random salt. Layout: "FTBK1" | iterations (4 bytes) | salt (16) | nonce (12) | ciphertext+tag.
 * The header is authenticated too, so any changed byte makes the file refuse to open.
 */
class BackupCodec(private val iterations: Int = 600_000, private val random: SecureRandom = SecureRandom()) {
    private val magic = "FTBK1".toByteArray(Charsets.US_ASCII)

    fun encrypt(plain: String, passphrase: CharArray): ByteArray {
        val salt = ByteArray(16).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        val header = ByteBuffer.allocate(magic.size + 4 + salt.size + iv.size).put(magic).putInt(iterations).put(salt).put(iv).array()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(passphrase, salt, iterations), GCMParameterSpec(128, iv))
        cipher.updateAAD(header)
        return header + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
    }

    fun decrypt(bytes: ByteArray, passphrase: CharArray): String {
        val headerSize = magic.size + 4 + 16 + 12
        if (bytes.size <= headerSize || !bytes.copyOfRange(0, magic.size).contentEquals(magic)) throw BackupException.NotABackup()
        val buf = ByteBuffer.wrap(bytes, magic.size, 4 + 16 + 12)
        val rounds = buf.int
        if (rounds !in 1_000..10_000_000) throw BackupException.CannotOpen()
        val salt = ByteArray(16).also { buf.get(it) }
        val iv = ByteArray(12).also { buf.get(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(passphrase, salt, rounds), GCMParameterSpec(128, iv))
        cipher.updateAAD(bytes, 0, headerSize)
        return try {
            String(cipher.doFinal(bytes, headerSize, bytes.size - headerSize), Charsets.UTF_8)
        } catch (e: AEADBadTagException) {
            throw BackupException.CannotOpen()
        }
    }

    private fun key(passphrase: CharArray, salt: ByteArray, rounds: Int): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, rounds, 256)
        try {
            return SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
