package com.memorypot.data.db

/**
 * Lightweight projections for UI + heuristics.
 */
data class MemoryListItem(
    val id: String,
    val label: String,
    val placeText: String,
    val photoPath: String,
    val createdAt: Long,
    val isArchived: Boolean,
    val latitude: Double?,
    val longitude: Double?
)

data class PlaceCount(
    val placeText: String,
    val cnt: Int,
    val lastCreatedAt: Long
)
