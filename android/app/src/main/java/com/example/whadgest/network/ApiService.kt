package com.example.whadgest.network

import com.example.whadgest.network.models.*
import retrofit2.Response
import retrofit2.http.*

/**
 * Retrofit API service interface for WhatsApp Digest backend
 * Defines all network endpoints for communication with the server
 */
interface ApiService {

    /**
     * Ingest WhatsApp message events to the backend
     */
    @POST("messages/ingest")
    suspend fun ingestEvents(
        @Body request: IngestRequest
    ): Response<IngestResponse>

    /**
     * Health check endpoint to verify server connectivity
     */
    @GET("health")
    suspend fun healthCheck(): Response<HealthResponse>

    /**
     * Get device statistics from the backend
     */
    @GET("summaries/stats")
    suspend fun getDeviceStats(): Response<StatsResponse>

    /**
     * Test connection with authentication
     */
    @GET("health")
    suspend fun testConnection(): Response<HealthResponse>
}
