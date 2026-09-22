package com.attendpro.store

import android.content.Context
import com.attendpro.core.CentralServerClient
import com.attendpro.core.DeviceIdentity
import com.attendpro.core.PairedEmployee
import com.attendpro.core.SecretCodec
import com.attendpro.core.StoreRepository

/**
 * Applies receiver-phone employee-management commands on the authoritative Store device.
 *
 * Important invariants:
 * - Never edits employee phone links, BLE/GATT pairing or pairing protocol tables.
 * - UPDATE/STATUS preserve pairingSecret, biometric templates, allowed methods and shifts.
 * - ADD creates only a local employee record; phone pairing remains an explicit Store action.
 */
object ReceiverEmployeeCommandSync {
    @Volatile private var inFlight = false
    @Volatile private var lastSyncAt = 0L

    fun syncIfDue(
        context: Context,
        repo: StoreRepository,
        force: Boolean = false,
        onStatus: ((String) -> Unit)? = null
    ) {
        val now = System.currentTimeMillis()
        if (inFlight) return
        if (!force && now - lastSyncAt < 8_000L) return
        if (!repo.isCentralActivationActive() || repo.serverUrl.isBlank() || repo.centralAccessToken.isBlank()) return

        lastSyncAt = now
        inFlight = true
        Thread {
            val identity = DeviceIdentity(context.applicationContext)
            try {
                val initialSnapshot = CentralServerClient.pushReceiverEmployeeSnapshot(
                    repo.serverUrl,
                    repo.centralAccessToken,
                    repo.storeId,
                    identity,
                    repo.employees()
                )
                if (initialSnapshot.isFailure) {
                    onStatus?.invoke(networkMessage(initialSnapshot.exceptionOrNull()))
                    return@Thread
                }

                val pulled = CentralServerClient.pullReceiverEmployeeCommands(
                    repo.serverUrl,
                    repo.centralAccessToken,
                    repo.storeId,
                    identity
                )
                if (pulled.isFailure) {
                    onStatus?.invoke(networkMessage(pulled.exceptionOrNull()))
                    return@Thread
                }

                val commands = pulled.getOrThrow()
                commands.forEach { command ->
                    val applied = runCatching { applyCommand(repo, command) }
                    val employee = applied.getOrNull()
                    CentralServerClient.ackReceiverEmployeeCommand(
                        repo.serverUrl,
                        repo.centralAccessToken,
                        repo.storeId,
                        identity,
                        command.commandId,
                        success = applied.isSuccess,
                        error = applied.exceptionOrNull()?.message.orEmpty(),
                        employee = employee
                    )
                    if (applied.isSuccess) {
                        onStatus?.invoke("تم تطبيق أمر إدارة الموظف: ${employee?.displayName ?: command.employeeId}")
                    }
                }

                if (commands.isNotEmpty()) {
                    CentralServerClient.pushReceiverEmployeeSnapshot(
                        repo.serverUrl,
                        repo.centralAccessToken,
                        repo.storeId,
                        identity,
                        repo.employees()
                    )
                }
            } finally {
                inFlight = false
            }
        }.apply {
            name = "receiver-employee-command-sync"
            isDaemon = true
        }.start()
    }

    private fun applyCommand(
        repo: StoreRepository,
        command: CentralServerClient.ReceiverEmployeeCommand
    ): PairedEmployee {
        val id = command.employeeId.trim()
        require(id.isNotBlank()) { "رقم الموظف غير صالح" }

        val existing = repo.employees().firstOrNull { it.employeeId.equals(id, true) }
        return when (command.action.uppercase()) {
            "ADD" -> {
                val name = command.employeeName.trim()
                val branch = command.branchId.trim().ifBlank { "MAIN" }
                require(name.length >= 2) { "اسم الموظف غير مكتمل" }

                if (existing != null) {
                    // Idempotent retry after a locally applied command whose ACK was interrupted.
                    require(existing.displayName == name && existing.branchId == branch) {
                        "يوجد موظف بنفس الرقم ببيانات مختلفة"
                    }
                    existing
                } else {
                    PairedEmployee(
                        employeeId = id,
                        displayName = name,
                        branchId = branch,
                        pairingSecret = SecretCodec.encode(SecretCodec.generate()),
                        companionEnabled = true,
                        active = true
                    ).also { repo.upsertEmployee(it) }
                }
            }

            "UPDATE" -> {
                require(existing != null) { "الموظف غير موجود على جهاز المحل" }
                val updated = existing.copy(
                    displayName = command.employeeName.trim().ifBlank { existing.displayName },
                    branchId = command.branchId.trim().ifBlank { existing.branchId }
                )
                repo.upsertEmployee(updated)
                updated
            }

            "STATUS" -> {
                require(existing != null) { "الموظف غير موجود على جهاز المحل" }
                val enabled = command.enabled ?: error("حالة الموظف غير صالحة")
                val updated = existing.copy(active = enabled)
                repo.upsertEmployee(updated)
                updated
            }

            else -> error("نوع أمر إدارة الموظف غير معروف")
        }
    }

    private fun networkMessage(error: Throwable?): String {
        val raw = error?.message.orEmpty()
        return when {
            raw.contains("Failed to connect", true) ||
                raw.contains("Network is unreachable", true) ||
                raw.contains("ENETUNREACH", true) ->
                "تعذر الوصول إلى الخادم؛ ستتم إعادة مزامنة أوامر إدارة الموظفين تلقائيًا."
            raw.contains("resolve", true) || raw.contains("UnknownHost", true) ->
                "تعذر حل عنوان الخادم؛ ستتم إعادة المحاولة تلقائيًا عبر مسار الاتصال الاحتياطي."
            raw.isBlank() -> "تعذر مزامنة أوامر إدارة الموظفين."
            else -> "تعذر مزامنة أوامر إدارة الموظفين: $raw"
        }
    }
}
