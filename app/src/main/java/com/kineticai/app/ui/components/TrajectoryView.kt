package com.kineticai.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kineticai.app.sensor.LocationSample
import com.kineticai.app.ui.theme.AccentGreen
import com.kineticai.app.ui.theme.AccentOrange
import com.kineticai.app.ui.theme.SkyBlue
import com.kineticai.app.ui.theme.TextMuted
import kotlin.math.cos
import kotlin.math.max

/**
 * Draws a 2D bird's-eye trajectory of GPS points.
 * Uses Mercator-like projection (lat/lon → x/y with cos(lat) correction).
 */
@Composable
fun TrajectoryView(
    points: List<LocationSample>,
    title: String = "Run Trajectory",
    heightDp: Int = 250,
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
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (points.size < 2) {
                Text(
                    text = if (points.isEmpty()) "Waiting for GPS points…"
                    else "1 point recorded — keep moving!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val lineColor = SkyBlue
                val startColor = AccentGreen
                val endColor = AccentOrange
                val gridColor = TextMuted.copy(alpha = 0.1f)

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(heightDp.dp),
                ) {
                    val padding = 20.dp.toPx()
                    val drawW = size.width - padding * 2
                    val drawH = size.height - padding * 2

                    val lats = points.map { it.latitude }
                    val lons = points.map { it.longitude }
                    val minLat = lats.min()
                    val maxLat = lats.max()
                    val minLon = lons.min()
                    val maxLon = lons.max()

                    val cosLat = cos(Math.toRadians((minLat + maxLat) / 2.0)).toFloat()
                    val latRange = max((maxLat - minLat).toFloat(), 0.00001f)
                    val lonRange = max((maxLon - minLon).toFloat() * cosLat, 0.00001f)

                    val scale = minOf(drawW / lonRange, drawH / latRange)

                    fun project(lat: Double, lon: Double): Offset {
                        val x = padding + ((lon - minLon).toFloat() * cosLat * scale)
                        val y = padding + drawH - ((lat - minLat).toFloat() * scale)
                        return Offset(x, y)
                    }

                    // Grid lines
                    for (i in 0..4) {
                        val y = padding + drawH * i / 4f
                        drawLine(gridColor, Offset(padding, y), Offset(padding + drawW, y))
                        val x = padding + drawW * i / 4f
                        drawLine(gridColor, Offset(x, padding), Offset(x, padding + drawH))
                    }

                    // Trajectory line
                    for (i in 0 until points.size - 1) {
                        val p1 = project(points[i].latitude, points[i].longitude)
                        val p2 = project(points[i + 1].latitude, points[i + 1].longitude)
                        drawLine(
                            color = lineColor,
                            start = p1,
                            end = p2,
                            strokeWidth = 3.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                    }

                    // Start point
                    val start = project(points.first().latitude, points.first().longitude)
                    drawCircle(startColor, radius = 6.dp.toPx(), center = start)

                    // Current/end point
                    val end = project(points.last().latitude, points.last().longitude)
                    drawCircle(endColor, radius = 6.dp.toPx(), center = end)
                }
            }
        }
    }
}
