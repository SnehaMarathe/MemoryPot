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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.util.concurrent.Executor

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
    val executor: Executor = remember { ContextCompat.getMainExecutor(context) }

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FIT_CENTER
        }
    }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

    LaunchedEffect(Unit) {
        onImageCaptureReady(imageCapture)
    }

    DisposableEffect(Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val listener = Runnable {
            val cameraProvider = cameraProviderFuture.get()
            bindCamera(
                context = context,
                lifecycleOwner = lifecycleOwner,
                cameraProvider = cameraProvider,
                previewView = previewView,
                executor = executor,
                imageCapture = imageCapture,
                onPickerResults = onPickerResults
            )
        }
        cameraProviderFuture.addListener(listener, executor)

        onDispose {
            try {
                val provider = cameraProviderFuture.get()
                provider.unbindAll()
            } catch (_: Throwable) {
            }
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
    executor: Executor,
    imageCapture: ImageCapture,
    onPickerResults: (List<DetectedObjectBox>, List<NormalizedBox>) -> Unit
) {
    cameraProvider.unbindAll()

    val preview = Preview.Builder().build().also {
        it.setSurfaceProvider(previewView.surfaceProvider)
    }

    val analysis = ImageAnalysis.Builder()
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .build()

    val picker = LiveObjectPicker(
        getPreviewSizePx = { Size(previewView.width.coerceAtLeast(1), previewView.height.coerceAtLeast(1)) },
        onResults = onPickerResults
    )

    analysis.setAnalyzer(executor, picker)

    val selector = CameraSelector.DEFAULT_BACK_CAMERA

    cameraProvider.bindToLifecycle(
        lifecycleOwner,
        selector,
        preview,
        imageCapture,
        analysis
    )
}
