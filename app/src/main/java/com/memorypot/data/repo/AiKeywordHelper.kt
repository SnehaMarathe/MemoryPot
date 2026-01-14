package com.memorypot.data.repo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Simple on-device "AI" keyword extraction using ML Kit Image Labeling.
 *
 * - No network required.
 * - Returns human friendly labels (e.g., "Food", "Dog", "Beach").
 */
class AiKeywordHelper(private val context: Context) {

    private val labeler: ImageLabeler by lazy {
        ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
    }

    /**
     * Suggest keywords from a photo stored at [photoPath].
     *
     * NOTE: We intentionally decode a scaled Bitmap and use InputImage.fromBitmap(...) instead of
     * InputImage.fromFilePath(...). This avoids occasional Uri/FilePath issues on some devices and
     * reduces memory usage for very large images.
     */
    suspend fun suggestKeywords(
        photoPath: String,
        max: Int = 8,
        minConfidence: Float = 0.50f
    ): List<String> = withContext(Dispatchers.IO) {
        runCatching {
            val bmp = decodeScaledBitmap(photoPath, maxDim = 1024)
                ?: error("Failed to decode image")
            val image = InputImage.fromBitmap(bmp, /* rotationDegrees = */ 0)
            val labels = labeler.process(image).await()
            labels
                .sortedByDescending { it.confidence }
                .filter { it.confidence >= minConfidence }
                .map { it.text.trim() }
                .filter { it.isNotBlank() }
                .map { it.lowercase(Locale.getDefault()) }
                .distinct()
                .take(max)
        }.getOrElse { t ->
            Log.w("AiKeywordHelper", "ML Kit labeling failed for path=$photoPath", t)
            emptyList()
        }
    }

    private fun decodeScaledBitmap(path: String, maxDim: Int): Bitmap? {
        val file = File(path)
        if (!file.exists()) return null

        // Read bounds
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        // Compute sample size
        var sample = 1
        var w = bounds.outWidth
        var h = bounds.outHeight
        while (w / sample > maxDim || h / sample > maxDim) {
            sample *= 2
        }

        return BitmapFactory.Options().run {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
            BitmapFactory.decodeFile(path, this)
        }
    }

    /**
     * Merge user-provided keywords/prompt text into an existing keyword list.
     * Accepts comma-separated or space-separated words.
     */
    fun mergePromptKeywords(base: List<String>, prompt: String, max: Int = 20): List<String> {
        val extra = prompt
            .replace('#', ' ')
            .split(',', ';', '\n', '\t', ' ')
            .map { it.trim() }
            .filter { it.length >= 2 }
            .map { it.lowercase(Locale.getDefault()) }

        return (base + extra)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(max)
    }
}
