package com.kineticai.app.ui.tabs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kineticai.app.data.db.RunEntity
import com.kineticai.app.network.AiCoachService
import com.kineticai.app.ui.components.MetricCard
import com.kineticai.app.ui.screens.AiChatScreen
import com.kineticai.app.ui.screens.MetricDetailScreen
import com.kineticai.app.ui.screens.PostAnalysisScreen
import com.kineticai.app.ui.theme.AccentGreen
import com.kineticai.app.ui.theme.AccentOrange
import com.kineticai.app.ui.theme.AccentRed
import com.kineticai.app.ui.theme.AccentYellow
import com.kineticai.app.ui.theme.SkyBlue
import com.kineticai.app.viewmodel.DashboardViewModel
import com.kineticai.app.viewmodel.MetricDetailViewModel
import com.kineticai.app.viewmodel.PostAnalysisViewModel
import com.kineticai.app.viewmodel.RunHistoryViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StatsTab(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "stats_home",
        modifier = modifier,
    ) {
        composable("stats_home") {
            StatsHome(
                onMetricClick = { metric -> navController.navigate("metric/$metric") },
                onRunClick = { runId -> navController.navigate("run_detail/$runId") },
                onAiChat = { navController.navigate("ai_chat") },
            )
        }

        composable("ai_chat") {
            val aiService = remember { AiCoachService() }
            AiChatScreen(
                aiCoachService = aiService,
                sessionContext = "No session loaded — user is browsing historical data.",
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = "metric/{type}",
            arguments = listOf(navArgument("type") { type = NavType.StringType }),
        ) { entry ->
            val metricType = entry.arguments?.getString("type") ?: return@composable
            val vm: MetricDetailViewModel = viewModel()
            LaunchedEffect(metricType) { vm.loadMetric(metricType) }
            MetricDetailScreen(
                viewModel = vm,
                metricType = metricType,
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = "run_detail/{runId}",
            arguments = listOf(navArgument("runId") { type = NavType.LongType }),
        ) { entry ->
            val runId = entry.arguments?.getLong("runId") ?: return@composable
            val vm: PostAnalysisViewModel = viewModel()
            LaunchedEffect(runId) { vm.loadRun(runId) }
            PostAnalysisScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

@Composable
private fun StatsHome(
    onMetricClick: (String) -> Unit,
    onRunClick: (Long) -> Unit,
    onAiChat: () -> Unit = {},
) {
    val dashVm: DashboardViewModel = viewModel()
    val histVm: RunHistoryViewModel = viewModel()
    val stats by dashVm.stats.collectAsState()
    val runs by histVm.runs.collectAsState()

    LaunchedEffect(Unit) { dashVm.refresh() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Insights",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        item {
            Card(
                onClick = onAiChat,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SkyBlue.copy(alpha = 0.12f)),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("✨", fontSize = 24.sp)
                    Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("Ask AI Coach", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Chat about your technique, get personalized advice",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = SkyBlue)
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MetricCard("Total Runs", "${stats.totalRuns}", accentColor = SkyBlue,
                    modifier = Modifier.weight(1f), onClick = { onMetricClick("total_runs") })
                MetricCard("Avg Kinetic Score", "${stats.avgKineticScore}", unit = "/ 200", accentColor = AccentGreen,
                    modifier = Modifier.weight(1f), onClick = { onMetricClick("avg_kinetic_score") })
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MetricCard("Peak Kinetic Score", "${stats.peakKineticScore}", unit = "/ 200", accentColor = AccentGreen,
                    modifier = Modifier.weight(1f), onClick = { onMetricClick("peak_kinetic_score") })
                MetricCard("Top Speed", String.format("%.0f", stats.topSpeed), unit = "km/h", accentColor = AccentOrange,
                    modifier = Modifier.weight(1f), onClick = { onMetricClick("top_speed") })
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MetricCard("Distance", String.format("%.1f", stats.totalDistance / 1000f), unit = "km", accentColor = AccentYellow,
                    modifier = Modifier.weight(1f), onClick = { onMetricClick("total_distance") })
                MetricCard("Vertical", String.format("%.0f", stats.totalVertical), unit = "m",
                    modifier = Modifier.weight(1f), onClick = { onMetricClick("total_vertical") })
            }
        }

        // Run history section
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Run History",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }

        if (runs.isEmpty()) {
            item {
                Text(
                    text = "No runs yet — go ski!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(runs, key = { it.id }) { run ->
                RunHistoryCard(run = run, onClick = { onRunClick(run.id) })
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun RunHistoryCard(run: RunEntity, onClick: () -> Unit) {
    val dateFormat = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
    val durationSec = run.durationMs / 1000
    val durationStr = "%d:%02d".format(durationSec / 60, durationSec % 60)
    val scoreColor = when {
        run.skiIQ >= 140 -> AccentGreen
        run.skiIQ >= 115 -> SkyBlue
        run.skiIQ >= 90 -> AccentOrange
        else -> AccentRed
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    dateFormat.format(Date(run.startTime)),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(durationStr, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${String.format("%.0f", run.maxSpeedKmh)} km/h", style = MaterialTheme.typography.bodySmall, color = AccentOrange)
                    Text("${run.turnCount} turns", style = MaterialTheme.typography.bodySmall, color = SkyBlue)
                }
            }
            Text(
                "${run.skiIQ}",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = scoreColor,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
