package com.example.whadgest

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.whadgest.utils.PreferenceManager
import com.example.whadgest.work.SyncWorker
import java.util.concurrent.TimeUnit

/**
 * Application class for WhatsApp Digest
 * Handles global initialization and WorkManager configuration
 */
class WhadgestApplication : Application(), Configuration.Provider {

    companion object {
        private const val TAG = "WhadgestApplication"
    }

    private lateinit var preferenceManager: PreferenceManager

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Application starting")

        preferenceManager = PreferenceManager(this)

        // Initialize WorkManager with custom configuration
        WorkManager.initialize(this, workManagerConfiguration)

        // Schedule sync work if configured
        if (preferenceManager.isConfigured()) {
            scheduleSyncWork()
        }

        Log.i(TAG, "Application initialized")
    }

    /**
     * Schedule periodic sync work
     */
    private fun scheduleSyncWork() {
        try {
            val syncInterval = preferenceManager.getSyncInterval().toLong()

            val syncWorkRequest = PeriodicWorkRequestBuilder<SyncWorker>(
                syncInterval, TimeUnit.MINUTES,
                15, TimeUnit.MINUTES // Flex interval
            )
                .addTag(SyncWorker.WORK_NAME)
                .build()

            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                SyncWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                syncWorkRequest
            )

            Log.i(TAG, "Scheduled sync work with ${syncInterval}min interval")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule sync work", e)
        }
    }
}
