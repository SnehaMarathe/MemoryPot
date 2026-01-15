package com.memorypot.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.compose.material3.OutlinedButton
import com.memorypot.data.repo.LocationHelper
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.memorypot.data.repo.PhotoStore
import com.memorypot.di.LocalAppContainer
import com.memorypot.ui.components.SimpleTopBar
import com.memorypot.viewmodel.AddMemoryViewModel
import com.memorypot.viewmodel.AddVmFactory
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executor

private enum class AddStep { CAMERA, EDIT }

@Composable
fun AddMemoryScreen(
    onDone: (String) -> Unit,
    onCancel: () -> Unit
) {
    val container = LocalAppContainer.current
    val vm: AddMemoryViewModel = viewModel(factory = AddVmFactory(container.repository, container.aiKeywordHelper))
    val state by vm.state.collectAsState()

    val context = LocalContext.current
    val photoStore = remember { PhotoStore(context) }
    val scope = rememberCoroutineScope()

    var step by remember { mutableStateOf(AddStep.CAMERA) }
    var capturedPath by remember { mutableStateOf<String?>(null) }

    // Location permission (optional, if the user enabled Save location in Settings)
    val saveLocationEnabled by container.settings.saveLocationFlow.collectAsState(initial = true)
    val locationHelper = remember { LocationHelper(context) }
    val locationPermLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { /* repo will read permission at save time */ }

    Scaffold(
        topBar = {
            SimpleTopBar(title = if (step == AddStep.CAMERA) "Add Memory" else "Details")
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            when (step) {
                AddStep.CAMERA -> {
                    CameraCapture(
                        onBack = onCancel,
                        onCaptured = { path ->
                            capturedPath = path
                            step = AddStep.EDIT
                        },
                        photoStore = photoStore
                    )
                }
                AddStep.EDIT -> {
                    val photoPath = capturedPath
                    if (photoPath == null) {
                        Text("No photo captured.")
                        return@Column
                    }

                    // Auto-generate AI keywords once per capture
                    LaunchedEffect(photoPath) {
                        vm.generateKeywords(photoPath)
                    }

                    Column(
                        Modifier.fillMaxSize().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AsyncImage(
                            model = photoPath,
                            contentDescription = "Preview",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                        )

                        OutlinedTextField(
                            value = state.label,
                            onValueChange = vm::updateLabel,
                            label = { Text("Label (recommended)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = state.note,
                            onValueChange = vm::updateNote,
                            label = { Text("Note") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = state.placeText,
                            onValueChange = vm::updatePlace,
                            label = { Text("Place (auto-filled if location available)") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (saveLocationEnabled && !locationHelper.hasLocationPermission()) {
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Location is enabled, but permission is missing.")
                                    Text(
                                        "Grant location permission to auto-fill GPS coordinates and place.",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Button(onClick = {
                                        locationPermLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.ACCESS_FINE_LOCATION,
                                                Manifest.permission.ACCESS_COARSE_LOCATION
                                            )
                                        )
                                    }) {
                                        Text("Grant location permission")
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = state.keywords,
                            onValueChange = vm::updateKeywords,
                            label = { Text("Keywords (AI suggested, editable)") },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("e.g., beach, sunset, friends") }
                        )

                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = state.keywordPrompt,
                                onValueChange = vm::updateKeywordPrompt,
                                label = { Text("Add more keywords") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                placeholder = { Text("Type and tap Apply") }
                            )
                            Button(onClick = vm::applyKeywordPrompt) {
                                Text("Apply")
                            }
                        }

                        if (state.isGeneratingKeywords) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(8.dp))
                                Text("Generating keywords…")
                            }
                        }

                        if (state.error != null) {
                            Text(state.error!!, color = MaterialTheme.colorScheme.error)
                        }

                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = {
                                // user abandons -> delete captured file
                                runCatching { File(photoPath).delete() }
                                onCancel()
                            }) {
                                Text("Cancel")
                            }

                            Button(
                                onClick = {
                                    vm.save(photoPath, onDone)
                                },
                                enabled = !state.isSaving
                            ) {
                                if (state.isSaving) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.size(8.dp))
                                }
                                Text("Save")
                            }
                        }

                        // Lightweight suggestions hint (optional): show top 1 under place field when label changes.
                        LaunchedEffect(state.label) {
                            val l = state.label.trim()
                            if (l.isNotBlank()) {
                                val sugg = vm.suggestionsForLabel(l)
                                if (sugg.isNotEmpty() && state.placeText.isBlank()) {
                                    // Don't force; just prefill gently with best guess.
                                    vm.updatePlace(sugg.first().placeText)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraCapture(
    onBack: () -> Unit,
    onCaptured: (String) -> Unit,
    photoStore: PhotoStore
) {
    val context = LocalContext.current
    val executor: Executor = ContextCompat.getMainExecutor(context)
    val hasCamera = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    Box(Modifier.fillMaxSize()) {
        if (!hasCamera) {
            Column(
                Modifier.fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Camera permission is required to capture a photo.")
                Spacer(Modifier.height(12.dp))
                Text("Go to Settings → Apps → Memory Pot → Permissions and enable Camera.")
                Spacer(Modifier.height(12.dp))
                Button(onClick = onBack) { Text("Back") }
            }
            return@Box
        }

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }

                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val capture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()
                    imageCapture = capture
                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            (ctx as androidx.activity.ComponentActivity),
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            capture
                        )
                    } catch (_: Throwable) { }
                }, executor)

                previewView
            }
        )

        Row(
            Modifier.align(Alignment.TopStart).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
        }

        Button(
            onClick = {
                val capture = imageCapture ?: return@Button
                val file = photoStore.newPhotoFile()
                val output = ImageCapture.OutputFileOptions.Builder(file).build()
                capture.takePicture(
                    output,
                    executor,
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            onCaptured(file.absolutePath)
                        }

                        override fun onError(exception: ImageCaptureException) {
                            runCatching { file.delete() }
                        }
                    }
                )
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(20.dp)
        ) {
            Text("Capture")
        }
    }
}
