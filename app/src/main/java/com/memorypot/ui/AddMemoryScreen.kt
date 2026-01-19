package com.memorypot.ui

import androidx.camera.core.ImageCapture
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.memorypot.camera.CameraPreviewWithPicker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMemoryScreen(
    onDone: () -> Unit,
    vm: AddMemoryViewModel = viewModel()
) {
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var label by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var place by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val pickerState by vm.pickerState.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Add memory") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
            ) {
                CameraPreviewWithPicker(
                    modifier = Modifier.fillMaxSize(),
                    onImageCaptureReady = { imageCapture = it },
                    onPickerResults = { viewBoxes, normalizedBoxes ->
                        vm.updateDetections(viewBoxes, normalizedBoxes)
                    }
                )

                // Tap-to-select boxes
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(pickerState.viewBoxes, pickerState.selectedIds) {
                            detectTapGestures { tap ->
                                val hit = pickerState.viewBoxes.firstOrNull { it.rect.contains(tap.x, tap.y) }
                                if (hit != null) {
                                    vm.toggleSelection(hit.trackingId)
                                }
                            }
                        }
                )

                // Overlay boxes
                Canvas(modifier = Modifier.fillMaxSize()) {
                    for (b in pickerState.viewBoxes) {
                        val selected = pickerState.selectedIds.contains(b.trackingId)
                        val strokeWidth = if (selected) 6f else 3f
                        val pathEffect = if (selected) null else PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)

                        drawRect(
                            color = if (selected) Color(0xFF00C853) else Color(0xFF00B0FF),
                            topLeft = Offset(b.rect.left, b.rect.top),
                            size = androidx.compose.ui.geometry.Size(b.rect.width(), b.rect.height()),
                            style = Stroke(width = strokeWidth, pathEffect = pathEffect)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Selected: ${vm.selectedCount()}", style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = { vm.clearSelection() }, enabled = vm.selectedCount() > 0) {
                    Text("Clear")
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = place,
                    onValueChange = { place = it },
                    label = { Text("Place") },
                    modifier = Modifier.fillMaxWidth()
                )

                if (error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                }

                Spacer(Modifier.height(6.dp))

                Button(
                    onClick = {
                        val cap = imageCapture ?: run {
                            error = "Camera not ready"
                            return@Button
                        }
                        saving = true
                        error = null
                        vm.saveMemory(
                            label = label,
                            note = note,
                            placeText = place,
                            imageCapture = cap
                        ) { ok, err ->
                            saving = false
                            if (ok) onDone() else error = err
                        }
                    },
                    enabled = !saving && imageCapture != null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (saving) "Saving..." else "Save & Submit")
                }
            }
        }
    }
}
