package com.memorypot.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memorypot.data.repo.MemoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class AddState(
    val label: String = "",
    val note: String = "",
    val placeText: String = "",
    val isSaving: Boolean = false,
    val error: String? = null
)

class AddMemoryViewModel(private val repo: MemoryRepository) : ViewModel() {
    private val _state = MutableStateFlow(AddState())
    val state: StateFlow<AddState> = _state

    fun updateLabel(v: String) { _state.value = _state.value.copy(label = v) }
    fun updateNote(v: String) { _state.value = _state.value.copy(note = v) }
    fun updatePlace(v: String) { _state.value = _state.value.copy(placeText = v) }

    fun save(photoPath: String, onDone: (String) -> Unit) {
        val s = _state.value
        _state.value = s.copy(isSaving = true, error = null)
        viewModelScope.launch {
            try {
                val id = repo.createMemory(
                    label = s.label,
                    note = s.note,
                    placeTextUser = s.placeText,
                    photoPath = photoPath
                )
                _state.value = _state.value.copy(isSaving = false)
                onDone(id)
            } catch (t: Throwable) {
                _state.value = _state.value.copy(isSaving = false, error = t.message ?: "Failed to save")
            }
        }
    }

    suspend fun suggestionsForLabel(label: String) = repo.suggestionsForLabel(label)
}
