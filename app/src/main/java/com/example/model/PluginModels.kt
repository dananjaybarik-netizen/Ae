package com.example.model

import kotlinx.serialization.Serializable

enum class PluginCategory(val displayName: String) {
    CINEMATIC_GRADE("Cinematic Grade"),
    OPTICAL_GLOW("Optical & Glow"),
    SHARPEN_DETAIL("Sharpen & Detail"),
    DISTORTION_FX("Distortion & Glitch"),
    LIGHTING_VFX("Lighting & Flares")
}

@Serializable
data class AifxParameter(
    val key: String,
    val label: String,
    val min: Float,
    val max: Float,
    val defaultValue: Float
)

@Serializable
data class AifxPreset(
    val name: String,
    val values: Map<String, Float>
)

@Serializable
data class AifxCompatibility(
    val minApiLevel: Int = 28,
    val minGlEsVersion: String = "3.0",
    val supportsLowRam4Gb: Boolean = true
)

@Serializable
data class AifxManifest(
    val pluginId: String,
    val name: String,
    val description: String,
    val author: String,
    val version: String,
    val versionCode: Int,
    val category: PluginCategory,
    val parameters: List<AifxParameter>,
    val presets: List<AifxPreset>,
    val shaderEntry: String = "shader.frag",
    val compatibility: AifxCompatibility = AifxCompatibility()
)

data class InstalledPlugin(
    val pluginId: String,
    val manifest: AifxManifest,
    val fragmentShaderCode: String,
    val signatureSha256: String,
    val isSignatureVerified: Boolean,
    val isEnabled: Boolean,
    val installPath: String,
    val availableUpdateVersion: String? = null
)

data class StorePluginListing(
    val manifest: AifxManifest,
    val fragmentShaderCode: String,
    val badge: String,
    val isInstalled: Boolean = false,
    val hasUpdate: Boolean = false
)
