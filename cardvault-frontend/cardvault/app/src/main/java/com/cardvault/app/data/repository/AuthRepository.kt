package com.cardvault.app.data.repository

import com.cardvault.app.data.model.SubscriptionStatus
import com.cardvault.app.data.model.User
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val currentUser: Flow<User?>

    /** Launches the Google Play Services "Phone Number Hint" picker; returns the picked number. */
    suspend fun requestPhoneNumberHint(): String?

    /** Sends OTP to the given number via Firebase Phone Auth. */
    suspend fun sendOtp(phone: String)

    /** Verifies the OTP (auto-filled via SMS Retriever where possible) and signs the user in. */
    suspend fun verifyOtp(code: String): User

    suspend fun signOut()

    /** Reads entitlement from Play Billing / your backend's subscription record. */
    suspend fun refreshSubscriptionStatus(): SubscriptionStatus
}

/*
 * Implementation notes for whoever wires this up:
 *
 * 1. Phone Number Hint  -> com.google.android.gms:play-services-auth
 *      Identity.getSignInClient(context).getPhoneNumberHintIntent(request)
 *      No READ_PHONE_STATE permission needed; user confirms via a one-tap bottom sheet.
 *
 * 2. OTP send/verify     -> Firebase Auth (PhoneAuthProvider)
 *      firebase-auth-ktx. Enable "Phone" sign-in method in the Firebase console.
 *
 * 3. Auto-read OTP       -> SMS Retriever API (com.google.android.gms:play-services-auth-api-phone)
 *      Requires the SMS body to contain your app's 11-char hash; Firebase can inject this
 *      automatically if you use their SMS templates.
 *
 * 4. Subscription        -> Play Billing Library (com.android.billingclient:billing-ktx)
 *      Define a ₹2000/yr subscription product in Play Console. Validate the purchase token
 *      server-side (Google Play Developer API) rather than trusting the client-reported status.
 */
