package com.kineticai.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kineticai.app.KineticAIApp
import com.kineticai.app.data.db.RunEntity
import com.kineticai.app.data.repository.RunRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ChartPoint(val label: String, val value: Float)

data class MetricChartData(
    val title: String = "",
    val subtitle: String = "",
    val yAxisLabel: String = "",
    val points: List<ChartPoint> = emptyList(),
    val isHistogram: Boolean = false,
    val highlightMax: Boolean = false,
    val loaded: Boolean = false,
)

class MetricDetailViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = RunRepository((app as KineticAIApp).database)

    private val _chart = MutableStateFlow(MetricChartData())
    val chart: StateFlow<MetricChartData> = _chart

    private val _speedTimeline = MutableStateFlow<List<ChartPoint>>(emptyList())
    val speedTimeline: StateFlow<List<ChartPoint>> = _speedTimeline

    fun loadMetric(metricType: String) {
        viewModelScope.launch {
            val runs = repo.getAllRunsChronological()
            _chart.value = when (metricType) {
                "total_runs" -> buildRunsHistogram(runs)
                "avg_kinetic_score" -> buildKineticScoreOverTime(runs)
                "peak_kinetic_score" -> buildKineticScoreOverTime(runs, highlightMax = true)
                "top_speed" -> buildTopSpeedChart(runs)
                "total_distance" -> buildDistancePerRun(runs)
                "total_vertical" -> buildVerticalPerRun(runs)
                else -> MetricChartData(title = "Unknown metric", loaded = true)
            }

            if (metricType == "top_speed") {
                loadSpeedTimeline()
            }
        }
    }

    private suspend fun loadSpeedTimeline() {
        val latestRun = repo.getLatestRun() ?: return
        val sensorData = repo.getSensorData(latestRun.id)
        if (sensorData.isEmpty()) return

        val startTime = sensorData.first().timestamp
        val step = (sensorData.size / 300).coerceAtLeast(1)
        _speedTimeline.value = sensorData
            .filterIndexed { i, _ -> i % step == 0 }
            .mapNotNull { s ->
                val speed = s.speed ?: return@mapNotNull null
                val sec = (s.timestamp - startTime) / 1000f
                val minSec = "%d:%02d".format((sec / 60).toInt(), (sec % 60).toInt())
                ChartPoint(minSec, speed * 3.6f)
            }
    }

    private fun buildRunsHistogram(runs: List<RunEntity>): MetricChartData {
        if (runs.isEmpty()) return MetricChartData(
            title = "Runs per Time Slot", subtitle = "No runs yet", loaded = true,
        )
        val buckets = mutableMapOf<String, Int>()
        val fmt = java.text.SimpleDateFormat("MM/dd HH:00", java.util.Locale.getDefault())
        runs.forEach { run ->
            val hourSlot = fmt.format(java.util.Date(run.startTime))
            buckets[hourSlot] = (buckets[hourSlot] ?: 0) + 1
        }
        return MetricChartData(
            title = "Runs per Time Slot",
            subtitle = "${runs.size} total runs",
            yAxisLabel = "# of runs",
            points = buckets.map { ChartPoint(it.key, it.value.toFloat()) },
            isHistogram = true,
            loaded = true,
        )
    }

    private fun buildKineticScoreOverTime(
        runs: List<RunEntity>,
        highlightMax: Boolean = false,
    ): MetricChartData {
        if (runs.isEmpty()) return MetricChartData(
            title = "Kinetic Score Over Time", subtitle = "No runs yet", loaded = true,
        )
        val fmt = java.text.SimpleDateFormat("MM/dd", java.util.Locale.getDefault())
        val points = runs.mapIndexed { i, run ->
            ChartPoint(fmt.format(java.util.Date(run.startTime)), run.skiIQ.toFloat())
        }
        val peak = runs.maxOfOrNull { it.skiIQ } ?: 0
        return MetricChartData(
            title = if (highlightMax) "Peak Kinetic Score Progression" else "Avg Kinetic Score Over Time",
            subtitle = if (highlightMax) "Peak: $peak / 200" else "Across ${runs.size} runs",
            yAxisLabel = "Kinetic Score",
            points = points,
            highlightMax = highlightMax,
            loaded = true,
        )
    }

    private fun buildTopSpeedChart(runs: List<RunEntity>): MetricChartData {
        if (runs.isEmpty()) return MetricChartData(
            title = "Top Speed per Run", subtitle = "No runs yet", loaded = true,
        )
        val fmt = java.text.SimpleDateFormat("MM/dd", java.util.Locale.getDefault())
        val points = runs.map { run ->
            ChartPoint(fmt.format(java.util.Date(run.startTime)), run.maxSpeedKmh)
        }
        return MetricChartData(
            title = "Top Speed per Run",
            subtitle = "All-time max: ${String.format("%.1f", runs.maxOf { it.maxSpeedKmh })} km/h",
            yAxisLabel = "km/h",
            points = points,
            highlightMax = true,
            loaded = true,
        )
    }

    private fun buildDistancePerRun(runs: List<RunEntity>): MetricChartData {
        if (runs.isEmpty()) return MetricChartData(
            title = "Distance per Run", subtitle = "No runs yet", loaded = true,
        )
        val fmt = java.text.SimpleDateFormat("MM/dd", java.util.Locale.getDefault())
        val points = runs.map { run ->
            ChartPoint(fmt.format(java.util.Date(run.startTime)), run.totalDistance)
        }
        return MetricChartData(
            title = "Distance per Run",
            subtitle = "Total: ${String.format("%.1f", runs.sumOf { it.totalDistance.toDouble() } / 1000)} km",
            yAxisLabel = "meters",
            points = points,
            isHistogram = true,
            loaded = true,
        )
    }

    private fun buildVerticalPerRun(runs: List<RunEntity>): MetricChartData {
        if (runs.isEmpty()) return MetricChartData(
            title = "Vertical Drop per Run", subtitle = "No runs yet", loaded = true,
        )
        val fmt = java.text.SimpleDateFormat("MM/dd", java.util.Locale.getDefault())
        val points = runs.map { run ->
            ChartPoint(fmt.format(java.util.Date(run.startTime)), run.altitudeDrop)
        }
        return MetricChartData(
            title = "Vertical Drop per Run",
            subtitle = "Total: ${String.format("%.0f", runs.sumOf { it.altitudeDrop.toDouble() })} m",
            yAxisLabel = "meters",
            points = points,
            isHistogram = true,
            loaded = true,
        )
    }
}
