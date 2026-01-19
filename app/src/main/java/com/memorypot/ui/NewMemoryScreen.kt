package com.memorypot.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.memorypot.MemoryPotApp
import com.memorypot.camera.CameraPickerViewModel
import com.memorypot.camera.LiveCameraMultiSelectPicker
import com.memorypot.camera.takePhoto
import com.memorypot.data.MemoryEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewMemoryScreen(
    hasCameraPermission: Boolean,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val vm: CameraPickerViewModel = viewModel()

    var label by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var place by remember { mutableStateOf("") }

    var imageCapture by remember { mutableStateOf<androidx.camera.core.ImageCapture?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("New memory") },
            colors = TopAppBarDefaults.topAppBarColors(),
            navigationIcon = {
                Button(onClick = onBack, modifier = Modifier.padding(horizontal = 8.dp)) { Text("Back") }
            }
        )

        if (!hasCameraPermission) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Camera permission is required for live multi-select object picking.")
                Spacer(Modifier.height(8.dp))
                Text("Go back and tap 'Enable camera'.")
            }
            return
        }

        // Live camera + picking area
        LiveCameraMultiSelectPicker(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            viewModel = vm,
            onImageCaptureReady = { cap -> imageCapture = cap }
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { vm.clearSelection() }) {
                    Text("Clear selection")
                }
                val count = vm.selectedIds.value.size
                Text("Selected: $count", modifier = Modifier.padding(top = 10.dp))
            }

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
                label = { Text("Place (optional)") },
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        // Save & Submit: capture photo and insert into DB.
                        val cap = imageCapture ?: return@Button
                        takePhoto(ctx, cap) { uri, absPath ->
                            if (uri == null || absPath == null) return@takePhoto

                            // Use current PreviewView size (best effort). If unknown, fall back to 1f.
                            // The overlay mapping uses view px, so normalize against current display metrics.
                            val dm = ctx.resources.displayMetrics
                            val previewW = dm.widthPixels.toFloat().coerceAtLeast(1f)
                            val previewH = (dm.heightPixels * 0.55f).coerceAtLeast(1f)

                            val json = vm.selectedBoxesToJson(previewW, previewH)

                            val dao = (ctx.applicationContext as MemoryPotApp).db.memories()
                            CoroutineScope(Dispatchers.IO).launch {
                                dao.insert(
                                    MemoryEntity(
                                        label = label.trim(),
                                        note = note.trim(),
                                        placeText = place.trim(),
                                        timestampMs = System.currentTimeMillis(),
                                        photoPath = absPath,
                                        selectedObjectsJson = json
                                    )
                                )
                            }

                            onBack()
                        }
                    }
                ) {
                    Text("Save & Submit")
                }
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = onBack
                ) { Text("Cancel") }
            }
        }
    }
}
