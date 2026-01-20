package com.memorypot.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
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
// Grid is intentionally not used in the "Apple Photos" style feed.
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.memorypot.ui.components.IOSSearchField
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
            // iOS-like large title header + trailing settings action
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Memories",
                        style = MaterialTheme.typography.headlineLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val subtitle = when (filter) {
                        HomeFilter.ACTIVE -> "Active"
                        HomeFilter.ARCHIVED -> "Archived"
                    }
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                }
            }

            // Search + Filter container
            Surface(
                tonalElevation = 2.dp,
                shadowElevation = 0.dp,
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    IOSSearchField(
                        value = query,
                        onValue = { vm.query.value = it },
                        placeholder = "I remember…"
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
                // Apple-ish: a calm vertical feed with a small "Memory Moments" strip on top.
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (query.isBlank()) {
                        item {
                            MemoryMoments(
                                memories = memories,
                                onOpen = onOpen,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        item {
                            Text(
                                "All memories",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(top = 4.dp, start = 2.dp)
                            )
                        }
                    }

                    items(memories, key = { it.id }) { m ->
                        MemoryFeedCard(
                            label = m.label,
                            place = m.placeText,
                            photoPath = m.photoPath,
                            createdAt = m.createdAt,
                            isArchived = m.isArchived,
                            onOpen = { onOpen(m.id) },
                            onSecondary = { if (m.isArchived) vm.unarchive(m.id) else vm.markFound(m.id) }
                        )
                    }

                    item { Spacer(Modifier.height(96.dp)) } // FAB breathing room
                }
            }
        }
    }
}

@Composable
private fun MemoryMoments(
    memories: List<com.memorypot.data.db.MemoryListItem>,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val featured = remember(memories) {
        if (memories.isEmpty()) emptyList() else {
            // A tiny bit of "alive" feeling without complex rules.
            val recent = memories.sortedByDescending { it.createdAt }.take(8)
            // Pick 3: most recent + two more (if available).
            listOfNotNull(
                recent.getOrNull(0),
                recent.getOrNull(1),
                recent.getOrNull(2)
            )
        }
    }

    if (featured.isEmpty()) return

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Memory moments", style = MaterialTheme.typography.titleMedium)
            Text(
                "Tap to open",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(featured, key = { it.id }) { m ->
                FeaturedMemoryCard(
                    label = m.label,
                    photoPath = m.photoPath,
                    subtitle = m.placeText.ifBlank { java.text.DateFormat.getDateInstance().format(java.util.Date(m.createdAt)) },
                    onClick = { onOpen(m.id) }
                )
            }
        }
    }
}

@Composable
private fun FeaturedMemoryCard(
    label: String,
    photoPath: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .size(width = 240.dp, height = 156.dp)
    ) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(
                model = java.io.File(photoPath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            ) {
                Text(
                    label.ifBlank { "Untitled" },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun MemoryFeedCard(
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
            .clip(RoundedCornerShape(22.dp))
            .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(22.dp))
            .clickable { onOpen() },
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column {
            AsyncImage(
                model = java.io.File(photoPath),
                contentDescription = "Photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    label.ifBlank { "Untitled" },
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    place.ifBlank { java.text.DateFormat.getDateInstance().format(java.util.Date(createdAt)) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        java.text.DateFormat.getDateInstance().format(java.util.Date(createdAt)),
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
            .height(232.dp)
            .clip(RoundedCornerShape(22.dp))
            .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(22.dp))
            .clickable { onOpen() },
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column {
            Box(modifier = Modifier.weight(1f)) {
                AsyncImage(
                    model = java.io.File(photoPath),
                    contentDescription = "Photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(154.dp)
                        .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                )

                // Small label pill overlay
                val title = label.ifBlank { "Untitled" }
                // Apple-ish: small pill with slightly more breathing room.
                Pill(
                    label = title,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
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
