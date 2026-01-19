package com.memorypot.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [MemoryEntity::class],
    version = 3,
    exportSchema = true
)
abstract class MemoryDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao

    companion object {
        fun build(context: Context): MemoryDatabase =
            Room.databaseBuilder(context, MemoryDatabase::class.java, "memory_pot.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
