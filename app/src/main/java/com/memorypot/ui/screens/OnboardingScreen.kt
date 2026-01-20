package com.memorypot.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.memorypot.di.LocalAppContainer
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()

    val permissions = remember {
        buildList {
            add(Manifest.permission.CAMERA)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Regardless of result, allow user to proceed. App works without optional permissions.
        scope.launch {
            container.settings.setOnboardingDone(true)
            onDone()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Memory Pot", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Save a photo + note so you remember where you kept things. Everything stays on your phone by default.",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.height(16.dp))

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Optional permissions", style = MaterialTheme.typography.titleMedium)
                Text("• Camera — capture photos fast")
                Text("• Location — auto-fill place and nearby prompt")
                Text("• Notifications — reserved for future reminders")
            }
        }

        Spacer(Modifier.height(20.dp))

        Button(onClick = { launcher.launch(permissions) }) {
            Text("Continue & Request Permissions")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = {
            scope.launch {
                container.settings.setOnboardingDone(true)
                onDone()
            }
        }) {
            Text("Skip (You can enable later)")
        }
    }
}
