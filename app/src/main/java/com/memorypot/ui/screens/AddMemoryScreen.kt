package com.memorypot.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.DisposableEffectResult
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.ui.window.Dialog
import kotlin.math.max
import kotlin.math.min
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import android.graphics.RectF
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions

private enum class ReflectPage { CLUES, NOTE, PLACE }

// Live preview object detection box in normalized [0..1] coordinates.
private data class LiveBox(val rect: RectF, val label: String?)

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
    var showObjectPicker by remember { mutableStateOf(false) }

    // Live-preview object selections (normalized 0..1 rects) carried over to the captured photo.
    var pendingLiveSelections by remember { mutableStateOf<List<android.graphics.RectF>>(emptyList()) }
    var appliedLiveSelectionsForPath by remember { mutableStateOf<String?>(null) }

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
                        vm.resetForNewCapture()
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
                    onCaptured = { path, liveSelections ->
                        // New capture: ensure we don't carry over the previous photo's AI clues.
                        vm.resetForNewCapture()
                        capturedPath = path
                        pendingLiveSelections = liveSelections
                        appliedLiveSelectionsForPath = null
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
                    if (pendingLiveSelections.isNotEmpty() && appliedLiveSelectionsForPath != photoPath) {
                        appliedLiveSelectionsForPath = photoPath
                        vm.generateKeywordsForNormalizedRegions(photoPath, pendingLiveSelections)
                    } else {
                        vm.generateKeywords(photoPath)
                    }
                }

                if (showObjectPicker) {
                    ObjectSelectDialog(
                        photoPath = photoPath,
                        isDetecting = state.isDetectingObjects,
                        objects = state.detectedObjects,
                        bitmapWidth = state.detectedBitmapWidth,
                        bitmapHeight = state.detectedBitmapHeight,
                        onRequestDetect = { vm.detectObjects(photoPath) },
                        onDismiss = { showObjectPicker = false },
                        onUseSelection = { rects ->
                            vm.generateKeywordsForRegions(photoPath, rects)
                            showObjectPicker = false
                        }
                    )
                }

                if (showReflectSheet) {
                    ModalBottomSheet(
                        onDismissRequest = {
                            // iOS-like: dismissing the sheet returns you to camera (retake)
                            capturedPath = null
                            showReflectSheet = false
                            vm.resetForNewCapture()
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
                            onSelectObject = { showObjectPicker = true },
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
                                vm.resetForNewCapture()
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
    onSelectObject: () -> Unit,
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
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 8.dp),
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

                        OutlinedButton(
                            onClick = onSelectObject,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Select an object in the photo")
                        }
                        Text(
                            "Helpful when there are multiple objects — tap a detected object or draw a box.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (state.error != null) {
                            Text(state.error, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                ReflectPage.NOTE -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 8.dp),
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
                                .heightIn(min = 160.dp, max = 240.dp),
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
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 8.dp),
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
                        Spacer(Modifier.height(12.dp))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ObjectSelectDialog(
    photoPath: String,
    isDetecting: Boolean,
    objects: List<com.memorypot.data.repo.AiKeywordHelper.DetectedRegion>,
    bitmapWidth: Int,
    bitmapHeight: Int,
    onRequestDetect: () -> Unit,
    onDismiss: () -> Unit,
    onUseSelection: (List<android.graphics.Rect>) -> Unit
) {
    LaunchedEffect(photoPath) {
        // Kick off detection the first time the dialog appears.
        onRequestDetect()
    }

    val selectedBoxes = remember { mutableStateListOf<android.graphics.Rect>() }
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragEnd by remember { mutableStateOf<Offset?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Scaffold(
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Close")
                    }
                    Text(
                        "Select object",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(onClick = onRequestDetect, enabled = !isDetecting) {
                        Text("Refresh")
                    }
                }
            },
            bottomBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isDetecting) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(10.dp))
                            Text("Detecting objects…")
                        }
                    } else {
                        Text(
                            "Tap a highlighted object, or drag to draw a box.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = {
                                selectedBoxes.clear()
                                dragStart = null
                                dragEnd = null
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Clear") }

                        Button(
                            onClick = {
                                if (selectedBoxes.isEmpty()) return@Button
                                // Snapshot the selection at click time.
                                onUseSelection(selectedBoxes.toList())
                            },
                            enabled = selectedBoxes.isNotEmpty(),
                            modifier = Modifier.weight(1f)
                        ) { Text("Use selection") }
                    }
                }
            }
        ) { pad ->
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(pad)
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                // We render the image Fit so mapping is stable.
                AsyncImage(
                    model = photoPath,
                    contentDescription = "Photo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )

                val bw = bitmapWidth
                val bh = bitmapHeight

                // If detection didn't run yet, we can still allow manual selection by drawing.
                // For manual selection, we map view coords to bitmap coords; if bitmap size unknown,
                // we fall back to view-space rect and let the VM crop using those numbers by
                // decoding a 1280px bitmap and clamping.

                // Draw overlays and handle interactions.
                val primaryColor = MaterialTheme.colorScheme.primary
                val tertiaryColor = MaterialTheme.colorScheme.tertiary
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(objects, bw, bh) {
                            detectTapGestures { tap ->
                                if (bw <= 0 || bh <= 0) return@detectTapGestures
                                val w = size.width
                                val h = size.height
                                val scale = kotlin.math.min(w / bw.toFloat(), h / bh.toFloat())
                                val dx = (w - bw * scale) / 2f
                                val dy = (h - bh * scale) / 2f
                                val bx = ((tap.x - dx) / scale)
                                val by = ((tap.y - dy) / scale)
                                val hit = objects.firstOrNull { r ->
                                    r.boundingBox.contains(bx.toInt(), by.toInt())
                                }
                                if (hit != null) {
                                    val rect = android.graphics.Rect(hit.boundingBox)
                                    val idx = selectedBoxes.indexOfFirst { it == rect }
                                    if (idx >= 0) selectedBoxes.removeAt(idx) else selectedBoxes.add(rect)
                                    dragStart = null
                                    dragEnd = null
                                }
                            }
                        }
                        .pointerInput(bw, bh) {
                            detectDragGestures(
                                onDragStart = { start ->
                                    dragStart = start
                                    dragEnd = start
                                },
                                onDrag = { change, _ ->
                                    dragEnd = change.position
                                },
                                onDragEnd = {
                                    // Convert the drag rect from view coords to bitmap coords if possible.
                                    if (bw <= 0 || bh <= 0) return@detectDragGestures
                                    val s = dragStart
                                    val e = dragEnd
                                    if (s == null || e == null) return@detectDragGestures
                                    val w = size.width
                                    val h = size.height
                                    val scale = kotlin.math.min(w / bw.toFloat(), h / bh.toFloat())
                                    val dx = (w - bw * scale) / 2f
                                    val dy = (h - bh * scale) / 2f

                                    fun toBitmap(o: Offset): Offset {
                                        val bx = ((o.x - dx) / scale).coerceIn(0f, bw.toFloat())
                                        val by = ((o.y - dy) / scale).coerceIn(0f, bh.toFloat())
                                        return Offset(bx, by)
                                    }

                                    val bs = toBitmap(s)
                                    val be = toBitmap(e)
                                    val rect = android.graphics.Rect(
                                        kotlin.math.min(bs.x, be.x).toInt(),
                                        kotlin.math.min(bs.y, be.y).toInt(),
                                        kotlin.math.max(bs.x, be.x).toInt(),
                                        kotlin.math.max(bs.y, be.y).toInt()
                                    )
                                    // Add the manual rect as an additional selection.
                                    if (rect.width() > 12 && rect.height() > 12) {
                                        selectedBoxes.add(rect)
                                    }
                                }
                            )
                        }
                ) {
                    val w = size.width
                    val h = size.height

                    // If bitmap dims known, we can draw accurate boxes.
                    if (bw > 0 && bh > 0) {
                        val scale = kotlin.math.min(w / bw.toFloat(), h / bh.toFloat())
                        val dx = (w - bw * scale) / 2f
                        val dy = (h - bh * scale) / 2f

                        objects.forEach { r ->
                            val b = r.boundingBox
                            val left = dx + b.left * scale
                            val top = dy + b.top * scale
                            val right = dx + b.right * scale
                            val bottom = dy + b.bottom * scale

                            drawRect(
                                color = primaryColor.copy(alpha = 0.20f),
                                topLeft = Offset(left, top),
                                size = androidx.compose.ui.geometry.Size(right - left, bottom - top)
                            )
                            drawRect(
                                color = primaryColor.copy(alpha = 0.90f),
                                topLeft = Offset(left, top),
                                size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
                            )
                        }

                        // Draw selected boxes (tap or manual)
                        selectedBoxes.forEach { sel ->
                            val left = dx + sel.left * scale
                            val top = dy + sel.top * scale
                            val right = dx + sel.right * scale
                            val bottom = dy + sel.bottom * scale
                            drawRect(
                                color = tertiaryColor.copy(alpha = 0.28f),
                                topLeft = Offset(left, top),
                                size = androidx.compose.ui.geometry.Size(right - left, bottom - top)
                            )
                            drawRect(
                                color = tertiaryColor,
                                topLeft = Offset(left, top),
                                size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f)
                            )
                        }
                    } else {
                        // Bitmap size unknown: draw manual view-space rect so the user still sees feedback.
                        val s = dragStart
                        val e = dragEnd
                        if (s != null && e != null) {
                            val left = kotlin.math.min(s.x, e.x)
                            val top = kotlin.math.min(s.y, e.y)
                            val right = kotlin.math.max(s.x, e.x)
                            val bottom = kotlin.math.max(s.y, e.y)
                            drawRect(
                                color = tertiaryColor.copy(alpha = 0.28f),
                                topLeft = Offset(left, top),
                                size = androidx.compose.ui.geometry.Size(right - left, bottom - top)
                            )
                            drawRect(
                                color = tertiaryColor,
                                topLeft = Offset(left, top),
                                size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f)
                            )
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
    onCaptured: (String, List<RectF>) -> Unit,
    photoStore: PhotoStore
) {
    val context = LocalContext.current
    // IMPORTANT: bind CameraX to the *real* LifecycleOwner from Compose.
    // Using (ctx as ComponentActivity) is flaky in Compose because the AndroidView context
    // can be a ContextThemeWrapper and the cast fails (resulting in no analysis + no boxes).
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val executor: Executor = ContextCompat.getMainExecutor(context)
    val hasCamera = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    // Use a controller-based approach so ImageAnalysis is reliably bound in Compose.
    // This avoids silent bind failures (and therefore missing boxes) on some devices/CI.
    val cameraController = remember(context) {
        LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            setEnabledUseCases(CameraController.IMAGE_CAPTURE or CameraController.IMAGE_ANALYSIS)
        }
    }

    // Live preview selector
    // We run on-device ML Kit object detection on a throttled preview stream and let the user
    // tap multiple boxes before capturing.
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val detector by remember {
        mutableStateOf(
            ObjectDetection.getClient(
                ObjectDetectorOptions.Builder()
                    .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
                    .enableMultipleObjects()
                    .enableClassification()
                    .build()
            )
        )
    }

    var liveImageW by remember { mutableStateOf(0) }
    var liveImageH by remember { mutableStateOf(0) }
    var liveBoxes by remember { mutableStateOf<List<LiveBox>>(emptyList()) }
    // Explicit type helps Kotlin choose the correct collection extensions (clear/isNotEmpty/toList)
    // across differing Kotlin/Compose compiler versions.
    val selectedLiveBoxes: androidx.compose.runtime.snapshots.SnapshotStateList<RectF> =
        remember { mutableStateListOf() }
    val isAnalyzing = remember { AtomicBoolean(false) }
    var lastAnalyzeMs by remember { mutableStateOf(0L) }

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

        // Camera preview view (Controller-based; reliable in Compose)
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PreviewView(ctx).apply {
                    // Use FIT_CENTER so our overlay mapping math is stable.
                    scaleType = PreviewView.ScaleType.FIT_CENTER
                    controller = cameraController
                }
            },
            update = { it.controller = cameraController }
        )

        // Overlay with live boxes + multi-select.
        val livePrimaryColor = MaterialTheme.colorScheme.primary
        val liveTertiaryColor = MaterialTheme.colorScheme.tertiary
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(liveBoxes, liveImageW, liveImageH) {
                    detectTapGestures { tap ->
                        if (liveImageW <= 0 || liveImageH <= 0) return@detectTapGestures
                        val w = size.width
                        val h = size.height
                        val scale = kotlin.math.min(w / liveImageW.toFloat(), h / liveImageH.toFloat())
                        val dx = (w - liveImageW * scale) / 2f
                        val dy = (h - liveImageH * scale) / 2f

                        val ix = ((tap.x - dx) / scale) / liveImageW.toFloat()
                        val iy = ((tap.y - dy) / scale) / liveImageH.toFloat()

                        val hit = liveBoxes.firstOrNull { b ->
                            b.rect.contains(ix, iy)
                        }
                        if (hit != null) {
                            val r = RectF(hit.rect)
                            val idx = selectedLiveBoxes.indexOfFirst { it == r }
                            if (idx >= 0) selectedLiveBoxes.removeAt(idx) else selectedLiveBoxes.add(r)
                        }
                    }
                }
        ) {
            val w = size.width
            val h = size.height
            if (liveImageW > 0 && liveImageH > 0) {
                val scale = kotlin.math.min(w / liveImageW.toFloat(), h / liveImageH.toFloat())
                val dx = (w - liveImageW * scale) / 2f
                val dy = (h - liveImageH * scale) / 2f

                liveBoxes.forEach { b ->
                    val left = dx + b.rect.left * liveImageW * scale
                    val top = dy + b.rect.top * liveImageH * scale
                    val right = dx + b.rect.right * liveImageW * scale
                    val bottom = dy + b.rect.bottom * liveImageH * scale
                    drawRect(
                        color = livePrimaryColor.copy(alpha = 0.20f),
                        topLeft = Offset(left, top),
                        size = androidx.compose.ui.geometry.Size(right - left, bottom - top)
                    )
                    drawRect(
                        color = livePrimaryColor.copy(alpha = 0.85f),
                        topLeft = Offset(left, top),
                        size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
                    )
                }

                // Selected boxes
                selectedLiveBoxes.forEach { rf ->
                    val left = dx + rf.left * liveImageW * scale
                    val top = dy + rf.top * liveImageH * scale
                    val right = dx + rf.right * liveImageW * scale
                    val bottom = dy + rf.bottom * liveImageH * scale
                    drawRect(
                        color = liveTertiaryColor.copy(alpha = 0.28f),
                        topLeft = Offset(left, top),
                        size = androidx.compose.ui.geometry.Size(right - left, bottom - top)
                    )
                    drawRect(
                        color = liveTertiaryColor,
                        topLeft = Offset(left, top),
                        size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f)
                    )
                }
            }
        }

        // Minimal hints + clear button
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedVisibility(visible = selectedLiveBoxes.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text("Selected: ${'$'}{selectedLiveBoxes.size}", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { selectedLiveBoxes.clear() }) { Text("Clear") }
                }
            }
        }

        // iOS-like: one primary shutter button, no clutter.
        Button(
            onClick = {
                val file = photoStore.newPhotoFile()
                val output = ImageCapture.OutputFileOptions.Builder(file).build()
                cameraController.takePicture(
                    output,
                    executor,
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            // SnapshotStateList is live; pass a stable deep copy of the currently selected boxes.
                            val snapshot = List(selectedLiveBoxes.size) { i -> RectF(selectedLiveBoxes[i]) }
                            onCaptured(file.absolutePath, snapshot)
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

    // Bind analysis + capture to lifecycle and clean up properly when leaving composition.
    // We intentionally avoid using `onDispose {}` to keep compatibility with CI environments
    // that have had symbol-resolution issues for that extension.
    DisposableEffect(cameraController, lifecycleOwner) {
        // Analyzer must be set *before* binding to ensure we start receiving frames immediately.
        cameraController.setImageAnalysisAnalyzer(analysisExecutor) { imageProxy ->
            analyzeFrameForObjects(
                imageProxy = imageProxy,
                detector = detector,
                mainExecutor = executor,
                onResult = { w, h, boxes ->
                    liveImageW = w
                    liveImageH = h
                    liveBoxes = boxes
                },
                isAnalyzing = isAnalyzing,
                getLastMs = { lastAnalyzeMs },
                setLastMs = { lastAnalyzeMs = it }
            )
        }
        runCatching { cameraController.bindToLifecycle(lifecycleOwner) }

        return@DisposableEffect object : DisposableEffectResult {
            override fun dispose() {
                runCatching { cameraController.clearImageAnalysisAnalyzer() }
                runCatching { cameraController.unbind() }
                runCatching { analysisExecutor.shutdown() }
                runCatching { detector.close() }
            }
        }
    }
}

/**
 * Throttled ML Kit object detection on a CameraX preview stream.
 *
 * Returns boxes in normalized [0..1] coordinates in the *upright* image space (after applying
 * [ImageProxy.imageInfo.rotationDegrees]).
 */
private fun analyzeFrameForObjects(
    imageProxy: ImageProxy,
    detector: ObjectDetector,
    mainExecutor: Executor,
    onResult: (imageW: Int, imageH: Int, boxes: List<LiveBox>) -> Unit,
    isAnalyzing: AtomicBoolean,
    getLastMs: () -> Long,
    setLastMs: (Long) -> Unit,
    minIntervalMs: Long = 450L
) {
    val now = System.currentTimeMillis()
    if (now - getLastMs() < minIntervalMs) {
        imageProxy.close()
        return
    }
    if (!isAnalyzing.compareAndSet(false, true)) {
        imageProxy.close()
        return
    }
    setLastMs(now)

    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        isAnalyzing.set(false)
        imageProxy.close()
        return
    }

    val rotation = imageProxy.imageInfo.rotationDegrees
    // For 90/270 rotations, the upright image width/height are swapped.
    val uprightW = if (rotation == 90 || rotation == 270) imageProxy.height else imageProxy.width
    val uprightH = if (rotation == 90 || rotation == 270) imageProxy.width else imageProxy.height

    val input = InputImage.fromMediaImage(mediaImage, rotation)
    detector.process(input)
        .addOnSuccessListener { objs ->
            val boxes = objs.mapNotNull { obj ->
                val b = obj.boundingBox
                if (uprightW <= 0 || uprightH <= 0) return@mapNotNull null
                val rf = RectF(
                    (b.left.toFloat() / uprightW).coerceIn(0f, 1f),
                    (b.top.toFloat() / uprightH).coerceIn(0f, 1f),
                    (b.right.toFloat() / uprightW).coerceIn(0f, 1f),
                    (b.bottom.toFloat() / uprightH).coerceIn(0f, 1f)
                )
                val lbl = obj.labels.firstOrNull()?.text?.trim()?.takeIf { it.isNotBlank() }
                LiveBox(rect = rf, label = lbl)
            }
            // Push results to the main thread since we're mutating Compose state.
            mainExecutor.execute { onResult(uprightW, uprightH, boxes) }
        }
        .addOnFailureListener {
            // Ignore; keep last boxes.
        }
        .addOnCompleteListener {
            isAnalyzing.set(false)
            imageProxy.close()
        }
}
