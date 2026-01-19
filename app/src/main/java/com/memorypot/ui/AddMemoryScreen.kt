package com.memorypot.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.RectF
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.memorypot.di.LocalAppContainer
import com.memorypot.viewmodel.AddVmFactory
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private data class LiveDet(
    val id: Int,
    val boxNorm: RectF,
    val label: String?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMemoryScreen(
    onDone: (String) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val vm: AddMemoryViewModel = viewModel(factory = AddVmFactory(container.repository, container.aiKeywordHelper))

    val permissionGranted = remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> permissionGranted.value = granted }

    if (!permissionGranted.value) {
        val activity = context.findActivity()
        val permanentlyDenied = activity?.let {
            !ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA)
        } == true

        Scaffold(topBar = { TopAppBar(title = { Text("Camera") }) }) { pad ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(pad)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Camera permission is required to capture and select objects.")
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Grant camera permission")
                }
                if (permanentlyDenied) {
                    OutlinedButton(onClick = { context.openAppSettings() }) { Text("Open settings") }
                }
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
        }
        return
    }

    CameraCaptureAndPick(
        onBack = onCancel,
        onSaveSubmit = { path, selections ->
            vm.save(path, selections, onDone)
        }
    )
}

@Composable
private fun CameraCaptureAndPick(
    onBack: () -> Unit,
    onSaveSubmit: (String, List<RectF>) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    var detections by remember { mutableStateOf<List<LiveDet>>(emptyList()) }
    val selectedIds = remember { mutableStateListOf<Int>() }

    val detector = remember {
        val opts = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableMultipleObjects()
            .enableClassification()
            .build()
        ObjectDetection.getClient(opts)
    }

    DisposableEffect(Unit) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        val cancelled = AtomicBoolean(false)

        val listener = Runnable {
            if (cancelled.get()) return@Runnable
            val provider = providerFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            analysis.setAnalyzer(analysisExecutor) { proxy ->
                analyzeFrame(detector, proxy) { dets ->
                    detections = dets
                    val alive = dets.map { it.id }.toSet()
                    selectedIds.removeAll { it !in alive }
                }
            }

            imageCapture = capture

            try {
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    capture,
                    analysis
                )
            } catch (_: Throwable) {
                // If binding fails, preview stays blank.
            }
        }

        providerFuture.addListener(listener, ContextCompat.getMainExecutor(context))

        onDispose {
            cancelled.set(true)
            try { providerFuture.get().unbindAll() } catch (_: Throwable) {}
            detector.close()
            cameraExecutor.shutdown()
            analysisExecutor.shutdown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Capture") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { pad ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(pad)
        ) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(detections) {
                        detectTapGestures { pos ->
                            val w = constraints.maxWidth.toFloat().coerceAtLeast(1f)
                            val h = constraints.maxHeight.toFloat().coerceAtLeast(1f)
                            val hit = detections.asReversed().firstOrNull { d ->
                                val r = d.boxNorm
                                val left = r.left * w
                                val top = r.top * h
                                val right = r.right * w
                                val bottom = r.bottom * h
                                pos.x in left..right && pos.y in top..bottom
                            }
                            if (hit != null) {
                                if (selectedIds.contains(hit.id)) selectedIds.remove(hit.id) else selectedIds.add(hit.id)
                            }
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    detections.forEach { d ->
                        val r = d.boxNorm
                        val left = r.left * w
                        val top = r.top * h
                        val right = r.right * w
                        val bottom = r.bottom * h
                        val isSel = selectedIds.contains(d.id)
                        drawRect(
                            color = if (isSel) Color(0xAA00FF00) else Color(0xAAFFFFFF),
                            topLeft = Offset(left, top),
                            size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = if (isSel) 6f else 3f)
                        )
                    }
                }
            }

            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color(0x66000000))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Selected: ${selectedIds.size}",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )
                    OutlinedButton(
                        onClick = { selectedIds.clear() },
                        enabled = selectedIds.isNotEmpty(),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Clear") }
                }

                Button(
                    onClick = {
                        val cap = imageCapture ?: return@Button
                        takePhoto(context, cap, cameraExecutor) { path ->
                            val sel = detections.filter { selectedIds.contains(it.id) }.map { it.boxNorm }
                            onSaveSubmit(path, sel)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Save & Submit")
                }
            }
        }
    }
}

private fun takePhoto(
    context: Context,
    capture: ImageCapture,
    executor: java.util.concurrent.Executor,
    onSaved: (String) -> Unit
) {
    val dir = File(context.filesDir, "photos").apply { mkdirs() }
    val file = File(dir, "MP_${System.currentTimeMillis()}.jpg")

    val opts = ImageCapture.OutputFileOptions.Builder(file).build()
    capture.takePicture(
        opts,
        executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                onSaved(file.absolutePath)
            }

            override fun onError(exception: ImageCaptureException) {
                // no-op
            }
        }
    )
}

private fun analyzeFrame(
    detector: com.google.mlkit.vision.objects.ObjectDetector,
    proxy: ImageProxy,
    onResult: (List<LiveDet>) -> Unit
) {
    val media = proxy.image
    if (media == null) {
        proxy.close();
        return
    }

    val rotation = proxy.imageInfo.rotationDegrees
    val image = InputImage.fromMediaImage(media, rotation)

    detector.process(image)
        .addOnSuccessListener { objs ->
            val iw = if (rotation == 90 || rotation == 270) proxy.height else proxy.width
            val ih = if (rotation == 90 || rotation == 270) proxy.width else proxy.height

            val dets = objs.mapIndexed { idx, o ->
                val bb = o.boundingBox
                val left = (bb.left.toFloat() / iw).coerceIn(0f, 1f)
                val top = (bb.top.toFloat() / ih).coerceIn(0f, 1f)
                val right = (bb.right.toFloat() / iw).coerceIn(0f, 1f)
                val bottom = (bb.bottom.toFloat() / ih).coerceIn(0f, 1f)
                val label = o.labels.firstOrNull()?.text
                LiveDet(id = o.trackingId ?: idx, boxNorm = RectF(left, top, right, bottom), label = label)
            }
            onResult(dets)
        }
        .addOnFailureListener { onResult(emptyList()) }
        .addOnCompleteListener { proxy.close() }
}

private fun Context.openAppSettings() {
    val act = findActivity() ?: return
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", act.packageName, null)
    }
    act.startActivity(intent)
}

private fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
