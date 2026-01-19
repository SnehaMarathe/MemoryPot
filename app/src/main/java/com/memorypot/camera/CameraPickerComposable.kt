package com.memorypot.camera

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
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

@Composable
fun LiveCameraMultiSelectPicker(
    modifier: Modifier = Modifier,
    viewModel: CameraPickerViewModel,
    onImageCaptureReady: (ImageCapture) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val boxes by viewModel.boxes.collectAsState()
    val selectedIds by viewModel.selectedIds.collectAsState()

    val previewView = remember {
        PreviewView(context).apply {
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
        bindCamera(
            context = context,
            lifecycleOwner = lifecycleOwner,
            previewView = previewView,
            detector = detector!!,
            analysisExecutor = analysisExecutor!!,
            viewModel = viewModel,
            onImageCaptureReady = onImageCaptureReady
        )
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

        // Transparent overlay
        Canvas(modifier = Modifier.fillMaxSize()) {
            // subtle dim to make boxes visible
            drawRect(Color.Black.copy(alpha = 0.08f))

            boxes.forEach { b ->
                val isSel = selectedIds.contains(b.id)
                val stroke = if (isSel) 6f else 3f
                val color = if (isSel) Color(0xFF00E676) else Color.White

                // stroke rectangle
                drawRect(
                    color = color,
                    topLeft = Offset(b.left, b.top),
                    size = Size((b.right - b.left).coerceAtLeast(0f), (b.bottom - b.top).coerceAtLeast(0f)),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke)
                )
            }
        }

        // touch debug indicator (optional background)
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

                    // ML Kit boxes are in the upright image coordinate space.
                    val rawW = input.width
                    val rawH = input.height

                    val imgW: Float
                    val imgH: Float
                    if (rotation == 90 || rotation == 270) {
                        imgW = rawH.toFloat()
                        imgH = rawW.toFloat()
                    } else {
                        imgW = rawW.toFloat()
                        imgH = rawH.toFloat()
                    }

                    // FIT_CENTER mapping
                    val scale = minOf(viewW / imgW, viewH / imgH)
                    val dx = (viewW - imgW * scale) / 2f
                    val dy = (viewH - imgH * scale) / 2f

                    val mapped = objects.mapNotNull { obj ->
                        val bb = obj.boundingBox ?: return@mapNotNull null
                        val id = obj.trackingId ?: (bb.left * 31 + bb.top).hashCode()
                        val (label, conf) = viewModel.bestLabelForObject(obj)

                        val l = bb.left.toFloat() * scale + dx
                        val t = bb.top.toFloat() * scale + dy
                        val r = bb.right.toFloat() * scale + dx
                        val b = bb.bottom.toFloat() * scale + dy

                        DetectedBox(id = id, left = l, top = t, right = r, bottom = b, label = label, confidence = conf)
                    }

                    viewModel.updateBoxes(mapped)
                }
                .addOnFailureListener {
                    // swallow; analyzer is best-effort
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
        } catch (_: Exception) {
            // no-op
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
