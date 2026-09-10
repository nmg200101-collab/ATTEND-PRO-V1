package com.attendpro.foundation.backup

import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class BackupEnvelopeMetadata(
    val backupId: String,
    val role: String,
    val createdAt: Long,
    val sourceIdHash: String,
    val formatVersion: Int,
    val sizeBytes: Int
)

/**
 * Password-encrypted, portable ATTEND PRO backup envelope.
 *
 * The payload is compressed before AES-256-GCM encryption. Envelope metadata is
 * authenticated as AAD so it cannot be changed without invalidating the backup.
 */
object EncryptedBackupCodec {
    const val FORMAT = "ATTEND-PRO-ENCRYPTED-BACKUP"
    const val FORMAT_VERSION = 1
    const val PBKDF2_ITERATIONS = 310_000
    const val MAX_PLAINTEXT_BYTES = 24 * 1024 * 1024
    const val MAX_ENVELOPE_BYTES = 18 * 1024 * 1024
    private const val MIN_PASSWORD_LENGTH = 8
    private const val MAX_PASSWORD_LENGTH = 256
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val KEY_BITS = 256

    fun encrypt(
        payload: String,
        password: CharArray,
        role: String,
        sourceId: String,
        createdAt: Long = System.currentTimeMillis(),
        random: SecureRandom = SecureRandom()
    ): String {
        validatePassword(password)
        val cleanRole = role.trim().uppercase()
        require(cleanRole in setOf("STORE", "EMPLOYEE")) { "Unsupported backup role" }
        require(sourceId.isNotBlank()) { "Backup source is required" }
        require(createdAt > 0L) { "Invalid backup time" }
        val plain = payload.toByteArray(Charsets.UTF_8)
        require(plain.size <= MAX_PLAINTEXT_BYTES) { "Backup data is too large" }

        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val backupId = UUID.randomUUID().toString()
        val sourceHash = sha256B64(sourceId)
        val aad = authenticatedMetadata(backupId, cleanRole, createdAt, sourceHash)
        val compressed = gzip(plain)
        val key = deriveKey(password, salt, PBKDF2_ITERATIONS)
        val cipherText = try {
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
                updateAAD(aad.toByteArray(Charsets.UTF_8))
                doFinal(compressed)
            }
        } finally {
            key.encoded?.fill(0)
            plain.fill(0)
            compressed.fill(0)
        }

