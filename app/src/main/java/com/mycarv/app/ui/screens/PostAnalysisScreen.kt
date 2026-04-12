package com.mycarv.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mycarv.app.ui.components.MetricCard
import com.mycarv.app.ui.components.SkiIQRing
import com.mycarv.app.ui.components.SkillBreakdownCard
import com.mycarv.app.ui.components.TrajectoryView
import com.mycarv.app.ui.components.TurnBalanceBar
import com.mycarv.app.ui.theme.AccentGreen
import com.mycarv.app.ui.theme.AccentOrange
import com.mycarv.app.ui.theme.AccentYellow
import com.mycarv.app.ui.theme.SkyBlue
import com.mycarv.app.viewmodel.PostAnalysisViewModel

@Composable
fun PostAnalysisScreen(
    viewModel: PostAnalysisViewModel,
    onBack: () -> Unit,
) {
    val run by viewModel.run.collectAsState()
    val feedback by viewModel.feedback.collectAsState()
    val speedProfile by viewModel.speedProfile.collectAsState()
    val edgeAngleProfile by viewModel.edgeAngleProfile.collectAsState()
    val gForceProfile by viewModel.gForceProfile.collectAsState()
    val trajectory by viewModel.trajectory.collectAsState()

    val r = run ?: return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Run Analysis",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Ski:IQ Ring
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            SkiIQRing(skiIQ = r.skiIQ)
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 4-Skill Breakdown
        SkillBreakdownCard(
            balanceScore = r.balanceScore,
            edgingScore = r.edgingScore,
            rotaryScore = r.rotaryScore,
            pressureScore = r.pressureScore,
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Run stats
        val durationSec = r.durationMs / 1000
        val durationStr = "%d:%02d".format(durationSec / 60, durationSec % 60)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MetricCard(
                label = "Duration",
                value = durationStr,
                accentColor = SkyBlue,
                modifier = Modifier.weight(1f),
            )
            MetricCard(
                label = "Max Speed",
                value = String.format("%.1f", r.maxSpeedKmh),
                unit = "km/h",
                accentColor = AccentOrange,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MetricCard(
                label = "Vertical Drop",
                value = String.format("%.0f", r.altitudeDrop),
                unit = "m",
                accentColor = AccentGreen,
                modifier = Modifier.weight(1f),
            )
            MetricCard(
                label = "Distance",
                value = String.format("%.0f", r.totalDistance),
                unit = "m",
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Detailed edging metrics
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MetricCard(
                label = "Edge Angle",
                value = String.format("%.1f", r.edgeAngle),
                unit = "°",
                accentColor = SkyBlue,
                modifier = Modifier.weight(1f),
            )
            MetricCard(
                label = "Early Edging",
                value = "${r.earlyEdging.toInt()}",
                unit = "/ 100",
                accentColor = AccentGreen,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MetricCard(
                label = "Max G-Force",
                value = String.format("%.1f", r.maxGForce),
                unit = "G",
                accentColor = AccentOrange,
                modifier = Modifier.weight(1f),
            )
            MetricCard(
                label = "Weight Release",
                value = "${r.transitionWeightRelease.toInt()}",
                unit = "/ 100",
                accentColor = AccentYellow,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        TurnBalanceBar(leftTurns = r.leftTurns, rightTurns = r.rightTurns)

        Spacer(modifier = Modifier.height(16.dp))

        // Trajectory reconstruction
        if (trajectory.size >= 2) {
            TrajectoryView(
                points = trajectory,
                title = "Run Trajectory",
                heightDp = 300,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Charts
        if (speedProfile.isNotEmpty()) {
            SensorChart(
                title = "Speed Profile",
                values = speedProfile,
                unit = "km/h",
                lineColor = AccentOrange,
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (edgeAngleProfile.isNotEmpty()) {
            SensorChart(
                title = "Edge Angle",
                values = edgeAngleProfile,
                unit = "°",
                lineColor = SkyBlue,
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (gForceProfile.isNotEmpty()) {
            SensorChart(
                title = "G-Force",
                values = gForceProfile,
                unit = "G",
                lineColor = AccentGreen,
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Coaching Insights
        if (feedback.isNotEmpty()) {
            Text(
                text = "Coaching Insights",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(12.dp))
            feedback.forEach { tip ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Text(
                        text = tip,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
internal fun SensorChart(
    title: String,
    values: List<Float>,
    unit: String,
    lineColor: androidx.compose.ui.graphics.Color,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(12.dp))

            val max = values.maxOrNull() ?: 1f
            val min = values.minOrNull() ?: 0f
            val range = (max - min).coerceAtLeast(1f)

            androidx.compose.foundation.Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
            ) {
                if (values.size < 2) return@Canvas
                val step = size.width / (values.size - 1)
                val points = values.mapIndexed { i, v ->
                    val x = i * step
                    val y = size.height - ((v - min) / range) * size.height
                    androidx.compose.ui.geometry.Offset(x, y)
                }
                for (i in 0 until points.size - 1) {
                    drawLine(
                        color = lineColor,
                        start = points[i],
                        end = points[i + 1],
                        strokeWidth = 3.dp.toPx(),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Min: ${String.format("%.1f", values.minOrNull() ?: 0f)} $unit",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Max: ${String.format("%.1f", values.maxOrNull() ?: 0f)} $unit",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
