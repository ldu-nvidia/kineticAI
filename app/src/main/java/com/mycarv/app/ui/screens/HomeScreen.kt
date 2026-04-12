package com.mycarv.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.DownhillSkiing
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mycarv.app.analysis.SkillLevel
import com.mycarv.app.analysis.SkillProgressionData
import com.mycarv.app.ui.theme.AccentGreen
import com.mycarv.app.ui.theme.AccentOrange
import com.mycarv.app.ui.theme.AccentYellow
import com.mycarv.app.ui.theme.DeepBlue
import com.mycarv.app.ui.theme.SkyBlue

@Composable
fun HomeScreen(
    onFreeRun: () -> Unit,
    onTrainingLevel: (SkillLevel) -> Unit,
    onSeasonStats: () -> Unit,
    onRunHistory: () -> Unit,
    onCustomize: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(40.dp))

        Icon(
            Icons.Filled.DownhillSkiing,
            contentDescription = null,
            tint = SkyBlue,
            modifier = Modifier.size(56.dp),
        )

        Text(
            text = "MyCarv",
            fontSize = 34.sp,
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

        // ── FREE RUN ──
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .clickable(onClick = onFreeRun),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(listOf(SkyBlue, DeepBlue)),
                        shape = RoundedCornerShape(20.dp),
                    )
                    .padding(20.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "FREE RUN",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Full sensor + GPS recording\nReconstruct your entire run dynamics",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f),
                            lineHeight = 16.sp,
                        )
                    }
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(40.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.School,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = "  TRAINING RUNS",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp,
            )
        }

        Text(
            text = "Pick a level. Practice drills. Match the reference metrics.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 12.dp),
        )

        // ── 2×2 TRAINING LEVEL GRID ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TrainingLevelCard(
                level = SkillLevel.BEGINNER,
                color = AccentGreen,
                drillCount = SkillProgressionData.getDrillsByLevel(SkillLevel.BEGINNER).size,
                modifier = Modifier.weight(1f),
                onClick = { onTrainingLevel(SkillLevel.BEGINNER) },
            )
            TrainingLevelCard(
                level = SkillLevel.INTERMEDIATE,
                color = SkyBlue,
                drillCount = SkillProgressionData.getDrillsByLevel(SkillLevel.INTERMEDIATE).size,
                modifier = Modifier.weight(1f),
                onClick = { onTrainingLevel(SkillLevel.INTERMEDIATE) },
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TrainingLevelCard(
                level = SkillLevel.ADVANCED,
                color = AccentOrange,
                drillCount = SkillProgressionData.getDrillsByLevel(SkillLevel.ADVANCED).size,
                modifier = Modifier.weight(1f),
                onClick = { onTrainingLevel(SkillLevel.ADVANCED) },
            )
            TrainingLevelCard(
                level = SkillLevel.EXPERT,
                color = AccentYellow,
                drillCount = SkillProgressionData.getDrillsByLevel(SkillLevel.EXPERT).size,
                modifier = Modifier.weight(1f),
                onClick = { onTrainingLevel(SkillLevel.EXPERT) },
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // ── STATS, HISTORY & CUSTOMIZE ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            TextButton(onClick = onSeasonStats) {
                Icon(Icons.Filled.BarChart, null, modifier = Modifier.size(18.dp).padding(end = 4.dp))
                Text("Stats")
            }
            TextButton(onClick = onRunHistory) {
                Icon(Icons.Filled.History, null, modifier = Modifier.size(18.dp).padding(end = 4.dp))
                Text("History")
            }
            TextButton(onClick = onCustomize) {
                Icon(Icons.Filled.Brush, null, modifier = Modifier.size(18.dp).padding(end = 4.dp))
                Text("Customize")
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun TrainingLevelCard(
    level: SkillLevel,
    color: Color,
    drillCount: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val skiIQRange = when (level) {
        SkillLevel.BEGINNER -> "IQ 0–70"
        SkillLevel.INTERMEDIATE -> "IQ 50–110"
        SkillLevel.ADVANCED -> "IQ 85–140"
        SkillLevel.EXPERT -> "IQ 115–200"
    }

    Card(
        modifier = modifier
            .height(100.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.5.dp, color.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.08f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = level.displayName.uppercase(),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = color,
            )
            Column {
                Text(
                    text = "$drillCount drills",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = skiIQRange,
                    style = MaterialTheme.typography.labelSmall,
                    color = color.copy(alpha = 0.7f),
                )
            }
        }
    }
}
