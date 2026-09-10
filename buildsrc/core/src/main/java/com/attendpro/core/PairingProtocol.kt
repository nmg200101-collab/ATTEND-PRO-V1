package com.attendpro.core

import java.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater

object PairingProtocol {
    private const val PREFIX_QR_PAIRING_V5 = "AP5Q:"
    private const val PREFIX_PROVISION_V4 = "AP4P:"
    private const val PREFIX_PROVISION_V3 = "AP3P:"
    private const val PREFIX_STORE_V2 = "AP2S:"
    private const val PREFIX_EMPLOYEE_V2 = "AP2E:"
    private const val PREFIX_STORE_V1 = "ATTENDPRO1:S:"
    private const val PREFIX_EMPLOYEE_V1 = "ATTENDPRO1:E:"
    const val DEFAULT_TTL_MILLIS = 10 * 60_000L

    data class StoreInvite(
        val storeId: String,
        val storeName: String,
        val branchId: String,
        val nonce: String,
        val expiresAt: Long
    )

    data class EmployeeResponse(
        val storeId: String,
        val nonce: String,
        val employeeId: String,
        val displayName: String,
        val branchId: String,
        val pairingSecret: String,
        val pinHash: String,
        val expiresAt: Long
    )

    data class QrPairingEnvelope(val code: String, val provisionText: String)

    data class EmployeeProvision(
        val storeId: String,
        val storeName: String,
        val employeeId: String,
        val displayName: String,
        val branchId: String,
        val phone: String,
        val jobTitle: String,
        val pairingSecret: String,
        val pinHash: String,
        val expiresAt: Long,
        val storeLatitude: Double = Double.NaN,
        val storeLongitude: Double = Double.NaN,
        val gpsRadiusMeters: Int = 150,
        val allowedMethods: Set<String> = emptySet(),
        val shiftStartHour: Int = 8,
        val shiftStartMinute: Int = 0,
        val shiftEndHour: Int = 16,
        val shiftEndMinute: Int = 0,
        val employeeVoicePromptsEnabled: Boolean = true,
        val geoArrivalAlertsEnabled: Boolean = true,
        val serverUrl: String = "https://attend-pro-central.nmg200101.workers.dev",
        val employeeRequestVoiceText: String = "{name}، يرجى إثبات حضورك",
        val employeeMissingProofVoiceText: String = "{name}، لم يتم إثبات حضورك، يرجى إثبات الحضور الآن",
        val employeeLateVoiceText: String = "موعد دوامك بدأ ولم يتم إثبات حضورك",
        val employeeVoiceRatePercent: Int = 92,
        val employeeVoiceVolumePercent: Int = 100,
        val lateAlertEnabled: Boolean = true,
        val lateGraceMinutes: Int = 10,
        val lateFirstAlertDelayMinutes: Int = 0,
        val lateAlertCount: Int = 3,
        val lateRepeatMinutes: Int = 15,
        val lateAlertMode: String = "NOTIFICATION_VOICE",
        /** New in 1.9.40. Kept separate from legacy pinHash so old PIN data is never reinterpreted as a password. */
        val passwordHash: String = ""
    )

    fun randomNonce(): String = SecretCodec.encode(ByteArray(8).also { SecureRandom().nextBytes(it) })

    fun encodeEmployeeProvision(p: EmployeeProvision): String = PREFIX_PROVISION_V3 + encodeJson(JSONObject().apply {
        put("v", 3); put("s", p.storeId); put("m", p.storeName); put("i", p.employeeId); put("d", p.displayName)
        put("b", p.branchId); put("t", p.phone); put("j", p.jobTitle); put("k", p.pairingSecret); put("p", p.pinHash); put("e", p.expiresAt)
        if (p.storeLatitude.isFinite() && p.storeLongitude.isFinite()) { put("la", p.storeLatitude); put("lo", p.storeLongitude); put("gr", p.gpsRadiusMeters) }
        put("am", JSONArray().apply { p.allowedMethods.sorted().forEach { put(it) } })
        put("sh", p.shiftStartHour); put("sm", p.shiftStartMinute); put("eh", p.shiftEndHour); put("em", p.shiftEndMinute)
        put("vp", p.employeeVoicePromptsEnabled); put("ga", p.geoArrivalAlertsEnabled)
        put("u", p.serverUrl)
        put("vrq", p.employeeRequestVoiceText); put("vmp", p.employeeMissingProofVoiceText); put("vlt", p.employeeLateVoiceText)
        put("vrr", p.employeeVoiceRatePercent); put("vrv", p.employeeVoiceVolumePercent)
        put("lae", p.lateAlertEnabled); put("lag", p.lateGraceMinutes); put("laf", p.lateFirstAlertDelayMinutes)
        put("lac", p.lateAlertCount); put("lar", p.lateRepeatMinutes); put("lam", p.lateAlertMode)
        if (p.passwordHash.isNotBlank()) put("pw", p.passwordHash)
    })

