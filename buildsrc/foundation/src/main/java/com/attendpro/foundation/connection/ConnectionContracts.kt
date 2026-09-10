package com.attendpro.foundation.connection

/** New boundary only. V1.9.70/1.9.82 transports remain the implementation of record. */
enum class Transport { BLE_GATT, QR, PRESENCE, GPS, LOCAL_NETWORK, CLOUD }

enum class LinkState {
    IDLE,
    DISCOVERING,
    DISCOVERED,
    CONNECTING,
    AUTHENTICATING,
    ACK_PENDING,
    ACK_VERIFIED,
    CONNECTED,
    RECONNECTING,
    DISCONNECTED,
    ERROR
}

data class ConnectionSnapshot(
    val transport: Transport,
    val state: LinkState,
    val peerId: String?,
    val observedAt: Long,
    val diagnostic: String? = null
)

interface ConnectionGateway {
    val transport: Transport
    fun snapshot(): List<ConnectionSnapshot>
    fun start(): Result<Unit>
    fun stop()
}

interface PairingGateway {
    fun acceptQr(payload: String): Result<Unit>
    fun reconnect(peerId: String): Result<Unit>
}

/**
 * Pure regression boundary for the future transport adapters. Requiring ACK_VERIFIED before
 * CONNECTED prevents a refactor from silently weakening the stable pairing confirmation flow.
 */
object ConnectionStateMachine {
    private val transitions = mapOf(
        LinkState.IDLE to setOf(LinkState.DISCOVERING, LinkState.CONNECTING, LinkState.ERROR),
        LinkState.DISCOVERING to setOf(LinkState.DISCOVERED, LinkState.IDLE, LinkState.ERROR),
        LinkState.DISCOVERED to setOf(LinkState.CONNECTING, LinkState.AUTHENTICATING, LinkState.DISCONNECTED, LinkState.ERROR),
        LinkState.CONNECTING to setOf(LinkState.AUTHENTICATING, LinkState.DISCONNECTED, LinkState.ERROR),
        LinkState.AUTHENTICATING to setOf(LinkState.ACK_PENDING, LinkState.ACK_VERIFIED, LinkState.DISCONNECTED, LinkState.ERROR),
        LinkState.ACK_PENDING to setOf(LinkState.ACK_VERIFIED, LinkState.DISCONNECTED, LinkState.ERROR),
        LinkState.ACK_VERIFIED to setOf(LinkState.CONNECTED, LinkState.DISCONNECTED, LinkState.ERROR),
        LinkState.CONNECTED to setOf(LinkState.RECONNECTING, LinkState.DISCONNECTED, LinkState.ERROR),
        LinkState.RECONNECTING to setOf(LinkState.CONNECTING, LinkState.CONNECTED, LinkState.DISCONNECTED, LinkState.ERROR),
        LinkState.DISCONNECTED to setOf(LinkState.RECONNECTING, LinkState.DISCOVERING, LinkState.IDLE),
        LinkState.ERROR to setOf(LinkState.RECONNECTING, LinkState.IDLE)
    )

    fun canTransition(from: LinkState, to: LinkState): Boolean = to in transitions.getValue(from)

    fun requireTransition(from: LinkState, to: LinkState): LinkState {
        require(canTransition(from, to)) { "Illegal connection transition: $from -> $to" }
        return to
    }
}
