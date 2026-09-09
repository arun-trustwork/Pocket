// Reference dependency block — merge into your actual app/build.gradle.kts.
// Versions intentionally omitted; use the latest stable via the version catalog / BOM.

dependencies {
    // Compose
    implementation(platform("androidx.compose:compose-bom:LATEST"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:LATEST")
    implementation("androidx.navigation:navigation-compose:LATEST")

    // Camera + on-device barcode/QR decoding for the Scan screen
    implementation("androidx.camera:camera-camera2:LATEST")
    implementation("androidx.camera:camera-lifecycle:LATEST")
    implementation("androidx.camera:camera-view:LATEST")
    implementation("com.google.mlkit:barcode-scanning:LATEST")

    // QR generation for the MyCard screen
    implementation("com.google.zxing:core:LATEST")

    // Auth: phone number hint + OTP
    implementation("com.google.android.gms:play-services-auth:LATEST")
    implementation("com.google.android.gms:play-services-auth-api-phone:LATEST") // SMS Retriever
    implementation(platform("com.google.firebase:firebase-bom:LATEST"))
    implementation("com.google.firebase:firebase-auth-ktx")

    // Subscription billing (₹2000/yr product configured in Play Console)
    implementation("com.android.billingclient:billing-ktx:LATEST")

    // Networking to your backend (contact CRUD, Claude API scan endpoint, search)
    implementation("com.squareup.retrofit2:retrofit:LATEST")
    implementation("com.squareup.retrofit2:converter-gson:LATEST")
    implementation("com.google.code.gson:gson:LATEST")

    // Local cache / offline-first (optional but recommended for a scan-heavy field app)
    implementation("androidx.room:room-runtime:LATEST")
    implementation("androidx.room:room-ktx:LATEST")
}
