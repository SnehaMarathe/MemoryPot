package com.memorypot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import com.memorypot.di.LocalAppContainer
import com.memorypot.ui.navigation.AppNav
import com.memorypot.ui.theme.MemoryPotTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as MemoryPotApp
        setContent {
            CompositionLocalProvider(LocalAppContainer provides app.container) {
                // Keep a consistent, premium look across devices ("Apple-like" stability),
                // instead of per-device dynamic colors.
                MemoryPotTheme(useDynamicColor = false) {
                    AppNav()
                }
            }
        }
    }
}
