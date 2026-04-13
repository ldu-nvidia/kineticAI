package com.kineticai.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kineticai.app.sensor.ImuSample
import com.kineticai.app.ui.theme.AccentGreen
import com.kineticai.app.ui.theme.AccentOrange
import com.kineticai.app.ui.theme.AccentRed
import com.kineticai.app.ui.theme.SkyBlue
import com.kineticai.app.ui.theme.TextMuted
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

@Composable
fun BootSensorPanel(
    bleStatus: String,
    leftImu: ImuSample?,
    rightImu: ImuSample?,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Boot Sensors (BLE)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = bleStatus,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = if (leftImu != null || rightImu != null) AccentGreen else TextMuted,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BootColumn(
                    label = "LEFT",
                    imu = leftImu,
                    color = SkyBlue,
                    modifier = Modifier.weight(1f),
                )
                BootColumn(
                    label = "RIGHT",
                    imu = rightImu,
                    color = AccentOrange,
                    modifier = Modifier.weight(1f),
                )
            }

            // Edge similarity (if both connected)
            if (leftImu != null && rightImu != null) {
                val leftRoll = edgeAngle(leftImu)
                val rightRoll = edgeAngle(rightImu)
                val diff = abs(leftRoll - rightRoll)
                val similarity = ((1f - diff / maxOf(leftRoll, rightRoll, 1f)) * 100).coerceIn(0f, 100f)

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Edge Similarity",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${similarity.toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (similarity > 70) AccentGreen else AccentOrange,
                    )
                }
            }
        }
    }
}

@Composable
private fun BootColumn(
    label: String,
    imu: ImuSample?,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (imu != null) AccentGreen else AccentRed),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = color)
        }

        if (imu != null) {
            val roll = edgeAngle(imu)
            Text(
                text = "${String.format("%.1f", roll)}°",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = color,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text("edge angle", style = MaterialTheme.typography.labelSmall, color = TextMuted)

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "${String.format("%.1f", imu.gForce)}G",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text("g-force", style = MaterialTheme.typography.labelSmall, color = TextMuted)
        } else {
            Text(
                text = "---",
                fontSize = 24.sp,
                color = TextMuted,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text("not connected", style = MaterialTheme.typography.labelSmall, color = TextMuted)
        }
    }
}

private fun edgeAngle(imu: ImuSample): Float {
    val roll = atan2(imu.accelX, sqrt(imu.accelY * imu.accelY + imu.accelZ * imu.accelZ))
    return abs(Math.toDegrees(roll.toDouble()).toFloat())
}
