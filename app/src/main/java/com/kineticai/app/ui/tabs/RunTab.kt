package com.kineticai.app.ui.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DownhillSkiing
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kineticai.app.ui.screens.LiveRunScreen
import com.kineticai.app.ui.screens.PermissionScreen
import com.kineticai.app.ui.screens.PostAnalysisScreen
import com.kineticai.app.ui.theme.DeepBlue
import com.kineticai.app.ui.theme.SkyBlue
import com.kineticai.app.viewmodel.DashboardViewModel
import com.kineticai.app.viewmodel.LiveRunViewModel
import com.kineticai.app.viewmodel.PostAnalysisViewModel

@Composable
fun RunTab(
    modifier: Modifier = Modifier,
    onHideTabBar: () -> Unit = {},
    onShowTabBar: () -> Unit = {},
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "run_home",
        modifier = modifier,
    ) {
        composable("run_home") {
            onShowTabBar()
            RunHome(
                onStartRun = { navController.navigate("permissions") },
            )
        }

        composable("permissions") {
            onShowTabBar()
            PermissionScreen(
                onAllGranted = {
                    navController.navigate("live_run") {
                        popUpTo("permissions") { inclusive = true }
                    }
                },
            )
        }

        composable("live_run") {
            onHideTabBar()
            val vm: LiveRunViewModel = viewModel()
            LiveRunScreen(
                viewModel = vm,
                onRunStopped = { runId ->
                    navController.navigate("post_analysis/$runId") {
                        popUpTo("run_home")
                    }
                },
            )
            DisposableEffect(Unit) {
                onDispose { onShowTabBar() }
            }
        }

        composable(
            route = "post_analysis/{runId}",
            arguments = listOf(navArgument("runId") { type = NavType.LongType }),
        ) { entry ->
            onShowTabBar()
            val runId = entry.arguments?.getLong("runId") ?: return@composable
            val vm: PostAnalysisViewModel = viewModel()
            LaunchedEffect(runId) { vm.loadRun(runId) }
            PostAnalysisScreen(
                viewModel = vm,
                onBack = {
                    navController.navigate("run_home") {
                        popUpTo("run_home") { inclusive = true }
                    }
                },
            )
        }
    }
}

@Composable
private fun RunHome(onStartRun: () -> Unit) {
    val dashVm: DashboardViewModel = viewModel()
    val stats by dashVm.stats.collectAsState()

    LaunchedEffect(Unit) { dashVm.refresh() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Icon(
            Icons.Filled.DownhillSkiing,
            contentDescription = null,
            tint = SkyBlue,
            modifier = Modifier.size(56.dp),
        )

        Text(
            text = "KineticAI",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 6.dp),
        )

        Text(
            text = "Your AI Ski Coach",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Big Free Run Button
        Button(
            onClick = onStartRun,
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SkyBlue),
        ) {
            Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(32.dp))
            Text(
                text = "Start Free Run",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 10.dp),
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Quick stats card
        if (stats.totalRuns > 0) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Your Season",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        QuickStat("Runs", "${stats.totalRuns}", SkyBlue)
                        QuickStat("Kinetic Score", "${stats.avgKineticScore}", Color(0xFF4ADE80))
                        QuickStat("Top", "${String.format("%.0f", stats.topSpeed)} km/h", Color(0xFFFB923C))
                        QuickStat("Vert", "${String.format("%.0f", stats.totalVertical)}m", Color(0xFFFACC15))
                    }
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Ready to ski?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Hit Start Free Run to begin tracking",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun QuickStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = color,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
