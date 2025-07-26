package com.example.whadgest.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.whadgest.R
import com.example.whadgest.data.AppDatabase
import com.example.whadgest.data.entity.QueuedEvent
import com.example.whadgest.utils.CryptoUtils
import com.example.whadgest.utils.NotificationParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * NotificationListenerService to capture WhatsApp notifications
 * Filters for com.whatsapp package and stores message data in encrypted Room database
 */
class WhatsAppNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "WhatsAppNotificationListener"
        private const val WHATSAPP_PACKAGE = "com.whatsapp"
        private const val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"
        private const val FOREGROUND_NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "whadgest_service"

        // Notification categories to filter
        private val IGNORED_CATEGORIES = setOf(
            Notification.CATEGORY_CALL,
            Notification.CATEGORY_STATUS,
            Notification.CATEGORY_SYSTEM
        )

        /**
         * Check if notification listener permission is granted
         */
        fun isNotificationAccessGranted(context: Context): Boolean {
            val enabledListeners = android.provider.Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            )

            return enabledListeners?.contains(context.packageName) == true
        }

        /**
         * Open notification access settings
         */
        fun openNotificationAccessSettings(context: Context) {
            val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var database: AppDatabase
    private lateinit var notificationParser: NotificationParser
    private var isInitialized = false

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "WhatsAppNotificationListener created")

        try {
            initializeService()
            createNotificationChannel()
            startForegroundService()
            isInitialized = true
            Log.i(TAG, "Service initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize service", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "WhatsAppNotificationListener destroyed")

        try {
            AppDatabase.closeDatabase()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing database", e)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!isInitialized) {
            Log.w(TAG, "Service not initialized, ignoring notification")
            return
        }

        try {
            handleNotification(sbn, isRemoved = false)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling posted notification", e)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // We don't need to handle removed notifications for our use case
        // but we could log it for debugging
        Log.v(TAG, "Notification removed: ${sbn.packageName}")
    }

    /**
     * Initialize the service components
     */
    private fun initializeService() {
        // Initialize database with encryption
        val passphrase = CryptoUtils.getDatabasePassphrase(this)
        database = AppDatabase.getDatabase(this, passphrase)

        // Initialize notification parser
        notificationParser = NotificationParser(this)
    }

    /**
     * Handle incoming notification
     */
    private fun handleNotification(sbn: StatusBarNotification, isRemoved: Boolean) {
        // Filter by package name
        if (!isWhatsAppPackage(sbn.packageName)) {
            return
        }

        // Filter out non-message notifications
        if (!isMessageNotification(sbn)) {
            Log.v(TAG, "Ignoring non-message notification from ${sbn.packageName}")
            return
        }

        // Parse notification content
        val parsedData = notificationParser.parseNotification(sbn)
        if (parsedData == null) {
            Log.w(TAG, "Failed to parse notification content")
            return
        }

        // Store in database
        serviceScope.launch {
            try {
                storeNotificationData(parsedData, sbn)
                Log.d(TAG, "Stored notification: chat=${parsedData.chatId}, sender=${parsedData.sender}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to store notification data", e)
            }
        }
    }

    /**
     * Check if package is WhatsApp or WhatsApp Business
     */
    private fun isWhatsAppPackage(packageName: String): Boolean {
        return packageName == WHATSAPP_PACKAGE || packageName == WHATSAPP_BUSINESS_PACKAGE
    }

    /**
     * Filter out non-message notifications (calls, status updates, etc.)
     */
    private fun isMessageNotification(sbn: StatusBarNotification): Boolean {
        val notification = sbn.notification ?: return false

        // Check if it's a category we should ignore
        if (notification.category in IGNORED_CATEGORIES) {
            return false
        }

        // Check if it has message-like content
        val hasText = !notification.extras.getCharSequence(Notification.EXTRA_TEXT).isNullOrBlank()
        val hasTitle = !notification.extras.getCharSequence(Notification.EXTRA_TITLE).isNullOrBlank()

        return hasText && hasTitle
    }

    /**
     * Store parsed notification data in database
     */
    private suspend fun storeNotificationData(
        parsedData: NotificationParser.ParsedNotification,
        sbn: StatusBarNotification
    ) {
        // Insert notification data in IO context
        withContext(Dispatchers.IO) {
            val queuedEvent = QueuedEvent(
                chatId = parsedData.chatId,
                sender = parsedData.sender,
                body = parsedData.body,
                timestamp = parsedData.timestamp,
                notificationKey = sbn.key,
                packageName = sbn.packageName
            )
            database.queuedEventDao().insert(queuedEvent)
        }
        // Notify UI to refresh statistics
        val updateIntent = Intent("com.example.whadgest.NEW_MESSAGE")
        sendBroadcast(updateIntent)
    }

    /**
     * Create notification channel for foreground service
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description)
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Start foreground service to prevent being killed
     */
    private fun startForegroundService() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_service_title))
            .setContentText(getString(R.string.notification_service_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setShowWhen(false)
            .setSound(null)
            .build()

        startForeground(FOREGROUND_NOTIFICATION_ID, notification)
    }
}
