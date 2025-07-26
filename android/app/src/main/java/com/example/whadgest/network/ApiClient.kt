package com.example.whadgest.network

import android.util.Log
import com.example.whadgest.network.models.*
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Singleton API client for communicating with the WhatsApp Digest backend
 * Handles Retrofit configuration, authentication, and error handling
 */
class ApiClient private constructor(
    private val baseUrl: String,
    private val deviceToken: String
) {

    companion object {
        private const val TAG = "ApiClient"
        private const val CONNECT_TIMEOUT = 30L
        private const val READ_TIMEOUT = 60L
        private const val WRITE_TIMEOUT = 60L

        @Volatile
        private var INSTANCE: ApiClient? = null

        /**
         * Get or create ApiClient instance
         */
        fun getInstance(baseUrl: String, deviceToken: String): ApiClient {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ApiClient(baseUrl, deviceToken).also { INSTANCE = it }
            }
        }

        /**
         * Clear current instance (for configuration changes)
         */
        fun clearInstance() {
            INSTANCE = null
        }
    }

    private val gson: Gson = GsonBuilder()
        .setLenient()
        .create()

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
            .addInterceptor(createAuthInterceptor())
            .addInterceptor(createLoggingInterceptor())
            .build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(ensureTrailingSlash(baseUrl))
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    private val apiService: ApiService by lazy {
        retrofit.create(ApiService::class.java)
    }

    /**
     * Create authentication interceptor
     */
    private fun createAuthInterceptor(): Interceptor {
        return Interceptor { chain ->
            val original = chain.request()

            // Don't add auth to health check
            if (original.url.encodedPath.endsWith("/health")) {
                return@Interceptor chain.proceed(original)
            }

            val requestBuilder = original.newBuilder()
                .header("Authorization", "Bearer $deviceToken")
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("User-Agent", "WhatsAppDigest-Android/1.0.0")

            chain.proceed(requestBuilder.build())
        }
    }

    /**
     * Create logging interceptor for debugging
     */
    private fun createLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor { message ->
            Log.d(TAG, message)
        }.apply {
            level = if (android.util.Log.isLoggable(TAG, android.util.Log.DEBUG)) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
    }

    /**
     * Ensure URL has trailing slash
     */
    private fun ensureTrailingSlash(url: String): String {
        return if (url.endsWith("/")) url else "$url/"
    }

    /**
     * Ingest events to backend
     */
    suspend fun ingestEvents(request: IngestRequest): Response<IngestResponse> {
        return try {
            Log.d(TAG, "Ingesting ${request.events.size} events to backend")
            apiService.ingestEvents(request)
        } catch (e: Exception) {
            Log.e(TAG, "Error ingesting events", e)
            throw e
        }
    }

    /**
     * Test connection to backend
     */
    suspend fun testConnection(): Response<HealthResponse> {
        return try {
            Log.d(TAG, "Testing connection to backend")
            apiService.testConnection()
        } catch (e: Exception) {
            Log.e(TAG, "Error testing connection", e)
            throw e
        }
    }

    /**
     * Health check (no auth required)
     */
    suspend fun healthCheck(): Response<HealthResponse> {
        return try {
            Log.d(TAG, "Performing health check")
            apiService.healthCheck()
        } catch (e: Exception) {
            Log.e(TAG, "Error during health check", e)
            throw e
        }
    }

    /**
     * Get device statistics
     */
    suspend fun getDeviceStats(): Response<StatsResponse> {
        return try {
            Log.d(TAG, "Getting device statistics")
            apiService.getDeviceStats()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting device stats", e)
            throw e
        }
    }

    /**
     * Check if the client is properly configured
     */
    fun isConfigured(): Boolean {
        return baseUrl.isNotBlank() && deviceToken.isNotBlank()
    }

    /**
     * Get current base URL
     */
    fun getBaseUrl(): String = baseUrl

    /**
     * Get current device token (masked for logging)
     */
    fun getMaskedToken(): String {
        return if (deviceToken.length > 8) {
            "${deviceToken.take(4)}****${deviceToken.takeLast(4)}"
        } else {
            "****"
        }
    }
}
