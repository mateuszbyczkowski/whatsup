package com.example.whadgest.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.whadgest.utils.PreferenceManager
import com.example.whadgest.work.SyncWorker
import java.util.concurrent.TimeUnit

/**
 * BroadcastReceiver to handle device boot and app updates
 * Restarts WorkManager sync jobs after device reboot
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "Received broadcast: ${intent.action}")

        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                Log.i(TAG, "Device boot completed, restarting sync work")
                restartSyncWork(context)
            }
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_REPLACED -> {
                val packageName = intent.data?.schemeSpecificPart
                if (packageName == context.packageName) {
                    Log.i(TAG, "App updated, restarting sync work")
                    restartSyncWork(context)
                }
            }
        }
    }

    /**
     * Restart periodic sync work
     */
    private fun restartSyncWork(context: Context) {
        try {
            val preferenceManager = PreferenceManager(context)

            // Only restart if the app is configured
            if (!preferenceManager.isConfigured()) {
                Log.i(TAG, "App not configured, skipping sync work restart")
                return
            }

            val syncInterval = preferenceManager.getSyncInterval().toLong()

            val syncWorkRequest = PeriodicWorkRequestBuilder<SyncWorker>(
                syncInterval, TimeUnit.MINUTES,
                15, TimeUnit.MINUTES // Flex interval
            )
                .addTag(SyncWorker.WORK_NAME)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                SyncWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,
                syncWorkRequest
            )

            Log.i(TAG, "Sync work restarted with ${syncInterval}min interval")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restart sync work", e)
        }
    }
}
