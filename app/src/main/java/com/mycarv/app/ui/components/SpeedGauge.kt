package com.mycarv.app.ui.components

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
import com.mycarv.app.ui.theme.AccentOrange
import com.mycarv.app.ui.theme.SkyBlue
import com.mycarv.app.ui.theme.TextMuted

@Composable
fun SpeedGauge(
    speedKmh: Float,
    maxSpeed: Float = 120f,
    modifier: Modifier = Modifier,
) {
    val fraction by animateFloatAsState(
        targetValue = (speedKmh / maxSpeed).coerceIn(0f, 1f),
        animationSpec = tween(300),
        label = "speed",
    )
    val sweepAngle = 240f
    val startAngle = 150f

    val arcColor = if (speedKmh > 80f) AccentOrange else SkyBlue
    val trackColor = TextMuted.copy(alpha = 0.2f)

    Box(modifier = modifier.size(200.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(200.dp)) {
            val stroke = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)
            val arcSize = Size(size.width - stroke.width, size.height - stroke.width)
            val topLeft = Offset(stroke.width / 2, stroke.width / 2)

            drawArc(
                color = trackColor,
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke,
            )
            drawArc(
                color = arcColor,
                startAngle = startAngle,
                sweepAngle = sweepAngle * fraction,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke,
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = String.format("%.0f", speedKmh),
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "km/h",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
