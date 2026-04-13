package com.kineticai.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kineticai.app.KineticAIApp
import com.kineticai.app.data.db.RunEntity
import com.kineticai.app.data.repository.RunRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RunHistoryViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = RunRepository((app as KineticAIApp).database)

    val runs: StateFlow<List<RunEntity>> = repo.allRuns()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteRun(runId: Long) {
        viewModelScope.launch {
            repo.deleteRun(runId)
        }
    }
}
