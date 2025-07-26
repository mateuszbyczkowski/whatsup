package com.example.whadgest.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

/**
 * Entity representing a queued WhatsApp notification event
 * This stores the raw notification data before it's sent to the backend
 */
@Entity(
    tableName = "queued_events",
    indices = [
        Index(value = ["chat_id"]),
        Index(value = ["timestamp"]),
        Index(value = ["is_sent"])
    ]
)
data class QueuedEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "chat_id")
    val chatId: String,

    @ColumnInfo(name = "sender")
    val sender: String,

    @ColumnInfo(name = "body")
    val body: String,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    @ColumnInfo(name = "notification_key")
    val notificationKey: String? = null,

    @ColumnInfo(name = "package_name")
    val packageName: String = "com.whatsapp",

    @ColumnInfo(name = "is_sent", defaultValue = "0")
    val isSent: Boolean = false,

    @ColumnInfo(name = "retry_count", defaultValue = "0")
    val retryCount: Int = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "sent_at")
    val sentAt: Long? = null,

    @ColumnInfo(name = "error_message")
    val errorMessage: String? = null
) {
    /**
     * Convert to JSON format for API submission
     */
    fun toApiJson(): Map<String, Any> {
        return mapOf(
            "chatId" to chatId,
            "sender" to sender,
            "body" to body,
            "timestamp" to timestamp,
            "packageName" to packageName
        )
    }

    /**
     * Check if this event should be retried
     */
    fun shouldRetry(): Boolean {
        return !isSent && retryCount < MAX_RETRY_COUNT
    }

    companion object {
        const val MAX_RETRY_COUNT = 3
    }
}
