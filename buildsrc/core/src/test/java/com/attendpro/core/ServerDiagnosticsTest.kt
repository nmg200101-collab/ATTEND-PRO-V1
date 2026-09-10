package com.attendpro.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerDiagnosticsTest {
    @Test fun latestFailureInvalidatesFreshStateAndRecoveryPreservesHistory() {
        assertFalse(ServerDiagnostics.snapshot().isFresh())
        ServerDiagnostics.success(200, "/health")
        assertTrue(ServerDiagnostics.snapshot().isFresh())
        Thread.sleep(2)
        ServerDiagnostics.failure(503, "/health", "down")
        val failed = ServerDiagnostics.snapshot()
        assertFalse(failed.isFresh())
        assertEquals(503, failed.lastHttpStatus)
        assertEquals("/health", failed.lastErrorPath)
        Thread.sleep(2)
        ServerDiagnostics.success(204, "/employees/poll")
        val recovered = ServerDiagnostics.snapshot()
        assertTrue(recovered.isFresh())
        assertEquals("down", recovered.lastError)
        assertEquals("/employees/poll", recovered.lastSuccessPath)
    }
}
