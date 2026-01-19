package com.memorypot.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val note: String,
    val placeText: String,
    val timestampMs: Long,
    val photoPath: String,
    // JSON array of selected objects (normalized box coords + optional label)
    val selectedObjectsJson: String,
)