    /**
     * Compact offline QR payload. The logical provision stays AP3P for Bluetooth/LAN and
     * backwards compatibility, while AP4P deflates that same provision so screen-to-screen
     * QR scanning is much less dense and succeeds on the first attempt on more cameras.
     */
    fun encodeEmployeeProvisionCompact(p: EmployeeProvision): String = compactProvision(encodeEmployeeProvision(p))

    /**
     * V5 QR envelope binds the visible QR to the currently-open Store pairing session.
     * The provision still carries the employee secret, while the 8-char code lets the
     * Employee app send an authenticated ACK back to the Store before pairing UI transitions.
     */
    fun encodeQrPairingEnvelope(code: String, provision: EmployeeProvision): String {
        val normalized = code.replace(Regex("[^A-Za-z0-9]"), "").uppercase().take(8)
        require(normalized.length == 8) { "Pairing code must contain 8 alphanumeric characters" }
        return PREFIX_QR_PAIRING_V5 + normalized + ":" + encodeEmployeeProvisionCompact(provision)
    }

    fun decodeQrPairingEnvelope(text: String): QrPairingEnvelope? = runCatching {
        val clean = text.trim()
        if (!clean.startsWith(PREFIX_QR_PAIRING_V5)) return@runCatching null
        val rest = clean.removePrefix(PREFIX_QR_PAIRING_V5)
        val split = rest.indexOf(':')
        if (split != 8) return@runCatching null
        val code = rest.substring(0, split).uppercase()
        if (!code.matches(Regex("[A-Z0-9]{8}"))) return@runCatching null
        val provision = rest.substring(split + 1)
        if (decodeEmployeeProvision(provision) == null) return@runCatching null
        QrPairingEnvelope(code, provision)
    }.getOrNull()

    fun compactProvision(encodedProvision: String): String {
        val clean = encodedProvision.trim()
        if (!clean.startsWith(PREFIX_PROVISION_V3)) return clean
        val input = clean.toByteArray(Charsets.UTF_8)
        val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
        return try {
            deflater.setInput(input)
            deflater.finish()
            val output = ByteArrayOutputStream(input.size)
            val buffer = ByteArray(512)
            while (!deflater.finished()) {
                val count = deflater.deflate(buffer)
                if (count <= 0) break
                output.write(buffer, 0, count)
            }
            PREFIX_PROVISION_V4 + Base64.getUrlEncoder().withoutPadding().encodeToString(output.toByteArray())
        } finally {
            deflater.end()
        }
    }

    private fun expandCompactProvision(text: String): String? = runCatching {
        val clean = text.trim()
        if (!clean.startsWith(PREFIX_PROVISION_V4)) return@runCatching clean
        val compressed = Base64.getUrlDecoder().decode(clean.removePrefix(PREFIX_PROVISION_V4))
        if (compressed.isEmpty() || compressed.size > 32_768) return@runCatching null
        val inflater = Inflater(true)
        try {
            inflater.setInput(compressed)
            val output = ByteArrayOutputStream(4096)
            val buffer = ByteArray(1024)
            while (!inflater.finished() && !inflater.needsInput()) {
                val count = inflater.inflate(buffer)
                if (count <= 0) break
                output.write(buffer, 0, count)
                if (output.size() > 32_768) return@runCatching null
            }
            output.toByteArray().toString(Charsets.UTF_8).takeIf { it.startsWith(PREFIX_PROVISION_V3) }
        } finally {
            inflater.end()
        }
    }.getOrNull()

