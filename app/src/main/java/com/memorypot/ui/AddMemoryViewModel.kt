package com.memorypot.ui

import android.graphics.Rect
import android.graphics.RectF
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val isDetectingObjects: Boolean = false,
    val detectedObjects: List<AiKeywordHelper.DetectedRegion> = emptyList(),
    val detectedBitmapWidth: Int = 0,
    val detectedBitmapHeight: Int = 0,
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

    fun resetForNewCapture() {
        _state.value = _state.value.copy(
            keywords = "",
            keywordPrompt = "",
            isGeneratingKeywords = false,
            isDetectingObjects = false,
            detectedObjects = emptyList(),
            detectedBitmapWidth = 0,
            detectedBitmapHeight = 0,
            error = null
        )
    }

    fun generateKeywords(photoPath: String) {
        if (_state.value.isGeneratingKeywords) return
        _state.value = _state.value.copy(isGeneratingKeywords = true, error = null)
        viewModelScope.launch {
            try {
                val suggested = ai.suggestKeywords(photoPath)
                val merged = ai.mergePromptKeywords(suggested, _state.value.keywordPrompt)
                val text = merged.joinToString(", ")
                _state.value = _state.value.copy(
                    keywords = if (_state.value.keywords.isBlank()) text else _state.value.keywords,
                    isGeneratingKeywords = false,
                    error = if (suggested.isEmpty()) "No AI keywords detected for this photo." else null
                )
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    isGeneratingKeywords = false,
                    error = t.message ?: "Failed to generate AI keywords"
                )
            }
        }
    }

    fun detectObjects(photoPath: String) {
        if (_state.value.isDetectingObjects) return
        _state.value = _state.value.copy(isDetectingObjects = true, error = null)
        viewModelScope.launch {
            try {
                val res = ai.detectObjects(photoPath)
                _state.value = _state.value.copy(
                    isDetectingObjects = false,
                    detectedObjects = res.regions,
                    detectedBitmapWidth = res.bitmapWidth,
                    detectedBitmapHeight = res.bitmapHeight,
                    error = if (res.regions.isEmpty()) "No objects detected." else null
                )
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    isDetectingObjects = false,
                    error = t.message ?: "Failed to detect objects"
                )
            }
        }
    }

    fun generateKeywordsForRegion(photoPath: String, region: Rect) {
        if (_state.value.isGeneratingKeywords) return
        _state.value = _state.value.copy(isGeneratingKeywords = true, error = null)
        viewModelScope.launch {
            try {
                val suggested = ai.suggestKeywordsForRegion(photoPath, region)
                val merged = ai.mergePromptKeywords(suggested, _state.value.keywordPrompt)
                _state.value = _state.value.copy(
                    keywords = merged.joinToString(", "),
                    isGeneratingKeywords = false,
                    error = if (suggested.isEmpty()) "No AI keywords detected for the selected region." else null
                )
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    isGeneratingKeywords = false,
                    error = t.message ?: "Failed to generate region keywords"
                )
            }
        }
    }

    fun save(
        photoPath: String,
        selectedBoxesNormalized: List<RectF> = emptyList(),
        onDone: (String) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (_state.value.isSaving) return
        _state.value = _state.value.copy(isSaving = true, error = null)
        viewModelScope.launch {
            try {
                val id = repo.createMemory(
                    label = _state.value.label,
                    note = _state.value.note,
                    placeTextUser = _state.value.placeText,
                    keywordsUser = _state.value.keywords,
                    photoPath = photoPath
                )
                _state.value = _state.value.copy(isSaving = false)
                onDone(id)
            } catch (t: Throwable) {
                val msg = t.message ?: "Failed to save"
                _state.value = _state.value.copy(isSaving = false, error = msg)
                onError(msg)
            }
        }
    }
}
