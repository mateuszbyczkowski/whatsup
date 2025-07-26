package com.example.whadgest.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.Data
import androidx.work.ListenableWorker
import com.example.whadgest.data.AppDatabase
import com.example.whadgest.data.entity.QueuedEvent
import com.example.whadgest.network.ApiClient
import com.example.whadgest.network.models.IngestRequest
import com.example.whadgest.network.models.IngestResponse
import com.example.whadgest.utils.CryptoUtils
import com.example.whadgest.utils.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * WorkManager worker for syncing queued events to backend
 * Runs hourly to upload collected WhatsApp message data
 */
class SyncWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "SyncWorker"
        const val WORK_NAME = "sync_events_work"

        // Result data keys
        const val KEY_EVENTS_SENT = "events_sent"
        const val KEY_ERROR_MESSAGE = "error_message"
        const val KEY_SYNC_DURATION = "sync_duration"

        // Batch size for uploads
        private const val BATCH_SIZE = 100
        private const val MAX_RETRY_ATTEMPTS = 3
    }

    private lateinit var database: AppDatabase
    private lateinit var apiClient: ApiClient
    private lateinit var preferenceManager: PreferenceManager

    override suspend fun doWork(): Result {
        Log.i(TAG, "Starting sync work")
        val startTime = System.currentTimeMillis()

        return try {
            initializeComponents()

            // Check if sync is enabled and configured
            if (!isSyncConfigured()) {
                Log.w(TAG, "Sync not configured, skipping")
                return Result.success(createResultData(0, null, 0))
            }

            // Check network conditions
            if (!shouldSyncNow()) {
                Log.i(TAG, "Network conditions not met, retrying later")
                return Result.retry()
            }

            // Perform the sync
            val eventsSent = performSync()
            val duration = System.currentTimeMillis() - startTime

            Log.i(TAG, "Sync completed successfully: $eventsSent events sent in ${duration}ms")

            // Update last sync time and statistics
            preferenceManager.setLastSyncTime(System.currentTimeMillis())
            preferenceManager.incrementEventsSent(eventsSent)

            // Clean up failed events
            cleanupFailedEvents()

            return Result.success(createResultData(eventsSent, null, duration))

        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            Log.e(TAG, "Sync failed", e)

            when (e) {
                is ConnectException, is SocketTimeoutException, is UnknownHostException -> {
                    // Network-related errors - retry
                    Log.w(TAG, "Network error, will retry: ${e.message}")
                    Result.retry()
                }
                else -> {
                    // Other errors - fail permanently but log for debugging
                    val errorMessage = e.message ?: "Unknown error"
                    Result.failure(createResultData(0, errorMessage, duration))
                }
            }
        }
    }

    /**
     * Initialize required components
     */
    private fun initializeComponents() {
        preferenceManager = PreferenceManager(context)

        // Initialize database
        val passphrase = CryptoUtils.getDatabasePassphrase(context)
        database = AppDatabase.getDatabase(context, passphrase)

        // Initialize API client
        apiClient = ApiClient.getInstance(
            baseUrl = preferenceManager.getServerUrl(),
            deviceToken = preferenceManager.getDeviceToken()
        )
    }

    /**
     * Check if sync is properly configured
     */
    private fun isSyncConfigured(): Boolean {
        val serverUrl = preferenceManager.getServerUrl()
        val deviceToken = preferenceManager.getDeviceToken()

        if (serverUrl.isBlank() || deviceToken.isBlank()) {
            Log.w(TAG, "Missing server URL or device token")
            return false
        }

        return true
    }

    /**
     * Check if network conditions allow syncing
     */
    private fun shouldSyncNow(): Boolean {
        val networkManager = NetworkManager(context)

        // Check if network is available
        if (!networkManager.isNetworkAvailable()) {
            Log.w(TAG, "No network available")
            return false
        }

        // Check Wi-Fi only preference
        if (preferenceManager.isWifiOnlyEnabled() && !networkManager.isWifiConnected()) {
            Log.i(TAG, "Wi-Fi only mode enabled but not connected to Wi-Fi")
            return false
        }

        return true
    }

    /**
     * Perform the actual sync operation
     */
    private suspend fun performSync(): Int = withContext(Dispatchers.IO) {
        var totalEventsSent = 0
        var attempt = 0

        while (attempt < MAX_RETRY_ATTEMPTS) {
            try {
                // Get unsent events in batches
                val unsentEvents = database.queuedEventDao().getUnsentEvents()

                if (unsentEvents.isEmpty()) {
                    Log.i(TAG, "No events to sync")
                    break
                }

                Log.i(TAG, "Found ${unsentEvents.size} unsent events")

                // Process events in batches
                val batches = unsentEvents.chunked(BATCH_SIZE)

                for (batch in batches) {
                    val success = syncBatch(batch)
                    if (success) {
                        totalEventsSent += batch.size
                        // Delete events immediately after successful upload
                        batch.forEach { event ->
                            database.queuedEventDao().delete(event)
                        }
                        Log.d(TAG, "Successfully sent and deleted batch of ${batch.size} events")
                    } else {
                        // Mark failed events for retry
                        val eventIds = batch.map { it.id }
                        database.queuedEventDao().incrementRetryCount(
                            eventIds,
                            "Batch upload failed"
                        )
                        Log.w(TAG, "Failed to send batch of ${batch.size} events")
                    }
                }

                break // Success, exit retry loop

            } catch (e: Exception) {
                attempt++
                Log.w(TAG, "Sync attempt $attempt failed: ${e.message}")

                if (attempt >= MAX_RETRY_ATTEMPTS) {
                    throw e
                }

                // Wait before retrying
                kotlinx.coroutines.delay(1000L * attempt)
            }
        }

        totalEventsSent
    }

    /**
     * Sync a batch of events to the backend
     */
    private suspend fun syncBatch(events: List<QueuedEvent>): Boolean {
        return try {
            // Convert events to API format
            val eventData = events.map { it.toApiJson() }

            val request = IngestRequest(
                deviceId = preferenceManager.getDeviceId(),
                events = eventData,
                timestamp = System.currentTimeMillis(),
                batchSize = events.size
            )

            // Send to backend
            val response = apiClient.ingestEvents(request)

            if (response.isSuccessful) {
                val responseBody = response.body()
                if (responseBody != null) {
                    Log.d(TAG, "Batch upload successful: processed ${responseBody.processedCount} events")
                    if (responseBody.duplicatesSkipped != null && responseBody.duplicatesSkipped > 0) {
                        Log.i(TAG, "Skipped ${responseBody.duplicatesSkipped} duplicate events")
                    }
                    if (responseBody.validationFailures != null && responseBody.validationFailures > 0) {
                        Log.w(TAG, "Failed validation for ${responseBody.validationFailures} events")
                    }
                } else {
                    Log.d(TAG, "Batch upload successful but no response body")
                }
                true
            } else {
                Log.w(TAG, "Batch upload failed: ${response.code()} - ${response.message()}")
                response.errorBody()?.string()?.let { errorBody ->
                    Log.w(TAG, "Error details: $errorBody")
                }
                false
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error uploading batch", e)
            false
        }
    }

    /**
     * Create result data for WorkManager
     */
    private fun createResultData(eventsSent: Int, errorMessage: String?, duration: Long): Data {
        return Data.Builder()
            .putInt(KEY_EVENTS_SENT, eventsSent)
            .putString(KEY_ERROR_MESSAGE, errorMessage)
            .putLong(KEY_SYNC_DURATION, duration)
            .build()
    }

    /**
     * Clean up failed events that have exceeded retry limit
     */
    private suspend fun cleanupFailedEvents() {
        withContext(Dispatchers.IO) {
            try {
                // Delete events that have exceeded retry limit
                val failedEvents = database.queuedEventDao().getFailedEvents()
                if (failedEvents.isNotEmpty()) {
                    Log.i(TAG, "Cleaning up ${failedEvents.size} failed events")
                    failedEvents.forEach { event ->
                        database.queuedEventDao().delete(event)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during cleanup", e)
            }
            Unit
        }
    }
}

/**
 * Helper class for network condition checking
 */
private class NetworkManager(private val context: Context) {

    fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as android.net.ConnectivityManager

        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            capabilities?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            networkInfo?.isConnected == true
        }
    }

    fun isWifiConnected(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as android.net.ConnectivityManager

        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            capabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.getNetworkInfo(android.net.ConnectivityManager.TYPE_WIFI)
            networkInfo?.isConnected == true
        }
    }
}
