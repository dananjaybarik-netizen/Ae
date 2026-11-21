package com.example.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.ExportResolution
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.studioDataStore by preferencesDataStore(name = "ai_studio_settings")

enum class PreviewResolutionPreset(val label: String, val maxHeight: Int) {
    AUTO("Auto (RAM Adaptive)", 720),
    P360("360p Ultra-Fast Proxy", 360),
    P540("540p Balanced", 540),
    P720("720p HD Preview", 720),
    P1080("1080p Full Preview", 1080)
}

data class StudioSettingsState(
    val lowRamSafeMode: Boolean = true,
    val previewResolution: PreviewResolutionPreset = PreviewResolutionPreset.AUTO,
    val enableEffectCache: Boolean = true,
    val thermalThrottlingEnabled: Boolean = true,
    val preferVulkanWhenSupported: Boolean = true,
    val defaultExportResolution: ExportResolution = ExportResolution.P1080,
    val defaultExportFps: Int = 30
)

class StudioPreferences(private val context: Context) {

    private object Keys {
        val LOW_RAM_SAFE_MODE = booleanPreferencesKey("low_ram_safe_mode")
        val PREVIEW_RESOLUTION = stringPreferencesKey("preview_resolution")
        val EFFECT_CACHE = booleanPreferencesKey("enable_effect_cache")
        val THERMAL_THROTTLING = booleanPreferencesKey("thermal_throttling")
        val PREFER_VULKAN = booleanPreferencesKey("prefer_vulkan")
        val DEFAULT_EXPORT_RES = stringPreferencesKey("default_export_res")
        val DEFAULT_EXPORT_FPS = intPreferencesKey("default_export_fps")
    }

    val settingsFlow: Flow<StudioSettingsState> = context.studioDataStore.data.map { prefs ->
        val resName = prefs[Keys.PREVIEW_RESOLUTION] ?: PreviewResolutionPreset.AUTO.name
        val exportResName = prefs[Keys.DEFAULT_EXPORT_RES] ?: ExportResolution.P1080.name
        StudioSettingsState(
            lowRamSafeMode = prefs[Keys.LOW_RAM_SAFE_MODE] ?: true,
            previewResolution = PreviewResolutionPreset.entries.firstOrNull { it.name == resName }
                ?: PreviewResolutionPreset.AUTO,
            enableEffectCache = prefs[Keys.EFFECT_CACHE] ?: true,
            thermalThrottlingEnabled = prefs[Keys.THERMAL_THROTTLING] ?: true,
            preferVulkanWhenSupported = prefs[Keys.PREFER_VULKAN] ?: true,
            defaultExportResolution = ExportResolution.entries.firstOrNull { it.name == exportResName }
                ?: ExportResolution.P1080,
            defaultExportFps = prefs[Keys.DEFAULT_EXPORT_FPS] ?: 30
        )
    }

    suspend fun setLowRamSafeMode(enabled: Boolean) {
        context.studioDataStore.edit { it[Keys.LOW_RAM_SAFE_MODE] = enabled }
    }

    suspend fun setPreviewResolution(preset: PreviewResolutionPreset) {
        context.studioDataStore.edit { it[Keys.PREVIEW_RESOLUTION] = preset.name }
    }

    suspend fun setEffectCacheEnabled(enabled: Boolean) {
        context.studioDataStore.edit { it[Keys.EFFECT_CACHE] = enabled }
    }

    suspend fun setThermalThrottling(enabled: Boolean) {
        context.studioDataStore.edit { it[Keys.THERMAL_THROTTLING] = enabled }
    }

    suspend fun setPreferVulkan(enabled: Boolean) {
        context.studioDataStore.edit { it[Keys.PREFER_VULKAN] = enabled }
    }

    suspend fun setDefaultExportResolution(resolution: ExportResolution) {
        context.studioDataStore.edit { it[Keys.DEFAULT_EXPORT_RES] = resolution.name }
    }

    suspend fun setDefaultExportFps(fps: Int) {
        context.studioDataStore.edit { it[Keys.DEFAULT_EXPORT_FPS] = fps }
    }
}
