package com.example.whadgest.utils

import android.app.Notification
import android.content.Context
import android.service.notification.StatusBarNotification
import android.util.Log
import java.util.regex.Pattern

/**
 * Utility class to parse WhatsApp notification content
 * Extracts chatId, sender, and message body from notification data
 */
class NotificationParser(private val context: Context) {

    companion object {
        private const val TAG = "NotificationParser"

        // Regex patterns for parsing WhatsApp notifications
        private val GROUP_MESSAGE_PATTERN = Pattern.compile("^(.+): (.+)$")
        private val PRIVATE_MESSAGE_PATTERN = Pattern.compile("^(.+)$")

        // Known WhatsApp notification patterns
        private val WHATSAPP_CALL_INDICATORS = setOf(
            "calling",
            "incoming call",
            "missed call",
            "video call",
            "voice call"
        )

        private val WHATSAPP_STATUS_INDICATORS = setOf(
            "status update",
            "story",
            "broadcast",
            "security code"
        )
    }

    /**
     * Data class representing parsed notification content
     */
    data class ParsedNotification(
        val chatId: String,
        val sender: String,
        val body: String,
        val timestamp: Long,
        val isGroupMessage: Boolean = false,
        val packageName: String
    )

    /**
     * Parse WhatsApp notification and extract message data
     */
    fun parseNotification(sbn: StatusBarNotification): ParsedNotification? {
        val notification = sbn.notification ?: return null
        val extras = notification.extras

        try {
            // Extract basic notification data
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()

            if (title.isNullOrBlank() || text.isNullOrBlank()) {
                Log.w(TAG, "Notification missing title or text")
                return null
            }

            // Use big text if available, otherwise fall back to regular text
            val messageBody = bigText?.takeIf { it.isNotBlank() } ?: text

            // Filter out non-message notifications
            if (isNonMessageNotification(title, messageBody)) {
                Log.v(TAG, "Filtered out non-message notification: $title")
                return null
            }

            // Parse message content
            val parsedContent = parseMessageContent(title, messageBody, subText)
                ?: return null

            return ParsedNotification(
                chatId = parsedContent.chatId,
                sender = parsedContent.sender,
                body = parsedContent.body,
                timestamp = sbn.postTime,
                isGroupMessage = parsedContent.isGroupMessage,
                packageName = sbn.packageName
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing notification", e)
            return null
        }
    }

    /**
     * Parse message content from notification fields
     */
    private fun parseMessageContent(
        title: String,
        body: String,
        subText: String?
    ): ParsedContent? {

        // Check if it's a group message (usually has sender: message format)
        val groupMatcher = GROUP_MESSAGE_PATTERN.matcher(body)

        return if (groupMatcher.matches() && !subText.isNullOrBlank()) {
            // Group message: title is group name, body has "sender: message"
            val sender = groupMatcher.group(1) ?: return null
            val message = groupMatcher.group(2) ?: return null

            ParsedContent(
                chatId = generateChatId(title, isGroup = true),
                sender = sender.trim(),
                body = message.trim(),
                isGroupMessage = true
            )
        } else {
            // Private message: title is sender name, body is message
            ParsedContent(
                chatId = generateChatId(title, isGroup = false),
                sender = title.trim(),
                body = body.trim(),
                isGroupMessage = false
            )
        }
    }

    /**
     * Generate a consistent chat ID from chat name
     */
    private fun generateChatId(chatName: String, isGroup: Boolean): String {
        // Remove special characters and normalize
        val normalized = chatName.trim()
            .replace(Regex("[^\\w\\s-]"), "")
            .replace(Regex("\\s+"), "_")
            .lowercase()

        val prefix = if (isGroup) "group" else "private"
        return "${prefix}_${normalized}"
    }

    /**
     * Check if notification is not a message (call, status update, etc.)
     */
    private fun isNonMessageNotification(title: String, body: String): Boolean {
        val titleLower = title.lowercase()
        val bodyLower = body.lowercase()

        // Check for call indicators
        WHATSAPP_CALL_INDICATORS.forEach { indicator ->
            if (titleLower.contains(indicator) || bodyLower.contains(indicator)) {
                return true
            }
        }

        // Check for status indicators
        WHATSAPP_STATUS_INDICATORS.forEach { indicator ->
            if (titleLower.contains(indicator) || bodyLower.contains(indicator)) {
                return true
            }
        }

        // Check for empty or very short messages (likely system notifications)
        if (body.trim().length < 2) {
            return true
        }

        // Check for emoji-only messages (might be reactions)
        if (isEmojiOnly(body.trim())) {
            return true
        }

        return false
    }

    /**
     * Check if text contains only emojis
     */
    private fun isEmojiOnly(text: String): Boolean {
        if (text.length > 10) return false // Probably not just emojis

        // Simple check for common emoji patterns
        val emojiPattern = Pattern.compile(
            "[\ud83c\udf00-\ud83d\ude4f]|[\ud83d\ude80-\ud83d\udeff]|[\u2600-\u26ff]|[\u2700-\u27bf]"
        )

        val withoutEmojis = emojiPattern.matcher(text).replaceAll("")
        return withoutEmojis.trim().isEmpty()
    }

    /**
     * Extract conversation participants from group message
     */
    fun extractParticipants(body: String): List<String> {
        val participants = mutableSetOf<String>()

        // Look for patterns like "John: message" in the body
        val lines = body.split("\n")
        for (line in lines) {
            val matcher = GROUP_MESSAGE_PATTERN.matcher(line.trim())
            if (matcher.matches()) {
                val sender = matcher.group(1)
                if (!sender.isNullOrBlank()) {
                    participants.add(sender.trim())
                }
            }
        }

        return participants.toList()
    }

    /**
     * Extract media type from notification (if any)
     */
    fun extractMediaType(body: String): String? {
        val bodyLower = body.lowercase()

        return when {
            bodyLower.contains("photo") || bodyLower.contains("image") -> "image"
            bodyLower.contains("video") -> "video"
            bodyLower.contains("audio") || bodyLower.contains("voice") -> "audio"
            bodyLower.contains("document") || bodyLower.contains("pdf") -> "document"
            bodyLower.contains("location") -> "location"
            bodyLower.contains("contact") -> "contact"
            bodyLower.contains("sticker") -> "sticker"
            else -> null
        }
    }

    /**
     * Check if message contains sensitive content that should be filtered
     */
    fun containsSensitiveContent(body: String): Boolean {
        val sensitivePatterns = listOf(
            "password", "pin", "otp", "verification code",
            "credit card", "bank account", "ssn", "social security"
        )

        val bodyLower = body.lowercase()
        return sensitivePatterns.any { pattern ->
            bodyLower.contains(pattern)
        }
    }

    /**
     * Internal data class for parsed content
     */
    private data class ParsedContent(
        val chatId: String,
        val sender: String,
        val body: String,
        val isGroupMessage: Boolean
    )
}
