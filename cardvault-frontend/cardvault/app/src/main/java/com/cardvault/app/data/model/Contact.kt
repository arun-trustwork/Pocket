package com.cardvault.app.data.model

import java.util.UUID

/**
 * A single scanned or manually-added contact.
 * Vendors and customers share this same model — `type` is what distinguishes them,
 * so filtering/searching works identically across both.
 */
data class Contact(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val designation: String? = null,
    val company: String? = null,
    val phones: List<String> = emptyList(),
    val emails: List<String> = emptyList(),
    val website: String? = null,
    val address: String? = null,
    val type: ContactType = ContactType.VENDOR,
    val tags: List<String> = emptyList(),
    val notes: String? = null,
    val rawOcrJson: String? = null, // original Claude API extraction, kept for audit/re-parsing
    val source: ContactSource = ContactSource.SCANNED_CARD,
    val createdAt: Long = System.currentTimeMillis()
) {
    /** Concatenated searchable blob — mirrors the tsvector column the backend will index. */
    fun searchText(): String = buildList {
        add(name); designation?.let { add(it) }; company?.let { add(it) }
        addAll(phones); addAll(emails); address?.let { add(it) }
        addAll(tags); notes?.let { add(it) }
    }.joinToString(" ").lowercase()
}

enum class ContactType { VENDOR, CONSUMER, BOTH }

enum class ContactSource { SCANNED_CARD, MYCARD_QR, MANUAL }

data class Business(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val vertical: String // e.g. "Real Estate", "Construction", "Medical"
)

enum class BusinessRole { SUPPLIER, CONTRACTOR, CONSUMER, CONSULTANT, OTHER }

data class BusinessLink(
    val businessId: String,
    val contactId: String,
    val role: BusinessRole
)

/** What the top filter menu switches between. */
enum class FilterScope { ALL, VENDORS, CONSUMERS, BUSINESSES }
