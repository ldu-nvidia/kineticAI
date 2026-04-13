package com.kineticai.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kineticai.app.sensor.ProximityData
import com.kineticai.app.sensor.ProximityLevel
import com.kineticai.app.ui.theme.AccentOrange
import com.kineticai.app.ui.theme.AccentRed
import com.kineticai.app.ui.theme.AccentYellow
import com.kineticai.app.ui.theme.DarkBase

@Composable
fun ProximityAlert(
    proximity: ProximityData?,
    modifier: Modifier = Modifier,
) {
    val visible = proximity != null && proximity.level != ProximityLevel.CLEAR

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(500)),
        modifier = modifier,
    ) {
        if (proximity == null) return@AnimatedVisibility

        val bgColor = when (proximity.level) {
            ProximityLevel.DANGER -> AccentRed
            ProximityLevel.WARNING -> AccentOrange
            ProximityLevel.CAUTION -> AccentYellow
            else -> Color.Transparent
        }

        val pulseAlpha = if (proximity.level == ProximityLevel.DANGER) {
            val transition = rememberInfiniteTransition(label = "pulse")
            val alpha by transition.animateFloat(
                initialValue = 1f,
                targetValue = 0.4f,
                animationSpec = infiniteRepeatable(tween(300), RepeatMode.Reverse),
                label = "pulseAlpha",
            )
            alpha
        } else 1f

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(pulseAlpha)
                .background(bgColor, RoundedCornerShape(12.dp))
                .padding(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = null,
                    tint = DarkBase,
                )
                Column {
                    Text(
                        text = proximity.level.label.uppercase(),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = DarkBase,
                    )
                    Text(
                        text = "Object ${String.format("%.1f", proximity.distanceM)}m behind, " +
                            "closing at ${String.format("%.0f", proximity.closingSpeedKmh)} km/h",
                        fontSize = 12.sp,
                        color = DarkBase.copy(alpha = 0.8f),
                    )
                }
            }
        }
    }
}
