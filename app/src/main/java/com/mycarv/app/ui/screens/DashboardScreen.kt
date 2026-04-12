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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.mycarv.app.ui.theme.AccentGreen
import com.mycarv.app.ui.theme.AccentOrange
import com.mycarv.app.ui.theme.AccentYellow
import com.mycarv.app.ui.theme.SkyBlue
import com.mycarv.app.viewmodel.DashboardViewModel

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onMetricClick: (String) -> Unit = {},
    onBack: () -> Unit = {},
) {
    val stats by viewModel.stats.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Season Stats",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MetricCard(
                label = "Total Runs",
                value = "${stats.totalRuns}",
                accentColor = SkyBlue,
                modifier = Modifier.weight(1f),
                onClick = { onMetricClick("total_runs") },
            )
            MetricCard(
                label = "Avg Ski:IQ",
                value = "${stats.avgSkiIQ}",
                unit = "/ 200",
                accentColor = AccentGreen,
                modifier = Modifier.weight(1f),
                onClick = { onMetricClick("avg_ski_iq") },
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MetricCard(
                label = "Peak Ski:IQ",
                value = "${stats.peakSkiIQ}",
                unit = "/ 200",
                accentColor = AccentGreen,
                modifier = Modifier.weight(1f),
                onClick = { onMetricClick("peak_ski_iq") },
            )
            MetricCard(
                label = "Top Speed",
                value = String.format("%.0f", stats.topSpeed),
                unit = "km/h",
                accentColor = AccentOrange,
                modifier = Modifier.weight(1f),
                onClick = { onMetricClick("top_speed") },
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MetricCard(
                label = "Total Distance",
                value = String.format("%.1f", stats.totalDistance / 1000f),
                unit = "km",
                accentColor = AccentYellow,
                modifier = Modifier.weight(1f),
                onClick = { onMetricClick("total_distance") },
            )
            MetricCard(
                label = "Total Vertical",
                value = String.format("%.0f", stats.totalVertical),
                unit = "m",
                modifier = Modifier.weight(1f),
                onClick = { onMetricClick("total_vertical") },
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}
