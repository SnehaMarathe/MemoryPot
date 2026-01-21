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
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.focus.onFocusChanged
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

/**
 * Lightweight, low-risk “wow” feature: generate a single-sentence explanation for why this
 * moment will be easy to remember. Pure UI helper (no model/db changes).
 */
private fun buildRememberInsight(
    keywords: String,
    placeText: String
): String? {
    val parts = keywords
        .split(',', '\n')
        .map { it.trim() }
        .filter { it.isNotBlank() }
    if (parts.isEmpty()) return null

    val lower = parts.joinToString(" ") { it.lowercase() }

    // A simple “primary thing” guess: first non-trivial phrase.
    val primary = parts.firstOrNull { it.length >= 3 }?.trim()?.lowercase()

    val place = placeText.trim().ifBlank { null }?.lowercase()

    val colorWords = setOf(
        "red", "pink", "orange", "yellow", "green", "blue", "purple",
        "black", "white", "gray", "grey", "gold", "silver", "brown"
    )
    val hasColor = colorWords.any { lower.contains(it) }

    val dailyObjects = setOf(
        "keys", "wallet", "phone", "remote", "charger", "earbuds", "glasses",
        "tissue", "tissues", "bottle", "mug", "cup", "book", "bag"
    )
    val isDaily = dailyObjects.any { lower.contains(it) }

    return when {
        place != null && isDaily -> "You’ll remember this because it’s part of your daily routine at $place."
        place != null && primary != null -> "You’ll remember this because the $primary stands out in $place."
        hasColor && primary != null -> "You’ll remember this because the $primary is visually distinct."
        isDaily -> "You’ll remember this because it’s tied to something you use often."
        else -> "You’ll remember this because it’s distinct and context-specific."
    }
}

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
    // Redesigned UI: avoid overlaying form fields on top of the photo.
    // We render a top photo header and the reflect UI below it (no bottom-sheet overlap).
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
                    },
                    photoStore = photoStore
                )
            } else {
                // Redesigned: keep the photo visible without letting inputs overlap it.
                Column(Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = photoPath,
                        contentDescription = "Captured photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
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
                            // Replace the clue words with object-focused clues so the user sees
                            // an immediate, obvious change after selection.
                            vm.generateKeywordsForRegionsReplace(photoPath, rects)
                            showObjectPicker = false
                        }
                    )
                }

                    // Content below the photo
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface)
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

    // Professional layout: actions are *absolutely* pinned to the bottom edge.
    // (No navigation-bar/IME inset padding applied to the bottom bar — per request.)
    val bottomBarHeight = 72.dp

    // Use a simple fill-max Column so the bottom bar is *physically* the last element in the
    // layout tree (no BoxScope.align needed, which can break depending on receivers/imports).
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 6.dp)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(androidx.compose.foundation.layout.WindowInsetsSides.Top)),
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
                    .weight(1f, fill = true)
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

                        // “Wow” line: a one-sentence, calm explanation for why this will stick.
                        val rememberInsight = remember(state.keywords, state.placeText) {
                            buildRememberInsight(state.keywords, state.placeText)
                        }
                        AnimatedVisibility(
                            visible = !rememberInsight.isNullOrBlank() && state.keywords.isNotBlank(),
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            Text(
                                text = "💡 ${rememberInsight ?: ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 2.dp)
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
                    // When the keyboard opens, ensure the focused field is brought into view.
                    val noteBringIntoView = remember { BringIntoViewRequester() }
                    val titleBringIntoView = remember { BringIntoViewRequester() }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            // Extra bottom padding when IME is shown prevents "squeezed" layouts
                            // and keeps the caret/text visible above the keyboard.
                            .windowInsetsPadding(WindowInsets.ime)
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
                                .heightIn(min = 160.dp, max = 240.dp)
                                .bringIntoViewRequester(noteBringIntoView)
                                .onFocusChanged {
                                    if (it.isFocused) {
                                        scope.launch { noteBringIntoView.bringIntoView() }
                                    }
                                },
                            minLines = 5
                        )

                        OutlinedTextField(
                            value = state.label,
                            onValueChange = onTitleChange,
                            label = { Text("Title (optional)") },
                            placeholder = { Text("E.g., Late night coding") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .bringIntoViewRequester(titleBringIntoView)
                                .onFocusChanged {
                                    if (it.isFocused) {
                                        scope.launch { titleBringIntoView.bringIntoView() }
                                    }
                                },
                            singleLine = true
                        )
                    }
                }

                ReflectPage.PLACE -> {
                    val placeBringIntoView = remember { BringIntoViewRequester() }
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .windowInsetsPadding(WindowInsets.ime)
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .bringIntoViewRequester(placeBringIntoView)
                                .onFocusChanged {
                                    if (it.isFocused) {
                                        scope.launch { placeBringIntoView.bringIntoView() }
                                    }
                                },
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

        Surface(
            tonalElevation = 3.dp,
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(bottomBarHeight)
                // Keep actions reachable when the keyboard is open.
                .windowInsetsPadding(WindowInsets.ime)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
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
                    Text("Save")
                }
            }
        }
    }
}

