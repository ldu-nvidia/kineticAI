package com.mycarv.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mycarv.app.sensor.ImuSample
import com.mycarv.app.sensor.LocationSample
import com.mycarv.app.ui.theme.AccentGreen
import com.mycarv.app.ui.theme.AccentOrange
import com.mycarv.app.ui.theme.AccentYellow
import com.mycarv.app.ui.theme.SkyBlue

@Composable
fun RawSensorPanel(
    imu: ImuSample?,
    location: LocationSample?,
    rollDeg: Float,
    pitchDeg: Float,
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
                text = "Live Sensor Data",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            if (imu != null) {
                SensorSection(
                    title = "ACCELEROMETER",
                    color = SkyBlue,
                    values = listOf(
                        "X" to String.format("%+8.3f", imu.accelX),
                        "Y" to String.format("%+8.3f", imu.accelY),
                        "Z" to String.format("%+8.3f", imu.accelZ),
                        "|a|" to String.format("%7.3f", imu.accelMagnitude),
                    ),
                    unit = "m/s²",
                )

                SensorSection(
                    title = "GYROSCOPE",
                    color = AccentOrange,
                    values = listOf(
                        "X" to String.format("%+8.4f", imu.gyroX),
                        "Y" to String.format("%+8.4f", imu.gyroY),
                        "Z" to String.format("%+8.4f", imu.gyroZ),
                        "|ω|" to String.format("%7.4f", imu.gyroMagnitude),
                    ),
                    unit = "rad/s",
                )

                SensorSection(
                    title = "MAGNETOMETER",
                    color = AccentGreen,
                    values = listOf(
                        "X" to String.format("%+8.1f", imu.magX),
                        "Y" to String.format("%+8.1f", imu.magY),
                        "Z" to String.format("%+8.1f", imu.magZ),
                    ),
                    unit = "µT",
                )

                SensorSection(
                    title = "BAROMETER",
                    color = AccentYellow,
                    values = listOf(
                        "P" to String.format("%8.2f", imu.pressure),
                    ),
                    unit = "hPa",
                )

                SensorSection(
                    title = "ORIENTATION (Madgwick)",
                    color = SkyBlue,
                    values = listOf(
                        "Roll" to String.format("%+7.1f", rollDeg),
                        "Pitch" to String.format("%+7.1f", pitchDeg),
                    ),
                    unit = "°",
                )

                SensorSection(
                    title = "DERIVED",
                    color = AccentOrange,
                    values = listOf(
                        "G-Force" to String.format("%6.2f", imu.gForce),
                    ),
                    unit = "G",
                )
            } else {
                Text(
                    text = "Waiting for sensor data…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun GpsPanel(
    location: LocationSample?,
    pointCount: Int,
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "GPS",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            if (location != null) {
                MonoRow("Lat", String.format("%12.7f", location.latitude), "°", AccentGreen)
                MonoRow("Lon", String.format("%12.7f", location.longitude), "°", AccentGreen)
                MonoRow("Alt", String.format("%8.1f", location.altitude), "m", SkyBlue)
                MonoRow("Speed", String.format("%6.1f", location.speed * 3.6f), "km/h", AccentOrange)
                MonoRow("Bearing", String.format("%6.1f", location.bearing), "°", AccentYellow)
                MonoRow("Accuracy", String.format("%5.1f", location.accuracy), "m", MaterialTheme.colorScheme.onSurfaceVariant)
                MonoRow("Track pts", "$pointCount", "", SkyBlue)
            } else {
                Text(
                    text = "Acquiring GPS signal…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SensorSection(
    title: String,
    color: Color,
    values: List<Pair<String, String>>,
    unit: String,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = color,
            )
            Text(
                text = unit,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        values.forEach { (label, value) ->
            MonoRow(label, value, "", color)
        }
    }
}

@Composable
private fun MonoRow(
    label: String,
    value: String,
    unit: String,
    color: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.3f),
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            color = color,
            modifier = Modifier.weight(0.5f),
        )
        if (unit.isNotEmpty()) {
            Text(
                text = unit,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(0.2f),
            )
        }
    }
}
