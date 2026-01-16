package com.memorypot.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.memorypot.di.LocalAppContainer
import com.memorypot.ui.components.AppTopBar
import com.memorypot.viewmodel.SettingsViewModel
import com.memorypot.viewmodel.SettingsVmFactory
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: SettingsViewModel = viewModel(factory = SettingsVmFactory(container.settings, container.repository, container.exportImport))
    val state by vm.state.collectAsState()

    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var confirmClear by remember { mutableStateOf(false) }

    val createDoc = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            vm.exportJson(uri) { msg ->
                scope.launch { snack.showSnackbar(msg) }
            }
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Settings",
                onBack = onBack
            )
        },
        snackbarHost = { SnackbarHost(snack) }
    ) { padding ->
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
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Save location", style = MaterialTheme.typography.titleMedium)
                            Text("Attach location when saving (permission-dependent).", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = state.saveLocation, onCheckedChange = vm::setSaveLocation)
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Prompt me to clear when nearby", style = MaterialTheme.typography.titleMedium)
                            Text("Checks only when Home opens.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = state.nearbyPrompt, onCheckedChange = vm::setNearbyPrompt)
                    }
                        }
                    }
                }

                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Export / Backup", style = MaterialTheme.typography.titleMedium)
                            Text("v0 exports JSON metadata only (photos not embedded).", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(onClick = { createDoc.launch("memory-pot-export.json") }) {
                                Text("Export JSON")
                            }
                        }
                    }
                }

                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Danger zone", style = MaterialTheme.typography.titleMedium)
                            Button(onClick = { confirmClear = true }) {
                                Text("Clear all data")
                            }
                        }
                    }
                }

                item { Spacer(Modifier.padding(bottom = 84.dp)) }
            }
        }

        if (confirmClear) {
            AlertDialog(
                onDismissRequest = { confirmClear = false },
                title = { Text("Clear all data?") },
                text = { Text("This will delete all memories and photos from this device. This cannot be undone.") },
                confirmButton = {
                    Button(onClick = {
                        confirmClear = false
                        vm.clearAll { msg -> scope.launch { snack.showSnackbar(msg) } }
                    }) { Text("Clear") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
                }
            )
        }
    }
}
