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

    // We keep track of where a keyword came from so we can rank more precisely.
    // Users primarily expect concrete nouns (objects), so we prefer those.
    private enum class Source { OBJECT_DIRECT, OBJECT_CROP, SCENE, OCR }

    private data class Candidate(
        val token: String,
        val score: Float,
        val source: Source
    )

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

            val totalArea = (bmp.width * bmp.height).toFloat().coerceAtLeast(1f)

            // --- A) OBJECTS (primary) ---------------------------------------------------------
            // We treat object-derived keywords as "truth" and only allow scene/ocr to fill gaps.
            val objectCandidates: List<Candidate> = runCatching {
                val objects = objectDetector.process(image).await()

                val direct = objects.flatMap { obj ->
                    val areaRatio = (obj.boundingBox.width() * obj.boundingBox.height()).toFloat() / totalArea
                    val sizeBoost = (0.95f + 0.55f * kotlin.math.sqrt(areaRatio.coerceIn(0f, 1f)))
                    obj.labels.map { lbl ->
                        Candidate(
                            token = lbl.text.trim(),
                            score = (lbl.confidence * sizeBoost * 1.25f).coerceAtMost(1f),
                            source = Source.OBJECT_DIRECT
                        )
                    }
                }

                val cropped = objects.flatMap { obj ->
                    val crop = safeCrop(bmp, obj.boundingBox) ?: return@flatMap emptyList()
                    val areaRatio = (obj.boundingBox.width() * obj.boundingBox.height()).toFloat() / totalArea
                    val sizeBoost = (0.95f + 0.60f * kotlin.math.sqrt(areaRatio.coerceIn(0f, 1f)))
                    val cropImage = InputImage.fromBitmap(crop, 0)
                    runCatching {
                        labeler.process(cropImage).await()
                            .sortedByDescending { it.confidence }
                            // Crop-labels can be noisy; require higher confidence.
                            .filter { it.confidence >= 0.65f }
                            .take(3)
                            .map { lbl ->
                                val raw = lbl.text.trim()
                                val penalty = if (GENERIC_LABELS.contains(raw.lowercase(Locale.getDefault()))) 0.72f else 1.0f
                                Candidate(
                                    token = raw,
                                    score = (lbl.confidence * sizeBoost * 0.95f * penalty).coerceAtMost(1f),
                                    source = Source.OBJECT_CROP
                                )
                            }
                    }.getOrElse { emptyList() }
                }

                // Keep only solid object candidates.
                (direct + cropped)
                    .filter { it.token.isNotBlank() }
                    .filter { it.score >= 0.55f }
            }.getOrElse { emptyList() }

            val hasObjects = objectCandidates.isNotEmpty()

            // --- B0) OCR FIRST PASS (signals) ------------------------------------------------
            // OCR is often the most precise signal for electronics/photos of branded items.
            // We do a light pass here to detect strong domain hints (e.g., laptop brands),
            // then run the full OCR candidate extraction later.
            val (ocrRawLower, ocrHintTags) = runCatching {
                val raw = textRecognizer.process(image).await().text
                    .lowercase(Locale.getDefault())
                val tokens = raw
                    .replace(Regex("[^a-z0-9\\n ]"), " ")
                    .split(Regex("\\s+"))
                    .map { it.trim() }
                    .filter { it.length in 3..24 }
                    .filter { it.any { ch -> ch.isLetter() } }

                val hasLaptopHint = tokens.any { LAPTOP_HINTS.contains(it) || LAPTOP_BRANDS.contains(it) }
                raw to (if (hasLaptopHint) setOf("laptop") else emptySet())
            }.getOrElse { "" to emptySet() }

            // --- B) SCENE (only if very confident, or if we didn't find objects) --------------
            val sceneCandidates: List<Candidate> = runCatching {
                labeler.process(image).await()
                    .sortedByDescending { it.confidence }
                    .filter { lbl ->
                        val t = lbl.text.trim().lowercase(Locale.getDefault())
                        val c = lbl.confidence
                        // If objects exist, allow only very high-confidence scene/context labels.
                        val threshold = if (hasObjects) 0.78f else minConfidence
                        c >= threshold && !GENERIC_LABELS.contains(t) && !BAD_LABELS.contains(t)
                    }
                    .take(if (hasObjects) 2 else 5)
                    .map { lbl ->
                        Candidate(
                            token = lbl.text.trim(),
                            score = (lbl.confidence * 0.85f).coerceAtMost(1f),
                            source = Source.SCENE
                        )
                    }
            }.getOrElse { emptyList() }

            // --- C) OCR (only used as a precision booster; heavily filtered) -----------------
            val ocrCandidates: List<Candidate> = runCatching {
                val raw = if (ocrRawLower.isNotBlank()) ocrRawLower
                else textRecognizer.process(image).await().text.lowercase(Locale.getDefault())

                val tokens = raw
                    .replace(Regex("[^a-z0-9\\n ]"), " ")
                    .split(Regex("\\s+"))
                    .map { it.trim() }
                    .filter { it.length in 4..18 }
                    .filter { it.any { ch -> ch.isLetter() } }
                    .filterNot { STOP_WORDS.contains(it) }
                    .filterNot { GENERIC_LABELS.contains(it) }
                    .filterNot { BAD_LABELS.contains(it) }

                // Useful bigrams, but require at least one "strong" token.
                val bigrams = tokens.windowed(size = 2, step = 1, partialWindows = false)
                    .map { (a, b) -> "$a $b" }
                    .filter { it.length in 7..26 }
                    .filter { bg ->
                        val parts = bg.split(' ')
                        parts.any { it.length >= 6 }
                    }

                val hasLaptopHint = tokens.any { LAPTOP_HINTS.contains(it) || LAPTOP_BRANDS.contains(it) } || ocrHintTags.contains("laptop")

                // If we detect a laptop/computer hint via OCR (brands, model words), we keep more
                // OCR tokens because they're usually highly searchable ("lenovo", "thinkpad", etc.).
                // Otherwise keep OCR conservative to avoid noisy text.
                val ocrTake = when {
                    hasLaptopHint -> if (hasObjects) 6 else 8
                    hasObjects -> 2
                    else -> 4
                }

                val keep = (bigrams + tokens)
                    .distinct()
                    .take(ocrTake)

                val injected = if (hasLaptopHint) listOf("laptop", "computer", "keyboard") else emptyList()

                (keep + injected).distinct().map {
                    val baseScore = when {
                        hasLaptopHint -> if (hasObjects) 0.72f else 0.78f
                        hasObjects -> 0.62f
                        else -> 0.68f
                    }
                    Candidate(
                        token = it,
                        score = baseScore,
                        source = Source.OCR
                    )
                }
            }.getOrElse { emptyList() }

            // If OCR strongly indicates a laptop/computer, suppress common mislabels that are
            // frequent false-positives in desk/keyboard scenes.
            val ocrSuggestsLaptop = ocrCandidates.any { it.token.lowercase(Locale.getDefault()) in setOf("laptop", "computer", "keyboard") }
            val filteredScene = if (ocrSuggestsLaptop) {
                sceneCandidates.filterNot {
                    val t = it.token.lowercase(Locale.getDefault())
                    t in MUSIC_MISLABELS
                }
            } else sceneCandidates

            val all = (objectCandidates + filteredScene + ocrCandidates)

            // Expand + dedupe while keeping the BEST score per token.
            val scored = all
                .flatMap { c ->
                    expandTokens(normalizeToken(c.token)).map { t ->
                        Candidate(token = t, score = c.score, source = c.source)
                    }
                }
                .filter { it.token.isNotBlank() && it.token.length >= 2 }
                .filterNot { STOP_WORDS.contains(it.token) }
                .filterNot { GENERIC_LABELS.contains(it.token) }
                .filterNot { BAD_LABELS.contains(it.token) }
                .groupBy { it.token }
                .map { (t, xs) ->
                    // Prefer object sources when scores are similar.
                    val best = xs.maxByOrNull { it.score + sourceBoost(it.source) }!!
                    best.copy(token = t)
                }
                .sortedByDescending { it.score + sourceBoost(it.source) }

            // Ensure object-centric results dominate when we have them.
            val out = mutableListOf<String>()
            val objectFirst = scored.filter { it.source == Source.OBJECT_DIRECT || it.source == Source.OBJECT_CROP }
            val others = scored.filterNot { it.source == Source.OBJECT_DIRECT || it.source == Source.OBJECT_CROP }

            if (objectFirst.isNotEmpty()) {
                out += objectFirst.map { it.token }.take((max * 0.75f).toInt().coerceAtLeast(4))
            }
            out += (others.map { it.token }).filterNot { out.contains(it) }

            out.distinct().take(max)
        }.getOrElse { t ->
            Log.w("AiKeywordHelper", "ML Kit labeling failed for path=$photoPath", t)
            emptyList()
        }
    }

    private fun sourceBoost(s: Source): Float = when (s) {
        Source.OBJECT_DIRECT -> 0.12f
        Source.OBJECT_CROP -> 0.08f
        Source.SCENE -> 0.02f
        Source.OCR -> 0.04f
    }

    private fun normalizeToken(raw: String): String {
        // Keep it human-friendly and searchable.
        // IMPORTANT: ML Kit labels are often Title Case (e.g., "Cup").
        // Our previous sanitizer only allowed [a-z], which stripped the first capital letter
        // ("Cup" -> "up"). We lowercase first, then strip punctuation safely.
        val lower = raw.lowercase(Locale.getDefault())
        return lower
            .replace(Regex("[^a-z0-9 ]"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
    }

    private fun expandTokens(token: String): List<String> {
        // ML Kit sometimes returns multi-word labels like "mobile phone".
        // We keep:
        //  - the full phrase (good for search)
        //  - the head noun (usually the last meaningful word) to avoid spammy splits
        // We intentionally do NOT explode into every individual word.
        val words = token.split(' ').map { it.trim() }.filter { it.isNotBlank() }
        if (words.size <= 1) return listOf(token)
        val trimmed = words
            .map { it.lowercase(Locale.getDefault()) }
            .filterNot { STOP_WORDS.contains(it) }

        // Head noun: last word that is not a common modifier.
        val head = trimmed.lastOrNull { w -> w.length >= 3 && !MODIFIER_WORDS.contains(w) }
        return (listOf(token) + listOfNotNull(head)).distinct()
    }

    private companion object {
        private val STOP_WORDS = setOf(
            "a", "an", "the", "and", "or", "of", "to", "in", "on", "with",
            "object", "thing", "items", "item", "photo", "picture", "image"
        )

        // Labels that are common but usually not helpful as "object" keywords.
        // We filter these aggressively to improve precision.
        private val GENERIC_LABELS = setOf(
            "indoor", "indoors", "outdoor", "outdoors", "room", "floor", "ceiling",
            "wall", "furniture", "product", "goods", "material", "brand",
            "photography", "photo", "image", "picture",
            "art", "design", "pattern", "text", "font",
            "food", "meal", "cuisine", "dish",
            "plant", "flower", "tree", "nature",
            "person", "people", "human", "man", "woman", "child"
        )

        // Frequent false-positives / low-value labels we never want as user-facing clues.
        private val BAD_LABELS = setOf(
            "jumper",
            "musical instrument",
            "instrument",
            "string instrument",
            "guitar",
            "music"
        )

        // Subset used specifically to suppress scene mislabels when OCR indicates a laptop/desk.
        private val MUSIC_MISLABELS = setOf(
            "musical instrument",
            "instrument",
            "string instrument",
            "guitar",
            "music"
        )

        // Words that are usually modifiers and become noisy when split out.
        private val MODIFIER_WORDS = setOf(
            "mobile", "portable", "wireless", "digital", "electric", "electronic",
            "small", "large", "black", "white", "silver", "blue", "red"
        )

        // OCR hints that strongly imply the photo is of a computer/laptop setup.
        private val LAPTOP_HINTS = setOf(
            "laptop", "notebook", "thinkpad", "macbook", "keyboard", "touchpad", "windows"
        )

        private val LAPTOP_BRANDS = setOf(
            "lenovo", "dell", "hp", "asus", "acer", "apple", "microsoft",
            "intel", "amd", "nvidia", "thinkpad", "ideapad"
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
