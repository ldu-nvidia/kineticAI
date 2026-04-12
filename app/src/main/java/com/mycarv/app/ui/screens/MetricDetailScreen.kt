package com.mycarv.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mycarv.app.ui.theme.AccentGreen
import com.mycarv.app.ui.theme.AccentOrange
import com.mycarv.app.ui.theme.AccentYellow
import com.mycarv.app.ui.theme.SkyBlue
import com.mycarv.app.ui.theme.TextMuted
import com.mycarv.app.viewmodel.ChartPoint
import com.mycarv.app.viewmodel.MetricChartData
import com.mycarv.app.viewmodel.MetricDetailViewModel

@Composable
fun MetricDetailScreen(
    viewModel: MetricDetailViewModel,
    metricType: String,
    onBack: () -> Unit,
) {
    val chart by viewModel.chart.collectAsState()
    val speedTimeline by viewModel.speedTimeline.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Column(modifier = Modifier.padding(start = 8.dp)) {
                Text(
                    text = chart.title.ifEmpty { "Loading…" },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (chart.subtitle.isNotEmpty()) {
                    Text(
                        text = chart.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (chart.loaded && chart.points.isNotEmpty()) {
            val chartColor = when (metricType) {
                "total_runs" -> SkyBlue
                "avg_ski_iq", "peak_ski_iq" -> AccentGreen
                "top_speed" -> AccentOrange
                "total_distance" -> SkyBlue
                "total_vertical" -> AccentYellow
                else -> SkyBlue
            }

            ChartCard(
                data = chart,
                color = chartColor,
                heightDp = 280,
            )

            // Speed timeline for the last run
            if (metricType == "top_speed" && speedTimeline.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Last Run — Speed Over Time",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(12.dp))

                ChartCard(
                    data = MetricChartData(
                        title = "",
                        yAxisLabel = "km/h",
                        points = speedTimeline,
                        isHistogram = false,
                    ),
                    color = AccentOrange,
                    heightDp = 220,
                )
            }

            // Data table
            Spacer(modifier = Modifier.height(24.dp))
            DataTable(chart.points, chart.yAxisLabel, chartColor)

        } else if (chart.loaded) {
            Spacer(modifier = Modifier.height(48.dp))
            Text(
                text = "No data yet. Go ski!",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        } else {
            Text(
                text = "Loading…",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun ChartCard(
    data: MetricChartData,
    color: Color,
    heightDp: Int,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (data.yAxisLabel.isNotEmpty()) {
                Text(
                    text = data.yAxisLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            val gridColor = TextMuted.copy(alpha = 0.15f)
            val labelColor = TextMuted

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(heightDp.dp),
            ) {
                if (data.points.isEmpty()) return@Canvas

                val padding = 8.dp.toPx()
                val drawW = size.width - padding * 2
                val drawH = size.height - padding * 2
                val values = data.points.map { it.value }
                val maxVal = (values.maxOrNull() ?: 1f).coerceAtLeast(1f)
                val minVal = if (data.isHistogram) 0f else (values.minOrNull() ?: 0f)
                val range = (maxVal - minVal).coerceAtLeast(0.001f)

                // Grid
                for (i in 0..4) {
                    val y = padding + drawH * (1f - i / 4f)
                    drawLine(gridColor, Offset(padding, y), Offset(padding + drawW, y), strokeWidth = 1.dp.toPx())
                }

                val maxIdx = if (data.highlightMax) values.indexOf(values.max()) else -1

                if (data.isHistogram) {
                    drawHistogram(data.points, minVal, range, padding, drawW, drawH, color, maxIdx)
                } else {
                    drawLineChart(data.points, minVal, range, padding, drawW, drawH, color, maxIdx)
                }
            }

            // X-axis labels (first, middle, last)
            if (data.points.size >= 2) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(data.points.first().label, fontSize = 10.sp, color = TextMuted)
                    if (data.points.size > 2) {
                        Text(data.points[data.points.size / 2].label, fontSize = 10.sp, color = TextMuted)
                    }
                    Text(data.points.last().label, fontSize = 10.sp, color = TextMuted)
                }
            }
        }
    }
}

private fun DrawScope.drawHistogram(
    points: List<ChartPoint>,
    minVal: Float, range: Float,
    padding: Float, drawW: Float, drawH: Float,
    color: Color, maxIdx: Int,
) {
    val n = points.size
    val barWidth = (drawW / n) * 0.7f
    val gap = (drawW / n) * 0.15f

    points.forEachIndexed { i, pt ->
        val barH = ((pt.value - minVal) / range) * drawH
        val x = padding + (drawW / n) * i + gap
        val y = padding + drawH - barH
        val barColor = if (i == maxIdx) AccentOrange else color

        drawRect(
            color = barColor,
            topLeft = Offset(x, y),
            size = Size(barWidth, barH),
        )
    }
}

private fun DrawScope.drawLineChart(
    points: List<ChartPoint>,
    minVal: Float, range: Float,
    padding: Float, drawW: Float, drawH: Float,
    color: Color, maxIdx: Int,
) {
    val n = points.size
    if (n < 2) return

    val step = drawW / (n - 1)
    val offsets = points.mapIndexed { i, pt ->
        val x = padding + i * step
        val y = padding + drawH - ((pt.value - minVal) / range) * drawH
        Offset(x, y)
    }

    for (i in 0 until offsets.size - 1) {
        drawLine(color, offsets[i], offsets[i + 1], strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
    }

    // Dot on each point
    offsets.forEachIndexed { i, o ->
        val dotColor = if (i == maxIdx) AccentOrange else color
        val dotRadius = if (i == maxIdx) 6.dp.toPx() else 3.dp.toPx()
        drawCircle(dotColor, dotRadius, o)
    }
}

@Composable
private fun DataTable(
    points: List<ChartPoint>,
    unit: String,
    color: Color,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Data Points",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))

            val displayPoints = if (points.size > 20) {
                points.takeLast(20)
            } else points

            displayPoints.forEach { pt ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = pt.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${String.format("%.1f", pt.value)} $unit",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = color,
                    )
                }
            }
            if (points.size > 20) {
                Text(
                    text = "… showing last 20 of ${points.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
