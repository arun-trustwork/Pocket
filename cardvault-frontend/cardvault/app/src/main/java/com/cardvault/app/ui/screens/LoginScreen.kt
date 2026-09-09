package com.cardvault.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cardvault.app.data.repository.AuthRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(auth: AuthRepository, onLoggedIn: () -> Unit) {
    var phone by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var otpSent by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(topBar = { TopAppBar(title = { Text("Sign in") }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Sign in to sync your vendor & customer DB", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = phone, onValueChange = { phone = it },
                label = { Text("Phone number") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            TextButton(onClick = {
                // Launches the Play Services Phone Number Hint picker; fills `phone` on success.
                scope.launch { auth.requestPhoneNumberHint()?.let { phone = it } }
            }) { Text("Use number on this device") }

            if (otpSent) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = otp, onValueChange = { otp = it },
                    label = { Text("OTP") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                // SMS Retriever fills `otp` automatically when the code arrives, if wired up.
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    scope.launch {
                        if (!otpSent) {
                            auth.sendOtp(phone)
                            otpSent = true
                        } else {
                            auth.verifyOtp(otp)
                            onLoggedIn()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (otpSent) "Verify & continue" else "Send OTP") }
        }
    }
}
