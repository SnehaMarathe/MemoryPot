package com.memorypot.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.memorypot.di.LocalAppContainer
import com.memorypot.ui.components.AppTopBar
import com.memorypot.ui.components.KeywordEditor
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
    var keywordPrompt by remember { mutableStateOf("") }
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
            AppTopBar(
                title = "Edit",
                onBack = onCancel
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
                    AsyncImage(
                        model = photoPath,
                        contentDescription = "Photo",
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(4f / 3f)
                            .heightIn(max = 320.dp)
                    )
                }

                item {
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text("Label") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                item {
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Note") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    OutlinedTextField(
                        value = place,
                        onValueChange = { place = it },
                        label = { Text("Place") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    KeywordEditor(
                        keywords = keywords,
                        onKeywordsChange = { keywords = it },
                        prompt = keywordPrompt,
                        onPromptChange = { keywordPrompt = it },
                        onApplyPrompt = {
                            val merged = (keywords + "," + keywordPrompt)
                                .split(',', '\n', ';')
                                .map { it.trim() }
                                .filter { it.isNotBlank() }
                                .distinctBy { it.lowercase() }
                                .joinToString(", ")
                            keywords = merged
                            keywordPrompt = ""
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    if (error != null) {
                        Text(error!!)
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f)
                ) { Text("Cancel") }
                Button(
                    onClick = { saving = true },
                    enabled = !saving,
                    modifier = Modifier.weight(1f)
                ) {
                    if (saving) {
                        CircularProgressIndicator()
                        Spacer(Modifier.padding(4.dp))
                    }
                    Text("Save")
                }
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
