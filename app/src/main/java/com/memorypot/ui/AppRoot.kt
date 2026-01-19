package com.memorypot.ui

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val hasCamera = remember { mutableStateOf(hasPermission(context, Manifest.permission.CAMERA)) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasCamera.value = granted }
    )

    LaunchedEffect(Unit) {
        if (!hasCamera.value) launcher.launch(Manifest.permission.CAMERA)
    }

    if (!hasCamera.value) {
        PermissionGate(
            onRequest = { launcher.launch(Manifest.permission.CAMERA) }
        )
    } else {
        // Delegate to the app's real navigation graph.
        // (There is also a com.memorypot.ui.AppNav wrapper for backwards compatibility.)
        AppNav()
    }
}

@Composable
private fun PermissionGate(onRequest: () -> Unit) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Camera permission is required for live object picking.",
                style = MaterialTheme.typography.titleMedium
            )
            Button(onClick = onRequest) { Text("Grant camera permission") }
        }
    }
}

private fun hasPermission(context: Context, permission: String): Boolean {
    return ContextCompat.checkSelfPermission(context, permission) == PermissionChecker.PERMISSION_GRANTED
}


