package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "installed_plugins")
data class PluginEntity(
    @PrimaryKey val pluginId: String,
    val manifestJson: String,
    val fragmentShaderCode: String,
    val signatureSha256: String,
    val isSignatureVerified: Boolean,
    val isEnabled: Boolean,
    val installPath: String,
    val availableUpdateVersion: String? = null,
    val installedAt: Long = System.currentTimeMillis()
)
