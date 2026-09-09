package com.cardvault.app.data.model

data class User(
    val uid: String,               // Firebase Auth uid
    val phone: String,              // verified via OTP
    val name: String = "",
    val company: String? = null,
    val subscription: SubscriptionStatus = SubscriptionStatus.TRIAL
)

enum class SubscriptionStatus { TRIAL, ACTIVE, EXPIRED }

/** Encoded into the QR code on the MyCard screen and decoded by another user's scanner. */
data class MyCardPayload(
    val name: String,
    val designation: String?,
    val company: String?,
    val phone: String,
    val email: String?,
    val website: String?
)
