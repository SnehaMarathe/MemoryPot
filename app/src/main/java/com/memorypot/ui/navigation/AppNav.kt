package com.memorypot.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.memorypot.di.LocalAppContainer
import com.memorypot.ui.screens.AddMemoryScreen
import com.memorypot.ui.screens.EditMemoryScreen
import com.memorypot.ui.screens.HomeScreen
import com.memorypot.ui.screens.MemoryDetailsScreen
import com.memorypot.ui.screens.OnboardingScreen
import com.memorypot.ui.screens.SettingsScreen

@Composable
fun AppNav() {
    val nav = rememberNavController()
    val container = LocalAppContainer.current
    val onboardingDone by container.settings.onboardingDoneFlow.collectAsState(initial = false)

    val start = if (onboardingDone) NavRoutes.HOME else NavRoutes.ONBOARDING

    NavHost(navController = nav, startDestination = start) {
        composable(NavRoutes.ONBOARDING) {
            OnboardingScreen(
                onDone = {
                    nav.navigate(NavRoutes.HOME) {
                        popUpTo(NavRoutes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }
        composable(NavRoutes.HOME) {
            HomeScreen(
                onAdd = { nav.navigate(NavRoutes.ADD) },
                onOpen = { id -> nav.navigate(NavRoutes.details(id)) },
                onSettings = { nav.navigate(NavRoutes.SETTINGS) }
            )
        }
        composable(NavRoutes.ADD) {
            AddMemoryScreen(
                onDone = { id ->
                    nav.navigate(NavRoutes.details(id)) {
                        popUpTo(NavRoutes.HOME)
                    }
                },
                onCancel = { nav.popBackStack() }
            )
        }
        composable(
            NavRoutes.DETAILS,
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { backStack ->
            val id = backStack.arguments?.getString("id") ?: return@composable
            MemoryDetailsScreen(
                id = id,
                onBack = { nav.popBackStack() },
                onEdit = { nav.navigate(NavRoutes.edit(id)) },
                onSettings = { nav.navigate(NavRoutes.SETTINGS) }
            )
        }
        composable(
            NavRoutes.EDIT,
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { backStack ->
            val id = backStack.arguments?.getString("id") ?: return@composable
            EditMemoryScreen(
                id = id,
                onDone = { nav.popBackStack() },
                onCancel = { nav.popBackStack() }
            )
        }
        composable(NavRoutes.SETTINGS) {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
    }
}
