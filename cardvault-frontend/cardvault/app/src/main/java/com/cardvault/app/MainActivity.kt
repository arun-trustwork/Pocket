package com.cardvault.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import com.cardvault.app.data.repository.InMemoryContactRepository
import com.cardvault.app.ui.navigation.CardVaultApp

// TODO: replace with a real AuthRepository implementation (Firebase Phone Auth) once wired up.
// A stub is intentionally omitted here so this won't compile silently against a fake logged-in
// user — plug in FirebaseAuthRepository before running.

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CardVaultApp(
                        auth = TODO("Inject FirebaseAuthRepository"),
                        repository = InMemoryContactRepository()
                    )
                }
            }
        }
    }
}
