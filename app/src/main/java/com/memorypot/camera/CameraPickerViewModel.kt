package com.memorypot.camera

import android.app.Application
import android.graphics.RectF
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class CameraPickerViewModel(app: Application) : AndroidViewModel(app) {

    private val _boxes = MutableStateFlow<List<DetectedBox>>(emptyList())
    val boxes: StateFlow<List<DetectedBox>> = _boxes

    private val _selected = MutableStateFlow<Set<Int>>(emptySet())
    val selected: StateFlow<Set<Int>> = _selected

    fun updateDetections(newBoxes: List<DetectedBox>) {
        _boxes.value = newBoxes
        // Drop selections that no longer exist
        val ids = newBoxes.map { it.trackingId }.toSet()
        _selected.update { it.intersect(ids) }
    }

    fun toggleSelectionAt(x: Float, y: Float) {
        val hit = _boxes.value
            .sortedByDescending { it.rect.width() * it.rect.height() }
            .firstOrNull { it.rect.contains(x, y) }
            ?: return

        _selected.update { current ->
            if (current.contains(hit.trackingId)) current - hit.trackingId else current + hit.trackingId
        }
    }

    fun clearSelection() {
        _selected.value = emptySet()
    }

    fun getSelectedBoxes(): List<DetectedBox> {
        val sel = _selected.value
        return _boxes.value.filter { sel.contains(it.trackingId) }
    }

    // Serialize selected boxes as normalized coords relative to preview size
    fun selectedAsJson(previewW: Float, previewH: Float): String {
        val selectedBoxes = getSelectedBoxes()
        fun norm(v: Float, max: Float) = if (max <= 0f) 0f else (v / max).coerceIn(0f, 1f)

        val parts = selectedBoxes.map { b ->
            val r = b.rect
            val left = norm(r.left, previewW)
            val top = norm(r.top, previewH)
            val right = norm(r.right, previewW)
            val bottom = norm(r.bottom, previewH)
            val lbl = b.label?.replace("\"", "\\\"") ?: ""
            val conf = b.confidence ?: -1f
            "{\"id\":${b.trackingId},\"l\":$left,\"t\":$top,\"r\":$right,\"b\":$bottom,\"label\":\"$lbl\",\"conf\":$conf}"
        }
        return "[${parts.joinToString(",")}]"
    }

    companion object {
        fun mapImageToViewRect(
            imgRect: android.graphics.Rect,
            rotationDegrees: Int,
            imageWidth: Int,
            imageHeight: Int,
            viewWidth: Int,
            viewHeight: Int
        ): RectF {
            // ML Kit returns rect in the rotated (upright) image coordinate system.
            val rot = ((rotationDegrees % 360) + 360) % 360
            val imgW = if (rot == 90 || rot == 270) imageHeight else imageWidth
            val imgH = if (rot == 90 || rot == 270) imageWidth else imageHeight

            val scale = kotlin.math.min(viewWidth.toFloat() / imgW.toFloat(), viewHeight.toFloat() / imgH.toFloat())
            val dx = (viewWidth - imgW * scale) / 2f
            val dy = (viewHeight - imgH * scale) / 2f

            return RectF(
                imgRect.left * scale + dx,
                imgRect.top * scale + dy,
                imgRect.right * scale + dx,
                imgRect.bottom * scale + dy
            )
        }
    }
}
