package com.attendpro.core

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** 1.9.80: slow salted credential hashing for local administration gates. */
object CredentialHash1980 {
    private const val PREFIX = "pbkdf2-sha256"
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256

    fun hash(value: String): String {
        val clean = value.trim()
        require(clean.isNotBlank()) { "credential must not be blank" }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val derived = derive(clean, salt, ITERATIONS)
        return "$PREFIX:$ITERATIONS:${b64(salt)}:${b64(derived)}"
    }

    fun verify(stored: String, entered: String): Boolean {
        if (stored.isBlank() || entered.isBlank()) return false
        if (!stored.startsWith("$PREFIX:")) return PairingProtocol.matchesPin(stored, entered)
        return runCatching {
            val parts = stored.split(':')
            require(parts.size == 4 && parts[0] == PREFIX)
            val rounds = parts[1].toInt().coerceIn(50_000, 500_000)
            val salt = b64d(parts[2])
            val expected = b64d(parts[3])
            val actual = derive(entered.trim(), salt, rounds)
            MessageDigest.isEqual(expected, actual)
        }.getOrDefault(false)
    }

    fun needsUpgrade(stored: String): Boolean = stored.isNotBlank() && !stored.startsWith("$PREFIX:")

    private fun derive(value: String, salt: ByteArray, rounds: Int): ByteArray {
        val spec = PBEKeySpec(value.toCharArray(), salt, rounds, KEY_BITS)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }

    private fun b64(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    private fun b64d(text: String): ByteArray = Base64.getUrlDecoder().decode(text)
}
