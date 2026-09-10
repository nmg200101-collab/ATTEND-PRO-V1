package com.attendpro.foundation

import com.attendpro.foundation.domain.Message
import com.attendpro.foundation.domain.MessageChannel
import com.attendpro.foundation.domain.MessageStatus
import com.attendpro.foundation.messaging.CloudMessenger
import com.attendpro.foundation.messaging.DeliveryReceipt
import com.attendpro.foundation.messaging.InboxCoordinator
import com.attendpro.foundation.messaging.LocalBleMessenger
import com.attendpro.foundation.messaging.MessagingRouter
import com.attendpro.foundation.messaging.NotificationGateway
import com.attendpro.foundation.repository.MessageRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessagingLayerTest {
    @Test fun cloudMessageUsesOnlyCloudAndPersistsStatus() {
        val repository = MemoryMessages()
        var cloudCalls = 0
        var localCalls = 0
        val router = MessagingRouter(
            cloud = CloudMessenger {
                cloudCalls += 1
                Result.success(DeliveryReceipt(it.id, MessageChannel.CLOUD, 200L))
            },
            local = LocalBleMessenger {
                localCalls += 1
                Result.failure(AssertionError("Local transport must not be used"))
            },
            messages = repository
        )

        assertTrue(router.send(message("m1", MessageChannel.CLOUD)).isSuccess)
        assertEquals(1, cloudCalls)
        assertEquals(0, localCalls)
        assertEquals(MessageStatus.SENT, repository.find("m1")?.status)
    }

    @Test fun transportFailureLeavesRetryableFailedRecord() {
        val repository = MemoryMessages()
        val router = MessagingRouter(
            cloud = CloudMessenger { Result.failure(IllegalStateException("offline")) },
            local = LocalBleMessenger { Result.failure(IllegalStateException("unused")) },
            messages = repository
        )

        assertTrue(router.send(message("m2", MessageChannel.CLOUD)).isFailure)
        assertEquals(MessageStatus.FAILED, repository.find("m2")?.status)
    }

    @Test fun duplicateIncomingMessageIsStoredAndNotifiedOnce() {
        val repository = MemoryMessages()
        var notifications = 0
        val inbox = InboxCoordinator(repository, NotificationGateway { notifications += 1 })
        val incoming = message("m3", MessageChannel.LOCAL_BLE)

        assertTrue(inbox.accept(incoming))
        assertFalse(inbox.accept(incoming))
        assertEquals(1, notifications)
        assertEquals(MessageStatus.DELIVERED, repository.find("m3")?.status)
    }

    private fun message(id: String, channel: MessageChannel) = Message(
        id = id,
        senderId = "sender",
        recipientId = "recipient",
        channel = channel,
        body = "hello",
        createdAt = 100L
    )

    private class MemoryMessages : MessageRepository {
        private val items = linkedMapOf<String, Message>()

        override fun find(id: String): Message? = items[id]

        override fun insert(value: Message): Boolean {
            if (items.containsKey(value.id)) return false
            items[value.id] = value
            return true
        }

        override fun inbox(recipientId: String, limit: Int): List<Message> = items.values
            .filter { it.recipientId == recipientId }
            .sortedByDescending(Message::createdAt)
            .take(limit)

        override fun updateStatus(id: String, status: MessageStatus, deliveredAt: Long?): Boolean {
            val old = items[id] ?: return false
            items[id] = old.copy(status = status, deliveredAt = deliveredAt ?: old.deliveredAt)
            return true
        }
    }
}
