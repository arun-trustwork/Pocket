package com.cardvault.app.data.repository

import com.cardvault.app.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

interface CardVaultApi {
    @GET("api/contacts")
    suspend fun searchContacts(@Query("q") query: String?): List<Contact>

    @POST("api/contacts")
    suspend fun saveContact(@Body contact: Contact): Map<String, Any>

    @Multipart
    @POST("api/scan")
    suspend fun scanCard(@Part image: MultipartBody.Part): ScanResponse

    /** Text-only extraction: accepts { rawText } JSON, returns ScanResponse. */
    @POST("api/extract-text")
    suspend fun extractText(@Body body: Map<String, String>): ScanResponse
}

data class ScanResponse(
    val success: Boolean,
    val extracted: Contact?
)

class CloudflareContactRepository : ContactRepository {

    // ── OkHttp client with explicit timeouts (Fix B) ────────────────────────
    // Without this, a stalled Cloudflare Worker can hang the app for 30+ seconds.
    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)  // scan upload can be larger
        .retryOnConnectionFailure(true)
        .build()

    private val api = Retrofit.Builder()
        .baseUrl("https://cardvault-backend.arun-cardvault.workers.dev/")
        .client(okHttp)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(CardVaultApi::class.java)

    // ── Contact list ─────────────────────────────────────────────────────────
    // NOTE: This is a cold flow. The caching / deduplication of network calls
    // is handled by HomeViewModel (which uses StateFlow + WhileSubscribed).
    override fun observeContacts(scope: FilterScope, query: String): Flow<List<Contact>> = flow {
        val contacts = api.searchContacts(if (query.isBlank()) null else query)
        val filtered = contacts.filter {
            (scope == FilterScope.ALL ||
             (scope == FilterScope.VENDORS   && it.type != ContactType.CONSUMER) ||
             (scope == FilterScope.CONSUMERS && it.type != ContactType.VENDOR))
        }
        emit(filtered)
    }

    override suspend fun saveContact(contact: Contact) {
        api.saveContact(contact)
    }

    override suspend fun deleteContact(id: String) {
        // Endpoint exists on backend (DELETE /api/contacts/:id) — wire up when needed
    }

    override suspend fun extractContactFromText(rawText: String): Contact {
        val response = api.extractText(mapOf("rawText" to rawText))
        return response.extracted ?: Contact()
    }

    override suspend fun extractContactFromImage(imageBytes: ByteArray): Contact {
        val requestBody = imageBytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
        val part = MultipartBody.Part.createFormData("image", "scan.jpg", requestBody)
        val response = api.scanCard(part)
        return response.extracted ?: Contact()
    }

    // ── Business stubs (backend endpoints not yet wired) ─────────────────────
    override suspend fun listBusinesses(): List<Business> = emptyList()
    override suspend fun saveBusiness(business: Business) {}
    override suspend fun linkContactToBusiness(link: BusinessLink) {}
}
