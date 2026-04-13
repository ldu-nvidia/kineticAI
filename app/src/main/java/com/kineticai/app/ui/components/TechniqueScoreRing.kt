package com.kineticai.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kineticai.app.ui.theme.AccentGreen
import com.kineticai.app.ui.theme.AccentOrange
import com.kineticai.app.ui.theme.AccentRed
import com.kineticai.app.ui.theme.AccentYellow
import com.kineticai.app.ui.theme.SkyBlue
import com.kineticai.app.ui.theme.TextMuted

/**
 * Displays the Kinetic Score score (0–200) in an animated ring.
 */
@Composable
fun KineticScoreRing(
    skiIQ: Int,
    modifier: Modifier = Modifier,
) {
    val fraction by animateFloatAsState(
        targetValue = skiIQ / 200f,
        animationSpec = tween(800),
        label = "skiiq",
    )

    val ringColor = when {
        skiIQ >= 140 -> AccentGreen
        skiIQ >= 115 -> SkyBlue
        skiIQ >= 90 -> AccentYellow
        else -> AccentOrange
    }

    val label = when {
        skiIQ >= 140 -> "EXPERT"
        skiIQ >= 115 -> "ADVANCED"
        skiIQ >= 90 -> "INTERMEDIATE"
        else -> "DEVELOPING"
    }

    Box(modifier = modifier.size(140.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(140.dp)) {
            val stroke = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
            val arcSize = Size(size.width - stroke.width, size.height - stroke.width)
            val topLeft = Offset(stroke.width / 2, stroke.width / 2)

            drawArc(
                color = TextMuted.copy(alpha = 0.15f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke,
            )
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = 360f * fraction,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke,
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$skiIQ",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = ringColor,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
@Deprecated("Use KineticScoreRing instead", replaceWith = ReplaceWith("KineticScoreRing(score, modifier)"))
fun TechniqueScoreRing(
    score: Int,
    modifier: Modifier = Modifier,
) {
    KineticScoreRing(skiIQ = score, modifier = modifier)
}
