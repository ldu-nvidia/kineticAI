package com.mycarv.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mycarv.app.sensor.ThermalVisionData
import com.mycarv.app.ui.theme.AccentGreen
import com.mycarv.app.ui.theme.AccentOrange
import com.mycarv.app.ui.theme.AccentRed
import com.mycarv.app.ui.theme.AccentYellow
import com.mycarv.app.ui.theme.SkyBlue
import com.mycarv.app.ui.theme.TextMuted
import kotlin.math.abs

@Composable
fun ThermalPanel(
    thermal: ThermalVisionData?,
    modifier: Modifier = Modifier,
) {
    if (thermal == null || !thermal.sensorPresent) return

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "Thermal Vision",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "AMG8833 8×8",
                    style = MaterialTheme.typography.labelSmall,
                    color = AccentOrange,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Person detection
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Person Behind", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                if (thermal.personBehind) {
                    Text(
                        "DETECTED (${thermal.personPixels}px, ${String.format("%.0f", thermal.personBearing)}°)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentRed,
                    )
                } else {
                    Text("Clear", color = AccentGreen)
                }
            }

            // Fused confidence
            if (thermal.fusedConfidence > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Detection Confidence", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                    Text(
                        "${thermal.fusedConfidence}% (radar+thermal)",
                        fontWeight = FontWeight.SemiBold,
                        color = when {
                            thermal.fusedConfidence > 80 -> AccentRed
                            thermal.fusedConfidence > 50 -> AccentOrange
                            else -> AccentYellow
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Temperature readings
            TempRow("Snow Surface", thermal.snowTemp, SkyBlue)
            TempRow("Ambient", thermal.ambientTemp, TextMuted)
            TempRow("Boot Surface", thermal.bootSurfaceTemp,
                if (thermal.bootSurfaceTemp < -10) AccentRed else AccentGreen)
            TempRow("Hottest Pixel", thermal.maxTemp, AccentOrange)

            // Frostbite warning
            if (thermal.frostbiteWarning) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "FROSTBITE RISK — warm up your feet!",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentRed,
                )
            }

            // Indoor detection
            if (thermal.indoors) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Indoors detected",
                    style = MaterialTheme.typography.labelSmall,
                    color = AccentYellow,
                )
            }

            // Fused alert
            if (thermal.fusedAlert) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "PERSON APPROACHING — confirmed by radar + thermal!",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    color = AccentRed,
                )
            }
        }
    }
}

@Composable
private fun TempRow(label: String, temp: Float, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        Text(
            String.format("%.1f°C", temp),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
    }
}