        val encoded = JSONObject()
            .put("format", FORMAT)
            .put("version", FORMAT_VERSION)
            .put("backupId", backupId)
            .put("role", cleanRole)
            .put("createdAt", createdAt)
            .put("sourceIdHash", sourceHash)
            .put("compression", "GZIP")
            .put("kdf", "PBKDF2WithHmacSHA256")
            .put("iterations", PBKDF2_ITERATIONS)
            .put("salt", b64(salt))
            .put("cipher", "AES-256-GCM")
            .put("iv", b64(iv))
            .put("payload", b64(cipherText))
            .toString()
        require(encoded.toByteArray(Charsets.UTF_8).size <= MAX_ENVELOPE_BYTES) { "Encrypted backup is too large" }
        cipherText.fill(0)
        return encoded
    }

    fun decrypt(envelope: String, password: CharArray, expectedRole: String? = null): String {
        validatePassword(password)
        val metadata = inspect(envelope)
        expectedRole?.let {
            require(metadata.role == it.trim().uppercase()) { "Backup belongs to a different ATTEND PRO app" }
        }
        val root = JSONObject(envelope)
        require(root.getString("compression") == "GZIP") { "Unsupported backup compression" }
        require(root.getString("kdf") == "PBKDF2WithHmacSHA256") { "Unsupported backup KDF" }
        require(root.getInt("iterations") == PBKDF2_ITERATIONS) { "Unsupported backup KDF strength" }
        require(root.getString("cipher") == "AES-256-GCM") { "Unsupported backup cipher" }
        val salt = b64d(root.getString("salt"), SALT_BYTES, SALT_BYTES)
        val iv = b64d(root.getString("iv"), IV_BYTES, IV_BYTES)
        val cipherText = b64d(root.getString("payload"), 17, MAX_ENVELOPE_BYTES)
        val aad = authenticatedMetadata(metadata.backupId, metadata.role, metadata.createdAt, metadata.sourceIdHash)
        val key = deriveKey(password, salt, PBKDF2_ITERATIONS)
        val compressed = try {
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
                updateAAD(aad.toByteArray(Charsets.UTF_8))
                doFinal(cipherText)
            }
        } catch (_: AEADBadTagException) {
            throw IllegalArgumentException("Backup password is incorrect or the file was modified")
        } finally {
            key.encoded?.fill(0)
            cipherText.fill(0)
        }
        return try {
            String(gunzipBounded(compressed), Charsets.UTF_8)
        } finally {
            compressed.fill(0)
        }
    }

    fun inspect(envelope: String): BackupEnvelopeMetadata {
        val bytes = envelope.toByteArray(Charsets.UTF_8)
        require(bytes.isNotEmpty() && bytes.size <= MAX_ENVELOPE_BYTES) { "Invalid backup size" }
        val root = runCatching { JSONObject(envelope) }.getOrElse { throw IllegalArgumentException("Invalid backup file") }
        require(root.optString("format") == FORMAT) { "This is not an ATTEND PRO backup" }
        require(root.optInt("version", -1) == FORMAT_VERSION) { "Unsupported backup version" }
        val backupId = root.optString("backupId").trim()
        val role = root.optString("role").trim().uppercase()
        val createdAt = root.optLong("createdAt", 0L)
        val sourceHash = root.optString("sourceIdHash").trim()
        require(backupId.length in 16..80 && role in setOf("STORE", "EMPLOYEE")) { "Invalid backup metadata" }
        require(createdAt > 0L && createdAt <= System.currentTimeMillis() + 86_400_000L) { "Invalid backup time" }
        require(sourceHash.length in 32..128) { "Invalid backup source" }
        return BackupEnvelopeMetadata(backupId, role, createdAt, sourceHash, FORMAT_VERSION, bytes.size)
    }

    fun sourceIdHash(sourceId: String): String = sha256B64(sourceId)

    private fun authenticatedMetadata(backupId: String, role: String, createdAt: Long, sourceHash: String): String =
        "$FORMAT\n$FORMAT_VERSION\n$backupId\n$role\n$createdAt\n$sourceHash\nGZIP\nPBKDF2WithHmacSHA256\n$PBKDF2_ITERATIONS\nAES-256-GCM"

    private fun validatePassword(password: CharArray) {
        require(password.size in MIN_PASSWORD_LENGTH..MAX_PASSWORD_LENGTH) { "Backup password must contain at least 8 characters" }
        require(password.any { !it.isWhitespace() }) { "Backup password cannot be blank" }
    }

    private fun deriveKey(password: CharArray, salt: ByteArray, rounds: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, rounds, KEY_BITS)
        return try {
            SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun gzip(input: ByteArray): ByteArray = ByteArrayOutputStream().use { output ->
        GZIPOutputStream(output).use { it.write(input) }
        output.toByteArray()
    }

    private fun gunzipBounded(input: ByteArray): ByteArray = GZIPInputStream(ByteArrayInputStream(input)).use { gzip ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val count = gzip.read(buffer)
            if (count < 0) break
            total += count
            require(total <= MAX_PLAINTEXT_BYTES) { "Backup expands beyond the safety limit" }
            output.write(buffer, 0, count)
        }
        output.toByteArray()
    }

    private fun sha256B64(value: String): String = b64(
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
    )

    private fun b64(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun b64d(value: String, minBytes: Int, maxBytes: Int): ByteArray {
        val decoded = runCatching { Base64.getUrlDecoder().decode(value) }
            .getOrElse { throw IllegalArgumentException("Invalid backup encoding") }
        require(decoded.size in minBytes..maxBytes) { "Invalid backup field size" }
        return decoded
    }
}
