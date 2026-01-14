package com.memorypot.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.memorypot.di.LocalAppContainer
import com.memorypot.ui.components.SimpleTopBar
import com.memorypot.viewmodel.DetailsViewModel
import com.memorypot.viewmodel.DetailsVmFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditMemoryScreen(
    id: String,
    onDone: () -> Unit,
    onCancel: () -> Unit
) {
    val container = LocalAppContainer.current
    val repo = container.repository
    var loading by remember { mutableStateOf(true) }
    var label by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var place by remember { mutableStateOf("") }
    var keywords by remember { mutableStateOf("") }
    var photoPath by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        loading = true
        val m = repo.getById(id)
        if (m == null) {
            error = "Not found"
        } else {
            label = m.label
            note = m.note
            place = m.placeText
            keywords = m.keywords
            photoPath = m.photoPath
        }
        loading = false
    }

    Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text("Edit") },
                navigationIcon = {
                    IconButton(onClick = onCancel) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        if (loading) {
            Column(Modifier.padding(padding).fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        if (error != null) {
            Column(Modifier.padding(padding).fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(error!!)
            }
            return@Scaffold
        }

        Column(
            Modifier.padding(padding).fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AsyncImage(model = photoPath, contentDescription = "Photo", modifier = Modifier.fillMaxWidth().height(260.dp))

            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("Label") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
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

            OutlinedTextField(
                value = keywords,
                onValueChange = { keywords = it },
                label = { Text("Keywords") },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("comma-separated") }
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onCancel) { Text("Cancel") }
                Button(
                    onClick = {
                        saving = true
                    },
                    enabled = !saving
                ) { Text("Save") }
            }
        }
    }

    // Save side-effect: keep UI simple and avoid extra VM; call repo directly.
    LaunchedEffect(saving) {
        if (!saving) return@LaunchedEffect
        try {
            repo.updateMemory(id, label, note, place, keywords)
            onDone()
        } catch (t: Throwable) {
            error = t.message ?: "Save failed"
        } finally {
            saving = false
        }
    }
}
