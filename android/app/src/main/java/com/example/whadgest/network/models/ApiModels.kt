package com.example.whadgest.network.models

import com.google.gson.annotations.SerializedName

/**
 * Request model for ingesting events to the backend
 */
data class IngestRequest(
    @SerializedName("device_id")
    val deviceId: String,

    @SerializedName("events")
    val events: List<Map<String, Any>>,

    @SerializedName("timestamp")
    val timestamp: Long,

    @SerializedName("batch_size")
    val batchSize: Int,

    @SerializedName("app_version")
    val appVersion: String = "1.0.0",

    @SerializedName("platform")
    val platform: String = "android"
)

/**
 * Response model for ingest endpoint
 */
data class IngestResponse(
    @SerializedName("success")
    val success: Boolean,

    @SerializedName("message")
    val message: String,

    @SerializedName("processed_count")
    val processedCount: Int,

    @SerializedName("timestamp")
    val timestamp: Long,

    @SerializedName("duplicates_skipped")
    val duplicatesSkipped: Int? = null,

    @SerializedName("validation_failures")
    val validationFailures: Int? = null
)

/**
 * Error response model
 */
data class ErrorResponse(
    @SerializedName("error")
    val error: String,

    @SerializedName("code")
    val code: String,

    @SerializedName("timestamp")
    val timestamp: Long,

    @SerializedName("details")
    val details: Map<String, Any>? = null
)

/**
 * Health check response model
 */
data class HealthResponse(
    @SerializedName("status")
    val status: String,

    @SerializedName("info")
    val info: Map<String, Any>? = null,

    @SerializedName("error")
    val error: Map<String, Any>? = null,

    @SerializedName("details")
    val details: Map<String, Any>? = null
)



/**
 * Statistics response model
 */
data class StatsResponse(
    @SerializedName("deviceId")
    val deviceId: String,

    @SerializedName("totalMessages")
    val totalMessages: Int,

    @SerializedName("totalChats")
    val totalChats: Int,

    @SerializedName("totalSummaries")
    val totalSummaries: Int,

    @SerializedName("totalTokensUsed")
    val totalTokensUsed: Int,

    @SerializedName("timestamp")
    val timestamp: Long
)
