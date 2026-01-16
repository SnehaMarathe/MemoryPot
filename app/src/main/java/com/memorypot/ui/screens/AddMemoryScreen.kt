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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.memorypot.data.repo.LocationHelper
import com.memorypot.data.repo.PhotoStore
import com.memorypot.di.LocalAppContainer
import com.memorypot.ui.components.KeywordEditor
import com.memorypot.viewmodel.AddMemoryViewModel
import com.memorypot.viewmodel.AddVmFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executor

private enum class ReflectPage { CLUES, NOTE, PLACE }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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

    var capturedPath by remember { mutableStateOf<String?>(null) }
    var showReflectSheet by remember { mutableStateOf(false) }

    // Location permission (optional, if the user enabled Save location in Settings)
    val saveLocationEnabled by container.settings.saveLocationFlow.collectAsState(initial = true)
    val locationHelper = remember { LocationHelper(context) }
    val locationPermLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { /* repo will read permission at save time */ }

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden }
    )

    Scaffold(
        topBar = {
            // Keep the top chrome minimal. The “magic” lives in the reflect sheet.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    if (capturedPath == null) onCancel() else {
                        // If already captured, go back to camera (retake) rather than exiting.
                        capturedPath = null
                        showReflectSheet = false
                    }
                }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
                Spacer(Modifier.weight(1f))
                Text(
                    if (capturedPath == null) "Capture" else "Reflect",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.size(48.dp))
            }
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val photoPath = capturedPath

            if (photoPath == null) {
                CameraCapture(
                    onBack = onCancel,
                    onCaptured = { path ->
                        capturedPath = path
                        showReflectSheet = true
                    },
                    photoStore = photoStore
                )
            } else {
                // Full-bleed photo preview is the emotional anchor.
                AsyncImage(
                    model = photoPath,
                    contentDescription = "Captured photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Auto-generate AI keywords once per capture.
                LaunchedEffect(photoPath) {
                    vm.generateKeywords(photoPath)
                }

                if (showReflectSheet) {
                    ModalBottomSheet(
                        onDismissRequest = {
                            // iOS-like: dismissing the sheet returns you to camera (retake)
                            capturedPath = null
                            showReflectSheet = false
                        },
                        sheetState = sheetState,
                        dragHandle = null,
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp
                    ) {
                        ReflectSheetContent(
                            state = state,
                            onKeywordsChange = vm::updateKeywords,
                            onPromptChange = vm::updateKeywordPrompt,
                            onApplyPrompt = vm::applyKeywordPrompt,
                            onNoteChange = vm::updateNote,
                            onTitleChange = vm::updateLabel,
                            onPlaceChange = vm::updatePlace,
                            isSaveLocationEnabled = saveLocationEnabled,
                            hasLocationPermission = locationHelper.hasLocationPermission(),
                            onRequestLocationPermission = {
                                locationPermLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            },
                            onRetake = {
                                capturedPath = null
                                showReflectSheet = false
                            },
                            onDoneClick = {
                                vm.save(photoPath, onDone)
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ReflectSheetContent(
    state: com.memorypot.viewmodel.AddState,
    onKeywordsChange: (String) -> Unit,
    onPromptChange: (String) -> Unit,
    onApplyPrompt: () -> Unit,
    onNoteChange: (String) -> Unit,
    onTitleChange: (String) -> Unit,
    onPlaceChange: (String) -> Unit,
    isSaveLocationEnabled: Boolean,
    hasLocationPermission: Boolean,
    onRequestLocationPermission: () -> Unit,
    onRetake: () -> Unit,
    onDoneClick: () -> Unit
) {
    val pages = remember { listOf(ReflectPage.CLUES, ReflectPage.NOTE, ReflectPage.PLACE) }
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Top handle + segmented control (Apple Photos vibe)
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 8.dp, bottom = 2.dp)
                .size(width = 44.dp, height = 5.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(MaterialTheme.colorScheme.outlineVariant)
        )

        TabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = MaterialTheme.colorScheme.surface,
            divider = {}
        ) {
            Tab(
                selected = pagerState.currentPage == 0,
                onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                text = { Text("Clues") }
            )
            Tab(
                selected = pagerState.currentPage == 1,
                onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                text = { Text("Note") }
            )
            Tab(
                selected = pagerState.currentPage == 2,
                onClick = { scope.launch { pagerState.animateScrollToPage(2) } },
                text = { Text("Place") }
            )
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
        ) { index ->
            when (pages[index]) {
                ReflectPage.CLUES -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "What stands out from this moment?",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "These memory clues help you find this later. On-device AI suggestions are editable.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        AnimatedVisibility(
                            visible = state.isGeneratingKeywords,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(10.dp))
                                Text("Finding clues…")
                            }
                        }

                        // "One-by-one" reveal (simple, robust): we fade the editor in shortly after generation.
                        var reveal by remember(state.keywords) { mutableStateOf(false) }
                        LaunchedEffect(state.keywords) {
                            reveal = false
                            // If keywords arrived, give a tiny beat so it feels like the app “thought”.
                            if (state.keywords.isNotBlank()) {
                                delay(120)
                                reveal = true
                            }
                        }
                        AnimatedVisibility(
                            visible = reveal || state.keywords.isBlank(),
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            KeywordEditor(
                                keywords = state.keywords,
                                onKeywordsChange = onKeywordsChange,
                                prompt = state.keywordPrompt,
                                onPromptChange = onPromptChange,
                                onApplyPrompt = onApplyPrompt,
                                title = "Memory Clues",
                                supportingText = "Tap × to remove. Add your own and we’ll merge them.",
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (state.error != null) {
                            Text(state.error, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                ReflectPage.NOTE -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "Want to say something?",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "A sentence you’ll appreciate later. Totally optional.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = state.note,
                            onValueChange = onNoteChange,
                            placeholder = { Text("Anything you’d like to remember?") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            minLines = 5
                        )

                        OutlinedTextField(
                            value = state.label,
                            onValueChange = onTitleChange,
                            label = { Text("Title (optional)") },
                            placeholder = { Text("E.g., Late night coding") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }

                ReflectPage.PLACE -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "Where was this?",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "If you’ve enabled location, we’ll save GPS + place automatically.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = state.placeText,
                            onValueChange = onPlaceChange,
                            label = { Text("Place") },
                            placeholder = { Text("Auto-detected or enter a place") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        if (isSaveLocationEnabled && !hasLocationPermission) {
                            Text(
                                "Location permission is off. Enable it to auto-save GPS/place.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedButton(onClick = onRequestLocationPermission) {
                                Text("Allow location")
                            }
                        }
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        // Bottom bar: iOS-like "Retake" + primary "Done".
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onRetake,
                modifier = Modifier.weight(1f)
            ) { Text("Retake") }

            Button(
                onClick = onDoneClick,
                enabled = !state.isSaving,
                modifier = Modifier.weight(1f)
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                }
                Text("Done")
            }
        }
        Spacer(Modifier.height(6.dp))
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
                Modifier
                    .fillMaxSize()
                    .padding(20.dp),
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
                    } catch (_: Throwable) {
                    }
                }, executor)

                previewView
            }
        )

        // iOS-like: one primary shutter button, no clutter.
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
                .padding(bottom = 28.dp)
                .clip(RoundedCornerShape(28.dp))
        ) {
            Icon(Icons.Default.CameraAlt, contentDescription = "Capture")
            Spacer(Modifier.size(8.dp))
            Text("Capture")
        }
    }
}
