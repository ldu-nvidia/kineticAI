package com.mycarv.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mycarv.app.sensor.CarveQuality
import com.mycarv.app.sensor.MicData
import com.mycarv.app.sensor.SnowType
import com.mycarv.app.ui.theme.AccentGreen
import com.mycarv.app.ui.theme.AccentOrange
import com.mycarv.app.ui.theme.AccentRed
import com.mycarv.app.ui.theme.AccentYellow
import com.mycarv.app.ui.theme.SkyBlue
import com.mycarv.app.ui.theme.TextMuted

/**
 * Displays microphone-derived data: snow surface type, carve quality,
 * speed proxy, fall/binding events, ambient level.
 */
@Composable
fun SurfacePanel(
    leftMic: MicData?,
    rightMic: MicData?,
    modifier: Modifier = Modifier,
) {
    val mic = leftMic ?: rightMic
    if (mic == null) return

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Surface & Audio Analysis",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Snow type
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Snow Surface", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                Text(
                    mic.snowType.label,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = snowColor(mic.snowType),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Carve quality
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Carve Quality", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                Text(
                    mic.carveQuality.label,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = carveColor(mic.carveQuality),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Speed proxy bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Audio Speed", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                Text(
                    "${mic.speedProxy}/255",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (mic.speedProxy > 180) AccentOrange else SkyBlue,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Ambient level
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Ambient Noise", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
                Text(
                    "${mic.ambientLevel}/255",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted,
                )
            }

            // Events
            if (mic.fallDetected) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "FALL DETECTED!",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentRed,
                )
            }

            if (mic.bindingClick) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Binding click detected",
                    style = MaterialTheme.typography.bodySmall,
                    color = AccentGreen,
                )
            }
        }
    }
}

private fun snowColor(type: SnowType): Color = when (type) {
    SnowType.POWDER -> Color.White
    SnowType.GROOMED -> AccentGreen
    SnowType.ICE -> SkyBlue
    SnowType.SLUSH -> AccentOrange
    SnowType.CRUD -> AccentRed
    SnowType.UNKNOWN -> TextMuted
}

private fun carveColor(quality: CarveQuality): Color = when (quality) {
    CarveQuality.CLEAN -> AccentGreen
    CarveQuality.MODERATE -> AccentYellow
    CarveQuality.SKIDDING -> AccentOrange
    CarveQuality.CHATTER -> AccentRed
    CarveQuality.UNKNOWN -> TextMuted
}
