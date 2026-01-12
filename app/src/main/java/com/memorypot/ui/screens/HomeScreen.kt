package com.memorypot.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.memorypot.di.LocalAppContainer
import com.memorypot.ui.components.Pill
import com.memorypot.ui.components.SearchField
import com.memorypot.ui.components.SimpleTopBar
import com.memorypot.ui.components.SuggestionCard
import com.memorypot.viewmodel.HomeFilter
import com.memorypot.viewmodel.HomeViewModel
import com.memorypot.viewmodel.HomeVmFactory
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAdd: () -> Unit,
    onOpen: (String) -> Unit,
    onSettings: () -> Unit
) {
    val container = LocalAppContainer.current
    val vm: HomeViewModel = viewModel(factory = HomeVmFactory(container.repository))

    val memories by vm.memories.collectAsState()
    val query by vm.query.collectAsState()
    val filter by vm.filter.collectAsState()
    val nearbyPrompt by vm.nearbyPrompt.collectAsState()

    val (suggestions, setSuggestions) = remember { mutableStateOf(emptyList<com.memorypot.data.repo.LocationSuggestion>()) }

    LaunchedEffect(Unit) { vm.refreshNearbyPrompt() }

    LaunchedEffect(query, filter) {
        if (filter == HomeFilter.ACTIVE && query.trim().isNotBlank()) {
            setSuggestions(container.repository.suggestionsForLabel(query.trim()))
        } else setSuggestions(emptyList())
    }

    if (nearbyPrompt != null) {
        AlertDialog(
            onDismissRequest = { vm.dismissNearbyPrompt() },
            title = { Text("Are you at this place?") },
            text = {
                Text(
                    "You're near “${nearbyPrompt!!.placeText}”. Mark “${nearbyPrompt!!.label.ifBlank { "this item" }}” as found?",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.markFound(nearbyPrompt!!.id) }) { Text("Mark as found") }
            },
            dismissButton = { TextButton(onClick = { vm.dismissNearbyPrompt() }) { Text("Not now") } }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            SimpleTopBar(
                title = "Memory Pot",
                actionIcon = Icons.Default.Settings,
                actionLabel = "",
                onAction = onSettings
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Search + Filter container
            Surface(
                tonalElevation = 2.dp,
                shadowElevation = 0.dp,
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SearchField(
                        value = query,
                        onValue = { vm.query.value = it },
                        placeholder = "Search label, note, place"
                    )

                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        val activeSelected = filter == HomeFilter.ACTIVE
                        SegmentedButton(
                            selected = activeSelected,
                            onClick = { vm.filter.value = HomeFilter.ACTIVE },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                        ) { Text("Active") }

                        SegmentedButton(
                            selected = !activeSelected,
                            onClick = { vm.filter.value = HomeFilter.ARCHIVED },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                        ) { Text("Archived") }
                    }
                }
            }

            SuggestionCard(
                query = query,
                suggestions = suggestions,
                onTapPlace = { place -> vm.query.value = place },
                modifier = Modifier.fillMaxWidth()
            )

            if (memories.isEmpty() && query.isBlank()) {
                EmptyState(onAdd = onAdd, archived = (filter == HomeFilter.ARCHIVED))
            } else if (memories.isEmpty()) {
                Text(
                    "No matches. Try a different word.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp)
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 170.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(memories, key = { it.id }) { m ->
                        MemoryCard(
                            label = m.label,
                            place = m.placeText,
                            photoPath = m.photoPath,
                            createdAt = m.createdAt,
                            isArchived = m.isArchived,
                            onOpen = { onOpen(m.id) },
                            onSecondary = {
                                if (m.isArchived) vm.unarchive(m.id) else vm.markFound(m.id)
                            }
                        )
                    }
                    item { Spacer(Modifier.height(84.dp)) } // FAB breathing room
                }
            }
        }
    }
}

@Composable
private fun EmptyState(onAdd: () -> Unit, archived: Boolean) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                if (archived) "No archived memories yet" else "Your Memory Pot is empty",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                if (archived) "When you mark items as found, they’ll show up here."
                else "Save a photo + quick note so you can find things in seconds.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!archived) {
                OutlinedButton(onClick = onAdd) { Text("Add your first memory") }
            }
        }
    }
}

@Composable
private fun MemoryCard(
    label: String,
    place: String,
    photoPath: String,
    createdAt: Long,
    isArchived: Boolean,
    onOpen: () -> Unit,
    onSecondary: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable { onOpen() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Box(modifier = Modifier.weight(1f)) {
                AsyncImage(
                    model = java.io.File(photoPath),
                    contentDescription = "Photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                )

                // Small label pill overlay
                val title = label.ifBlank { "Untitled" }
                Pill(
                    label = title,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    place.ifBlank { "Unknown place" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        DateFormat.getDateInstance().format(Date(createdAt)),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = onSecondary) {
                        Text(if (isArchived) "Unarchive" else "Found it")
                    }
                }
            }
        }
    }
}
