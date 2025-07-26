package com.example.whadgest.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import android.util.Log
import java.util.UUID

/**
 * Secure preference manager for app configuration
 * Uses EncryptedSharedPreferences for sensitive data storage
 */
class PreferenceManager(private val context: Context) {

    companion object {
        private const val TAG = "PreferenceManager"
        private const val PREFS_NAME = "whadgest_config"

        // Preference keys
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_DEVICE_TOKEN = "device_token"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_WIFI_ONLY = "wifi_only"
        private const val KEY_SYNC_INTERVAL = "sync_interval"
        private const val KEY_LAST_SYNC_TIME = "last_sync_time"
        private const val KEY_FIRST_LAUNCH = "first_launch"
        private const val KEY_PRIVACY_ACCEPTED = "privacy_accepted"
        private const val KEY_NOTIFICATION_PERMISSION_GRANTED = "notification_permission_granted"
        private const val KEY_TOTAL_EVENTS_SENT = "total_events_sent"
        private const val KEY_LAST_ERROR = "last_error"

        // Default values
        private const val DEFAULT_SERVER_URL = "https://your-server.com/api"
        private const val DEFAULT_SYNC_INTERVAL = 60 // minutes
        private const val DEFAULT_WIFI_ONLY = false
    }

    private val encryptedPrefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create encrypted preferences, falling back to regular preferences", e)
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    /**
     * Server Configuration
     */
    fun getServerUrl(): String {
        return encryptedPrefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
    }

    fun setServerUrl(url: String) {
        encryptedPrefs.edit()
            .putString(KEY_SERVER_URL, url.trim())
            .apply()
    }

    fun getDeviceToken(): String {
        return encryptedPrefs.getString(KEY_DEVICE_TOKEN, "") ?: ""
    }

    fun setDeviceToken(token: String) {
        encryptedPrefs.edit()
            .putString(KEY_DEVICE_TOKEN, token.trim())
            .apply()
    }

    fun getDeviceId(): String {
        var deviceId = encryptedPrefs.getString(KEY_DEVICE_ID, "")
        if (deviceId.isNullOrBlank()) {
            deviceId = generateDeviceId()
            setDeviceId(deviceId)
        }
        return deviceId
    }

    private fun setDeviceId(deviceId: String) {
        encryptedPrefs.edit()
            .putString(KEY_DEVICE_ID, deviceId)
            .apply()
    }

    private fun generateDeviceId(): String {
        return "device_${UUID.randomUUID().toString().replace("-", "").take(16)}"
    }

    /**
     * Sync Configuration
     */
    fun isWifiOnlyEnabled(): Boolean {
        return encryptedPrefs.getBoolean(KEY_WIFI_ONLY, DEFAULT_WIFI_ONLY)
    }

    fun setWifiOnlyEnabled(enabled: Boolean) {
        encryptedPrefs.edit()
            .putBoolean(KEY_WIFI_ONLY, enabled)
            .apply()
    }

    fun getSyncInterval(): Int {
        return encryptedPrefs.getInt(KEY_SYNC_INTERVAL, DEFAULT_SYNC_INTERVAL)
    }

    fun setSyncInterval(intervalMinutes: Int) {
        encryptedPrefs.edit()
            .putInt(KEY_SYNC_INTERVAL, intervalMinutes)
            .apply()
    }

    fun getLastSyncTime(): Long {
        return encryptedPrefs.getLong(KEY_LAST_SYNC_TIME, 0)
    }

    fun setLastSyncTime(timestamp: Long) {
        encryptedPrefs.edit()
            .putLong(KEY_LAST_SYNC_TIME, timestamp)
            .apply()
    }

    /**
     * App State
     */
    fun isFirstLaunch(): Boolean {
        return encryptedPrefs.getBoolean(KEY_FIRST_LAUNCH, true)
    }

    fun setFirstLaunchCompleted() {
        encryptedPrefs.edit()
            .putBoolean(KEY_FIRST_LAUNCH, false)
            .apply()
    }

    fun isPrivacyAccepted(): Boolean {
        return encryptedPrefs.getBoolean(KEY_PRIVACY_ACCEPTED, false)
    }

