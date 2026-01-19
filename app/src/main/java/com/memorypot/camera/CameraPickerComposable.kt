package com.memorypot.camera

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Live CameraX preview with ML Kit object detection in stream mode.
 * Users can tap to toggle-select multiple objects.
 */
@Composable
fun LiveCameraMultiSelectPicker(
    modifier: Modifier = Modifier,
    viewModel: CameraPickerViewModel,
    onImageCaptureReady: (ImageCapture) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val boxes by viewModel.boxes.collectAsState()
    val selected by viewModel.selected.collectAsState()

    val previewView = remember {
        PreviewView(context).apply {
            // Keep consistent with our mapping math (FIT_CENTER).
            scaleType = PreviewView.ScaleType.FIT_CENTER
        }
    }

    var analysisExecutor: ExecutorService? by remember { mutableStateOf(null) }
    var detector: ObjectDetector? by remember { mutableStateOf(null) }

    DisposableEffect(Unit) {
        analysisExecutor = Executors.newSingleThreadExecutor()
        detector = ObjectDetection.getClient(
            ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
                .enableMultipleObjects()
                .enableClassification()
                .build()
        )

        onDispose {
            runCatching { analysisExecutor?.shutdown() }
            runCatching { detector?.close() }
        }
    }

    LaunchedEffect(Unit) {
        val det = detector
        val exec = analysisExecutor
        if (det != null && exec != null) {
            bindCamera(
                context = context,
                lifecycleOwner = lifecycleOwner,
                previewView = previewView,
                detector = det,
                analysisExecutor = exec,
                viewModel = viewModel,
                onImageCaptureReady = onImageCaptureReady
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { pos ->
                    viewModel.toggleSelectionAt(pos.x, pos.y)
                }
            }
    ) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // Overlay with boxes + selection.
        Canvas(modifier = Modifier.fillMaxSize()) {
            // subtle dim to make boxes visible
            drawRect(color = Color.Black.copy(alpha = 0.06f))

            boxes.forEach { b ->
                val r = b.rect
                val isSel = selected.contains(b.trackingId)
                val strokeW = if (isSel) 6f else 3f
                val color = if (isSel) Color(0xFF00E676) else Color.White

                drawRect(
                    color = color,
                    topLeft = Offset(r.left, r.top),
                    size = Size((r.right - r.left).coerceAtLeast(0f), (r.bottom - r.top).coerceAtLeast(0f)),
                    style = Stroke(width = strokeW)
                )
            }
        }

        // Touch debug indicator (keeps overlay clickable and transparent)
        Box(modifier = Modifier.fillMaxSize().background(Color.Transparent))
    }
}

@SuppressLint("UnsafeOptInUsageError")
private fun bindCamera(
    context: Context,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    previewView: PreviewView,
    detector: ObjectDetector,
    analysisExecutor: ExecutorService,
    viewModel: CameraPickerViewModel,
    onImageCaptureReady: (ImageCapture) -> Unit,
) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    cameraProviderFuture.addListener({
        val cameraProvider = cameraProviderFuture.get()

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        val imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()

        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        analysis.setAnalyzer(analysisExecutor) { imageProxy ->
            val mediaImage = imageProxy.image
            if (mediaImage == null) {
                imageProxy.close(); return@setAnalyzer
            }

            val rotation = imageProxy.imageInfo.rotationDegrees
            val input = InputImage.fromMediaImage(mediaImage, rotation)

            detector.process(input)
                .addOnSuccessListener { objects ->
                    val viewW = previewView.width.toFloat().coerceAtLeast(1f)
                    val viewH = previewView.height.toFloat().coerceAtLeast(1f)

                    val rawW = input.width
                    val rawH = input.height

                    // After rotation, upright image size becomes:
                    val imgW: Int
                    val imgH: Int
                    if (rotation == 90 || rotation == 270) {
                        imgW = rawH
                        imgH = rawW
                    } else {
                        imgW = rawW
                        imgH = rawH
                    }

                    // FIT_CENTER mapping
                    val scale = minOf(viewW / imgW.toFloat(), viewH / imgH.toFloat())
                    val dx = (viewW - imgW * scale) / 2f
                    val dy = (viewH - imgH * scale) / 2f

                    val mapped = objects.map { obj ->
                        val bb = obj.boundingBox
                        val id = obj.trackingId ?: obj.hashCode()

                        val label = obj.labels.maxByOrNull { it.confidence }?.text
                        val conf = obj.labels.maxByOrNull { it.confidence }?.confidence

                        val rectView = android.graphics.RectF(
                            bb.left.toFloat() * scale + dx,
                            bb.top.toFloat() * scale + dy,
                            bb.right.toFloat() * scale + dx,
                            bb.bottom.toFloat() * scale + dy
                        )

                        DetectedBox(
                            trackingId = id,
                            rect = rectView,
                            label = label,
                            confidence = conf
                        )
                    }

                    viewModel.updateDetections(mapped)
                }
                .addOnFailureListener { e ->
                    Log.w("LivePicker", "ML Kit detection failed", e)
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        }

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture,
                analysis
            )
            onImageCaptureReady(imageCapture)
        } catch (e: Exception) {
            Log.e("LivePicker", "Camera bind failed", e)
        }
    }, ContextCompat.getMainExecutor(context))
}

fun takePhoto(
    context: Context,
    imageCapture: ImageCapture,
    onResult: (Uri?, String?) -> Unit
) {
    val photosDir = File(context.filesDir, "photos").apply { mkdirs() }
    val file = File(photosDir, "mem_${System.currentTimeMillis()}.jpg")

    val output = ImageCapture.OutputFileOptions.Builder(file).build()
    imageCapture.takePicture(
        output,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                onResult(Uri.fromFile(file), file.absolutePath)
            }

            override fun onError(exception: ImageCaptureException) {
                onResult(null, null)
            }
        }
    )
}
