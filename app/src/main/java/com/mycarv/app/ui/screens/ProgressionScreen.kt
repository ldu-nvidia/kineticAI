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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mycarv.app.analysis.SkillCategory
import com.mycarv.app.analysis.SkillDrill
import com.mycarv.app.analysis.SkillLevel
import com.mycarv.app.analysis.SkillProgressionData
import com.mycarv.app.ui.theme.AccentGreen
import com.mycarv.app.ui.theme.AccentOrange
import com.mycarv.app.ui.theme.AccentYellow
import com.mycarv.app.ui.theme.SkyBlue

@Composable
fun ProgressionScreen(
    filterLevel: SkillLevel? = null,
    onDrillClick: (String) -> Unit,
    onBack: () -> Unit,
) {
    val levels = if (filterLevel != null) listOf(filterLevel) else SkillLevel.entries.toList()
    val totalDrills = levels.sumOf { SkillProgressionData.getDrillsByLevel(it).size }
    val title = filterLevel?.displayName ?: "Skill Progression"
    val subtitle = if (filterLevel != null) {
        "$totalDrills drills · ${filterLevel.displayName} level"
    } else {
        "$totalDrills drills · Beginner to Expert"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Column(modifier = Modifier.padding(start = 8.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            levels.forEach { level ->
                val drills = SkillProgressionData.getDrillsByLevel(level)
                if (filterLevel == null) {
                    item { LevelHeader(level) }
                }
                items(drills, key = { it.id }) { drill ->
                    DrillCard(drill = drill, onClick = { onDrillClick(drill.id) })
                }
                item { Spacer(modifier = Modifier.height(8.dp)) }
            }
        }
    }
}

@Composable
private fun LevelHeader(level: SkillLevel) {
    val color = levelColor(level)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .height(3.dp)
                .weight(1f)
                .background(color.copy(alpha = 0.3f)),
        )
        Text(
            text = "  ${level.displayName.uppercase()}  ",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = color,
        )
        Box(
            modifier = Modifier
                .height(3.dp)
                .weight(1f)
                .background(color.copy(alpha = 0.3f)),
        )
    }
}

@Composable
private fun DrillCard(drill: SkillDrill, onClick: () -> Unit) {
    val color = levelColor(drill.level)
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
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
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
                Text(
                    text = drill.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "IQ ${drill.reference.skiIQMin}–${drill.reference.skiIQMax} · ${drill.terrain.substringBefore(" —")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = "View drill",
                tint = color,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

private fun levelColor(level: SkillLevel): Color = when (level) {
    SkillLevel.BEGINNER -> AccentGreen
    SkillLevel.INTERMEDIATE -> SkyBlue
    SkillLevel.ADVANCED -> AccentOrange
    SkillLevel.EXPERT -> AccentYellow
}
