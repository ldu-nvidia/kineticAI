package com.kineticai.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kineticai.app.ui.theme.AccentGreen
import com.kineticai.app.ui.theme.AccentOrange
import com.kineticai.app.ui.theme.AccentYellow
import com.kineticai.app.ui.theme.SkyBlue
import com.kineticai.app.ui.theme.TextMuted

@Composable
fun SkillCategoryBar(
    label: String,
    score: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val fraction by animateFloatAsState(
        targetValue = (score / 100f).coerceIn(0f, 1f),
        animationSpec = tween(600),
        label = "skill",
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "${score.toInt()}",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = color,
        )
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .padding(top = 4.dp),
    ) {
        val radius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
        drawRoundRect(
            color = TextMuted.copy(alpha = 0.15f),
            size = size,
            cornerRadius = radius,
        )
        drawRoundRect(
            color = color,
            size = Size(size.width * fraction, size.height),
            cornerRadius = radius,
        )
    }
}

@Composable
fun SkillBreakdownCard(
    balanceScore: Float,
    edgingScore: Float,
    rotaryScore: Float,
    pressureScore: Float,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Technique Breakdown",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            SkillCategoryBar("Balance", balanceScore, SkyBlue)
            SkillCategoryBar("Edging", edgingScore, AccentGreen)
            SkillCategoryBar("Rotary", rotaryScore, AccentOrange)
            SkillCategoryBar("Pressure", pressureScore, AccentYellow)
        }
    }
}
