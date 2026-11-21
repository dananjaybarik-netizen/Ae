package com.example

import android.app.Application
import android.content.ComponentCallbacks2
import coil.Coil
import coil.ImageLoader
import com.example.data.db.AiStudioDatabase
import com.example.data.preferences.StudioPreferences
import com.example.data.repository.PluginRepository
import com.example.data.repository.ProjectRepository
import com.example.engine.VideoExportEngine

class AiStudioApplication : Application(), ComponentCallbacks2 {

    lateinit var database: AiStudioDatabase
        private set
    lateinit var projectRepository: ProjectRepository
        private set
    lateinit var pluginRepository: PluginRepository
        private set
    lateinit var studioPreferences: StudioPreferences
        private set
    lateinit var exportEngine: VideoExportEngine
        private set

    override fun onCreate() {
        super.onCreate()
        database = AiStudioDatabase.getInstance(this)
        projectRepository = ProjectRepository(database.projectDao())
        pluginRepository = PluginRepository(this, database.pluginDao())
        studioPreferences = StudioPreferences(this)
        exportEngine = VideoExportEngine(this)

        // Cap Coil memory cache to stay safe on 4GB RAM phones
        Coil.setImageLoader(
            ImageLoader.Builder(this)
                .crossfade(true)
                .build()
        )
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            Coil.imageLoader(this).memoryCache?.clear()
        }
    }
}
