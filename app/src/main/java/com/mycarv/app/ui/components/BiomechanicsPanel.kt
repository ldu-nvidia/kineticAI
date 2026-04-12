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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mycarv.app.sensor.DualImuData
import com.mycarv.app.sensor.TriSegmentData
import com.mycarv.app.ui.theme.AccentGreen
import com.mycarv.app.ui.theme.AccentOrange
import com.mycarv.app.ui.theme.AccentRed
import com.mycarv.app.ui.theme.AccentYellow
import com.mycarv.app.ui.theme.SkyBlue
import com.mycarv.app.ui.theme.TextMuted

@Composable
fun BiomechanicsPanel(
    leftDual: DualImuData?,
    rightDual: DualImuData?,
    leftTri: TriSegmentData? = null,
    rightTri: TriSegmentData? = null,
    modifier: Modifier = Modifier,
) {
    val dual = leftDual ?: rightDual
    val tri = leftTri ?: rightTri
    if (dual == null || (!dual.lsm9ds1Present && !dual.vl6180xPresent)) return

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "Boot Biomechanics",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                val sensors = buildList {
                    if (dual.lsm9ds1Present) add("9-axis")
                    if (dual.vl6180xPresent) add("ToF")
                }.joinToString(" + ")
                Text(sensors, style = MaterialTheme.typography.labelSmall, color = AccentGreen)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Boot flex
            MetricRow(
                "Boot Flex",
                String.format("%+.0f°", dual.bootFlexAngle),
                if (dual.bootFlexAngle > 5) AccentGreen     // Forward lean = good
                else if (dual.bootFlexAngle > -5) SkyBlue    // Neutral
                else AccentRed,                               // Sitting back = bad
                if (dual.bootFlexAngle > 5) "Forward lean — good!"
                else if (dual.bootFlexAngle > -5) "Centered"
                else "Sitting back — lean forward!",
            )

            // Angulation
            MetricRow(
                "Angulation",
                String.format("%.0f°", dual.angulationAngle),
                if (dual.angulationAngle > 10) AccentGreen else SkyBlue,
                if (dual.angulationAngle > 15) "Strong ankle angulation"
                else if (dual.angulationAngle > 5) "Moderate"
                else "Low — work on ankle tilt",
            )

            // Ankle steering
            MetricRow(
                "Ankle Steering",
                String.format("%+.0f°", dual.ankleSteeringAngle),
                AccentOrange,
                "Foot rotation independent of leg",
            )

            // Vibration damping
            MetricRow(
                "Vibration Damping",
                String.format("%.0f%%", dual.vibrationDamping),
                if (dual.vibrationDamping > 60) AccentGreen else AccentYellow,
                "How much boot absorbs ski vibration",
            )

            // Absorption
            MetricRow(
                "Absorption",
                String.format("%.0f/100", dual.absorptionScore),
                if (dual.absorptionScore > 60) AccentGreen
                else if (dual.absorptionScore > 30) AccentYellow
                else AccentOrange,
                "Independent cuff-shell movement over bumps",
            )

            // Heading
            MetricRow(
                "Heading",
                String.format("%.0f°", dual.heading),
                SkyBlue,
                "Compass direction (magnetometer)",
            )

            // Airborne detection
            if (dual.vl6180xPresent) {
                MetricRow(
                    "Distance to Snow",
                    "${dual.distanceMm} mm",
                    if (dual.airborne) AccentYellow else TextMuted,
                    if (dual.airborne) "AIRBORNE! Jump detected!" else "On snow",
                )
            }

            // ── 3-Segment Metrics (if BNO055 present) ──
            if (tri != null && tri.bnoPresent) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("3-Segment Kinematics", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = AccentGreen)
                Spacer(modifier = Modifier.height(4.dp))

                MetricRow(
                    "Knee Flexion",
                    String.format("%.0f°", tri.kneeFlexion),
                    when {
                        tri.kneeFlexion > 40 -> AccentYellow  // Deep squat
                        tri.kneeFlexion > 15 -> AccentGreen   // Good athletic stance
                        else -> SkyBlue                        // Straight
                    },
                    when {
                        tri.kneeFlexion > 50 -> "Very deep — watch balance"
                        tri.kneeFlexion > 25 -> "Good athletic stance"
                        tri.kneeFlexion > 10 -> "Light flex"
                        else -> "Legs straight"
                    },
                )

                MetricRow(
                    "Knee Valgus",
                    String.format("%+.0f°", tri.kneeValgus),
                    if (kotlin.math.abs(tri.kneeValgus) > 10) AccentRed else AccentGreen,
                    if (kotlin.math.abs(tri.kneeValgus) > 10) "Knee collapsing — ACL risk!" else "Knee tracking well",
                )

                MetricRow(
                    "ACL Risk",
                    String.format("%.0f/100", tri.aclRiskScore),
                    when {
                        tri.aclRiskScore > 60 -> AccentRed
                        tri.aclRiskScore > 30 -> AccentOrange
                        else -> AccentGreen
                    },
                    when {
                        tri.aclRiskScore > 60 -> "HIGH RISK — correct knee alignment!"
                        tri.aclRiskScore > 30 -> "Moderate — watch your knee"
                        else -> "Low risk — good form"
                    },
                )

                MetricRow(
                    "Kinetic Chain",
                    tri.chainOrder.label,
                    when (tri.chainOrder) {
                        com.mycarv.app.sensor.ChainOrder.BOTTOM_UP -> AccentGreen
                        com.mycarv.app.sensor.ChainOrder.TOP_DOWN -> AccentOrange
                        com.mycarv.app.sensor.ChainOrder.SIMULTANEOUS -> AccentYellow
                    },
                    when (tri.chainOrder) {
                        com.mycarv.app.sensor.ChainOrder.BOTTOM_UP -> "Expert: feet initiate movement"
                        com.mycarv.app.sensor.ChainOrder.TOP_DOWN -> "Upper body leading — use your feet"
                        com.mycarv.app.sensor.ChainOrder.SIMULTANEOUS -> "Rigid — develop independent segments"
                    },
                )

                MetricRow(
                    "Separation",
                    String.format("%.0f/100", tri.separationScore),
                    if (tri.separationScore > 50) AccentGreen else AccentOrange,
                    "How independently your leg segments move",
                )

                MetricRow(
                    "Rebound",
                    String.format("%.0f°/s", tri.reboundSpeed),
                    if (tri.reboundSpeed > 200) AccentGreen else SkyBlue,
                    "Knee extension speed at transition",
                )
            }

            // ACL warning (from either dual or tri)
            if (dual.kneeStressWarning || (tri?.aclWarning == true)) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "ACL WARNING — correct knee alignment immediately!",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentRed,
                )
            }
        }
    }
}

@Composable
private fun MetricRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color,
    description: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(0.6f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(description, style = MaterialTheme.typography.labelSmall, color = TextMuted)
        }
        Text(
            value,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = valueColor,
        )
    }
}
