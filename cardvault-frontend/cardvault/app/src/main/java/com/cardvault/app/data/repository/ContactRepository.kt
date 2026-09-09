package com.cardvault.app.data.repository

import com.cardvault.app.data.model.*
import kotlinx.coroutines.flow.Flow

/**
 * Swap the implementation (below) for a real backend call (Retrofit/Ktor -> your API -> Postgres)
 * once the server side is ready. Screens only ever talk to this interface.
 */
interface ContactRepository {
    fun observeContacts(scope: FilterScope, query: String): Flow<List<Contact>>
    suspend fun saveContact(contact: Contact)
    suspend fun deleteContact(id: String)

    suspend fun listBusinesses(): List<Business>
    suspend fun saveBusiness(business: Business)
    suspend fun linkContactToBusiness(link: BusinessLink)

    /** Sends the captured image bytes to the Claude API extraction endpoint on your backend. */
    suspend fun extractContactFromImage(imageBytes: ByteArray): Contact
}

/** In-memory stub so the UI is fully clickable before the backend exists. Replace at DI setup. */
class InMemoryContactRepository : ContactRepository {
    private val contacts = mutableListOf(
        Contact(name = "Ravi Kumar", company = "Sri Lakshmi Electricals",
            phones = listOf("98450xxxxx"), type = ContactType.VENDOR,
            tags = listOf("Bulb supplier", "Mysore")),
        Contact(name = "Sunita Nair", company = "Nair Interiors",
            phones = listOf("99001xxxxx"), type = ContactType.CONSUMER,
            tags = listOf("Bengaluru"))
    )
    private val businesses = mutableListOf(Business(name = "Arun Trustwork", vertical = "Real Estate"))
    private val links = mutableListOf<BusinessLink>()

    override fun observeContacts(scope: FilterScope, query: String) =
        kotlinx.coroutines.flow.flow {
            val filtered = contacts.filter {
                (scope == FilterScope.ALL ||
                    (scope == FilterScope.VENDORS && it.type != ContactType.CONSUMER) ||
                    (scope == FilterScope.CONSUMERS && it.type != ContactType.VENDOR)) &&
                    (query.isBlank() || it.searchText().contains(query.lowercase()))
            }
            emit(filtered)
        }

    override suspend fun saveContact(contact: Contact) {
        contacts.removeAll { it.id == contact.id }
        contacts.add(contact)
    }

    override suspend fun deleteContact(id: String) { contacts.removeAll { it.id == id } }
    override suspend fun listBusinesses() = businesses
    override suspend fun saveBusiness(business: Business) { businesses.add(business) }
    override suspend fun linkContactToBusiness(link: BusinessLink) { links.add(link) }

    // TODO: replace with a call to your backend, which forwards the image to the Claude API
    // (see the vision-extraction prompt/schema discussed earlier) and returns parsed JSON.
    override suspend fun extractContactFromImage(imageBytes: ByteArray): Contact {
        throw NotImplementedError("Wire this to POST /api/scan on your backend")
    }
}
