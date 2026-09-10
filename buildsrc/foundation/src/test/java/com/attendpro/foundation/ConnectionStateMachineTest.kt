package com.attendpro.foundation

import com.attendpro.foundation.connection.ConnectionStateMachine
import com.attendpro.foundation.connection.LinkState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionStateMachineTest {
    @Test fun authenticatedPairingRequiresAckBeforeConnected() {
        var state = LinkState.IDLE
        listOf(
            LinkState.DISCOVERING,
            LinkState.DISCOVERED,
            LinkState.CONNECTING,
            LinkState.AUTHENTICATING,
            LinkState.ACK_PENDING,
            LinkState.ACK_VERIFIED,
            LinkState.CONNECTED
        ).forEach { state = ConnectionStateMachine.requireTransition(state, it) }
        assertEquals(LinkState.CONNECTED, state)
    }

    @Test fun discoveredPeerCannotSkipAuthenticationAndAck() {
        assertFalse(ConnectionStateMachine.canTransition(LinkState.DISCOVERED, LinkState.CONNECTED))
        assertThrows(IllegalArgumentException::class.java) {
            ConnectionStateMachine.requireTransition(LinkState.DISCOVERED, LinkState.CONNECTED)
        }
    }

    @Test fun connectedPeerCanEnterReconnectFlow() {
        assertTrue(ConnectionStateMachine.canTransition(LinkState.CONNECTED, LinkState.RECONNECTING))
    }
}
