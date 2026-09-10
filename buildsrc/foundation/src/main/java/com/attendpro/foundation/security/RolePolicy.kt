package com.attendpro.foundation.security

import com.attendpro.foundation.domain.SystemRole

enum class Capability {
    SYSTEM_ADMIN,
    ACTIVATION_MANAGE,
    AGENT_MANAGE,
    STORE_REGISTER,
    STORE_STATUS_VIEW,
    STORE_ADMIN,
    EMPLOYEE_MANAGE,
    ATTENDANCE_RECORD_OWN,
    ATTENDANCE_RECORD_ANY,
    ATTENDANCE_REPORT,
    PRESENCE_REQUEST,
    MESSAGE_SEND,
    MESSAGE_REPLY,
    SETTINGS_MANAGE
}

/** Deny-by-default role policy. Every non-owner permission must be listed explicitly. */
object RolePolicy {
    private val centralAgent = setOf(
        Capability.STORE_REGISTER,
        Capability.STORE_STATUS_VIEW,
        Capability.MESSAGE_SEND,
        Capability.MESSAGE_REPLY
    )

    private val storeManager = setOf(
        Capability.STORE_ADMIN,
        Capability.EMPLOYEE_MANAGE,
        Capability.ATTENDANCE_RECORD_ANY,
        Capability.ATTENDANCE_REPORT,
        Capability.PRESENCE_REQUEST,
        Capability.MESSAGE_SEND,
        Capability.MESSAGE_REPLY,
        Capability.SETTINGS_MANAGE
    )

    private val employee = setOf(
        Capability.ATTENDANCE_RECORD_OWN,
        Capability.MESSAGE_SEND,
        Capability.MESSAGE_REPLY
    )

    fun allows(role: SystemRole, capability: Capability): Boolean = when (role) {
        SystemRole.SYSTEM_OWNER -> true
        SystemRole.CENTRAL_AGENT -> capability in centralAgent
        SystemRole.STORE_MANAGER -> capability in storeManager
        SystemRole.EMPLOYEE -> capability in employee
    }
}
