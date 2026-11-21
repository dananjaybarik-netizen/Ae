package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PluginDao {
    @Query("SELECT * FROM installed_plugins ORDER BY installedAt DESC")
    fun observeInstalledPlugins(): Flow<List<PluginEntity>>

    @Query("SELECT * FROM installed_plugins WHERE pluginId = :pluginId")
    suspend fun getById(pluginId: String): PluginEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlugin(plugin: PluginEntity)

    @Query("UPDATE installed_plugins SET isEnabled = :enabled WHERE pluginId = :pluginId")
    suspend fun setPluginEnabled(pluginId: String, enabled: Boolean)

    @Query("DELETE FROM installed_plugins WHERE pluginId = :pluginId")
    suspend fun deleteById(pluginId: String)
}
