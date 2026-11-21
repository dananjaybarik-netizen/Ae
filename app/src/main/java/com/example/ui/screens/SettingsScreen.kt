package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.preferences.PreviewResolutionPreset
import com.example.data.preferences.StudioSettingsState
import com.example.engine.DeviceHardwareReport
import com.example.model.ExportResolution
import com.example.ui.components.StudioBadge
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldReady
import com.example.ui.theme.KeyframeAmber
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSurface
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: StudioSettingsState,
    hardwareReport: DeviceHardwareReport?,
    cacheSizeLabel: String,
    onToggleLowRamMode: (Boolean) -> Unit,
    onSelectPreviewResolution: (PreviewResolutionPreset) -> Unit,
    onToggleEffectCache: (Boolean) -> Unit,
    onToggleThermalThrottling: (Boolean) -> Unit,
    onTogglePreferVulkan: (Boolean) -> Unit,
    onSelectDefaultExportRes: (ExportResolution) -> Unit,
    onSelectDefaultExportFps: (Int) -> Unit,
    onClearCache: () -> Unit,
    onRefreshTelemetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianVoid),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Engine & Performance", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text = "4GB RAM optimization, GPU backend & thermal guards",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(onClick = onRefreshTelemetry) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh telemetry", modifier = Modifier.size(16.dp))
                }
            }
        }

        // Hardware Capabilities Report Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = StudioSurface),
                border = BorderStroke(1.dp, StudioBorder),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Memory, contentDescription = null, tint = ElectricCyan)
                            Spacer(Modifier.width(8.dp))
                            Text("Hardware Diagnostics", style = MaterialTheme.typography.titleMedium)
                        }
                        StudioBadge(
                            text = if (hardwareReport?.isLowRam4GbProfile == true) "4GB PROFILE" else "HIGH MEM",
                            accentColor = if (hardwareReport?.isLowRam4GbProfile == true) KeyframeAmber else EmeraldReady
                        )
                    }
                    if (hardwareReport != null) {
                        DiagnosticRow("GPU Renderer", hardwareReport.gpuRenderer)
                        DiagnosticRow("Graphics API", hardwareReport.glEsVersion)
                        DiagnosticRow("Vulkan Support", hardwareReport.vulkanVersionLabel)
                        DiagnosticRow(
                            "System Memory",
                            String.format(Locale.US, "%.2f GB Total (%d MB Free)", hardwareReport.totalRamGb, hardwareReport.availableRamMb)
                        )
                        DiagnosticRow("Max Hardware Encode", "${hardwareReport.maxSupportedResolution.label} • ${hardwareReport.supportedFpsOptions.joinToString("/")} FPS")
                        DiagnosticRow("Thermal Status", hardwareReport.thermalStatusLabel)
                    }
                }
            }
        }

        // 4GB RAM & GPU Memory Management
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = StudioSurface),
                border = BorderStroke(1.dp, StudioBorder),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Memory & Thermal Controls", style = MaterialTheme.typography.titleMedium)

                    SettingSwitchRow(
                        title = "4GB RAM Safe Mode",
                        subtitle = "Limits texture pool allocation, clears image caches on trim, and protects against OOM.",
                        checked = settings.lowRamSafeMode,
                        testTag = "setting_low_ram_switch",
                        onCheckedChange = onToggleLowRamMode
                    )

                    SettingSwitchRow(
                        title = "Effect Shader Cache",
                        subtitle = "Caches compiled GLSL programs and intermediate frame buffers for smooth scrubbing.",
                        checked = settings.enableEffectCache,
                        testTag = "setting_effect_cache_switch",
                        onCheckedChange = onToggleEffectCache
                    )

                    SettingSwitchRow(
                        title = "Thermal & Battery Throttling",
                        subtitle = "Monitors PowerManager thermal status and caps preview load under heat.",
                        checked = settings.thermalThrottlingEnabled,
                        testTag = "setting_thermal_switch",
                        onCheckedChange = onToggleThermalThrottling
                    )

                    SettingSwitchRow(
                        title = "Prefer Vulkan Where Supported",
                        subtitle = "Uses Vulkan compute/render bridge when hardware level is present; GLES 3.0 otherwise.",
                        checked = settings.preferVulkanWhenSupported,
                        testTag = "setting_vulkan_switch",
                        onCheckedChange = onTogglePreferVulkan
                    )
                }
            }
        }

        // Preview Resolution Presets
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = StudioSurface),
                border = BorderStroke(1.dp, StudioBorder),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Preview Viewport Resolution", style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PreviewResolutionPreset.entries.forEach { preset ->
                            FilterChip(
                                selected = settings.previewResolution == preset,
                                onClick = { onSelectPreviewResolution(preset) },
                                label = { Text(preset.label) }
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                    Text("Default Export Resolution & FPS", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ExportResolution.entries.forEach { res ->
                            FilterChip(
                                selected = settings.defaultExportResolution == res,
                                onClick = { onSelectDefaultExportRes(res) },
                                label = { Text(res.label) }
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(30, 60).forEach { fps ->
                            FilterChip(
                                selected = settings.defaultExportFps == fps,
                                onClick = { onSelectDefaultExportFps(fps) },
                                label = { Text("$fps FPS") }
                            )
                        }
                    }
                }
            }
        }

        // Cache Cleanup
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = StudioSurface),
                border = BorderStroke(1.dp, StudioBorder),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Media & Effect Cache", style = MaterialTheme.typography.titleMedium)
                        Text("Current disk usage: $cacheSizeLabel", style = MaterialTheme.typography.labelMedium, color = ElectricCyan)
                    }
                    Button(
                        onClick = onClearCache,
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = Color.Black),
                        modifier = Modifier.testTag("clear_cache_button")
                    ) {
                        Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Clear")
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Text(value, style = MaterialTheme.typography.labelMedium, color = ElectricCyan)
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}
