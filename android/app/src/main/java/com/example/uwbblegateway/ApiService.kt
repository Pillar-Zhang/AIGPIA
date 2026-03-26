package com.example.uwbblegateway

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

interface ApiService {
    @POST("api/login")
    suspend fun login(@Body credentials: Map<String, String>): Map<String, Any>

    @GET("api/pms3/connect")
    suspend fun connectPms(): Map<String, Any>

    @GET("api/ble/connect")
    suspend fun connectBle(): Map<String, Any>

    @GET("api/uwb/connect")
    suspend fun connectUwb(): Map<String, Any>

    @GET("api/aiglasses/connect")
    suspend fun connectGlasses(): Map<String, Any>

    // --- Test Data Sync Endpoints (Based on UWB_BLE_API对接文档.md) ---

    /**
     * Uploads BLE test logs including device info, connection params, and GATT stats.
     */
    @POST("api/test-data/ble")
    suspend fun uploadBleTestData(@Body data: @JvmSuppressWildcards Map<String, Any>): Map<String, Any>

    /**
     * Uploads UWB session config, ranging data, and quality metrics.
     */
    @POST("api/test-data/uwb")
    suspend fun uploadUwbTestData(@Body data: @JvmSuppressWildcards Map<String, Any>): Map<String, Any>

    /**
     * Uploads full fusion test records including environment, ground truth, and error analysis.
     */
    @POST("api/test-data/fusion")
    suspend fun uploadFusionTestData(@Body data: @JvmSuppressWildcards Map<String, Any>): Map<String, Any>

    /**
     * Fetches the list of historical fusion test summaries.
     */
    @GET("api/test-data/fusion")
    suspend fun getFusionTestList(): Map<String, Any>

    /**
     * Fetches detailed report for a specific test session by ID.
     */
    @GET("api/test-data/fusion/{test_id}")
    suspend fun getFusionTestDetail(@Path("test_id") testId: String): Map<String, Any>

    // Existing methods
    @POST("api/uwb/range")
    suspend fun sendUwbRange(@Body data: @JvmSuppressWildcards Map<String, Any>): Map<String, Any>
    @GET("api/status")
    suspend fun getStatus(): Map<String, Any>

    companion object {
        private const val BASE_URL = "http://10.0.2.2:3001/"

        fun create(): ApiService {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer mock-token-12345")
                        .build()
                    chain.proceed(request)
                }
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()

            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ApiService::class.java)
        }
    }
}