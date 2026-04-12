package com.mycarv.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mycarv.app.MyCarvApp
import com.mycarv.app.data.repository.RunRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class SeasonStats(
    val totalRuns: Int = 0,
    val avgSkiIQ: Int = 0,
    val peakSkiIQ: Int = 0,
    val topSpeed: Float = 0f,
    val totalDistance: Float = 0f,
    val totalVertical: Float = 0f,
)

class DashboardViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = RunRepository((app as MyCarvApp).database)

    private val _stats = MutableStateFlow(SeasonStats())
    val stats: StateFlow<SeasonStats> = _stats

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _stats.value = SeasonStats(
                totalRuns = repo.getRunCount(),
                avgSkiIQ = repo.getAverageSkiIQ()?.toInt() ?: 0,
                peakSkiIQ = repo.getPeakSkiIQ() ?: 0,
                topSpeed = repo.getAllTimeMaxSpeed() ?: 0f,
                totalDistance = repo.getTotalDistance() ?: 0f,
                totalVertical = repo.getTotalVertical() ?: 0f,
            )
        }
    }
}
