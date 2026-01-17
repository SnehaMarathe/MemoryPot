package com.memorypot.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.memorypot.data.repo.AiKeywordHelper
import com.memorypot.data.repo.MemoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class AddState(
    val label: String = "",
    val note: String = "",
    val placeText: String = "",
    val keywords: String = "",
    val keywordPrompt: String = "",
    val isGeneratingKeywords: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null
)

class AddMemoryViewModel(
    private val repo: MemoryRepository,
    private val ai: AiKeywordHelper
) : ViewModel() {
    private val _state = MutableStateFlow(AddState())
    val state: StateFlow<AddState> = _state

    fun updateLabel(v: String) { _state.value = _state.value.copy(label = v) }
    fun updateNote(v: String) { _state.value = _state.value.copy(note = v) }
    fun updatePlace(v: String) { _state.value = _state.value.copy(placeText = v) }
    fun updateKeywords(v: String) { _state.value = _state.value.copy(keywords = v) }
    fun updateKeywordPrompt(v: String) { _state.value = _state.value.copy(keywordPrompt = v) }

    /**
     * Called when the user retakes the photo (or starts a new capture).
     * We clear AI-derived fields so the next capture generates fresh results.
     */
    fun resetForNewCapture() {
        val s = _state.value
        _state.value = s.copy(
            keywords = "",
            keywordPrompt = "",
            isGeneratingKeywords = false,
            error = null
        )
    }

    fun generateKeywords(photoPath: String) {
        // only auto-generate if user hasn't typed anything yet
        val s = _state.value
        if (s.isGeneratingKeywords) return
        _state.value = s.copy(isGeneratingKeywords = true)
        viewModelScope.launch {
            try {
                val suggested = ai.suggestKeywords(photoPath)
                Log.d("AI_KEYWORDS_SUGGESTED", suggested.joinToString("|"))
                val merged = ai.mergePromptKeywords(suggested, _state.value.keywordPrompt)
                Log.d("AI_KEYWORDS_MERGED", merged.joinToString("|"))
                val text = merged.joinToString(", ")
                _state.value = _state.value.copy(
                    keywords = if (_state.value.keywords.isBlank()) text else _state.value.keywords,
                    isGeneratingKeywords = false,
                    error = if (suggested.isEmpty()) "No AI keywords detected for this photo (try adding your own below)." else null
                )
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    isGeneratingKeywords = false,
                    error = t.message ?: "Failed to generate AI keywords"
                )
            }
        }
    }

    fun applyKeywordPrompt() {
        val s = _state.value
        val base = s.keywords
            .split(',', ';', '\n')
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val merged = ai.mergePromptKeywords(base, s.keywordPrompt)
        _state.value = _state.value.copy(keywords = merged.joinToString(", "))
    }

    fun save(photoPath: String, onDone: (String) -> Unit) {
        val s = _state.value
        _state.value = s.copy(isSaving = true, error = null)
        viewModelScope.launch {
            try {
                val id = repo.createMemory(
                    label = s.label,
                    note = s.note,
                    placeTextUser = s.placeText,
                    keywordsUser = s.keywords,
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
