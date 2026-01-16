package com.memorypot.data.repo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
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
        // Lower internal threshold so we can apply our own ranking + dedupe logic.
        // This helps capture more object/scene candidates, especially in indoor or low-light shots.
        ImageLabeling.getClient(
            ImageLabelerOptions.Builder()
                .setConfidenceThreshold(0.30f)
                .build()
        )
    }

    private val objectDetector: ObjectDetector by lazy {
        // Object detection tends to surface concrete nouns ("bottle", "shoe", "cat") which users
        // expect from "object keyword" features. We keep it on-device.
        val opts = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
            .enableMultipleObjects()
            .enableClassification() // enables per-object labels when available
            .build()
        ObjectDetection.getClient(opts)
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

            // 1) Scene / general labels ("beach", "food", "sunset")
            val sceneLabels = runCatching {
                labeler.process(image).await()
                    .sortedByDescending { it.confidence }
                    .filter { it.confidence >= minConfidence }
                    .map { it.text.trim() to it.confidence }
            }.getOrElse { emptyList() }

            // 2) Object-centric labels ("bottle", "shoe", "cat")
            //    We slightly lower the threshold because object classification can be conservative.
            val objectLabels = runCatching {
                objectDetector.process(image).await()
                    .flatMap { obj -> obj.labels.map { it.text.trim() to it.confidence } }
                    .filter { it.first.isNotBlank() }
                    .sortedByDescending { it.second }
                    .filter { it.second >= (minConfidence - 0.15f).coerceAtLeast(0.25f) }
            }.getOrElse { emptyList() }

            // Merge + rank: object labels get a small boost because users typically expect them.
            val merged = (sceneLabels.map { it.first.lowercase(Locale.getDefault()) to it.second } +
                objectLabels.map { it.first.lowercase(Locale.getDefault()) to (it.second + 0.12f).coerceAtMost(1f) })

            merged
                .flatMap { (t, c) -> expandTokens(normalizeToken(t)).map { it to c } }
                .filter { (t, _) -> t.isNotBlank() && t.length >= 2 }
                .filterNot { (t, _) -> STOP_WORDS.contains(t) }
                .groupBy { it.first }
                .mapValues { (_, xs) -> xs.maxOf { it.second } }
                .toList()
                .sortedByDescending { it.second }
                .map { it.first }
                .take(max)
        }.getOrElse { t ->
            Log.w("AiKeywordHelper", "ML Kit labeling failed for path=$photoPath", t)
            emptyList()
        }
    }

    private fun normalizeToken(raw: String): String {
        // Keep it human-friendly and searchable.
        // - lowercased upstream
        // - remove stray punctuation
        // - collapse whitespace
        return raw
            .replace(Regex("[^a-z0-9 ]"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
    }

    private fun expandTokens(token: String): List<String> {
        // ML Kit sometimes returns multi-word labels like "mobile phone".
        // Keeping both the phrase and the most informative words improves search + UX.
        val words = token.split(' ').map { it.trim() }.filter { it.isNotBlank() }
        if (words.size <= 1) return listOf(token)
        val trimmed = words.filterNot { STOP_WORDS.contains(it) }
        val singleWords = trimmed.filter { it.length >= 3 }
        return (listOf(token) + singleWords).distinct()
    }

    private companion object {
        private val STOP_WORDS = setOf(
            "a", "an", "the", "and", "or", "of", "to", "in", "on", "with",
            "object", "thing", "items", "item", "photo", "picture", "image"
        )
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
