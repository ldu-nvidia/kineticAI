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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.kineticai.app.ui.theme.AccentGreen
import com.kineticai.app.ui.theme.AccentOrange
import com.kineticai.app.ui.theme.SkyBlue

@Composable
fun TurnBalanceBar(
    leftTurns: Int,
    rightTurns: Int,
    modifier: Modifier = Modifier,
) {
    val total = (leftTurns + rightTurns).coerceAtLeast(1)
    val leftFraction by animateFloatAsState(
        targetValue = leftTurns.toFloat() / total,
        animationSpec = tween(500),
        label = "leftFraction",
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Turn Balance",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("L: $leftTurns", style = MaterialTheme.typography.labelLarge, color = SkyBlue)
                Text("R: $rightTurns", style = MaterialTheme.typography.labelLarge, color = AccentOrange)
            }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .padding(top = 8.dp),
            ) {
                val barHeight = size.height
                val radius = CornerRadius(barHeight / 2, barHeight / 2)

                drawRoundRect(
                    color = AccentOrange,
                    size = Size(size.width, barHeight),
                    cornerRadius = radius,
                )

                if (leftFraction > 0f) {
                    drawRoundRect(
                        color = SkyBlue,
                        size = Size(size.width * leftFraction, barHeight),
                        cornerRadius = radius,
                    )
                }

                val symmetry = 1f - kotlin.math.abs(leftFraction - 0.5f) * 2f
                if (symmetry > 0.7f) {
                    drawCircle(
                        color = AccentGreen,
                        radius = 6.dp.toPx(),
                        center = Offset(size.width / 2, barHeight / 2),
                    )
                }
            }
        }
    }
}
