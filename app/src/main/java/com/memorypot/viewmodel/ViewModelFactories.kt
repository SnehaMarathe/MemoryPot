package com.memorypot.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.memorypot.data.repo.ExportImport
import com.memorypot.data.repo.MemoryRepository
import com.memorypot.data.settings.SettingsDataStore

class HomeVmFactory(private val repo: MemoryRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return HomeViewModel(repo) as T
    }
}

class AddVmFactory(private val repo: MemoryRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return AddMemoryViewModel(repo) as T
    }
}

class DetailsVmFactory(private val repo: MemoryRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return DetailsViewModel(repo) as T
    }
}

class SettingsVmFactory(
    private val settings: SettingsDataStore,
    private val repo: MemoryRepository,
    private val exportImport: ExportImport
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return SettingsViewModel(settings, repo, exportImport) as T
    }
}
