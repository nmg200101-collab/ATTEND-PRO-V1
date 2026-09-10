package com.attendpro.store

object StoreDirectLinkBridge1977 {
    @Volatile private var client: BleDirectLinkClient? = null
    fun bind(value: BleDirectLinkClient) { client = value }
    fun unbind(value: BleDirectLinkClient) { if (client === value) client = null }
    fun isConnected(employeeId: String): Boolean = client?.isConnected(employeeId) == true
    fun diagnosticState(employeeId: String): String = client?.diagnosticState(employeeId) ?: "غير متصل مباشر"
    fun sendMessage(employeeId: String, title: String, body: String, priority: String, voiceEnabled: Boolean, callback: (Boolean, String) -> Unit): Boolean {
        val c = client ?: return false
        return c.sendLocalMessage(employeeId, title, body, priority, voiceEnabled, callback) != null
    }
}
