package com.memorypot.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val SAVE_LOCATION = booleanPreferencesKey("save_location")
        val NEARBY_PROMPT = booleanPreferencesKey("nearby_prompt")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
    }

    val saveLocationFlow: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.SAVE_LOCATION] ?: true }

    val nearbyPromptFlow: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.NEARBY_PROMPT] ?: true }

    val onboardingDoneFlow: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.ONBOARDING_DONE] ?: false }

    suspend fun setSaveLocation(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SAVE_LOCATION] = enabled }
    }

    suspend fun setNearbyPrompt(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NEARBY_PROMPT] = enabled }
    }

    suspend fun setOnboardingDone(done: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_DONE] = done }
    }
}
