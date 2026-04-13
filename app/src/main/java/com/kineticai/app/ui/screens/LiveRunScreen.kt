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
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kineticai.app.ui.components.BiomechanicsPanel
import com.kineticai.app.ui.components.BootSensorPanel
import com.kineticai.app.ui.components.CoachingBanner
import com.kineticai.app.ui.components.ProximityAlert
import com.kineticai.app.ui.components.SurfacePanel
import com.kineticai.app.ui.components.ThermalPanel
import com.kineticai.app.ui.components.GpsPanel
import com.kineticai.app.ui.components.MetricCard
import com.kineticai.app.ui.components.RawSensorPanel
import com.kineticai.app.ui.components.KineticScoreRing
import com.kineticai.app.ui.components.SkillBreakdownCard
import com.kineticai.app.ui.components.SpeedGauge
import com.kineticai.app.ui.components.TrajectoryView
import com.kineticai.app.ui.components.TurnBalanceBar
import com.kineticai.app.ui.theme.AccentGreen
import com.kineticai.app.ui.theme.AccentOrange
import com.kineticai.app.ui.theme.AccentRed
import com.kineticai.app.ui.theme.SkyBlue
import com.kineticai.app.viewmodel.LiveRunViewModel

@Composable
fun LiveRunScreen(
    viewModel: LiveRunViewModel,
    onRunStopped: (Long) -> Unit,
) {
    val metrics by viewModel.metrics.collectAsState()
    val tip by viewModel.latestTip.collectAsState()
    val tipVisible by viewModel.tipVisible.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val rawImu by viewModel.rawImu.collectAsState()
    val location by viewModel.location.collectAsState()
    val trajectory by viewModel.trajectory.collectAsState()
    val leftBootImu by viewModel.leftBootImu.collectAsState()
    val rightBootImu by viewModel.rightBootImu.collectAsState()
    val bleStatus by viewModel.bleStatus.collectAsState()
    val leftMic by viewModel.leftMic.collectAsState()
    val rightMic by viewModel.rightMic.collectAsState()
    val proximity by viewModel.proximity.collectAsState()
    val leftDualImu by viewModel.leftDualImu.collectAsState()
    val rightDualImu by viewModel.rightDualImu.collectAsState()
    val leftTriSeg by viewModel.leftTriSegment.collectAsState()
    val rightTriSeg by viewModel.rightTriSegment.collectAsState()
    val thermalVision by viewModel.thermal.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Proximity warning (highest priority — shown at top)
        ProximityAlert(
            proximity = proximity,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Coaching banner
        CoachingBanner(
            tip = tip,
            visible = tipVisible,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Duration + Kinetic Score
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = metrics.runDurationFormatted,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "Duration",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            KineticScoreRing(skiIQ = metrics.skiIQ, modifier = Modifier.size(100.dp))
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Speed gauge
        SpeedGauge(speedKmh = metrics.currentSpeedKmh)

        Spacer(modifier = Modifier.height(24.dp))

        // Key live metrics
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MetricCard(
                label = "G-Force",
                value = String.format("%.2f", metrics.currentGForce),
                unit = "G",
                accentColor = if (metrics.currentGForce > 2f) AccentOrange else AccentGreen,
                modifier = Modifier.weight(1f),
            )
            MetricCard(
                label = "Edge Angle",
                value = String.format("%.1f", metrics.currentEdgeAngle),
                unit = "°",
                accentColor = SkyBlue,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MetricCard(
                label = "Altitude Drop",
                value = String.format("%.0f", metrics.altitudeDrop),
                unit = "m",
                modifier = Modifier.weight(1f),
            )
            MetricCard(
                label = "Turns",
                value = "${metrics.turnCount}",
                accentColor = SkyBlue,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Turn balance
        if (metrics.turnCount > 0) {
            TurnBalanceBar(
                leftTurns = metrics.leftTurnCount,
                rightTurns = metrics.rightTurnCount,
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Skill breakdown
        if (metrics.turnCount >= 3) {
            SkillBreakdownCard(
                balanceScore = metrics.balanceScore,
                edgingScore = metrics.edgingScore,
                rotaryScore = metrics.rotaryScore,
                pressureScore = metrics.pressureScore,
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // ── Boot Sensors (BLE) ──
        BootSensorPanel(
            bleStatus = bleStatus,
            leftImu = leftBootImu,
            rightImu = rightBootImu,
        )

        Spacer(modifier = Modifier.height(12.dp))

        // ── Boot Biomechanics (dual + tri-segment IMU) ──
        BiomechanicsPanel(
            leftDual = leftDualImu,
            rightDual = rightDualImu,
            leftTri = leftTriSeg,
            rightTri = rightTriSeg,
        )
        Spacer(modifier = Modifier.height(12.dp))

        // ── Thermal Vision ──
        ThermalPanel(thermal = thermalVision)
        Spacer(modifier = Modifier.height(12.dp))

        // ── Surface & Audio Analysis (from mic) ──
        if (leftMic != null || rightMic != null) {
            SurfacePanel(leftMic = leftMic, rightMic = rightMic)
            Spacer(modifier = Modifier.height(12.dp))
        }

        // ── GPS & Trajectory ──
        GpsPanel(
            location = location,
            pointCount = trajectory.size,
        )

        Spacer(modifier = Modifier.height(12.dp))

        TrajectoryView(
            points = trajectory,
            title = "Live Trajectory",
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ── Raw IMU Sensor Readouts ──
        RawSensorPanel(
            imu = rawImu,
            location = location,
            rollDeg = metrics.currentRoll,
            pitchDeg = metrics.currentPitch,
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Stop button
        Button(
            onClick = { viewModel.stopAndSave(onRunStopped) },
            enabled = !isSaving,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
        ) {
            Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(24.dp))
            Text(
                text = if (isSaving) "Saving…" else "Stop Run",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}
