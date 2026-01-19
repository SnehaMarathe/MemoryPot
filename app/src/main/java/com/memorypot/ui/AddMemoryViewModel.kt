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

/**
 * NOTE: This ViewModel intentionally does not import or reference Room/database types directly.
 * It only talks to [MemoryRepository].
 */
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
        val s = _state.value
        _state.value = s.copy(
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
        val s = _state.value
        if (s.isGeneratingKeywords) return
        _state.value = s.copy(isGeneratingKeywords = true)
        viewModelScope.launch {
            try {
                val suggested = ai.suggestKeywords(photoPath)
                val merged = ai.mergePromptKeywords(suggested, _state.value.keywordPrompt)
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

    fun detectObjects(photoPath: String) {
        val s = _state.value
        if (s.isDetectingObjects) return
        _state.value = s.copy(isDetectingObjects = true, error = null)
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
        val s = _state.value
        if (s.isGeneratingKeywords) return
        _state.value = s.copy(isGeneratingKeywords = true, error = null)
        viewModelScope.launch {
            try {
                val suggested = ai.suggestKeywordsForRegion(photoPath, region)
                val merged = ai.mergePromptKeywords(suggested, _state.value.keywordPrompt)
                val text = merged.joinToString(", ")
                _state.value = _state.value.copy(
                    keywords = text,
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

    /**
     * Save a captured photo + selection metadata.
     *
     * @param selectedBoxesNormalized List of RectF in 0..1 normalized coordinates, relative to the saved image.
     */
    fun save(
        photoPath: String,
        selectedBoxesNormalized: List<RectF>,
        onDone: (String) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val s = _state.value
        if (s.isSaving) return
        _state.value = s.copy(isSaving = true, error = null)
        viewModelScope.launch {
            try {
                val id = repo.createMemory(
                    label = _state.value.label,
                    note = _state.value.note,
                    placeText = _state.value.placeText,
                    photoPath = photoPath,
                    keywordsCsv = _state.value.keywords,
                    keywordPrompt = _state.value.keywordPrompt,
                    selectedBoxesNormalized = selectedBoxesNormalized
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
