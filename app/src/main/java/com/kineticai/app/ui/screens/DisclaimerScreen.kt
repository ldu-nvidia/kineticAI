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
fun DisclaimerScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Safety & Disclaimers") },
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
            Text("Safety Disclaimers", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))

            DisclaimerCard(
                "Not a Medical Device",
                "KineticAI is a sports analytics tool, NOT a medical device. It has not been evaluated or approved by the FDA, CE, or any medical regulatory authority. The ACL risk indicators, knee valgus warnings, and frostbite alerts are computational estimates based on sensor data and should never be used as a substitute for professional medical advice, diagnosis, or treatment. If you experience pain, injury symptoms, or health concerns, consult a qualified medical professional immediately.",
            )

            DisclaimerCard(
                "Assumption of Risk",
                "Skiing, snowboarding, and other action sports are inherently dangerous activities that carry risk of serious injury or death. KineticAI provides coaching guidance to help improve technique, but it does not eliminate the inherent risks of these activities. You participate at your own risk. Always ski and ride within your ability level, obey posted signs and closures, and follow the Responsibility Code.",
            )

            DisclaimerCard(
                "Sensor Accuracy Limitations",
                "Motion sensor data and the metrics derived from it (edge angle, G-force, turn shape, Kinetic Score, etc.) are estimates based on mathematical models of physical movement. They are subject to sensor noise, calibration drift, mounting position variations, and environmental interference. Absolute values may differ from laboratory-grade motion capture systems. Use metrics for relative tracking and improvement trends, not as precise physical measurements.",
            )

            DisclaimerCard(
                "Proximity & Safety Alerts",
                "The proximity warning system (thermal camera, radar) is an assistive feature, not a collision avoidance system. Detection range, accuracy, and reliability are limited by sensor capabilities, environmental conditions (fog, snow, sunlight), mounting angle, and speed. Never rely solely on electronic alerts for safety. Always maintain visual awareness of your surroundings and other people on the slope.",
            )

            DisclaimerCard(
                "Audio Coaching",
                "If using audio coaching through earbuds or headphones while skiing/snowboarding, ensure you can still hear ambient sounds (other skiers, patrol whistles, warnings). Consider using bone conduction headphones or keeping volume low. Many resorts prohibit noise-canceling headphones on the slopes.",
            )

            DisclaimerCard(
                "Phone & Sensor Mounting",
                "Ensure your phone and external sensors are securely mounted before skiing or riding. Loose devices can become projectiles during falls, causing injury to you or others. The manufacturer is not responsible for damage to devices, equipment, or persons resulting from improper mounting.",
            )

            DisclaimerCard(
                "No Warranty",
                "KineticAI is provided \"as is\" without warranty of any kind, express or implied. The developer makes no guarantees regarding the accuracy, reliability, or completeness of any data, analysis, or coaching provided by the app.",
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun DisclaimerCard(title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
