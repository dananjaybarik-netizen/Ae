package com.example.ui.components

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.engine.DeviceHardwareReport
import com.example.engine.ExportJobState
import com.example.model.ExportCodec
import com.example.model.ExportConfig
import com.example.model.ExportResolution
import com.example.model.ShaderEffectParams
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldReady
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioSurfaceHighest
import com.example.ui.theme.StudioViolet
import java.util.Locale

fun formatTimecode(ms: Long, fps: Int = 30): String {
    val safeMs = ms.coerceAtLeast(0L)
    val totalSeconds = safeMs / 1000L
    val minutes = (totalSeconds / 60L) % 60L
    val hours = totalSeconds / 3600L
    val seconds = totalSeconds % 60L
    val frames = ((safeMs % 1000L) * fps / 1000L).coerceIn(0L, (fps - 1).toLong())
    return String.format(Locale.US, "%02d:%02d:%02d:%02d", hours, minutes, seconds, frames)
}

@Composable
fun StudioBadge(
    text: String,
    accentColor: Color = ElectricCyan,
    modifier: Modifier = Modifier
) {
    Surface(
        color = accentColor.copy(alpha = 0.14f),
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.38f)),
        modifier = modifier
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = accentColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun PrecisionParameterSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    defaultValue: Float,
    valueFormat: (Float) -> String = { String.format(Locale.US, "%.2f", it) },
    accentColor: Color = ElectricCyan,
    testTag: String = "",
    onValueChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = valueFormat(value),
                    style = MaterialTheme.typography.labelMedium,
                    color = accentColor
                )
                if (kotlin.math.abs(value - defaultValue) > 0.01f) {
                    IconButton(
                        onClick = { onValueChange(defaultValue) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = "Reset $label",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
                inactiveTrackColor = StudioSurfaceHighest
            ),
            modifier = if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier
        )
    }
}

