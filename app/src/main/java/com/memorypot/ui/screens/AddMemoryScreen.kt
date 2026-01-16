package com.memorypot.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.core.content.ContextCompat
import androidx.compose.material3.OutlinedButton
import com.memorypot.data.repo.LocationHelper
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.memorypot.data.repo.PhotoStore
import com.memorypot.di.LocalAppContainer
import com.memorypot.ui.components.AppTopBar
import com.memorypot.ui.components.IOSBottomActionBar
import com.memorypot.ui.components.IOSGroupedSurface
import com.memorypot.ui.components.IOSSectionHeader
import com.memorypot.ui.components.KeywordEditor
import com.memorypot.viewmodel.AddMemoryViewModel
import com.memorypot.viewmodel.AddVmFactory
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executor

private enum class AddStep { CAMERA, REFLECT, DETAILS }

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
            val title = when (step) {
                AddStep.CAMERA -> "Capture"
                AddStep.REFLECT -> "Reflect"
                AddStep.DETAILS -> "Details"
            }
            AppTopBar(title = title, onBack = {
                when (step) {
                    AddStep.CAMERA -> onCancel()
                    AddStep.REFLECT -> {
                        // Go back to camera, keep the captured photo so the user can retake.
                        step = AddStep.CAMERA
                    }
                    AddStep.DETAILS -> {
                        step = AddStep.REFLECT
                    }
                }
            })
        },
        bottomBar = {
            val photoPath = capturedPath
            if (photoPath != null && step != AddStep.CAMERA) {
                IOSBottomActionBar {
                    when (step) {
                        AddStep.REFLECT -> {
                            OutlinedButton(
                                onClick = { step = AddStep.CAMERA },
                                modifier = Modifier.weight(1f)
                            ) { Text("Retake") }
                            Button(
                                onClick = { step = AddStep.DETAILS },
                                modifier = Modifier.weight(1f)
                            ) { Text("Next") }
                        }
                        AddStep.DETAILS -> {
                            OutlinedButton(
                                onClick = { step = AddStep.REFLECT },
                                modifier = Modifier.weight(1f)
                            ) { Text("Back") }
                            Button(
                                onClick = { vm.save(photoPath, onDone) },
                                enabled = !state.isSaving,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (state.isSaving) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.size(8.dp))
                                }
                                Text("Save")
                            }
                        }
                        else -> Unit
                    }
                }
            }
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            when (step) {
                AddStep.CAMERA -> {
                    CameraCapture(
                        onBack = onCancel,
                        onCaptured = { path ->
                            capturedPath = path
                            step = AddStep.REFLECT
                        },
                        photoStore = photoStore
                    )
                }
                AddStep.REFLECT, AddStep.DETAILS -> {
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
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp)
                            .imePadding()
                            .navigationBarsPadding(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AddStepIndicator(step = step)

                        // Scrollable content (keeps actions always reachable on small screens)
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            item {
                                AsyncImage(
                                    model = photoPath,
                                    contentDescription = "Preview",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(4f / 3f)
                                        .heightIn(max = 320.dp)
                                )
                            }

                            if (step == AddStep.REFLECT) {
                                item {
                                    IOSSectionHeader("✨ WHAT STANDS OUT?")
                                    Text(
                                        "These clues can help you find this memory later.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                }

                                item {
                                    KeywordEditor(
                                        keywords = state.keywords,
                                        onKeywordsChange = vm::updateKeywords,
                                        prompt = state.keywordPrompt,
                                        onPromptChange = vm::updateKeywordPrompt,
                                        onApplyPrompt = vm::applyKeywordPrompt,
                                        title = "Memory Clues",
                                        supportingText = "On-device AI suggestions. Tap × to remove, or add your own clues.",
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                item {
                                    AnimatedVisibility(
                                        visible = state.isGeneratingKeywords,
                                        enter = fadeIn() + expandVertically(),
                                        exit = fadeOut() + shrinkVertically()
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            CircularProgressIndicator(modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.size(8.dp))
                                            Text("Generating clues…")
                                        }
                                    }
                                }

                                item {
                                    IOSSectionHeader("📝 WANT TO SAY SOMETHING?")
                                    IOSGroupedSurface {
                                        Column(Modifier.padding(horizontal = 12.dp)) {
                                            OutlinedTextField(
                                                value = state.note,
                                                onValueChange = vm::updateNote,
                                                label = { Text("Optional note") },
                                                placeholder = { Text("A quick sentence you’ll appreciate later…") },
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }
                            }

                            if (step == AddStep.DETAILS) {
                                item {
                                    IOSSectionHeader("DETAILS")
                                    IOSGroupedSurface {
                                        Column(Modifier.padding(horizontal = 12.dp)) {
                                            OutlinedTextField(
                                                value = state.label,
                                                onValueChange = vm::updateLabel,
                                                label = { Text("Title") },
                                                supportingText = { Text("Short and memorable. (Optional)") },
                                                modifier = Modifier.fillMaxWidth(),
                                                singleLine = true
                                            )
                                            Spacer(Modifier.height(10.dp))
                                            OutlinedTextField(
                                                value = state.placeText,
                                                onValueChange = vm::updatePlace,
                                                label = { Text("Place") },
                                                supportingText = { Text("Auto-filled if location is available.") },
                                                placeholder = { Text("Where was this?") },
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }
                            }

                            if (saveLocationEnabled && !locationHelper.hasLocationPermission()) {
                                item {
                                    Card(Modifier.fillMaxWidth()) {
                                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text("Enable location permission")
                                            Text(
                                                "To auto-save GPS + place, allow location access.",
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
                                                Text("Grant permission")
                                            }
                                        }
                                    }
                                }
                            }

                            if (state.error != null) {
                                item {
                                    Text(state.error!!, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }

                        // Lightweight suggestions hint: only prefill when place is empty.
                        LaunchedEffect(state.label) {
                            val l = state.label.trim()
                            if (l.isNotBlank()) {
                                val sugg = vm.suggestionsForLabel(l)
                                if (sugg.isNotEmpty() && state.placeText.isBlank()) {
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
private fun AddStepIndicator(step: AddStep) {
    val current = when (step) {
        AddStep.CAMERA -> 0
        AddStep.REFLECT -> 1
        AddStep.DETAILS -> 2
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { idx ->
            val isActive = idx == current
            Box(
                modifier = Modifier
                    .padding(horizontal = 6.dp)
                    .size(if (isActive) 10.dp else 8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (isActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant
                    )
            )
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

        FloatingActionButton(
            onClick = {
                val capture = imageCapture ?: return@FloatingActionButton
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
                .padding(bottom = 28.dp)
        ) {
            Icon(Icons.Default.CameraAlt, contentDescription = "Capture")
        }
    }
}
