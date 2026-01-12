package com.memorypot.ui.navigation

object NavRoutes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val ADD = "add"
    const val DETAILS = "details/{id}"
    const val EDIT = "edit/{id}"
    const val SETTINGS = "settings"

    fun details(id: String) = "details/$id"
    fun edit(id: String) = "edit/$id"
}
