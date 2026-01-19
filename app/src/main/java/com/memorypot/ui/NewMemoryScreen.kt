package com.memorypot.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Backward-compatible entry point.
 *
 * Some older code paths may still reference `NewMemoryScreen`. The actual live-camera
 * multi-select object picking + save/submit workflow is implemented in [AddMemoryScreen].
 */
@Composable
fun NewMemoryScreen(
    onDone: () -> Unit = {},
    vm: AddMemoryViewModel = viewModel()
) {
    AddMemoryScreen(onDone = onDone, vm = vm)
}
