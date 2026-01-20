package com.memorypot.di

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import com.memorypot.data.db.MemoryDatabase
import com.memorypot.data.repo.AiKeywordHelper
import com.memorypot.data.repo.ExportImport
import com.memorypot.data.repo.LocationHelper
import com.memorypot.data.repo.MemoryRepository
import com.memorypot.data.repo.PhotoStore
import com.memorypot.data.settings.SettingsDataStore

class AppContainer(appContext: Context) {
    private val context = appContext.applicationContext

    private val db by lazy { MemoryDatabase.build(context) }
    val settings by lazy { SettingsDataStore(context) }
    private val photoStore by lazy { PhotoStore(context) }
    private val locationHelper by lazy { LocationHelper(context) }
    val aiKeywordHelper by lazy { AiKeywordHelper(context) }

    val repository by lazy {
        MemoryRepository(
            dao = db.memoryDao(),
            photoStore = photoStore,
            locationHelper = locationHelper,
            settings = settings
        )
    }

    val exportImport by lazy { ExportImport(context, db.memoryDao()) }
}

val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer not provided")
}