@Composable
fun IndependentPixelControlsPanel(
    params: ShaderEffectParams,
    onUpdate: ((ShaderEffectParams) -> ShaderEffectParams) -> Unit,
    onResetAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(StudioSurface)
            .border(1.dp, StudioBorder, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "GPU Pixel Shader Controls",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Real-time GLSL uniform matrix • Zero brightness drift",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(
                onClick = onResetAll,
                modifier = Modifier.testTag("reset_all_shaders_button")
            ) {
                Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Reset")
            }
        }

        Spacer(Modifier.height(10.dp))

        // Before / After Wipe
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(StudioSurfaceElevated)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Compare, contentDescription = "Compare split", tint = ElectricCyan, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Before / After Split Wipe", style = MaterialTheme.typography.titleSmall)
            }
            Switch(
                checked = params.splitCompareEnabled,
                onCheckedChange = { enabled -> onUpdate { it.copy(splitCompareEnabled = enabled) } }
            )
        }
        if (params.splitCompareEnabled) {
            PrecisionParameterSlider(
                label = "Split Divider Position",
                value = params.splitComparePosition,
                valueRange = 0.05f..0.95f,
                defaultValue = 0.5f,
                valueFormat = { "${(it * 100).toInt()}%" }
            ) { v -> onUpdate { it.copy(splitComparePosition = v) } }
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = StudioBorder)
        Spacer(Modifier.height(8.dp))

        PrecisionParameterSlider(
            label = "Effect Intensity",
            value = params.effectIntensity,
            valueRange = 0f..1.5f,
            defaultValue = 0f,
            valueFormat = { "${(it * 100).toInt()}%" },
            testTag = "slider_effect_intensity"
        ) { v -> onUpdate { it.copy(effectIntensity = v) } }

        PrecisionParameterSlider(
            label = "Sharpen Amount (Edge Deconvolve)",
            value = params.sharpenAmount,
            valueRange = 0f..2f,
            defaultValue = 0f,
            valueFormat = { "${(it * 100).toInt()}%" },
            testTag = "slider_sharpen_amount"
        ) { v -> onUpdate { it.copy(sharpenAmount = v) } }

        PrecisionParameterSlider(
            label = "Radius",
            value = params.radius,
            valueRange = 0.5f..8f,
            defaultValue = 1.5f,
            valueFormat = { String.format(Locale.US, "%.1f px", it) },
            testTag = "slider_radius"
        ) { v -> onUpdate { it.copy(radius = v) } }

        PrecisionParameterSlider(
            label = "Threshold",
            value = params.threshold,
            valueRange = 0f..1f,
            defaultValue = 0.08f,
            testTag = "slider_threshold"
        ) { v -> onUpdate { it.copy(threshold = v) } }

        PrecisionParameterSlider(
            label = "Glow Intensity",
            value = params.glowIntensity,
            valueRange = 0f..2f,
            defaultValue = 0f,
            accentColor = StudioViolet,
            valueFormat = { "${(it * 100).toInt()}%" },
            testTag = "slider_glow_intensity"
        ) { v -> onUpdate { it.copy(glowIntensity = v) } }

        PrecisionParameterSlider(
            label = "Highlight",
            value = params.highlight,
            valueRange = -1f..1f,
            defaultValue = 0f,
            testTag = "slider_highlight"
        ) { v -> onUpdate { it.copy(highlight = v) } }

        PrecisionParameterSlider(
            label = "Shadow",
            value = params.shadow,
            valueRange = -1f..1f,
            defaultValue = 0f,
            testTag = "slider_shadow"
        ) { v -> onUpdate { it.copy(shadow = v) } }

        PrecisionParameterSlider(
            label = "RGB Red Gain",
            value = params.redGain,
            valueRange = 0f..2f,
            defaultValue = 1f,
            accentColor = Color(0xFFFF6B6B),
            testTag = "slider_rgb_red"
        ) { v -> onUpdate { it.copy(redGain = v) } }

        PrecisionParameterSlider(
            label = "RGB Green Gain",
            value = params.greenGain,
            valueRange = 0f..2f,
            defaultValue = 1f,
            accentColor = Color(0xFF4ADE80),
            testTag = "slider_rgb_green"
        ) { v -> onUpdate { it.copy(greenGain = v) } }

        PrecisionParameterSlider(
            label = "RGB Blue Gain",
            value = params.blueGain,
            valueRange = 0f..2f,
            defaultValue = 1f,
            accentColor = Color(0xFF60A5FA),
            testTag = "slider_rgb_blue"
        ) { v -> onUpdate { it.copy(blueGain = v) } }

        PrecisionParameterSlider(
            label = "Saturation",
            value = params.saturation,
            valueRange = 0f..2f,
            defaultValue = 1f,
            testTag = "slider_saturation"
        ) { v -> onUpdate { it.copy(saturation = v) } }

        PrecisionParameterSlider(
            label = "Texture",
            value = params.texture,
            valueRange = -1f..1f,
            defaultValue = 0f,
            testTag = "slider_texture"
        ) { v -> onUpdate { it.copy(texture = v) } }

        PrecisionParameterSlider(
            label = "Contrast",
            value = params.contrast,
            valueRange = 0.2f..2f,
            defaultValue = 1f,
            testTag = "slider_contrast"
        ) { v -> onUpdate { it.copy(contrast = v) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExportBottomSheet(
    config: ExportConfig,
    exportState: ExportJobState,
    hardwareReport: DeviceHardwareReport?,
    onConfigChange: ((ExportConfig) -> ExportConfig) -> Unit,
    onStartExport: () -> Unit,
    onCancelExport: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = StudioSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.MovieCreation, contentDescription = null, tint = ElectricCyan)
                    Spacer(Modifier.width(10.dp))
                    Text("Hardware Export Engine", style = MaterialTheme.typography.headlineSmall)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close export panel")
                }
            }

            Spacer(Modifier.height(12.dp))

            when (exportState) {
                is ExportJobState.Idle -> {
                    Text("Resolution", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val maxRes = hardwareReport?.maxSupportedResolution ?: ExportResolution.UHD_4K
                        ExportResolution.entries.forEach { res ->
                            val supported = res.width <= maxRes.width
                            FilterChip(
                                selected = config.resolution == res,
                                onClick = {
                                    if (supported) {
                                        onConfigChange { it.copy(resolution = res, bitrateMbps = res.defaultBitrateMbps) }
                                    }
                                },
                                enabled = supported,
                                label = {
                                    Text(if (supported) res.label else "${res.label} (N/A)")
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ElectricCyan.copy(alpha = 0.2f),
                                    selectedLabelColor = ElectricCyan
                                )
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text("Frame Rate (FPS)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    val fpsList = hardwareReport?.supportedFpsOptions ?: listOf(30, 60)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        fpsList.forEach { fps ->
                            FilterChip(
                                selected = config.fps == fps,
                                onClick = { onConfigChange { it.copy(fps = fps) } },
                                label = { Text("$fps FPS") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = StudioViolet.copy(alpha = 0.22f),
                                    selectedLabelColor = StudioViolet
                                )
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text("Codec", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ExportCodec.entries.forEach { codec ->
                            val allowed = codec != ExportCodec.HEVC || (hardwareReport?.supportsHevcEncode ?: true)
                            FilterChip(
                                selected = config.codec == codec,
                                onClick = { if (allowed) onConfigChange { it.copy(codec = codec) } },
                                enabled = allowed,
                                label = { Text(codec.label) }
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    PrecisionParameterSlider(
                        label = "Target Bitrate",
                        value = config.bitrateMbps.toFloat(),
                        valueRange = 4f..60f,
                        defaultValue = config.resolution.defaultBitrateMbps.toFloat(),
                        valueFormat = { "${it.toInt()} Mbps" }
                    ) { v ->
                        val q = when {
                            v < 10f -> "Compact"
                            v < 24f -> "High"
                            else -> "Master Studio"
                        }
                        onConfigChange { it.copy(bitrateMbps = v.toInt(), qualityPreset = q) }
                    }

                    Text(
                        text = "Quality Profile: ${config.qualityPreset} • Hardware MediaCodec Pipeline",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(18.dp))
                    Button(
                        onClick = onStartExport,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("start_export_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = Color.Black)
                    ) {
                        Text("Render & Export MP4", fontWeight = FontWeight.Bold)
                    }
                }

                is ExportJobState.Running -> {
                    Text(exportState.stageLabel, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { exportState.progressPercent / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = ElectricCyan,
                        trackColor = StudioSurfaceHighest
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "${exportState.progressPercent}% Complete",
                        style = MaterialTheme.typography.labelMedium,
                        color = ElectricCyan
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = onCancelExport,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancel Export", color = CrimsonAlert)
                    }
                }

                is ExportJobState.Completed -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldReady, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Export Complete", style = MaterialTheme.typography.titleLarge, color = EmeraldReady)
                            Text(
                                text = "${exportState.outputFile.name} • ${String.format(Locale.US, "%.2f", exportState.fileSizeBytes / (1024f * 1024f))} MB",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    exportState.outputFile
                                )
                                val share = Intent(Intent.ACTION_SEND).apply {
                                    type = "video/mp4"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(share, "Share Exported Video"))
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = Color.Black)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Share MP4")
                        }
                        OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                            Text("Done")
                        }
                    }
                }

                is ExportJobState.Failed -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = CrimsonAlert)
                        Spacer(Modifier.width(10.dp))
                        Text(exportState.message, style = MaterialTheme.typography.bodyMedium, color = CrimsonAlert)
                    }
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                        Text("Back to Editor")
                    }
                }
            }
        }
    }
}
