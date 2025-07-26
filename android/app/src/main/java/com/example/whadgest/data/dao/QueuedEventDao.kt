package com.example.whadgest.data.dao

import androidx.room.*
import com.example.whadgest.data.entity.QueuedEvent
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for QueuedEvent entities
 * Provides database operations for WhatsApp notification events
 */
@Dao
interface QueuedEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: QueuedEvent): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<QueuedEvent>): List<Long>

    @Update
    suspend fun update(event: QueuedEvent)

    @Update
    suspend fun updateAll(events: List<QueuedEvent>)

    @Delete
    suspend fun delete(event: QueuedEvent)

    @Query("SELECT * FROM queued_events WHERE is_sent = 0 ORDER BY timestamp ASC")
    suspend fun getUnsentEvents(): List<QueuedEvent>

    @Query("SELECT * FROM queued_events WHERE chat_id = :chatId ORDER BY timestamp DESC")
    suspend fun getEventsForChat(chatId: String): List<QueuedEvent>

    @Query("""
        UPDATE queued_events
        SET is_sent = 1, sent_at = :sentAt
        WHERE id IN (:eventIds)
    """)
    suspend fun markAsSent(eventIds: List<Long>, sentAt: Long = System.currentTimeMillis())

    @Query("""
        UPDATE queued_events
        SET retry_count = retry_count + 1, error_message = :errorMessage
        WHERE id IN (:eventIds)
    """)
    suspend fun incrementRetryCount(eventIds: List<Long>, errorMessage: String?)

    @Query("SELECT COUNT(*) FROM queued_events WHERE is_sent = 0")
    suspend fun getUnsentCount(): Int

    @Query("SELECT COUNT(*) FROM queued_events")
    suspend fun getTotalCount(): Int

    @Query("SELECT COUNT(*) FROM queued_events WHERE chat_id = :chatId")
    suspend fun getCountForChat(chatId: String): Int

    @Query("SELECT MAX(timestamp) FROM queued_events")
    suspend fun getLatestTimestamp(): Long?

    @Query("""
        SELECT * FROM queued_events
        WHERE is_sent = 0 AND retry_count >= :maxRetries
        ORDER BY timestamp ASC
    """)
    suspend fun getFailedEvents(maxRetries: Int = QueuedEvent.MAX_RETRY_COUNT): List<QueuedEvent>

    @Query("""
        DELETE FROM queued_events
        WHERE is_sent = 1 AND sent_at < :cutoffTime
    """)
    suspend fun deleteOldSentEvents(cutoffTime: Long)

    @Query("DELETE FROM queued_events WHERE created_at < :cutoffTime")
    suspend fun deleteOldEvents(cutoffTime: Long)

    @Query("DELETE FROM queued_events")
    suspend fun deleteAll()

    @Query("""
        SELECT
            COUNT(*) as total,
            SUM(CASE WHEN is_sent = 0 THEN 1 ELSE 0 END) as unsent,
            SUM(CASE WHEN is_sent = 1 THEN 1 ELSE 0 END) as sent,
            MAX(timestamp) as latest_timestamp
        FROM queued_events
    """)
    fun getStatistics(): Flow<EventStatistics>

    @Query("""
        SELECT * FROM queued_events
        ORDER BY timestamp DESC
        LIMIT :limit
    """)
    fun getRecentEvents(limit: Int = 50): Flow<List<QueuedEvent>>

    @Query("""
        SELECT * FROM queued_events
        WHERE body LIKE '%' || :searchTerm || '%'
        OR sender LIKE '%' || :searchTerm || '%'
        OR chat_id LIKE '%' || :searchTerm || '%'
        ORDER BY timestamp DESC
        LIMIT :limit
    """)
    suspend fun searchEvents(searchTerm: String, limit: Int = 100): List<QueuedEvent>

    @Query("""
        SELECT chat_id, COUNT(*) as count, MAX(timestamp) as latest_timestamp
        FROM queued_events
        GROUP BY chat_id
        ORDER BY latest_timestamp DESC
    """)
    suspend fun getEventsByChat(): List<ChatEventSummary>
}

/**
 * Data class for event statistics
 */
data class EventStatistics(
    val total: Int,
    val unsent: Int,
    val sent: Int,
    val latest_timestamp: Long?
)

/**
 * Data class for chat event summary
 */
data class ChatEventSummary(
    val chat_id: String,
    val count: Int,
    val latest_timestamp: Long
)
