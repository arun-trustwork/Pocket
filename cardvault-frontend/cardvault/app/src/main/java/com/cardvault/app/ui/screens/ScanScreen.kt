package com.cardvault.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cardvault.app.data.model.Contact
import com.cardvault.app.data.model.ContactSource
import com.cardvault.app.data.repository.ContactRepository
import kotlinx.coroutines.launch

/**
 * Two capture paths share this one screen:
 *  - Printed card  -> photo goes to the Claude API extraction endpoint (async, costs a call)
 *  - Another user's MyCard QR -> decoded locally via ML Kit Barcode Scanning (instant, free)
 * CameraX + ML Kit's barcode analyzer run on the same preview frame, so which path fires
 * depends on what's in frame — no separate "scan QR" mode needed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    repository: ContactRepository,
    onExtracted: (Contact) -> Unit
) {
    val scope = rememberCoroutineScope()
    var isProcessing by remember { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text("Scan card") }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // TODO: replace this Box with an androidx.camera.compose CameraXPreview,
            // plus an ML Kit BarcodeScanner analyzer bound to the same ImageAnalysis use case.
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    "Camera preview — align a printed card or another\nuser's MyCard QR within the frame",
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            Button(
                onClick = {
                    scope.launch {
                        isProcessing = true
                        try {
                            // capturedBytes would come from the CameraX ImageCapture use case
                            val capturedBytes = ByteArray(0)
                            val contact = repository.extractContactFromImage(capturedBytes)
                                .copy(source = ContactSource.SCANNED_CARD)
                            onExtracted(contact)
                        } finally {
                            isProcessing = false
                        }
                    }
                },
                enabled = !isProcessing,
                modifier = Modifier.padding(24.dp)
            ) {
                Text(if (isProcessing) "Reading card..." else "Capture")
            }
        }
    }
}

/*
 * QR path (when ML Kit's analyzer detects a barcode instead of a full card):
 *
 *   val payload = Json.decodeFromString<MyCardPayload>(barcode.rawValue)
 *   val contact = Contact(
 *       name = payload.name, company = payload.company,
 *       phones = listOfNotNull(payload.phone), emails = listOfNotNull(payload.email),
 *       source = ContactSource.MYCARD_QR
 *   )
 *   onExtracted(contact)   // no network call needed
 */
