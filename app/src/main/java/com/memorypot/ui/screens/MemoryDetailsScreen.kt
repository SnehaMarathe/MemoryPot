package com.memorypot.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.memorypot.ui.components.InlineRowKeyValue
import com.memorypot.ui.components.SimpleTopBar
import com.memorypot.viewmodel.DetailsViewModel
import com.memorypot.viewmodel.DetailsVmFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryDetailsScreen(
    id: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onSettings: () -> Unit
) {
    val container = LocalAppContainer.current
    val vm: DetailsViewModel = viewModel(factory = DetailsVmFactory(container.repository))
    val state by vm.state.collectAsState()

    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(id) { vm.load(id) }

    Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text("Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
                }
            )
        }
    ) { padding ->
        val m = state.memory
        if (m == null) {
            Column(Modifier.padding(padding).fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.error ?: "Loading…")
            }
            return@Scaffold
        }

        Column(
            Modifier.padding(padding).fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AsyncImage(
                model = m.photoPath,
                contentDescription = "Photo",
                modifier = Modifier.fillMaxWidth().height(320.dp)
            )

            Text(m.label.ifBlank { "Untitled" }, style = MaterialTheme.typography.headlineSmall)
            if (m.note.isNotBlank()) Text(m.note, style = MaterialTheme.typography.bodyLarge)

            InlineRowKeyValue("Place:", m.placeText.ifBlank { "Unknown place" })
            InlineRowKeyValue("Saved:", java.text.DateFormat.getDateTimeInstance().format(java.util.Date(m.createdAt)))

            val locText = if (m.latitude != null && m.longitude != null) "${"%.5f".format(m.latitude)}, ${"%.5f".format(m.longitude)}" else "Not saved"
            InlineRowKeyValue("Location:", locText)

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { vm.markFound(id) { onBack() } },
                    modifier = Modifier.weight(1f),
                    enabled = !m.isArchived
                ) { Text(if (m.isArchived) "Archived" else "Mark as Found") }

                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                    Spacer(Modifier.padding(4.dp))
                    Text("Edit")
                }
            }

            OutlinedButton(
                onClick = { confirmDelete = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
                Spacer(Modifier.padding(4.dp))
                Text("Delete")
            }
        }

        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text("Delete memory?") },
                text = { Text("This will remove the memory and delete its photo from this device.") },
                confirmButton = {
                    Button(onClick = {
                        confirmDelete = false
                        vm.delete(id) { onBack() }
                    }) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
                }
            )
        }
    }
}
