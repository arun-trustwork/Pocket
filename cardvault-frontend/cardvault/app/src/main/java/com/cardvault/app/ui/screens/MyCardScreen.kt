package com.cardvault.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.cardvault.app.data.model.MyCardPayload
import com.cardvault.app.data.model.User

/**
 * Shown to another CardVault user's scanner to add yourself to their DB instantly —
 * no OCR needed, since the QR already carries structured data.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyCardScreen(user: User) {
    val payload = MyCardPayload(
        name = user.name, designation = null, company = user.company,
        phone = user.phone, email = null, website = null
    )

    Scaffold(topBar = { TopAppBar(title = { Text("My card") }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // TODO: generate with ZXing (com.google.zxing:core) -> Bitmap, e.g.
            //   val bitmap = QRCodeWriter().encode(Json.encodeToString(payload), BarcodeFormat.QR_CODE, 512, 512)
            //   Image(bitmap.asImageBitmap(), contentDescription = "My CardVault QR code")
            Box(
                Modifier.size(220.dp),
                contentAlignment = Alignment.Center
            ) { Text("[ QR code ]") }

            Spacer(Modifier.height(16.dp))
            Text(user.name, style = MaterialTheme.typography.titleMedium)
            user.company?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Text(user.phone, style = MaterialTheme.typography.bodyMedium)

            Spacer(Modifier.height(24.dp))
            Text(
                "Ask them to open CardVault's scanner and point it at this code.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
