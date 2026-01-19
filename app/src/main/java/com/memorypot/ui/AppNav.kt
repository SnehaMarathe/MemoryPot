package com.memorypot.ui

import androidx.compose.runtime.Composable
import com.memorypot.ui.navigation.AppNav as NewAppNav

/**
 * Backwards-compatible entry point.
 *
 * Some older builds referenced `com.memorypot.ui.AppNav`. The current navigation graph lives in
 * `com.memorypot.ui.navigation.AppNav`. This wrapper keeps both working and avoids stale
 * references (e.g. passing now-removed parameters like `hasCameraPermission`).
 */
@Composable
fun AppNav() {
    NewAppNav()
}
