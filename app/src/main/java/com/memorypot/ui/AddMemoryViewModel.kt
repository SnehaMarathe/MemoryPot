package com.memorypot.ui

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.memorypot.MemoryPotApp
import com.memorypot.camera.DetectedObjectBox
import com.memorypot.camera.NormalizedBox
import com.memorypot.data.MemoryEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PickerState(
    val viewBoxes: List<DetectedObjectBox> = emptyList(),
    val normalizedBoxes: List<NormalizedBox> = emptyList(),
    val selectedIds: Set<Int> = emptySet()
)

class AddMemoryViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = (app as MemoryPotApp).db.memories()

    private val _pickerState = MutableStateFlow(PickerState())
    val pickerState: StateFlow<PickerState> = _pickerState.asStateFlow()

    fun updateDetections(viewBoxes: List<DetectedObjectBox>, normalizedBoxes: List<NormalizedBox>) {
        _pickerState.update { it.copy(viewBoxes = viewBoxes, normalizedBoxes = normalizedBoxes) }
    }

    fun toggleSelection(id: Int) {
        _pickerState.update {
            val next = it.selectedIds.toMutableSet()
            if (next.contains(id)) next.remove(id) else next.add(id)
            it.copy(selectedIds = next)
        }
    }

    fun clearSelection() {
        _pickerState.update { it.copy(selectedIds = emptySet()) }
    }

    fun selectedCount(): Int = _pickerState.value.selectedIds.size

    fun saveMemory(
        label: String,
        note: String,
        placeText: String,
        imageCapture: ImageCapture,
        onDone: (success: Boolean, error: String?) -> Unit
    ) {
        val context = getApplication<Application>()
        val photosDir = File(context.filesDir, "photos").apply { mkdirs() }
        val name = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".jpg"
        val outFile = File(photosDir, name)

        val selected = _pickerState.value.normalizedBoxes.filter { _pickerState.value.selectedIds.contains(it.trackingId) }
        val selectedJson = JSONArray().apply {
            for (b in selected) {
                put(
                    JSONObject().apply {
                        put("id", b.trackingId)
                        put("l", b.left)
                        put("t", b.top)
                        put("r", b.right)
                        put("b", b.bottom)
                        if (b.label != null) put("label", b.label)
                        if (b.confidence != null) put("conf", b.confidence)
                    }
                )
            }
        }.toString()

        val output = ImageCapture.OutputFileOptions.Builder(outFile).build()
        imageCapture.takePicture(
            output,
            androidx.core.content.ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    viewModelScope.launch {
                        try {
                            dao.insert(
                                MemoryEntity(
                                    label = label,
                                    note = note,
                                    placeText = placeText,
                                    timestampMs = System.currentTimeMillis(),
                                    photoPath = outFile.absolutePath,
                                    selectedObjectsJson = selectedJson
                                )
                            )
                            onDone(true, null)
                        } catch (e: Throwable) {
                            Log.e("AddMemoryViewModel", "DB insert failed", e)
                            onDone(false, "Failed to save: ${e.message}")
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("AddMemoryViewModel", "Capture failed", exception)
                    onDone(false, "Capture failed: ${exception.message}")
                }
            }
        )
    }
}
