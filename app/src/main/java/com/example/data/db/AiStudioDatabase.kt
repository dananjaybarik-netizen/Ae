package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ProjectEntity::class, PluginEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AiStudioDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun pluginDao(): PluginDao

    companion object {
        @Volatile
        private var INSTANCE: AiStudioDatabase? = null

        fun getInstance(context: Context): AiStudioDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AiStudioDatabase::class.java,
                    "ai_studio_nle.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
