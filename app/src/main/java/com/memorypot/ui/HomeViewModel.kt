package com.memorypot.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memorypot.data.db.MemoryListItem
import com.memorypot.data.repo.MemoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class HomeFilter { ACTIVE, ARCHIVED }

data class NearbyPrompt(
    val id: String,
    val label: String,
    val placeText: String
)

/**
 * NOTE: No direct DB imports here besides the public DTO [MemoryListItem].
 * The repository owns persistence.
 */
class HomeViewModel(private val repo: MemoryRepository) : ViewModel() {

    val query = MutableStateFlow("")
    val filter = MutableStateFlow(HomeFilter.ACTIVE)

    val memories: StateFlow<List<MemoryListItem>> = combine(filter, query) { f, q ->
        f to q
    }.let { combined ->
        combined.flatMapLatestCompat { (f, q) ->
            repo.observeMemories(archived = (f == HomeFilter.ARCHIVED), query = q)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    private val _nearbyPrompt = MutableStateFlow<NearbyPrompt?>(null)
    val nearbyPrompt: StateFlow<NearbyPrompt?> = _nearbyPrompt

    fun refreshNearbyPrompt() {
        viewModelScope.launch {
            val m = repo.findNearbyPromptCandidate()
            _nearbyPrompt.value = m?.let {
                NearbyPrompt(it.id, it.label.ifBlank { "Untitled" }, it.placeText)
            }
        }
    }

    fun dismissNearbyPrompt() {
        _nearbyPrompt.value = null
    }

    fun markFound(id: String) {
        viewModelScope.launch {
            repo.markFound(id)
            dismissNearbyPrompt()
        }
    }

    fun unarchive(id: String) {
        viewModelScope.launch { repo.unarchive(id) }
    }
}

/**
 * Avoid adding an external dependency for flatMapLatest.
 */
private fun <T, R> kotlinx.coroutines.flow.Flow<T>.flatMapLatestCompat(
    transform: suspend (T) -> kotlinx.coroutines.flow.Flow<R>
): kotlinx.coroutines.flow.Flow<R> =
    kotlinx.coroutines.flow.channelFlow {
        var job: kotlinx.coroutines.Job? = null
        collect { value ->
            job?.cancel()
            job = launch {
                transform(value).collect { send(it) }
            }
        }
    }
