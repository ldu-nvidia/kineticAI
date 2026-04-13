package com.kineticai.app.ui.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.kineticai.app.analysis.SkillCategory
import com.kineticai.app.analysis.SkillDrill
import com.kineticai.app.analysis.SkillLevel
import com.kineticai.app.analysis.SkillProgressionData
import com.kineticai.app.ui.screens.DrillDetailScreen
import com.kineticai.app.ui.screens.LiveRunScreen
import com.kineticai.app.ui.screens.PermissionScreen
import com.kineticai.app.ui.screens.PostAnalysisScreen
import com.kineticai.app.ui.theme.AccentGreen
import com.kineticai.app.ui.theme.AccentOrange
import com.kineticai.app.ui.theme.AccentYellow
import com.kineticai.app.ui.theme.SkyBlue
import com.kineticai.app.viewmodel.LiveRunViewModel
import com.kineticai.app.viewmodel.PostAnalysisViewModel

@Composable
fun TrainTab(
    modifier: Modifier = Modifier,
    onHideTabBar: () -> Unit = {},
    onShowTabBar: () -> Unit = {},
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "train_home",
        modifier = modifier,
    ) {
        composable("train_home") {
            onShowTabBar()
            TrainHome(
                onDrillClick = { drillId -> navController.navigate("drill/$drillId") },
            )
        }

        composable(
            route = "drill/{drillId}",
            arguments = listOf(navArgument("drillId") { type = NavType.StringType }),
        ) { entry ->
            onShowTabBar()
            val drillId = entry.arguments?.getString("drillId") ?: return@composable
            DrillDetailScreen(
                drillId = drillId,
                onStartDrill = { navController.navigate("train_permissions") },
                onBack = { navController.popBackStack() },
            )
        }

        composable("train_permissions") {
            onShowTabBar()
            PermissionScreen(
                onAllGranted = {
                    navController.navigate("train_live_run") {
                        popUpTo("train_permissions") { inclusive = true }
                    }
                },
            )
        }

        composable("train_live_run") {
            onHideTabBar()
            val vm: LiveRunViewModel = viewModel()
            LiveRunScreen(
                viewModel = vm,
                onRunStopped = { runId ->
                    navController.navigate("train_analysis/$runId") {
                        popUpTo("train_home")
                    }
                },
            )
            DisposableEffect(Unit) {
                onDispose { onShowTabBar() }
            }
        }

        composable(
            route = "train_analysis/{runId}",
            arguments = listOf(navArgument("runId") { type = NavType.LongType }),
        ) { entry ->
            onShowTabBar()
            val runId = entry.arguments?.getLong("runId") ?: return@composable
            val vm: PostAnalysisViewModel = viewModel()
            LaunchedEffect(runId) { vm.loadRun(runId) }
            PostAnalysisScreen(
                viewModel = vm,
                onBack = {
                    navController.navigate("train_home") {
                        popUpTo("train_home") { inclusive = true }
                    }
                },
            )
        }
    }
}

@Composable
private fun TrainHome(onDrillClick: (String) -> Unit) {
    var selectedLevel by rememberSaveable { mutableStateOf<SkillLevel?>(null) }

    val drills = if (selectedLevel != null) {
        SkillProgressionData.getDrillsByLevel(selectedLevel!!)
    } else {
        SkillProgressionData.drills
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 24.dp, end = 24.dp, top = 24.dp),
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Training Drills",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "${drills.size} drills available",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Level filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LevelChip("All", selectedLevel == null, Color.White) { selectedLevel = null }
            LevelChip("Beginner", selectedLevel == SkillLevel.BEGINNER, AccentGreen) { selectedLevel = SkillLevel.BEGINNER }
            LevelChip("Inter", selectedLevel == SkillLevel.INTERMEDIATE, SkyBlue) { selectedLevel = SkillLevel.INTERMEDIATE }
            LevelChip("Advanced", selectedLevel == SkillLevel.ADVANCED, AccentOrange) { selectedLevel = SkillLevel.ADVANCED }
            LevelChip("Expert", selectedLevel == SkillLevel.EXPERT, AccentYellow) { selectedLevel = SkillLevel.EXPERT }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(drills, key = { it.id }) { drill ->
                DrillCard(drill = drill, onClick = { onDrillClick(drill.id) })
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun LevelChip(label: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = color.copy(alpha = 0.2f),
            selectedLabelColor = color,
        ),
    )
}

@Composable
private fun DrillCard(drill: SkillDrill, onClick: () -> Unit) {
    val color = when (drill.level) {
        SkillLevel.BEGINNER -> AccentGreen
        SkillLevel.INTERMEDIATE -> SkyBlue
        SkillLevel.ADVANCED -> AccentOrange
        SkillLevel.EXPERT -> AccentYellow
    }
    val catLabel = when (drill.category) {
        SkillCategory.BALANCE -> "BAL"
        SkillCategory.EDGING -> "EDG"
        SkillCategory.ROTARY -> "ROT"
        SkillCategory.PRESSURE -> "PRS"
        SkillCategory.MIXED -> "MIX"
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
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.12f))
                    .border(1.5.dp, color, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(catLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(drill.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "${drill.level.displayName} · IQ ${drill.reference.skiIQMin}–${drill.reference.skiIQMax}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.Filled.PlayArrow, null, tint = color, modifier = Modifier.size(24.dp))
        }
    }
}
