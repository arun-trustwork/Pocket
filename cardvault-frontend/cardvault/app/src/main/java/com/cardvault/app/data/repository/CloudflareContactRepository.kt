package com.cardvault.app.data.repository

import com.cardvault.app.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

interface CardVaultApi {
    @GET("api/contacts")
    suspend fun searchContacts(@Query("q") query: String?): List<Contact>

    @POST("api/contacts")
    suspend fun saveContact(@Body contact: Contact): Map<String, Any>

    @Multipart
    @POST("api/scan")
    suspend fun scanCard(@Part image: MultipartBody.Part): ScanResponse
}

data class ScanResponse(
    val success: Boolean,
    val extracted: Contact?
)

class CloudflareContactRepository : ContactRepository {
    private val api = Retrofit.Builder()
        .baseUrl("https://cardvault-backend.arun-cardvault.workers.dev/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(CardVaultApi::class.java)

    override fun observeContacts(scope: FilterScope, query: String): Flow<List<Contact>> = flow {
        val contacts = api.searchContacts(if (query.isBlank()) null else query)
        val filtered = contacts.filter {
            (scope == FilterScope.ALL ||
             (scope == FilterScope.VENDORS && it.type != ContactType.CONSUMER) ||
             (scope == FilterScope.CONSUMERS && it.type != ContactType.VENDOR))
        }
        emit(filtered)
    }

    override suspend fun saveContact(contact: Contact) {
        api.saveContact(contact)
    }

    override suspend fun extractContactFromImage(imageBytes: ByteArray): Contact {
        val requestBody = imageBytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
        val part = MultipartBody.Part.createFormData("image", "scan.jpg", requestBody)
        val response = api.scanCard(part)
        return response.extracted ?: Contact()
    }

    // Default stub implementations for the rest until endpoints are added
    override suspend fun deleteContact(id: String) {}
    override suspend fun listBusinesses(): List<Business> = emptyList()
    override suspend fun listProjects(businessId: String?): List<Project> = emptyList()
    override suspend fun linkContactToProject(link: ProjectLink) {}
}
