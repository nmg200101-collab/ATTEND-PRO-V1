package com.attendpro.core

import java.util.Base64
import java.security.SecureRandom

object SecretCodec {
    fun generate(size: Int = 16): ByteArray = ByteArray(size).also { SecureRandom().nextBytes(it) }

    fun encode(bytes: ByteArray): String = Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(bytes)

    fun decode(text: String): ByteArray? = try {
        Base64.getUrlDecoder().decode(text.trim())
    } catch (_: IllegalArgumentException) {
        null
    }

    fun isValid(text: String): Boolean = decode(text)?.size?.let { it >= 16 } == true
}
