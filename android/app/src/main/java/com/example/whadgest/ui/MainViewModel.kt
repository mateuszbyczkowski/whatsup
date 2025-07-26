package com.example.whadgest.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.whadgest.data.AppDatabase
import com.example.whadgest.network.ApiClient
import com.example.whadgest.utils.CryptoUtils
import com.example.whadgest.utils.PreferenceManager
import com.example.whadgest.work.SyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.asLiveData
import kotlinx.coroutines.flow.map
import android.util.Log

/**
 * ViewModel for MainActivity
 * Manages UI state, statistics, and sync operations
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MainViewModel"
    }

    private val context = application.applicationContext
    private val preferenceManager = PreferenceManager(context)

    // Reactive statistics from Room
    private val dao = getDatabase()
        .queuedEventDao()
    val statistics: LiveData<Statistics> = dao.getStatistics()
        .map { es ->
            Statistics(
                totalMessages = es.total,
                pendingMessages = es.unsent,
                storageUsed = AppDatabase.getDatabaseSize(context)
            )
        }
        .asLiveData()

    private val _syncStatus = MutableLiveData<SyncStatus>()
    val syncStatus: LiveData<SyncStatus> = _syncStatus

    /**
     * Data class for app statistics
     */
    data class Statistics(
        val totalMessages: Int,
        val pendingMessages: Int,
        val storageUsed: Long
    )

    /**
     * Sealed class for sync status
     */
    sealed class SyncStatus {
        object Running : SyncStatus()
        data class Success(val eventsSent: Int) : SyncStatus()
        data class Error(val error: String) : SyncStatus()
    }

    /**
     * Test connection to backend server
     */
    suspend fun testConnection(serverUrl: String, deviceToken: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val apiClient = ApiClient.getInstance(serverUrl, deviceToken)
                val response = apiClient.testConnection()
                if (response.isSuccessful) {
                    Log.i(TAG, "Connection test successful")
                    true
                } else {
                    Log.w(TAG, "Connection test failed: ${response.code()}")
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Connection test error", e)
                false
            }
        }
    }

    /**
     * Force immediate sync
     */
    fun forceSync() {
        _syncStatus.value = SyncStatus.Running

        viewModelScope.launch {
            try {
                // Create one-time sync work
                val syncWork = OneTimeWorkRequestBuilder<SyncWorker>()
                    .addTag("force_sync")
                    .build()

                val workManager = WorkManager.getInstance(context)
                workManager.enqueue(syncWork)

                // Monitor work result
                workManager.getWorkInfoByIdLiveData(syncWork.id).observeForever { workInfo ->
                    when {
                        workInfo.state.isFinished -> {
                            val eventsSent = workInfo.outputData.getInt(SyncWorker.KEY_EVENTS_SENT, 0)
                            val errorMessage = workInfo.outputData.getString(SyncWorker.KEY_ERROR_MESSAGE)

                            if (errorMessage.isNullOrBlank()) {
                                _syncStatus.value = SyncStatus.Success(eventsSent)
                            } else {
                                _syncStatus.value = SyncStatus.Error(errorMessage)
                            }

                            // Reload statistics after sync
                            // loadStatistics()
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error starting force sync", e)
                _syncStatus.value = SyncStatus.Error(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Clear all local data
     */
    suspend fun clearData() {
        withContext(Dispatchers.IO) {
            try {
                // Clear database
                val database = getDatabase()
                database.queuedEventDao().deleteAll()

                // Clear preferences statistics
                preferenceManager.clearConfiguration()

                Log.i(TAG, "All data cleared")
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing data", e)
                throw e
            }
        }
    }

    /**
     * Get database instance
     */
    private fun getDatabase(): AppDatabase {
        val passphrase = CryptoUtils.getDatabasePassphrase(context)
        return AppDatabase.getDatabase(context, passphrase)
    }

    /**
     * Check if the app is properly configured
     */
    fun isConfigured(): Boolean {
        return preferenceManager.isConfigured()
    }

    /**
     * Get configuration status
     */
    fun getConfigurationStatus(): PreferenceManager.ConfigStatus {
        return preferenceManager.getConfigurationStatus()
    }

    /**
     * Get last sync time
     */
    fun getLastSyncTime(): Long {
        return preferenceManager.getLastSyncTime()
    }

    /**
     * Get total events sent
     */
    fun getTotalEventsSent(): Long {
        return preferenceManager.getTotalEventsSent()
    }

    /**
     * Export configuration for backup
     */
    fun exportConfiguration(): Map<String, Any> {
        return preferenceManager.exportConfiguration()
    }

    /**
     * Import configuration from backup
     */
    fun importConfiguration(config: Map<String, Any>) {
        preferenceManager.importConfiguration(config)
        // loadStatistics()
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ViewModel cleared")
    }
}
