package com.memorypot.camera

import android.content.Context
import android.graphics.Rect
import android.graphics.RectF
import android.util.Size
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.ObjectDetectorOptions
import com.google.mlkit.vision.objects.ObjectDetection
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ImageAnalysis analyzer that runs ML Kit Object Detection in STREAM_MODE and reports
 * mapped boxes in PreviewView coordinates + normalized boxes for persistence.
 */
class LiveObjectPicker(
    private val context: Context,
    private val getPreviewSizePx: () -> Size, // PreviewView size in px
    private val onResults: (viewBoxes: List<DetectedObjectBox>, normalizedBoxes: List<NormalizedBox>) -> Unit
) : ImageAnalysis.Analyzer {

    private val busy = AtomicBoolean(false)

    private val detector: ObjectDetector by lazy {
        val options = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableMultipleObjects()
            .enableClassification()
            .build()
        ObjectDetection.getClient(options)
    }

    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        // Drop frames if ML is still running.
        if (!busy.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        val rotation = imageProxy.imageInfo.rotationDegrees
        val inputImage = InputImage.fromMediaImage(mediaImage, rotation)

        detector.process(inputImage)
            .addOnSuccessListener { objects ->
                val previewSize = getPreviewSizePx()

                // ML Kit bounding boxes are in the upright coordinate space of the input image.
                val imgW: Int
                val imgH: Int
                if (rotation == 90 || rotation == 270) {
                    imgW = inputImage.height
                    imgH = inputImage.width
                } else {
                    imgW = inputImage.width
                    imgH = inputImage.height
                }

                val (scale, dx, dy) = computeScaleAndOffsets(
                    viewW = previewSize.width.toFloat(),
                    viewH = previewSize.height.toFloat(),
                    imgW = imgW.toFloat(),
                    imgH = imgH.toFloat()
                )

                val viewBoxes = mutableListOf<DetectedObjectBox>()
                val normalizedBoxes = mutableListOf<NormalizedBox>()

                for (obj in objects) {
                    val id = obj.trackingId ?: obj.hashCode()
                    val bb: Rect = obj.boundingBox

                    val left = bb.left.toFloat()
                    val top = bb.top.toFloat()
                    val right = bb.right.toFloat()
                    val bottom = bb.bottom.toFloat()

                    val rectView = RectF(
                        left * scale + dx,
                        top * scale + dy,
                        right * scale + dx,
                        bottom * scale + dy
                    )

                    val label = obj.labels.firstOrNull()?.text
                    val conf = obj.labels.firstOrNull()?.confidence

                    viewBoxes.add(
                        DetectedObjectBox(
                            trackingId = id,
                            rect = rectView,
                            label = label,
                            confidence = conf
                        )
                    )

                    normalizedBoxes.add(
                        NormalizedBox(
                            trackingId = id,
                            left = (left / imgW).coerceIn(0f, 1f),
                            top = (top / imgH).coerceIn(0f, 1f),
                            right = (right / imgW).coerceIn(0f, 1f),
                            bottom = (bottom / imgH).coerceIn(0f, 1f),
                            label = label,
                            confidence = conf
                        )
                    )
                }

                onResults(viewBoxes, normalizedBoxes)
            }
            .addOnFailureListener {
                // Ignore; keep analyzer alive.
            }
            .addOnCompleteListener {
                busy.set(false)
                imageProxy.close()
            }
    }

    private fun computeScaleAndOffsets(viewW: Float, viewH: Float, imgW: Float, imgH: Float): Triple<Float, Float, Float> {
        // Center-crop-like mapping: fit image inside view while maintaining aspect ratio.
        val scale = minOf(viewW / imgW, viewH / imgH)
        val scaledW = imgW * scale
        val scaledH = imgH * scale
        val dx = (viewW - scaledW) / 2f
        val dy = (viewH - scaledH) / 2f
        return Triple(scale, dx, dy)
    }
}
