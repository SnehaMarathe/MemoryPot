package com.memorypot.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memorypot.data.db.MemoryEntity
import com.memorypot.data.repo.MemoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class DetailsState(
    val memory: MemoryEntity? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)

class DetailsViewModel(private val repo: MemoryRepository) : ViewModel() {
    private val _state = MutableStateFlow(DetailsState())
    val state: StateFlow<DetailsState> = _state

    fun load(id: String) {
        _state.value = DetailsState(isLoading = true)
        viewModelScope.launch {
            val m = repo.getById(id)
            _state.value = DetailsState(memory = m, isLoading = false, error = if (m == null) "Not found" else null)
        }
    }

    fun markFound(id: String, onDone: () -> Unit) {
        viewModelScope.launch {
            repo.markFound(id)
            onDone()
        }
    }

    fun delete(id: String, onDone: () -> Unit) {
        viewModelScope.launch {
            repo.deleteMemory(id)
            onDone()
        }
    }
}
