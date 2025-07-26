package com.example.whadgest.ui

import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.whadgest.R
import com.example.whadgest.databinding.ActivityMainBinding
import com.example.whadgest.service.WhatsAppNotificationListener
import com.example.whadgest.utils.PreferenceManager
import com.example.whadgest.work.SyncWorker
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Main activity for WhatsApp Digest configuration and monitoring
 * Provides UI for setup, status monitoring, and manual sync
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var preferenceManager: PreferenceManager
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferenceManager = PreferenceManager(this)

        setupUI()
        setupObservers()
        checkFirstLaunch()
        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    /**
     * Setup UI components and click listeners
     */
    private fun setupUI() {
        // Load current configuration
        loadConfiguration()

        // Setup click listeners
        binding.btnGrantPermission.setOnClickListener {
            requestNotificationPermission()
        }

        binding.btnSaveConfig.setOnClickListener {
            saveConfiguration()
        }

        binding.btnTestConnection.setOnClickListener {
            testConnection()
        }

        binding.btnForceSync.setOnClickListener {
            forceSync()
        }

        binding.btnClearData.setOnClickListener {
            showClearDataDialog()
        }

        binding.btnAcceptPrivacy.setOnClickListener {
            acceptPrivacy()
        }
    }

    /**
     * Setup observers for live data
     */
    private fun setupObservers() {
        viewModel.statistics.observe(this) { stats ->
            updateStatistics(stats)
        }

        viewModel.syncStatus.observe(this) { status ->
            updateSyncStatus(status)
        }
    }

    /**
     * Check if this is the first launch and show privacy notice
     */
    private fun checkFirstLaunch() {
        if (preferenceManager.isFirstLaunch() || !preferenceManager.isPrivacyAccepted()) {
            binding.cardPrivacyNotice.visibility = View.VISIBLE
        } else {
            binding.cardPrivacyNotice.visibility = View.GONE
        }
    }

    /**
     * Load configuration from preferences
     */
    private fun loadConfiguration() {
        binding.etServerUrl.setText(preferenceManager.getServerUrl())
        binding.etDeviceToken.setText(preferenceManager.getDeviceToken())
        binding.switchWifiOnly.isChecked = preferenceManager.isWifiOnlyEnabled()
        binding.etSyncInterval.setText(preferenceManager.getSyncInterval().toString())
    }

    /**
     * Save configuration to preferences
     */
    private fun saveConfiguration() {
        val serverUrl = binding.etServerUrl.text.toString().trim()
        val deviceToken = binding.etDeviceToken.text.toString().trim()
        val wifiOnly = binding.switchWifiOnly.isChecked
        val syncInterval = binding.etSyncInterval.text.toString().toIntOrNull() ?: 60

        // Validate inputs
        if (!validateConfiguration(serverUrl, deviceToken, syncInterval)) {
            return
        }

        // Save to preferences
        preferenceManager.setServerUrl(serverUrl)
        preferenceManager.setDeviceToken(deviceToken)
        preferenceManager.setWifiOnlyEnabled(wifiOnly)
        preferenceManager.setSyncInterval(syncInterval)

        // Schedule sync work
        scheduleSyncWork()

        showSnackbar(getString(R.string.msg_config_saved))
        Log.i(TAG, "Configuration saved")
    }

    /**
     * Validate configuration inputs
     */
    private fun validateConfiguration(serverUrl: String, deviceToken: String, syncInterval: Int): Boolean {
        if (serverUrl.isBlank()) {
            binding.etServerUrl.error = "Server URL is required"
            return false
        }

        if (!Patterns.WEB_URL.matcher(serverUrl).matches()) {
            binding.etServerUrl.error = "Invalid URL format"
            return false
        }

        if (deviceToken.isBlank()) {
            binding.etDeviceToken.error = "Device token is required"
            return false
        }

        if (deviceToken.length < 8) {
            binding.etDeviceToken.error = "Token too short (minimum 8 characters)"
            return false
        }

        if (syncInterval < 15 || syncInterval > 1440) {
            binding.etSyncInterval.error = "Interval must be between 15 and 1440 minutes"
            return false
        }

        return true
    }

    /**
     * Test connection to backend server
     */
    private fun testConnection() {
        val serverUrl = binding.etServerUrl.text.toString().trim()
        val deviceToken = binding.etDeviceToken.text.toString().trim()

        if (!validateConfiguration(serverUrl, deviceToken, 60)) {
            return
        }

        binding.btnTestConnection.isEnabled = false
        binding.btnTestConnection.text = "Testing..."

        lifecycleScope.launch {
            try {
                val success = viewModel.testConnection(serverUrl, deviceToken)
                if (success) {
                    showSnackbar(getString(R.string.msg_connection_success))
                } else {
                    showSnackbar(getString(R.string.msg_connection_failed, "Unknown error"))
                }
            } catch (e: Exception) {
                showSnackbar(getString(R.string.msg_connection_failed, e.message))
                Log.e(TAG, "Connection test failed", e)
            } finally {
                binding.btnTestConnection.isEnabled = true
                binding.btnTestConnection.text = getString(R.string.btn_test_connection)
            }
        }
    }

    /**
     * Request notification access permission
     */
    private fun requestNotificationPermission() {
        if (WhatsAppNotificationListener.isNotificationAccessGranted(this)) {
            preferenceManager.setNotificationPermissionGranted(true)
            updateStatus()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Notification Access Required")
            .setMessage("This app needs notification access to read WhatsApp messages. You'll be taken to the settings page to grant permission.")
            .setPositiveButton("Open Settings") { _, _ ->
                WhatsAppNotificationListener.openNotificationAccessSettings(this)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Update status indicators
     */
    private fun updateStatus() {
        // Check notification permission
        val hasNotificationPermission = WhatsAppNotificationListener.isNotificationAccessGranted(this)
        preferenceManager.setNotificationPermissionGranted(hasNotificationPermission)

        if (hasNotificationPermission) {
            binding.ivPermissionStatus.setImageResource(R.drawable.ic_check)
            binding.tvPermissionStatus.text = getString(R.string.status_permission_granted)
            binding.tvPermissionStatus.setTextAppearance(R.style.TextAppearance_Status_Good)
            binding.btnGrantPermission.visibility = View.GONE
        } else {
            binding.ivPermissionStatus.setImageResource(R.drawable.ic_error)
            binding.tvPermissionStatus.text = getString(R.string.status_permission_denied)
            binding.tvPermissionStatus.setTextAppearance(R.style.TextAppearance_Status_Error)
            binding.btnGrantPermission.visibility = View.VISIBLE
        }

        // Check service status
        val isConfigured = preferenceManager.isConfigured()
        if (hasNotificationPermission && isConfigured) {
            binding.ivServiceStatus.setImageResource(R.drawable.ic_check)
            binding.tvServiceStatus.text = getString(R.string.status_service_running)
            binding.tvServiceStatus.setTextAppearance(R.style.TextAppearance_Status_Good)
            binding.btnForceSync.isEnabled = true
        } else {
            binding.ivServiceStatus.setImageResource(R.drawable.ic_service_stopped)
            binding.tvServiceStatus.text = getString(R.string.status_service_stopped)
            binding.tvServiceStatus.setTextAppearance(R.style.TextAppearance_Status_Warning)
            binding.btnForceSync.isEnabled = false
        }

        // Update sync times
        updateSyncTimes()
    }

    /**
     * Update sync time displays
     */
    private fun updateSyncTimes() {
        val lastSyncTime = preferenceManager.getLastSyncTime()
        if (lastSyncTime > 0) {
            val formatter = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
            binding.tvLastSync.text = getString(R.string.status_last_sync, formatter.format(Date(lastSyncTime)))
        } else {
            binding.tvLastSync.text = getString(R.string.status_never_synced)
        }

        // Calculate next sync time
        val syncInterval = preferenceManager.getSyncInterval()
        val nextSyncTime = lastSyncTime + (syncInterval * 60 * 1000)
        if (nextSyncTime > System.currentTimeMillis()) {
            val minutesUntilSync = (nextSyncTime - System.currentTimeMillis()) / (60 * 1000)
            binding.tvNextSync.text = getString(R.string.status_next_sync, "In $minutesUntilSync minutes")
        } else {
            binding.tvNextSync.text = "Next sync: Soon"
        }
    }

    /**
     * Update statistics display
     */
    private fun updateStatistics(stats: MainViewModel.Statistics) {
        binding.tvMessagesCollected.text = getString(R.string.stats_messages_collected, stats.totalMessages)
        binding.tvMessagesPending.text = getString(R.string.stats_messages_pending, stats.pendingMessages)
        binding.tvStorageUsed.text = getString(R.string.stats_storage_used, formatFileSize(stats.storageUsed))
    }

    /**
     * Update sync status display
     */
    private fun updateSyncStatus(status: MainViewModel.SyncStatus) {
        when (status) {
            is MainViewModel.SyncStatus.Running -> {
                binding.btnForceSync.text = "Syncing..."
                binding.btnForceSync.isEnabled = false
            }
            is MainViewModel.SyncStatus.Success -> {
                binding.btnForceSync.text = getString(R.string.btn_force_sync)
                binding.btnForceSync.isEnabled = true
                showSnackbar(getString(R.string.msg_sync_completed, status.eventsSent))
                updateSyncTimes()
            }
            is MainViewModel.SyncStatus.Error -> {
                binding.btnForceSync.text = getString(R.string.btn_force_sync)
                binding.btnForceSync.isEnabled = true
                showSnackbar(getString(R.string.msg_sync_failed, status.error))
            }
        }
    }

    /**
     * Force immediate sync
     */
    private fun forceSync() {
        if (!preferenceManager.isConfigured()) {
            showSnackbar(getString(R.string.msg_config_error))
            return
        }

        showSnackbar(getString(R.string.msg_sync_started))
        viewModel.forceSync()
    }

    /**
     * Show dialog to confirm data clearing
     */
    private fun showClearDataDialog() {
        AlertDialog.Builder(this)
            .setTitle("Clear Data")
            .setMessage("This will delete all local message data and reset statistics. This action cannot be undone.")
            .setPositiveButton("Clear") { _, _ ->
                clearData()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Clear all local data
     */
    private fun clearData() {
        lifecycleScope.launch {
            try {
                viewModel.clearData()
                showSnackbar(getString(R.string.msg_data_cleared))
                updateStatus()
            } catch (e: Exception) {
                showSnackbar("Error clearing data: ${e.message}")
                Log.e(TAG, "Error clearing data", e)
            }
        }
    }

    /**
     * Accept privacy notice
     */
    private fun acceptPrivacy() {
        preferenceManager.setPrivacyAccepted(true)
        preferenceManager.setFirstLaunchCompleted()
        binding.cardPrivacyNotice.visibility = View.GONE
        updateStatus()
    }

    /**
     * Schedule periodic sync work
     */
    private fun scheduleSyncWork() {
        val syncInterval = preferenceManager.getSyncInterval().toLong()

        val syncWorkRequest = PeriodicWorkRequestBuilder<SyncWorker>(
            syncInterval, TimeUnit.MINUTES,
            15, TimeUnit.MINUTES // Flex interval
        )
            .addTag(SyncWorker.WORK_NAME)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            SyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            syncWorkRequest
        )

        Log.i(TAG, "Scheduled sync work with ${syncInterval}min interval")
    }

    /**
     * Show snackbar message
     */
    private fun showSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    /**
     * Format file size for display
     */
    private fun formatFileSize(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0

        return when {
            mb >= 1 -> String.format("%.1f MB", mb)
            kb >= 1 -> String.format("%.1f KB", kb)
            else -> "$bytes B"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // No receivers to unregister anymore
    }
}
