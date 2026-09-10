package com.attendpro.foundation.messaging

import com.attendpro.foundation.domain.Message
import com.attendpro.foundation.domain.MessageChannel
import com.attendpro.foundation.domain.MessageStatus
import com.attendpro.foundation.repository.MessageRepository

data class DeliveryReceipt(val messageId: String, val channel: MessageChannel, val acceptedAt: Long)

fun interface CloudMessenger { fun send(message: Message): Result<DeliveryReceipt> }
fun interface LocalBleMessenger { fun send(message: Message): Result<DeliveryReceipt> }
fun interface NotificationGateway { fun notify(message: Message) }

class MessagingRouter(
    private val cloud: CloudMessenger,
    private val local: LocalBleMessenger,
    private val messages: MessageRepository
) {
    /** The requested channel is exact. Cross-channel fallback requires an explicit later policy. */
    fun send(message: Message): Result<DeliveryReceipt> {
        val queued = message.copy(status = MessageStatus.QUEUED)
        if (messages.find(message.id) == null && !messages.insert(queued)) {
            return Result.failure(IllegalStateException("Unable to persist message before delivery"))
        }
        messages.updateStatus(message.id, MessageStatus.SENDING)
        val result = when (message.channel) {
            MessageChannel.CLOUD -> cloud.send(message)
            MessageChannel.LOCAL_BLE -> local.send(message)
        }
        result.fold(
            onSuccess = { messages.updateStatus(message.id, MessageStatus.SENT, it.acceptedAt) },
            onFailure = { messages.updateStatus(message.id, MessageStatus.FAILED) }
        )
        return result
    }
}

class InboxCoordinator(
    private val messages: MessageRepository,
    private val notifications: NotificationGateway
) {
    /** Idempotent by message ID: retries cannot create duplicate inbox rows or notifications. */
    fun accept(incoming: Message): Boolean {
        if (messages.find(incoming.id) != null) return false
        val delivered = incoming.copy(status = MessageStatus.DELIVERED)
        if (!messages.insert(delivered)) return false
        notifications.notify(delivered)
        return true
    }
}
