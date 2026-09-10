package com.attendpro.core

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.provider.Settings
import android.util.Base64
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.MessageDigest

/**
 * Non-exportable P-256 device identity used to bind central-server activation and API calls
 * to the physical Android installation. The private key never leaves Android Keystore.
 */
class DeviceIdentity(context: Context) {
    private val appContext = context.applicationContext
    private val alias = "attend_pro_device_identity_v1"
    init { context.applicationContext } // keep API symmetric; no Context is retained.

    fun publicKeyB64(): String {
        val entry = keyEntry()
        return Base64.encodeToString(entry.certificate.publicKey.encoded, Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)
    }

    fun sign(text: String): String {
        val entry = keyEntry()
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(entry.privateKey)
        signature.update(text.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(signature.sign(), Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)
    }

    fun deviceLabel(): String {
        val manufacturer = android.os.Build.MANUFACTURER.orEmpty().trim()
        val model = android.os.Build.MODEL.orEmpty().trim()
        return listOf(manufacturer, model).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Android" }.take(120)
    }

    fun recoveryFingerprint(): String {
        val androidId = Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
        val certificate = runCatching {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                appContext.packageManager.getPackageInfo(appContext.packageName, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES)
                    .signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
            } else {
                @Suppress("DEPRECATION")
                appContext.packageManager.getPackageInfo(appContext.packageName, android.content.pm.PackageManager.GET_SIGNATURES)
                    .signatures?.firstOrNull()?.toByteArray()
            }
        }.getOrNull() ?: ByteArray(0)
        val signingDigest = MessageDigest.getInstance("SHA-256").digest(certificate)
        val material = androidId.toByteArray(Charsets.UTF_8) + signingDigest + appContext.packageName.toByteArray(Charsets.UTF_8)
        return Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(material), Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)
    }

    private fun keyEntry(): KeyStore.PrivateKeyEntry {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(alias, null) as? KeyStore.PrivateKeyEntry)?.let { return it }
        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
        generator.initialize(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build()
        )
        generator.generateKeyPair()
        return ks.getEntry(alias, null) as KeyStore.PrivateKeyEntry
    }
}
