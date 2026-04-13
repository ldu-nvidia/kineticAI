package com.kineticai.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kineticai.app.ui.theme.AccentGreen
import com.kineticai.app.ui.theme.AccentOrange
import com.kineticai.app.ui.theme.AccentYellow
import com.kineticai.app.ui.theme.SkyBlue
import com.kineticai.app.xcski.XCSkiMetrics

/**
 * Live data panel for cross-country skiing mode.
 * Shows current technique, cadence, glide ratio, distance, and stride score.
 */
@Composable
fun XCLivePanel(metrics: XCSkiMetrics, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Cross-Country",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = SkyBlue,
            )
            Spacer(Modifier.height(8.dp))

            // Current technique (large label)
            Text(
                text = metrics.currentTechnique.displayName,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Terrain: ${metrics.currentTerrainGrade.displayName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(12.dp))

            // Key metrics row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                XCMetricItem("Cadence", "${String.format("%.0f", metrics.currentCycleRate)}/min", SkyBlue)
                XCMetricItem("Speed", "${String.format("%.1f", metrics.currentSpeedKmh)} km/h", AccentGreen)
                XCMetricItem("Strides", "${metrics.totalStrides}", AccentOrange)
                XCMetricItem("Distance", "${String.format("%.1f", metrics.totalDistanceM / 1000f)} km", AccentYellow)
            }

            Spacer(Modifier.height(12.dp))

            // Glide ratio bar
            Text("Glide Ratio", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LinearProgressIndicator(
                progress = { metrics.avgGlideRatio.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = SkyBlue,
                trackColor = MaterialTheme.colorScheme.surface,
            )
            Text(
                "${String.format("%.0f", metrics.avgGlideRatio * 100)}%",
                style = MaterialTheme.typography.labelSmall,
                color = SkyBlue,
            )

            Spacer(Modifier.height(8.dp))

            // Symmetry bar
            Text("L/R Symmetry", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LinearProgressIndicator(
                progress = { metrics.avgLateralSymmetry.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = AccentGreen,
                trackColor = MaterialTheme.colorScheme.surface,
            )
            Text(
                "${String.format("%.0f", metrics.avgLateralSymmetry * 100)}%",
                style = MaterialTheme.typography.labelSmall,
                color = AccentGreen,
            )

            // Stride Score
            if (metrics.strideScore > 0f) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        "Stride Score",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "${String.format("%.0f", metrics.strideScore)}/200",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            metrics.strideScore >= 150 -> AccentGreen
                            metrics.strideScore >= 100 -> SkyBlue
                            metrics.strideScore >= 60 -> AccentYellow
                            else -> AccentOrange
                        },
                    )
                }
            }

            // Fatigue warning
            if (metrics.fatigueFactor > 10f) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Fatigue: cadence down ${String.format("%.0f", metrics.fatigueFactor)}%",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = AccentOrange,
                )
            }
        }
    }
}

@Composable
private fun XCMetricItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
