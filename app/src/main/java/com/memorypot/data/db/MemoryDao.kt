package com.memorypot.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MemoryEntity)

    @Update
    suspend fun update(entity: MemoryEntity)

    @Query("SELECT * FROM memories WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): MemoryEntity?

    @Query(
        """
        SELECT id, label, placeText, photoPath, createdAt, isArchived, latitude, longitude
        FROM memories
        WHERE isArchived = :archived
        ORDER BY createdAt DESC
        """
    )
    fun observeByArchived(archived: Boolean): Flow<List<MemoryListItem>>

    @Query(
        """
        SELECT id, label, placeText, photoPath, createdAt, isArchived, latitude, longitude
        FROM memories
        WHERE isArchived = :archived
          AND (
            label LIKE '%' || :q || '%'
            OR note LIKE '%' || :q || '%'
            OR placeText LIKE '%' || :q || '%'
          )
        ORDER BY createdAt DESC
        """
    )
    fun observeSearchByArchived(q: String, archived: Boolean): Flow<List<MemoryListItem>>

    @Query(
        """
        SELECT * FROM memories
        WHERE isArchived = 0
        AND latitude IS NOT NULL AND longitude IS NOT NULL
        """
    )
    suspend fun getActiveWithLocation(): List<MemoryEntity>

    @Query(
        """
        SELECT placeText as placeText,
               COUNT(*) as cnt,
               MAX(createdAt) as lastCreatedAt
        FROM memories
        WHERE TRIM(LOWER(label)) = TRIM(LOWER(:label))
          AND placeText <> ''
        GROUP BY placeText
        ORDER BY cnt DESC, lastCreatedAt DESC
        LIMIT 3
        """
    )
    suspend fun topPlacesForLabel(label: String): List<PlaceCount>

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM memories")
    suspend fun deleteAll()

    @Query("SELECT * FROM memories ORDER BY createdAt DESC")
    suspend fun getAll(): List<MemoryEntity>
}
