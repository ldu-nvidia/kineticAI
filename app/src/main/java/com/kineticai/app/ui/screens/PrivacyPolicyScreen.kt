package com.kineticai.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy Policy") },
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
            Text("KineticAI Privacy Policy", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Last updated: April 2026", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))

            Section("1. Data We Collect") {
                """KineticAI collects the following data to provide real-time motion analysis and coaching:

• Sensor data: Accelerometer, gyroscope, magnetometer, and barometer readings from your phone and paired Bluetooth sensors
• Location data: GPS coordinates for trajectory mapping, speed calculation, and altitude tracking
• Microphone data: Short audio samples processed on-device for surface classification (not recorded or stored as audio)
• Thermal data: IR temperature grid from paired thermal sensors for proximity detection"""
            }

            Section("2. How Data Is Processed") {
                """All sensor data is processed locally on your device in real-time. KineticAI performs all motion analysis, scoring, and coaching on your phone — no sensor data is sent to external servers during normal operation.

Optional cloud features (weather, AI coaching, resort info) send only your GPS coordinates to the respective API providers when you explicitly use those features."""
            }

            Section("3. Data Storage") {
                """Run data (metrics, trajectories, scores) is stored locally in an on-device database. This data never leaves your phone unless you explicitly:

• Share a run card image via the share function
• Use the AI coaching feature (sends run summary metrics only, not raw sensor data)
• Export data to GPX or CSV files

You can delete all stored data at any time from the app settings."""
            }

            Section("4. Third-Party Services") {
                """When you opt in to internet features, KineticAI may share limited data with:

• OpenWeatherMap — Your GPS location for weather data (openweathermap.org/privacy)
• OpenAI — Run summary metrics for AI coaching analysis (openai.com/privacy)
• Ski resort APIs — Your GPS location for nearest resort lookup

No personal information (name, email, account) is collected or shared. KineticAI does not require account creation."""
            }

            Section("5. Bluetooth & Sensor Permissions") {
                """KineticAI requires Bluetooth permissions to connect to external motion sensors (e.g., M5StickC boot sensors). Location permission is required for GPS tracking and is also an Android requirement for Bluetooth scanning.

Sensor data from paired devices is processed identically to phone sensor data — locally, in real-time, with no external transmission."""
            }

            Section("6. Children's Privacy") {
                """KineticAI does not knowingly collect data from children under 13. The app does not require account creation and collects no personally identifiable information."""
            }

            Section("7. Data Retention & Deletion") {
                """All data is stored on your device. Uninstalling the app removes all stored data. You may also clear run history from within the app at any time."""
            }

            Section("8. Changes to This Policy") {
                """We may update this policy as features evolve. The "Last updated" date at the top reflects the most recent revision."""
            }

            Section("9. Contact") {
                """For privacy questions, open an issue at the KineticAI GitHub repository or contact the developer directly."""
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun Section(title: String, content: () -> String) {
    Spacer(Modifier.height(12.dp))
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(4.dp))
    Text(
        content(),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
