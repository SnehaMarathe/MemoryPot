package com.memorypot.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.memorypot.di.LocalAppContainer
import com.memorypot.viewmodel.HomeVmFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAdd: () -> Unit,
    onOpen: (String) -> Unit
) {
    val container = LocalAppContainer.current
    val vm: HomeViewModel = viewModel(factory = HomeVmFactory(container.repository))

    val memories by vm.memories.collectAsState()
    val query by vm.query.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MemoryPot") },
                actions = {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { vm.query.value = it },
                        singleLine = true,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .widthIn(min = 160.dp, max = 260.dp),
                        placeholder = { Text("Search") }
                    )
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) { Text("+") }
        }
    ) { pad ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(memories, key = { it.id }) { m ->
                Card(onClick = { onOpen(m.id) }) {
                    Column(Modifier.padding(12.dp)) {
                        Text(m.label.ifBlank { "Untitled" }, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(m.placeText, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
