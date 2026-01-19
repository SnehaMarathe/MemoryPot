package com.memorypot.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun AppNav(hasCameraPermission: Boolean) {
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onAdd = { nav.navigate("add") }
            )
        }
        composable("add") {
            AddMemoryScreen(
                hasCameraPermission = hasCameraPermission,
                onDone = { nav.popBackStack() }
            )
        }
    }
}
