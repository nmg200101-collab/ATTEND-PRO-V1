package com.attendpro.store

import com.attendpro.core.PairingProtocol

object OfflinePairingCache {
    @Volatile private var lastProvision: String = ""
    @Volatile private var activeCode: String = ""

    fun capture(provision: PairingProtocol.EmployeeProvision): String {
        val encoded = PairingProtocol.encodeEmployeeProvision(provision)
        captureEncoded(encoded)
        return encoded
    }

    fun captureEncoded(value: String): String {
        if (value.startsWith("AP3P:")) lastProvision = value
        return value
    }

    fun activate(code: String): String {
        activeCode = code.trim().uppercase().take(8)
        return lastProvision
    }

    fun provisionFor(code: String): String? {
        val normalized = code.trim().uppercase().take(8)
        val value = lastProvision
        return value.takeIf { normalized.isNotBlank() && normalized == activeCode && it.startsWith("AP3P:") }
    }

    fun clearActive() { activeCode = "" }
}
