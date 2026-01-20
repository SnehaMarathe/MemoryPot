package com.memorypot.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "memories",
    indices = [
        Index(value = ["label"]),
        Index(value = ["isArchived"]),
        Index(value = ["createdAt"]),
        Index(value = ["geoKey"])
    ]
)
data class MemoryEntity(
    @PrimaryKey val id: String,
    val label: String,
    val note: String,
    val placeText: String,
    /** Comma-separated keywords (AI suggested + user edited). */
    val keywords: String,
    val photoPath: String,
    val createdAt: Long,
    val isArchived: Boolean,
    val latitude: Double?,
    val longitude: Double?,
    val geoKey: String?
)
