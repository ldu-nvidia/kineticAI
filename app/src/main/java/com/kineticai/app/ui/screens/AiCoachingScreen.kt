package com.kineticai.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kineticai.app.network.AiCoachService
import com.kineticai.app.ui.theme.AccentGreen
import com.kineticai.app.ui.theme.AccentOrange
import com.kineticai.app.ui.theme.SkyBlue

/**
 * Post-session AI coaching screen.
 * Displays rich, personalized coaching insights from the cloud LLM
 * after a session is completed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiCoachingScreen(
    insight: AiCoachService.AiInsight?,
    isLoading: Boolean,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AutoAwesome, null, tint = SkyBlue, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("AI Coach Analysis")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            if (isLoading) {
                Spacer(Modifier.height(60.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(color = SkyBlue, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Your AI coach is analyzing your session...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Reviewing 34 metrics across all sensor data",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (insight == null) {
                Spacer(Modifier.height(40.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.AutoAwesome, null, tint = SkyBlue, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("AI Coach Not Available", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Set your API key in Settings → API Keys to enable AI coaching analysis. " +
                                "KineticAI supports OpenAI (GPT-4o-mini), Anthropic (Claude), or any compatible endpoint.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                AnimatedVisibility(visible = true, enter = fadeIn()) {
                    Column {
                        // Summary
                        InsightCard(
                            icon = Icons.Filled.AutoAwesome,
                            title = "Session Analysis",
                            iconTint = SkyBlue,
                        ) {
                            Text(
                                insight.summary,
                                style = MaterialTheme.typography.bodyMedium,
                                lineHeight = 22.sp,
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        // Strengths
                        if (insight.strengths.isNotEmpty()) {
                            InsightCard(
                                icon = Icons.Filled.Star,
                                title = "What You Did Well",
                                iconTint = AccentGreen,
                            ) {
                                insight.strengths.forEach { strength ->
                                    Row(modifier = Modifier.padding(vertical = 3.dp)) {
                                        Icon(
                                            Icons.Filled.CheckCircle, null,
                                            tint = AccentGreen,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(strength, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // Improvements
                        if (insight.improvements.isNotEmpty()) {
                            InsightCard(
                                icon = Icons.Filled.TrendingUp,
                                title = "Focus Areas",
                                iconTint = AccentOrange,
                            ) {
                                insight.improvements.forEachIndexed { i, improvement ->
                                    Row(modifier = Modifier.padding(vertical = 3.dp)) {
                                        Text(
                                            "${i + 1}.",
                                            fontWeight = FontWeight.Bold,
                                            color = AccentOrange,
                                            modifier = Modifier.width(20.dp),
                                        )
                                        Text(improvement, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // Next Drill
                        if (insight.nextDrill.isNotEmpty()) {
                            InsightCard(
                                icon = Icons.Filled.FitnessCenter,
                                title = "Recommended Next Drill",
                                iconTint = SkyBlue,
                            ) {
                                Text(
                                    insight.nextDrill,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // Motivation
                        if (insight.motivation.isNotEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = SkyBlue.copy(alpha = 0.1f)),
                            ) {
                                Text(
                                    "\"${insight.motivation}\"",
                                    modifier = Modifier.padding(20.dp),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontStyle = FontStyle.Italic,
                                    color = SkyBlue,
                                    lineHeight = 24.sp,
                                )
                            }
                        }

                        Spacer(Modifier.height(32.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun InsightCard(
    icon: ImageVector,
    title: String,
    iconTint: androidx.compose.ui.graphics.Color,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = iconTint, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}
