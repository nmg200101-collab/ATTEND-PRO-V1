package com.attendpro.employee

import android.content.Context
import com.attendpro.core.CentralServerClient
import com.attendpro.core.EmployeeIdentityStore
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

object EmployeeMessageFastPoller142 {
    private const val NORMAL_POLL_MS = 4_000L
    private const val MAX_BACKOFF_MS = 30_000L
    private val running = AtomicBoolean(false)
    @Volatile private var worker: Thread? = null

    fun start(context: Context) {
        if (!running.compareAndSet(false, true)) return
        val app = context.applicationContext
        worker = thread(name = "employee-message-fast-v142", isDaemon = true) {
            var failures = 0
            while (running.get()) {
                val identity = EmployeeIdentityStore(app)
                var sleepMs = NORMAL_POLL_MS
                if (identity.isConfigured && identity.serverLinked && identity.employeeMessageNotificationsEnabled &&
                    identity.serverUrl.isNotBlank() && identity.trustedStoreId.isNotBlank() &&
                    identity.employeeId.isNotBlank() && identity.pairingSecret.isNotBlank() && identity.installationId.isNotBlank()) {
                    val result = CentralServerClient.employeeMessages(
                        identity.serverUrl, identity.trustedStoreId, identity.employeeId,
                        identity.pairingSecret, identity.installationId, false, 20
                    )
                    if (result.isSuccess) {
                        failures = 0
                        result.getOrNull().orEmpty().forEach { message ->
                            EmployeeMessageNotifier1975.notify(app, message)
                        }
                    } else {
                        failures++
                        sleepMs = when {
                            failures <= 1 -> 5_000L
                            failures == 2 -> 8_000L
                            failures == 3 -> 15_000L
                            else -> MAX_BACKOFF_MS
                        }
                    }
                } else {
                    sleepMs = 8_000L
                }
                try { Thread.sleep(sleepMs) } catch (_: InterruptedException) { return@thread }
            }
        }
    }

    fun stop() {
        running.set(false)
        worker?.interrupt()
        worker = null
    }
}
