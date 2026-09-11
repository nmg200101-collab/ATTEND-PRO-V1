package com.attendpro.core

import java.util.UUID

/**
 * Optional authenticated Employee -> Store local reply channel.
 *
 * This is deliberately separate from BleDirectProtocol.COMMAND_UUID so the field-proven
 * pairing/heartbeat/ACK path stays byte-identical. The characteristic is added to the same
 * already-authenticated GATT service and is useful only after ACK-confirm succeeds.
 */
object BleLocalReplyChannel1977 {
    val REPLY_UUID: UUID = UUID.fromString("7c18b1d2-6ea7-4e58-9d6b-41d0e8a01977")
}
