package com.attendpro.employee

object EmployeeDirectReplyBridge1977 {
    @Volatile private var server: BleDirectLinkServer? = null

    fun bind(value: BleDirectLinkServer) { server = value }
    fun unbind(value: BleDirectLinkServer) { if (server === value) server = null }

    fun isAvailable(): Boolean = server?.hasAuthenticatedStore() == true

    fun queueReply(parentMessageId: String, message: String): Boolean {
        val s = server ?: return false
        return s.queueLocalReply(parentMessageId, message)
    }
}
