package com.kineticai.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Open Source Licenses") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Text("Open Source Libraries", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "KineticAI is built with the following open-source libraries. We are grateful to their authors and communities.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            LicenseEntry("Jetpack Compose", "Google / AOSP", "Apache License 2.0",
                "Modern declarative UI toolkit for Android.")
            LicenseEntry("Jetpack Compose Material 3", "Google / AOSP", "Apache License 2.0",
                "Material Design 3 components for Compose.")
            LicenseEntry("AndroidX Core KTX", "Google / AOSP", "Apache License 2.0",
                "Kotlin extensions for Android framework APIs.")
            LicenseEntry("AndroidX Lifecycle", "Google / AOSP", "Apache License 2.0",
                "Lifecycle-aware components including ViewModel and runtime.")
            LicenseEntry("AndroidX Navigation Compose", "Google / AOSP", "Apache License 2.0",
                "Navigation framework for Jetpack Compose.")
            LicenseEntry("AndroidX Room", "Google / AOSP", "Apache License 2.0",
                "SQLite abstraction layer for local data persistence.")
            LicenseEntry("AndroidX Activity Compose", "Google / AOSP", "Apache License 2.0",
                "Activity integration for Jetpack Compose.")
            LicenseEntry("Google Play Services Location", "Google", "Apache License 2.0",
                "Fused location provider for GPS tracking.")
            LicenseEntry("Kotlin Coroutines", "JetBrains", "Apache License 2.0",
                "Asynchronous programming for Kotlin.")
            LicenseEntry("Vico", "Patryk Goworowski", "Apache License 2.0",
                "Charting library for Jetpack Compose.")
            LicenseEntry("Material Icons Extended", "Google / AOSP", "Apache License 2.0",
                "Extended Material Design icon set.")

            Spacer(Modifier.height(16.dp))

            Text("Firmware Libraries", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))

            LicenseEntry("M5StickCPlus2 Library", "M5Stack", "MIT License",
                "Hardware abstraction for M5StickC Plus2.")
            LicenseEntry("ESP32 Arduino Core", "Espressif", "LGPL-2.1",
                "Arduino framework for ESP32 microcontrollers.")
            LicenseEntry("ESP32 BLE Arduino", "Neil Kolban / Espressif", "Apache License 2.0",
                "Bluetooth Low Energy library for ESP32.")

            Spacer(Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Apache License 2.0 Summary", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Licensed under the Apache License, Version 2.0. You may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0. " +
                            "Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an " +
                            "\"AS IS\" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun LicenseEntry(name: String, author: String, license: String, description: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text("$author — $license", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(2.dp))
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
