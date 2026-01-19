package com.memorypot.data.repo

import com.memorypot.data.db.MemoryDao
import com.memorypot.data.db.MemoryEntity
import com.memorypot.data.db.MemoryListItem
import com.memorypot.data.db.PlaceCount
import com.memorypot.data.settings.SettingsDataStore
import android.graphics.RectF
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.UUID

data class LocationSuggestion(
    val placeText: String,
    val confidence: Confidence,
    val count: Int,
    val lastSeenAt: Long
)

enum class Confidence { LOW, MEDIUM, HIGH }

class MemoryRepository(
    private val dao: MemoryDao,
    private val photoStore: PhotoStore,
    private val locationHelper: LocationHelper,
    private val settings: SettingsDataStore
) {

    fun observeMemories(archived: Boolean, query: String): Flow<List<MemoryListItem>> {
        val q = query.trim()
        return if (q.isBlank()) dao.observeByArchived(archived) else dao.observeSearchByArchived(q, archived)
    }

    suspend fun getById(id: String): MemoryEntity? = dao.getById(id)

    suspend fun createMemory(
        label: String,
        note: String,
        placeTextUser: String,
        keywordsUser: String,
        photoPath: String,
        selectedBoxesNormalized: List<RectF> = emptyList()
    ): String {
        val now = System.currentTimeMillis()

        val saveLocation = settings.saveLocationFlow.first()
        val loc = if (saveLocation && locationHelper.hasLocationPermission()) {
            locationHelper.getCurrentLocationOrNull()
        } else null

        val lat = loc?.latitude
        val lon = loc?.longitude
        val autoPlace = if (lat != null && lon != null) locationHelper.reverseGeocodeShort(lat, lon) else ""

        val finalPlace = placeTextUser.trim().ifBlank { autoPlace }.ifBlank { "Unknown place" }
        val geoKey = if (lat != null && lon != null) locationHelper.geoKey(lat, lon) else null

        val id = UUID.randomUUID().toString()
        val entity = MemoryEntity(
            id = id,
            label = label.trim(),
            note = note.trim(),
            placeText = finalPlace,
            keywords = keywordsUser.trim(),
            photoPath = photoPath,
            selectedBoxesJson = selectedBoxesNormalized
                .takeIf { it.isNotEmpty() }
                ?.let { SelectedBoxesJson.encode(it) },
            createdAt = now,
            isArchived = false,
            latitude = lat,
            longitude = lon,
            geoKey = geoKey
        )
        dao.upsert(entity)
        return id
    }

    suspend fun updateMemory(
        id: String,
        label: String,
        note: String,
        placeText: String,
        keywords: String
    ) {
        val existing = dao.getById(id) ?: return
        dao.update(
            existing.copy(
                label = label.trim(),
                note = note.trim(),
                placeText = placeText.trim().ifBlank { "Unknown place" },
                keywords = keywords.trim()
            )
        )
    }

    suspend fun markFound(id: String) {
        val existing = dao.getById(id) ?: return
        dao.update(existing.copy(isArchived = true))
    }

    suspend fun unarchive(id: String) {
        val existing = dao.getById(id) ?: return
        dao.update(existing.copy(isArchived = false))
    }

    suspend fun deleteMemory(id: String) {
        val existing = dao.getById(id)
        if (existing != null) {
            photoStore.deleteIfExists(existing.photoPath)
            dao.deleteById(id)
        }
    }

    suspend fun clearAll() {
        val all = dao.getAll()
        all.forEach { photoStore.deleteIfExists(it.photoPath) }
        dao.deleteAll()
    }

    suspend fun suggestionsForLabel(label: String): List<LocationSuggestion> {
        val trimmed = label.trim()
        if (trimmed.isBlank()) return emptyList()

        val now = System.currentTimeMillis()
        val top: List<PlaceCount> = dao.topPlacesForLabel(trimmed)

        return top.map {
            val ageDays = (now - it.lastCreatedAt) / (1000L * 60 * 60 * 24)
            val conf = when {
                it.cnt >= 3 && ageDays <= 30 -> Confidence.HIGH
                it.cnt >= 2 || ageDays <= 30 -> Confidence.MEDIUM
                else -> Confidence.LOW
            }
            LocationSuggestion(
                placeText = it.placeText,
                confidence = conf,
                count = it.cnt,
                lastSeenAt = it.lastCreatedAt
            )
        }
    }

    suspend fun findNearbyPromptCandidate(): MemoryEntity? {
        val enabled = settings.nearbyPromptFlow.first()
        val saveLocation = settings.saveLocationFlow.first()
        if (!enabled || !saveLocation) return null
        if (!locationHelper.hasLocationPermission()) return null

        val here = locationHelper.getCurrentLocationOrNull() ?: return null
        val candidates = dao.getActiveWithLocation()
        // First item within 50m, preferring most recent
        val within = candidates
            .sortedByDescending { it.createdAt }
            .firstOrNull { m ->
                val lat = m.latitude ?: return@firstOrNull false
                val lon = m.longitude ?: return@firstOrNull false
                locationHelper.distanceMeters(here.latitude, here.longitude, lat, lon) <= 50f
            }
        return within
    }
}