    fun decodeEmployeeProvision(text: String): EmployeeProvision? = runCatching {
        val incoming = text.trim()
        val clean = if (incoming.startsWith(PREFIX_PROVISION_V4)) expandCompactProvision(incoming) ?: return@runCatching null else incoming
        if (!clean.startsWith(PREFIX_PROVISION_V3)) return@runCatching null
        val o = decodeJson(clean.removePrefix(PREFIX_PROVISION_V3))
        EmployeeProvision(
            o.getString("s"), o.optString("m", "جهاز المحل"), o.getString("i"), o.optString("d", o.getString("i")),
            o.optString("b", "MAIN"), o.optString("t", ""), o.optString("j", ""), o.getString("k"), o.optString("p", ""), o.getLong("e"),
            o.optDouble("la", Double.NaN), o.optDouble("lo", Double.NaN), o.optInt("gr", 150),
            o.optJSONArray("am")?.let { a -> (0 until a.length()).mapNotNull { i -> a.optString(i).takeIf { it.isNotBlank() } }.toSet() } ?: emptySet(),
            o.optInt("sh", 8), o.optInt("sm", 0), o.optInt("eh", 16), o.optInt("em", 0),
            o.optBoolean("vp", true), o.optBoolean("ga", true),
            o.optString("u", "https://attend-pro-central.nmg200101.workers.dev"),
            o.optString("vrq", "{name}، يرجى إثبات حضورك"),
            o.optString("vmp", "{name}، لم يتم إثبات حضورك، يرجى إثبات الحضور الآن"),
            o.optString("vlt", "موعد دوامك بدأ ولم يتم إثبات حضورك"),
            o.optInt("vrr", 92), o.optInt("vrv", 100),
            o.optBoolean("lae", true), o.optInt("lag", 10), o.optInt("laf", 0),
            o.optInt("lac", 3), o.optInt("lar", 15), o.optString("lam", "NOTIFICATION_VOICE"),
            o.optString("pw", "")
        )
    }.getOrNull()

    fun encodeStoreInvite(invite: StoreInvite): String = PREFIX_STORE_V2 + encodeJson(JSONObject().apply {
        put("v", 2); put("s", invite.storeId); put("m", invite.storeName); put("b", invite.branchId); put("n", invite.nonce); put("e", invite.expiresAt)
    })

    fun decodeStoreInvite(text: String): StoreInvite? = runCatching {
        val clean = text.trim()
        when {
            clean.startsWith(PREFIX_STORE_V2) -> {
                val o = decodeJson(clean.removePrefix(PREFIX_STORE_V2))
                StoreInvite(o.getString("s"), o.optString("m", "جهاز المحل"), o.optString("b", "MAIN"), o.getString("n"), o.getLong("e"))
            }
            clean.startsWith(PREFIX_STORE_V1) -> {
                val o = decodeJson(clean.removePrefix(PREFIX_STORE_V1))
                StoreInvite(o.getString("storeId"), o.optString("storeName", "جهاز المحل"), o.optString("branchId", "MAIN"), o.getString("nonce"), o.getLong("expiresAt"))
            }
            else -> null
        }
    }.getOrNull()

    fun encodeEmployeeResponse(response: EmployeeResponse): String = PREFIX_EMPLOYEE_V2 + encodeJson(JSONObject().apply {
        put("v", 2); put("s", response.storeId); put("n", response.nonce); put("i", response.employeeId); put("d", response.displayName)
        put("b", response.branchId); put("k", response.pairingSecret); put("p", response.pinHash); put("e", response.expiresAt)
    })

    fun decodeEmployeeResponse(text: String): EmployeeResponse? = runCatching {
        val clean = text.trim()
        when {
            clean.startsWith(PREFIX_EMPLOYEE_V2) -> {
                val o = decodeJson(clean.removePrefix(PREFIX_EMPLOYEE_V2))
                EmployeeResponse(o.getString("s"), o.getString("n"), o.getString("i"), o.optString("d", o.getString("i")), o.optString("b", "MAIN"), o.getString("k"), o.optString("p", ""), o.getLong("e"))
            }
            clean.startsWith(PREFIX_EMPLOYEE_V1) -> {
                val o = decodeJson(clean.removePrefix(PREFIX_EMPLOYEE_V1))
                EmployeeResponse(o.getString("storeId"), o.getString("nonce"), o.getString("employeeId"), o.optString("displayName", o.getString("employeeId")), o.optString("branchId", "MAIN"), o.getString("pairingSecret"), o.optString("pinHash", ""), o.getLong("expiresAt"))
            }
            else -> null
        }
    }.getOrNull()

    fun pinHash(pin: String): String {
        if (pin.isBlank()) return ""
        val digest = MessageDigest.getInstance("SHA-256").digest(pin.trim().toByteArray(Charsets.UTF_8))
        return "sha256:" + Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    fun matchesPin(stored: String, entered: String): Boolean {
        if (stored.isBlank() || entered.isBlank()) return false
        return if (stored.startsWith("sha256:")) stored == pinHash(entered) else stored == entered.trim()
    }

    private fun encodeJson(o: JSONObject): String = Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(o.toString().toByteArray(Charsets.UTF_8))

    private fun decodeJson(text: String): JSONObject {
        val bytes = Base64.getUrlDecoder().decode(text)
        return JSONObject(bytes.toString(Charsets.UTF_8))
    }
}
