package com.memorypot

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import com.memorypot.di.AppContainer
import com.memorypot.di.LocalAppContainer
import com.memorypot.ui.navigation.AppNav
import com.memorypot.ui.theme.MemoryPotTheme

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val appContainer = remember { AppContainer(this) }
            val cameraPermissionState =
                rememberPermissionState(Manifest.permission.CAMERA)

            CompositionLocalProvider(
                LocalAppContainer provides appContainer
            ) {
                MemoryPotTheme(useDynamicColor = false) {

                    if (cameraPermissionState.status.isGranted) {
                        AppNav()
                    } else {
                        cameraPermissionState.launchPermissionRequest()
                    }

                }
            }
        }
    }
}
