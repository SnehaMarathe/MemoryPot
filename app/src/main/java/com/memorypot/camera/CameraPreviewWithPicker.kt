package com.memorypot.camera

import android.content.Context
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Hosts a CameraX PreviewView and wires ImageAnalysis to [LiveObjectPicker].
 * Exposes the bound [ImageCapture] so the caller can take a photo.
 */
@Composable
fun CameraPreviewWithPicker(
    modifier: Modifier = Modifier,
    onImageCaptureReady: (ImageCapture) -> Unit,
    onPickerResults: (viewBoxes: List<DetectedObjectBox>, normalizedBoxes: List<NormalizedBox>) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // CameraX + ML Kit analysis should not run on the main thread.
    val mainExecutor: Executor = remember { ContextCompat.getMainExecutor(context) }
    var analysisExecutor: ExecutorService? by remember { mutableStateOf(null) }

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FIT_CENTER
        }
    }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            // Help avoid "no supported surface combination" crashes on some devices.
            .setTargetResolution(Size(1280, 720))
            .build()
    }

    LaunchedEffect(Unit) {
        onImageCaptureReady(imageCapture)
    }

    DisposableEffect(Unit) {
        analysisExecutor = Executors.newSingleThreadExecutor()
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val listener = Runnable {
            val cameraProvider = cameraProviderFuture.get()
            bindCamera(
                context = context,
                lifecycleOwner = lifecycleOwner,
                cameraProvider = cameraProvider,
                previewView = previewView,
                mainExecutor = mainExecutor,
                analysisExecutor = analysisExecutor ?: return@Runnable,
                imageCapture = imageCapture,
                onPickerResults = onPickerResults
            )
        }
        cameraProviderFuture.addListener(listener, mainExecutor)

        onDispose {
            try {
                val provider = cameraProviderFuture.get()
                provider.unbindAll()
            } catch (_: Throwable) {
            }
            runCatching { analysisExecutor?.shutdown() }
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { previewView }
    )
}

private fun bindCamera(
    context: Context,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    cameraProvider: ProcessCameraProvider,
    previewView: PreviewView,
    mainExecutor: Executor,
    analysisExecutor: Executor,
    imageCapture: ImageCapture,
    onPickerResults: (List<DetectedObjectBox>, List<NormalizedBox>) -> Unit
) {
    cameraProvider.unbindAll()

    val preview = Preview.Builder().build().also {
        it.setSurfaceProvider(previewView.surfaceProvider)
    }

    val analysis = ImageAnalysis.Builder()
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        // Match capture resolution to reduce surface combination failures.
        .setTargetResolution(Size(1280, 720))
        .build()

    val picker = LiveObjectPicker(
        getPreviewSizePx = { Size(previewView.width.coerceAtLeast(1), previewView.height.coerceAtLeast(1)) },
        onResults = onPickerResults
    )

    analysis.setAnalyzer(analysisExecutor, picker)

    val selector = CameraSelector.DEFAULT_BACK_CAMERA

    try {
        cameraProvider.bindToLifecycle(
            lifecycleOwner,
            selector,
            preview,
            imageCapture,
            analysis
        )
    } catch (t: Throwable) {
        // If binding fails at runtime, avoid crashing the app.
        // The UI will remain visible; callers can show an error if desired.
        t.printStackTrace()
    }
}
