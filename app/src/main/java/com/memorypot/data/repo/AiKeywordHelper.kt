package com.memorypot.data.repo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
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

    private val textRecognizer: TextRecognizer by lazy {
        // Latin options are broadly suitable and keep it on-device.
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
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
            // Decode an upright bitmap (honoring camera EXIF rotation). This noticeably improves
            // ML Kit recognition quality for many devices.
            val bmp = decodeScaledBitmapUpright(photoPath, maxDim = 1280)
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
            //    ML Kit object classification can be conservative; we also do a second pass by
            //    cropping each detected object and running image labeling on the crop.
            val objectLabels = runCatching {
                val objects = objectDetector.process(image).await()

                val totalArea = (bmp.width * bmp.height).toFloat().coerceAtLeast(1f)

                // Direct per-object classification labels (when ML Kit provides them)
                // We boost larger objects a bit because users usually care about the main subject.
                val direct = objects
                    .flatMap { obj ->
                        val areaRatio = (obj.boundingBox.width() * obj.boundingBox.height()).toFloat() / totalArea
                        val sizeBoost = (0.85f + 0.35f * kotlin.math.sqrt(areaRatio.coerceIn(0f, 1f)))
                        obj.labels.map { it.text.trim() to (it.confidence * sizeBoost) }
                    }
                    .filter { it.first.isNotBlank() }

                // Crop each detected object and run labeling again.
                // This often turns "scene" labels into concrete nouns.
                val cropped = objects.flatMap { obj ->
                    val crop = safeCrop(bmp, obj.boundingBox) ?: return@flatMap emptyList()
                    val areaRatio = (obj.boundingBox.width() * obj.boundingBox.height()).toFloat() / totalArea
                    val sizeBoost = (0.90f + 0.45f * kotlin.math.sqrt(areaRatio.coerceIn(0f, 1f)))
                    val cropImage = InputImage.fromBitmap(crop, 0)
                    runCatching {
                        labeler.process(cropImage).await()
                            .sortedByDescending { it.confidence }
                            .take(3)
                            // Slightly downweight crop-labels, but boost by object size.
                            .map { it.text.trim() to (it.confidence * 0.88f * sizeBoost).coerceAtMost(1f) }
                    }.getOrElse { emptyList() }
                }

                (direct + cropped)
                    .filter { it.first.isNotBlank() }
                    .sortedByDescending { it.second }
                    .filter { it.second >= (minConfidence - 0.20f).coerceAtLeast(0.25f) }
            }.getOrElse { emptyList() }

            // 3) Text hints (signs, labels, packaging). We only keep short, clean tokens.
            val textLabels = runCatching {
                val raw = textRecognizer.process(image).await().text
                    .lowercase(Locale.getDefault())

                // Tokenize with some guardrails so we don't pollute keywords with noise.
                val tokens = raw
                    .replace(Regex("[^a-z0-9\\n ]"), " ")
                    .split(Regex("\\s+"))
                    .map { it.trim() }
                    .filter { it.length in 3..20 }
                    .filter { it.any { ch -> ch.isLetter() } } // avoid pure numbers
                    .filterNot { STOP_WORDS.contains(it) }

                // Add a few useful bigrams (e.g., "coca cola", "macbook pro")
                val bigrams = tokens.windowed(size = 2, step = 1, partialWindows = false)
                    .map { (a, b) -> "$a $b" }
                    .filter { it.length in 6..28 }

                (tokens + bigrams)
                    .distinct()
                    .take(16)
                    // Treat as medium confidence; we'll still rank with others.
                    .map { it to 0.62f }
            }.getOrElse { emptyList() }

            // Merge + rank: object labels get a small boost because users typically expect them.
            val merged = (
                sceneLabels.map { it.first.lowercase(Locale.getDefault()) to it.second } +
                    objectLabels.map { it.first.lowercase(Locale.getDefault()) to (it.second + 0.12f).coerceAtMost(1f) } +
                    // Text keywords get a small boost because they are often very specific.
                    textLabels.map { it.first.lowercase(Locale.getDefault()) to (it.second + 0.08f).coerceAtMost(1f) }
                )

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

    private fun decodeScaledBitmapUpright(path: String, maxDim: Int): Bitmap? {
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

        val decoded = BitmapFactory.Options().run {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
            BitmapFactory.decodeFile(path, this)
        }

        val rotation = runCatching { exifRotationDegrees(path) }.getOrElse { 0 }
        if (rotation == 0) return decoded
        return rotateBitmap(decoded ?: return null, rotation)
    }

    private fun exifRotationDegrees(path: String): Int {
        val exif = ExifInterface(path)
        return when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    }

    private fun rotateBitmap(src: Bitmap, degrees: Int): Bitmap {
        val m = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
            .also { if (it != src) src.recycle() }
    }

    private fun safeCrop(src: Bitmap, box: android.graphics.Rect): Bitmap? {
        val left = box.left.coerceAtLeast(0)
        val top = box.top.coerceAtLeast(0)
        val right = box.right.coerceAtMost(src.width)
        val bottom = box.bottom.coerceAtMost(src.height)
        val w = right - left
        val h = bottom - top
        if (w <= 20 || h <= 20) return null
        return runCatching { Bitmap.createBitmap(src, left, top, w, h) }.getOrNull()
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
