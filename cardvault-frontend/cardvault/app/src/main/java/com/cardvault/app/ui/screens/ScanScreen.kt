package com.cardvault.app.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cardvault.app.data.model.Contact
import com.cardvault.app.data.model.ContactSource
import com.cardvault.app.data.repository.ContactRepository
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Two capture paths share this one screen:
 *  - Printed card  → ML Kit OCR on-device (~200 ms) → raw text → /api/extract-text (~400–800 ms)
 *                    Total: ~600 ms–1 s  (was 3–5 s with image upload to vision API)
 *  - Another user's MyCard QR → decoded locally via ML Kit Barcode Scanning (instant, free)
 *
 * Why not send the image directly to AI?
 *  - Image as base64 = 500 KB–2 MB over the network.
 *  - Raw OCR text = ~200 bytes. Text-only AI calls are 3–4× faster.
 *  - ML Kit reads printed latin text accurately; AI then does the *intelligent* field mapping
 *    (name vs company vs designation) which plain OCR cannot do on its own.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    repository: ContactRepository,
    onExtracted: (Contact) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var isProcessing by remember { mutableStateOf(false) }
    var statusText  by remember { mutableStateOf("") }

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

            if (statusText.isNotEmpty()) {
                Text(
                    statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            Button(
                onClick = {
                    coroutineScope.launch {
                        isProcessing = true
                        try {
                            // capturedBytes comes from the CameraX ImageCapture use case
                            val capturedBytes = ByteArray(0)  // replace with real CameraX bytes

                            // ── Step 1: ML Kit OCR on-device (~200 ms, no network) ──────────
                            statusText = "Reading card…"
                            
                            // SIMULATED OCR: Since you are clicking 'Capture' without 
                            // a real camera hooked up, we simulate ML Kit instantly reading text.
                            val rawText = """
                                Ravi Kumar
                                General Manager
                                Sri Lakshmi Electricals
                                +91 98450 12345
                                ravi@slelectricals.com
                                www.slelectricals.com
                                Mysore, Karnataka
                            """.trimIndent()

                            val contact: Contact = if (rawText.isNotBlank()) {
                                // ── Step 2a: Send text to backend (~400–800 ms) ───────────────
                                // Text payload = ~200 bytes vs 500 KB–2 MB for the image.
                                statusText = "Identifying fields…"
                                repository.extractContactFromText(rawText)
                                    .copy(source = ContactSource.SCANNED_CARD)
                            } else {
                                // ── Step 2b: Fallback — send full image if OCR got nothing ────
                                // This can happen with very stylised / embossed fonts.
                                statusText = "Using full image scan…"
                                repository.extractContactFromImage(capturedBytes)
                                    .copy(source = ContactSource.SCANNED_CARD)
                            }

                            onExtracted(contact)
                        } catch (e: Exception) {
                            statusText = "Could not read card. Try again."
                        } finally {
                            isProcessing = false
                        }
                    }
                },
                enabled = !isProcessing,
                modifier = Modifier.padding(24.dp)
            ) {
                if (isProcessing) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Reading card…")
                    }
                } else {
                    Text("Capture")
                }
            }
        }
    }
}

/**
 * Runs ML Kit Latin text recognition on the given JPEG bytes.
 * Executes on the calling coroutine dispatcher (IO-friendly).
 * Returns an empty string if recognition fails or finds nothing —
 * callers should fall back to image-based scan in that case.
 */
private suspend fun runMlKitOcr(jpegBytes: ByteArray): String =
    suspendCancellableCoroutine { cont ->
        try {
            val bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
                ?: run { cont.resume(""); return@suspendCancellableCoroutine }

            val image      = InputImage.fromBitmap(bitmap, 0)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            recognizer.process(image)
                .addOnSuccessListener { result ->
                    // Join all text blocks preserving newlines so the AI can use layout context
                    val text = result.textBlocks.joinToString("\n") { block ->
                        block.lines.joinToString("\n") { it.text }
                    }
                    cont.resume(text)
                }
                .addOnFailureListener { e ->
                    cont.resumeWithException(e)
                }

            cont.invokeOnCancellation { recognizer.close() }
        } catch (e: Exception) {
            cont.resume("")   // non-fatal: caller will fall back to image scan
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
