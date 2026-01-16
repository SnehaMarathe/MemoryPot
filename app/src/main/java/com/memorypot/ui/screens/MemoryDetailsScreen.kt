package com.memorypot.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.lazy.LazyColumn
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
import com.memorypot.ui.components.AppTopBar
import com.memorypot.ui.components.IOSBottomActionBar
import com.memorypot.ui.components.IOSGroupedSurface
import com.memorypot.ui.components.IOSRow
import com.memorypot.ui.components.IOSSectionHeader
import com.memorypot.ui.components.KeywordChipsDisplay
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
            AppTopBar(
                title = "",
                onBack = onBack,
                actionIcon = Icons.Default.Settings,
                actionLabel = "Settings",
                onAction = onSettings
            )
        },
        bottomBar = {
            val m = state.memory
            if (m != null) {
                IOSBottomActionBar {
                    // Primary action mirrors Apple's bottom actions: full-width primary, secondary outlined.
                    OutlinedButton(
                        onClick = onEdit,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                        Spacer(Modifier.padding(4.dp))
                        Text("Edit")
                    }

                    Button(
                        onClick = { vm.markFound(id) { onBack() } },
                        modifier = Modifier.weight(1f),
                        enabled = !m.isArchived
                    ) { Text(if (m.isArchived) "Archived" else "Mark as Found") }
                }
            }
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
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 12.dp)
                .imePadding()
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    // Hero photo card (Apple Photos-ish)
                    AsyncImage(
                        model = m.photoPath,
                        contentDescription = "Photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 220.dp, max = 420.dp)
                            .aspectRatio(4f / 3f)
                            .clip(RoundedCornerShape(22.dp))
                    )
                }

                item {
                    Text(
                        m.label.ifBlank { "Untitled" },
                        style = MaterialTheme.typography.headlineLarge
                    )
                }

                if (m.note.isNotBlank()) {
                    item {
                        IOSSectionHeader("NOTE")
                        IOSGroupedSurface {
                            IOSRow(title = m.note, subtitle = null, showDivider = false)
                        }
                    }
                }

                item {
                    IOSSectionHeader("DETAILS")
                    IOSGroupedSurface {
                        IOSRow(
                            title = "Place",
                            subtitle = m.placeText.ifBlank { "Unknown place" },
                            showDivider = true
                        )
                        IOSRow(
                            title = "Saved",
                            subtitle = java.text.DateFormat.getDateTimeInstance().format(java.util.Date(m.createdAt)),
                            showDivider = true
                        )
                        val locText = if (m.latitude != null && m.longitude != null)
                            "${"%.5f".format(m.latitude)}, ${"%.5f".format(m.longitude)}"
                        else "Not saved"
                        IOSRow(
                            title = "GPS",
                            subtitle = locText,
                            showDivider = false
                        )
                    }
                }

                if (m.keywords.isNotBlank()) {
                    item {
                        IOSSectionHeader("KEYWORDS")
                        IOSGroupedSurface {
                            // Chips in a grouped surface feels iOS-like
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                KeywordChipsDisplay(m.keywords, title = "")
                            }
                        }
                    }
                }

                item {
                    IOSSectionHeader("DANGER ZONE")
                    IOSGroupedSurface {
                        IOSRow(
                            title = "Delete memory",
                            subtitle = "Removes the memory and deletes its photo from this device.",
                            trailing = { TextButton(onClick = { confirmDelete = true }) { Text("Delete") } },
                            showDivider = false
                        )
                    }
                }
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
