package com.cardvault.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import com.cardvault.app.data.repository.CloudflareContactRepository
import com.cardvault.app.ui.navigation.CardVaultApp
import com.cardvault.app.data.model.User
import com.cardvault.app.data.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

import com.cardvault.app.data.model.SubscriptionStatus

// Dummy Auth to get the app running without Firebase setup yet
class DummyAuthRepository : AuthRepository {
    override val currentUser: Flow<User?> = flowOf(User("1", "9999999999", name = "Test User"))
    
    override suspend fun requestPhoneNumberHint(): String? = null
    override suspend fun sendOtp(phone: String) {}
    override suspend fun verifyOtp(code: String): User = User("1", "9999999999", name = "Test User")
    override suspend fun signOut() {}
    override suspend fun refreshSubscriptionStatus(): SubscriptionStatus = SubscriptionStatus.ACTIVE
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CardVaultApp(
                        auth = DummyAuthRepository(),
                        repository = CloudflareContactRepository()
                    )
                }
            }
        }
    }
}
