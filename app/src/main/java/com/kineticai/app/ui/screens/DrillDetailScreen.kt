package com.kineticai.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kineticai.app.analysis.SkillDrill
import com.kineticai.app.analysis.SkillProgressionData
import com.kineticai.app.ui.theme.AccentGreen
import com.kineticai.app.ui.theme.AccentOrange
import com.kineticai.app.ui.theme.AccentYellow
import com.kineticai.app.ui.theme.SkyBlue

@Composable
fun DrillDetailScreen(
    drillId: String,
    onStartDrill: () -> Unit,
    onBack: () -> Unit,
) {
    val drill = try { SkillProgressionData.getDrill(drillId) } catch (_: Exception) { return }

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
            Column(modifier = Modifier.padding(start = 8.dp)) {
                Text(
                    text = drill.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${drill.level.displayName} · ${drill.category.name} · ${drill.source}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        SectionCard("What is it?", drill.description)
        Spacer(modifier = Modifier.height(12.dp))

        SectionCard("How to do it", drill.howTo)
        Spacer(modifier = Modifier.height(12.dp))

        SectionCard("Terrain", drill.terrain)
        Spacer(modifier = Modifier.height(12.dp))

        // Reference metrics
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Target Metrics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Hit these numbers to pass", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(12.dp))
                val ref = drill.reference
                RefRow("Edge Angle", "${ref.edgeAngleMin.toInt()}° – ${ref.edgeAngleMax.toInt()}°", SkyBlue)
                if (ref.earlyEdgingMin > 0) RefRow("Early Edging", "≥ ${ref.earlyEdgingMin.toInt()}", AccentGreen)
                if (ref.progressiveEdgeMin > 0) RefRow("Progressive Edge", "≥ ${ref.progressiveEdgeMin.toInt()}", AccentGreen)
                if (ref.turnShapeMin > 0) RefRow("Turn Shape", "≥ ${ref.turnShapeMin.toInt()}", AccentOrange)
                RefRow("G-Force", "${String.format("%.1f", ref.gForceMin)} – ${String.format("%.1f", ref.gForceMax)} G", AccentOrange)
                RefRow("Speed", "${ref.speedMinKmh.toInt()} – ${ref.speedMaxKmh.toInt()} km/h", AccentYellow)
                if (ref.weightReleaseMin > 0) RefRow("Weight Release", "≥ ${ref.weightReleaseMin.toInt()}", SkyBlue)
                if (ref.turnSymmetryMin > 0) RefRow("Turn Symmetry", "≥ ${(ref.turnSymmetryMin * 100).toInt()}%", AccentGreen)
                RefRow("Kinetic Score Range", "${ref.skiIQMin} – ${ref.skiIQMax}", SkyBlue)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        SectionCard("Pass Criteria", drill.passCriteria)

        Spacer(modifier = Modifier.height(12.dp))

        // Tips
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Pro Tips", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                drill.tips.forEach { tip ->
                    Row(modifier = Modifier.padding(vertical = 3.dp)) {
                        Icon(Icons.Filled.Star, null, tint = AccentYellow, modifier = Modifier.size(14.dp).padding(top = 3.dp))
                        Text(tip, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onStartDrill,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SkyBlue),
        ) {
            Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(24.dp))
            Text("Start This Drill", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun SectionCard(title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(6.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun RefRow(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace, color = color)
    }
}