    fun setPrivacyAccepted(accepted: Boolean) {
        encryptedPrefs.edit()
            .putBoolean(KEY_PRIVACY_ACCEPTED, accepted)
            .apply()
    }

    fun isNotificationPermissionGranted(): Boolean {
        return encryptedPrefs.getBoolean(KEY_NOTIFICATION_PERMISSION_GRANTED, false)
    }

    fun setNotificationPermissionGranted(granted: Boolean) {
        encryptedPrefs.edit()
            .putBoolean(KEY_NOTIFICATION_PERMISSION_GRANTED, granted)
            .apply()
    }

    /**
     * Statistics
     */
    fun getTotalEventsSent(): Long {
        return encryptedPrefs.getLong(KEY_TOTAL_EVENTS_SENT, 0)
    }

    fun incrementEventsSent(count: Int) {
        val current = getTotalEventsSent()
        encryptedPrefs.edit()
            .putLong(KEY_TOTAL_EVENTS_SENT, current + count)
            .apply()
    }

    fun getLastError(): String {
        return encryptedPrefs.getString(KEY_LAST_ERROR, "") ?: ""
    }

    fun setLastError(error: String) {
        encryptedPrefs.edit()
            .putString(KEY_LAST_ERROR, error)
            .apply()
    }

    fun clearLastError() {
        encryptedPrefs.edit()
            .remove(KEY_LAST_ERROR)
            .apply()
    }

    /**
     * Configuration validation
     */
    fun isConfigured(): Boolean {
        val serverUrl = getServerUrl()
        val deviceToken = getDeviceToken()

        return serverUrl.isNotBlank() &&
               serverUrl != DEFAULT_SERVER_URL &&
               deviceToken.isNotBlank() &&
               isPrivacyAccepted()
    }

    fun getConfigurationStatus(): ConfigStatus {
        return when {
            !isPrivacyAccepted() -> ConfigStatus.PRIVACY_NOT_ACCEPTED
            getServerUrl().isBlank() || getServerUrl() == DEFAULT_SERVER_URL -> ConfigStatus.MISSING_SERVER_URL
            getDeviceToken().isBlank() -> ConfigStatus.MISSING_TOKEN
            !isNotificationPermissionGranted() -> ConfigStatus.MISSING_PERMISSION
            else -> ConfigStatus.CONFIGURED
        }
    }

    /**
     * Reset configuration
     */
    fun clearConfiguration() {
        encryptedPrefs.edit()
            .remove(KEY_SERVER_URL)
            .remove(KEY_DEVICE_TOKEN)
            .remove(KEY_WIFI_ONLY)
            .remove(KEY_SYNC_INTERVAL)
            .remove(KEY_LAST_SYNC_TIME)
            .remove(KEY_TOTAL_EVENTS_SENT)
            .remove(KEY_LAST_ERROR)
            .apply()
    }

    fun clearAllData() {
        encryptedPrefs.edit().clear().apply()
    }

    /**
     * Export/Import configuration
     */
    fun exportConfiguration(): Map<String, Any> {
        return mapOf(
            "server_url" to getServerUrl(),
            "wifi_only" to isWifiOnlyEnabled(),
            "sync_interval" to getSyncInterval(),
            "device_id" to getDeviceId()
            // Note: device_token is intentionally excluded for security
        )
    }

    fun importConfiguration(config: Map<String, Any>) {
        val editor = encryptedPrefs.edit()

        (config["server_url"] as? String)?.let {
            editor.putString(KEY_SERVER_URL, it)
        }
        (config["wifi_only"] as? Boolean)?.let {
            editor.putBoolean(KEY_WIFI_ONLY, it)
        }
        (config["sync_interval"] as? Int)?.let {
            editor.putInt(KEY_SYNC_INTERVAL, it)
        }

        editor.apply()
    }

    enum class ConfigStatus {
        CONFIGURED,
        PRIVACY_NOT_ACCEPTED,
        MISSING_SERVER_URL,
        MISSING_TOKEN,
        MISSING_PERMISSION
    }
}