// NOTE: ReflectSheetContent uses Scaffold with multiple nested lambdas.
// This brace closes the ReflectSheetContent composable scope.
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

    // Runtime permission handling (required on Android 6+).
    val initialCameraGranted = remember {
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }
    var cameraGranted by remember { mutableStateOf(initialCameraGranted) }
    val cameraPermLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        cameraGranted = granted
    }

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    // If the camera cannot bind (e.g., missing hardware, permission, or a CameraX init issue),
    // expose a visible error instead of silently failing.
    var bindError by remember { mutableStateOf<String?>(null) }

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
    // Surface ML Kit / analyzer failures so we can debug on real devices.
    var liveDetectError by remember { mutableStateOf<String?>(null) }
    // If the detector is running but just not seeing anything, show a helpful hint.
    // (Users otherwise assume the feature is broken.)
    var liveDetectHint by remember { mutableStateOf<String?>(null) }
    // Explicit type helps Kotlin choose the correct collection extensions (clear/isNotEmpty/toList)
    // across differing Kotlin/Compose compiler versions.
    val selectedLiveBoxes: androidx.compose.runtime.snapshots.SnapshotStateList<RectF> =
        remember { mutableStateListOf() }

    // Manual selection fallback (drag-to-select) in normalized [0..1] coordinates.
    // Some devices/scenes produce few/no ML Kit object boxes; this keeps STREAM_MODE usable.
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragEnd by remember { mutableStateOf<Offset?>(null) }
    val isAnalyzing = remember { AtomicBoolean(false) }
    var lastAnalyzeMs by remember { mutableStateOf(0L) }

    Box(Modifier.fillMaxSize()) {
        if (!cameraGranted) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Camera permission is required to capture a photo.")
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { cameraPermLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Allow camera")
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "If you previously denied it, you can also enable it in Settings → Apps → Memory Pot → Permissions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = onBack) { Text("Back") }
            }
            return@Box
        }

        // If we have a live camera feed and the analyzer is running, but no objects have
        // been detected for a while, show a non-fatal hint.
        LaunchedEffect(cameraGranted, bindError) {
            if (!cameraGranted || bindError != null) return@LaunchedEffect
            // Reset when camera re-binds.
            liveDetectHint = null
            // Wait a moment for the pipeline to warm up.
            delay(2500)
            if (liveDetectError == null && liveBoxes.isEmpty()) {
                liveDetectHint = "No objects detected yet — try moving closer or improve lighting."
            }
        }

        // Clear the hint as soon as we have any detections.
        LaunchedEffect(liveBoxes) {
            if (liveBoxes.isNotEmpty()) liveDetectHint = null
        }

        // Camera preview view
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    // Use FIT_CENTER for the most reliable coordinate mapping between
                    // ImageAnalysis (ML Kit) and what the user sees. This avoids OEM-specific
                    // center-crop transforms that can make boxes/taps appear "not working".
                    scaleType = PreviewView.ScaleType.FIT_CENTER
                    // COMPATIBLE is more reliable across OEM devices, especially when embedded
                    // in Compose via AndroidView.
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }

                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val rotation = previewView.display?.rotation
                        ?: android.view.Surface.ROTATION_0

                    val preview = Preview.Builder()
                        .setTargetRotation(rotation)
                        .build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val capture = ImageCapture.Builder()
                        .setTargetRotation(rotation)
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()

                    val analysis = ImageAnalysis.Builder()
                        .setTargetRotation(rotation)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                        analyzeFrameForObjects(
                            imageProxy = imageProxy,
                            detector = detector,
                            mainExecutor = executor,
                            onResult = { w, h, boxes ->
                                liveImageW = w
                                liveImageH = h
                                liveBoxes = boxes
                                liveDetectError = null
                            },
                            onError = { msg ->
                                liveDetectError = msg
                                // Clear boxes so the overlay reflects the failure.
                                liveBoxes = emptyList()
                            },
                            isAnalyzing = isAnalyzing,
                            getLastMs = { lastAnalyzeMs },
                            setLastMs = { lastAnalyzeMs = it },
                            // More responsive live UI.
                            minIntervalMs = 180L
                        )
                    }

                    imageCapture = capture
                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            capture,
                            analysis
                        )
                        bindError = null
                    } catch (t: Throwable) {
                        // Surface binding failures are the #1 reason a PreviewView is black.
                        // Don't swallow this; show it so it's actionable.
                        bindError = t.message ?: t.javaClass.simpleName
                    }
                }, executor)

                previewView
            }
        )

        if (bindError != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(top = 24.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.90f))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    "Camera preview failed to start",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    bindError ?: "Unknown error",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (liveDetectError != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(top = 90.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.90f))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    "Object detection not running",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    liveDetectError ?: "Unknown",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (liveDetectHint != null && liveDetectError == null) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(top = 90.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.90f))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    "Object detection",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    liveDetectHint ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Always show a tiny status chip so it's obvious the live pipeline is active.
        if (bindError == null) {
            val selectedCount = selectedLiveBoxes.size
            val detectedCount = liveBoxes.size
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 16.dp, start = 12.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.82f))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    "Detected: $detectedCount  •  Selected: $selectedCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

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
                        // PreviewView is FIT_CENTER, so use the *min* scale.
                        val scale = kotlin.math.min(w / liveImageW.toFloat(), h / liveImageH.toFloat())
                        val dx = (w - liveImageW * scale) / 2f
                        val dy = (h - liveImageH * scale) / 2f

                        val ix = ((tap.x - dx) / scale) / liveImageW.toFloat()
                        val iy = ((tap.y - dy) / scale) / liveImageH.toFloat()

                        val hit = liveBoxes.firstOrNull { b ->
                            b.rect.contains(ix, iy)
                        }
                        if (hit != null) {
                            // RectF equality is not reliable across all Android versions/ART
                            // implementations when used from Kotlin (some builds fall back to
                            // reference equality). We toggle by approximate coordinate match.
                            val r = RectF(hit.rect)
                            val idx = selectedLiveBoxes.indexOfFirst { approxSame(it, r) }
                            if (idx >= 0) selectedLiveBoxes.removeAt(idx) else selectedLiveBoxes.add(r)
                        }
                    }
                }
                .pointerInput(liveBoxes, liveImageW, liveImageH) {
                    // Drag to add a manual selection (normalized RectF).
                    detectDragGestures(
                        onDragStart = { start ->
                            dragStart = start
                            dragEnd = start
                        },
                        onDrag = { change, _ ->
                            dragEnd = change.position
                        },
                        onDragCancel = {
                            dragStart = null
                            dragEnd = null
                        },
                        onDragEnd = {
                            val s = dragStart
                            val e = dragEnd
                            dragStart = null
                            dragEnd = null
                            if (s == null || e == null) return@detectDragGestures
                            if (liveImageW <= 0 || liveImageH <= 0) return@detectDragGestures

                            val w = size.width
                            val h = size.height
                            val scale = kotlin.math.min(w / liveImageW.toFloat(), h / liveImageH.toFloat())
                            val dx = (w - liveImageW * scale) / 2f
                            val dy = (h - liveImageH * scale) / 2f

                            val l = kotlin.math.min(s.x, e.x)
                            val t = kotlin.math.min(s.y, e.y)
                            val r = kotlin.math.max(s.x, e.x)
                            val b = kotlin.math.max(s.y, e.y)

                            // Ignore tiny drags.
                            if ((r - l) < 24f || (b - t) < 24f) return@detectDragGestures

                            val nl = (((l - dx) / scale) / liveImageW.toFloat()).coerceIn(0f, 1f)
                            val nt = (((t - dy) / scale) / liveImageH.toFloat()).coerceIn(0f, 1f)
                            val nr = (((r - dx) / scale) / liveImageW.toFloat()).coerceIn(0f, 1f)
                            val nb = (((b - dy) / scale) / liveImageH.toFloat()).coerceIn(0f, 1f)

                            val rect = RectF(
                                kotlin.math.min(nl, nr),
                                kotlin.math.min(nt, nb),
                                kotlin.math.max(nl, nr),
                                kotlin.math.max(nt, nb)
                            )

                            // Toggle if it overlaps an existing manual/selected rect.
                            val idx = selectedLiveBoxes.indexOfFirst { approxSame(it, rect) }
                            if (idx >= 0) selectedLiveBoxes.removeAt(idx) else selectedLiveBoxes.add(rect)
                        }
                    )
                }
        ) {
            val w = size.width
            val h = size.height
            if (liveImageW > 0 && liveImageH > 0) {
                // PreviewView is FIT_CENTER, so use the *min* scale.
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

                // In-progress manual drag rectangle (view-space feedback)
                val s = dragStart
                val e = dragEnd
                if (s != null && e != null) {
                    val left = kotlin.math.min(s.x, e.x)
                    val top = kotlin.math.min(s.y, e.y)
                    val right = kotlin.math.max(s.x, e.x)
                    val bottom = kotlin.math.max(s.y, e.y)
                    drawRect(
                        color = liveTertiaryColor.copy(alpha = 0.20f),
                        topLeft = Offset(left, top),
                        size = androidx.compose.ui.geometry.Size(right - left, bottom - top)
                    )
                    drawRect(
                        color = liveTertiaryColor.copy(alpha = 0.85f),
                        topLeft = Offset(left, top),
                        size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
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
            AnimatedVisibility(
                visible = liveBoxes.isNotEmpty() && selectedLiveBoxes.isEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        "Tap boxes (or drag to select) before capture",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
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
                val capture = imageCapture ?: return@Button
                val file = photoStore.newPhotoFile()
                val output = ImageCapture.OutputFileOptions.Builder(file).build()
                capture.takePicture(
                    output,
                    executor,
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
									// SnapshotStateList is a live (mutable) list; pass a stable copy.
									// Also deep-copy RectF values so callers never depend on reference equality.
									val snapshot = selectedLiveBoxes.map { rf -> RectF(rf) }
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

    // Avoid leaking threads when this composable leaves composition.
    // We intentionally avoid using `onDispose {}` to keep compatibility with CI environments
    // that have had symbol-resolution issues for that extension.
    DisposableEffect(Unit) {
        return@DisposableEffect object : DisposableEffectResult {
            override fun dispose() {
                runCatching { analysisExecutor.shutdown() }
                runCatching { detector.close() }
            }
        }
    }
}

private fun approxSame(a: RectF, b: RectF, eps: Float = 0.004f): Boolean {
    return kotlin.math.abs(a.left - b.left) < eps &&
        kotlin.math.abs(a.top - b.top) < eps &&
        kotlin.math.abs(a.right - b.right) < eps &&
        kotlin.math.abs(a.bottom - b.bottom) < eps
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
    onError: (String) -> Unit,
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
    // IMPORTANT:
    // ML Kit returns bounding boxes in the coordinate space of the *rotated* InputImage.
    // That size is derived from the underlying mediaImage buffer.
    val bufferW = mediaImage.width
    val bufferH = mediaImage.height
    val inputW = if (rotation == 90 || rotation == 270) bufferH else bufferW
    val inputH = if (rotation == 90 || rotation == 270) bufferW else bufferH

    val input = InputImage.fromMediaImage(mediaImage, rotation)
    detector.process(input)
        .addOnSuccessListener { objs ->
            val boxes = objs.mapNotNull { obj ->
                val b = obj.boundingBox
                if (inputW <= 0 || inputH <= 0) return@mapNotNull null
                val rf = RectF(
                    (b.left.toFloat() / inputW).coerceIn(0f, 1f),
                    (b.top.toFloat() / inputH).coerceIn(0f, 1f),
                    (b.right.toFloat() / inputW).coerceIn(0f, 1f),
                    (b.bottom.toFloat() / inputH).coerceIn(0f, 1f)
                )
                val lbl = obj.labels.firstOrNull()?.text?.trim()?.takeIf { it.isNotBlank() }
                LiveBox(rect = rf, label = lbl)
            }
            // Push results to the main thread since we're mutating Compose state.
            mainExecutor.execute { onResult(inputW, inputH, boxes) }
        }
        .addOnFailureListener {
            mainExecutor.execute {
                onError(it.message ?: it.javaClass.simpleName ?: "ML Kit failed")
            }
        }
        .addOnCompleteListener {
            isAnalyzing.set(false)
            imageProxy.close()
        }
}
