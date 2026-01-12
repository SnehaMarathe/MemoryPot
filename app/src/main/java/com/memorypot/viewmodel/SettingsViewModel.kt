package com.memorypot.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memorypot.data.repo.ExportImport
import com.memorypot.data.repo.MemoryRepository
import com.memorypot.data.settings.SettingsDataStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsState(
    val saveLocation: Boolean = true,
    val nearbyPrompt: Boolean = true,
    val isWorking: Boolean = false,
    val message: String? = null
)

class SettingsViewModel(
    private val settings: SettingsDataStore,
    private val repo: MemoryRepository,
    private val exportImport: ExportImport
) : ViewModel() {

    val state: StateFlow<SettingsState> = combine(
        settings.saveLocationFlow,
        settings.nearbyPromptFlow
    ) { saveLoc, near ->
        SettingsState(saveLocation = saveLoc, nearbyPrompt = near)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsState())

    fun setSaveLocation(enabled: Boolean) {
        viewModelScope.launch { settings.setSaveLocation(enabled) }
    }

    fun setNearbyPrompt(enabled: Boolean) {
        viewModelScope.launch { settings.setNearbyPrompt(enabled) }
    }

    fun setOnboardingDone(done: Boolean) {
        viewModelScope.launch { settings.setOnboardingDone(done) }
    }

    fun exportJson(uri: Uri, onDone: (String) -> Unit) {
        viewModelScope.launch {
            try {
                exportImport.exportJson(uri)
                onDone("Exported JSON")
            } catch (t: Throwable) {
                onDone("Export failed: ${t.message ?: "unknown"}")
            }
        }
    }

    fun clearAll(onDone: (String) -> Unit) {
        viewModelScope.launch {
            try {
                repo.clearAll()
                onDone("Cleared all data")
            } catch (t: Throwable) {
                onDone("Clear failed: ${t.message ?: "unknown"}")
            }
        }
    }
}
