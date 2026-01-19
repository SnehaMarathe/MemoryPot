package com.memorypot.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memorypot.data.repo.AiKeywordHelper
import com.memorypot.data.repo.MemoryRepository
import android.graphics.RectF
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
            isDetectingObjects = false,
            detectedObjects = emptyList(),
            detectedBitmapWidth = 0,
            detectedBitmapHeight = 0,
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

    /**
     * Detect objects to enable post-capture tap-to-select.
     */
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
                    error = if (res.regions.isEmpty()) "No objects detected. You can draw a box manually." else null
                )
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    isDetectingObjects = false,
                    error = t.message ?: "Failed to detect objects"
                )
            }
        }
    }

    /**
     * Generate keywords from a specific selected region (auto box or manual selection).
     * This is the key to accuracy when multiple objects are present.
     */
    fun generateKeywordsForRegion(photoPath: String, region: android.graphics.Rect) {
        val s = _state.value
        if (s.isGeneratingKeywords) return
        _state.value = s.copy(isGeneratingKeywords = true, error = null)
        viewModelScope.launch {
            try {
                val suggested = ai.suggestKeywordsForRegion(photoPath, region)

                // Preserve any user-entered keywords already in the editor.
                val current = _state.value.keywords
                    .split(',', ';', '\n')
                    .map { it.trim() }
                    .filter { it.isNotBlank() }

                val merged = (current + suggested)
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .take(20)

                val promptMerged = ai.mergePromptKeywords(merged, _state.value.keywordPrompt)
                _state.value = _state.value.copy(
                    keywords = promptMerged.joinToString(", "),
                    isGeneratingKeywords = false,
                    error = if (suggested.isEmpty()) "No AI keywords detected for this selection (try drawing a slightly larger box)." else null
                )
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    isGeneratingKeywords = false,
                    error = t.message ?: "Failed to generate AI keywords"
                )
            }
        }
    }

    /**
     * Generate keywords from multiple selected regions.
     * This is used when the user taps several detected objects (or draws multiple boxes).
     */
    fun generateKeywordsForRegions(photoPath: String, regions: List<android.graphics.Rect>) {
        val s = _state.value
        if (s.isGeneratingKeywords) return
        if (regions.isEmpty()) return
        _state.value = s.copy(isGeneratingKeywords = true, error = null)
        viewModelScope.launch {
            try {
                // Aggregate suggestions across boxes.
                val suggested = buildList {
                    regions.forEach { r ->
                        addAll(ai.suggestKeywordsForRegion(photoPath, r, max = 8))
                    }
                }

                // Preserve any user-entered keywords already in the editor.
                val current = _state.value.keywords
                    .split(',', ';', '\n')
                    .map { it.trim() }
                    .filter { it.isNotBlank() }

                val merged = (current + suggested)
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .take(25)

                val promptMerged = ai.mergePromptKeywords(merged, _state.value.keywordPrompt)
                _state.value = _state.value.copy(
                    keywords = promptMerged.joinToString(", "),
                    isGeneratingKeywords = false,
                    error = if (suggested.isEmpty()) "No AI keywords detected for these selections (try slightly larger boxes)." else null
                )
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    isGeneratingKeywords = false,
                    error = t.message ?: "Failed to generate AI keywords"
                )
            }
        }
    }

    /**
     * Like [generateKeywordsForRegions] but replaces the current keyword list.
     *
     * Rationale: when the user explicitly selects objects, they expect the clue
     * words to noticeably change to match that selection, not silently merge into
     * the previous full-image AI set.
     */
    fun generateKeywordsForRegionsReplace(photoPath: String, regions: List<android.graphics.Rect>) {
        val s = _state.value
        if (s.isGeneratingKeywords) return
        if (regions.isEmpty()) return
        _state.value = s.copy(isGeneratingKeywords = true, error = null)
        viewModelScope.launch {
            try {
                val suggested = buildList {
                    regions.forEach { r ->
                        addAll(ai.suggestKeywordsForRegion(photoPath, r, max = 10))
                    }
                }

                // Keep any user-entered prompt text, but swap the core keyword set.
                val merged = suggested
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .take(25)

                val promptMerged = ai.mergePromptKeywords(merged, _state.value.keywordPrompt)
                _state.value = _state.value.copy(
                    keywords = promptMerged.joinToString(", "),
                    isGeneratingKeywords = false,
                    error = if (suggested.isEmpty()) "No AI keywords detected for these selections (try slightly larger boxes)." else null
                )
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    isGeneratingKeywords = false,
                    error = t.message ?: "Failed to generate AI keywords"
                )
            }
        }
    }

    /**
     * Generate keywords for multiple regions that are specified in normalized [0..1] coordinates.
     * Used by the live camera selector so selections can carry across to the captured photo.
     */
    fun generateKeywordsForNormalizedRegions(photoPath: String, regions: List<RectF>) {
        val s = _state.value
        if (s.isGeneratingKeywords) return
        if (regions.isEmpty()) return
        _state.value = s.copy(isGeneratingKeywords = true, error = null)
        viewModelScope.launch {
            try {
                val suggested = ai.suggestKeywordsForNormalizedRegions(photoPath, regions)

                val current = _state.value.keywords
                    .split(',', ';', '\n')
                    .map { it.trim() }
                    .filter { it.isNotBlank() }

                val merged = (current + suggested)
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .take(25)

                val promptMerged = ai.mergePromptKeywords(merged, _state.value.keywordPrompt)
                _state.value = _state.value.copy(
                    keywords = promptMerged.joinToString(", "),
                    isGeneratingKeywords = false,
                    error = if (suggested.isEmpty()) "No AI keywords detected for these selections." else null
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
