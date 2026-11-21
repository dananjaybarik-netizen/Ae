package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.repository.StudioProjectSummary
import com.example.engine.DeviceHardwareReport
import com.example.model.EffectCategory
import com.example.ui.components.StudioBadge
import com.example.ui.components.formatTimecode
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldReady
import com.example.ui.theme.KeyframeAmber
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioViolet
import java.util.Locale

@Composable
fun HomeScreen(
    projects: List<StudioProjectSummary>,
    hardwareReport: DeviceHardwareReport?,
    isGeneratingSample: Boolean,
    installedPluginCount: Int,
    onCreateNewSequence: () -> Unit,
    onImportVideo: (Uri) -> Unit,
    onGenerateSampleAndOpen: () -> Unit,
    onOpenProject: (Long) -> Unit,
    onOpenEffectsCategory: (EffectCategory) -> Unit,
    onNavigatePlugins: () -> Unit,
    onNavigateProjects: () -> Unit,
    modifier: Modifier = Modifier
) {
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) onImportVideo(uri)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianVoid),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Studio Showcase
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(228.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .border(1.dp, StudioBorder, RoundedCornerShape(22.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_studio_hero_1795266068646),
                    contentDescription = "AI STUDIO GPU Cinema Hero",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.25f),
                                    ObsidianVoid.copy(alpha = 0.94f)
                                )
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StudioBadge(text = "GPU SHADER NLE", accentColor = ElectricCyan)
                        StudioBadge(
                            text = if (hardwareReport?.isLowRam4GbProfile == true) "4GB RAM SAFE" else "STUDIO PRO",
                            accentColor = EmeraldReady
                        )
                    }

                    Column {
                        Text(
                            text = "AI STUDIO",
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Multi-track timeline, OpenGL ES pixel shaders, .aifx plugins & hardware MP4 export.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = Color.Black),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("home_import_video_button")
                            ) {
                                Icon(Icons.Default.VideoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Import Video", fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = onCreateNewSequence,
                                border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.55f)),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("home_new_project_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("New Cut", color = ElectricCyan)
                            }
                        }
                    }
                }
            }
        }

        // Calibration Clip Generator (Real MediaCodec H.264 MP4)
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
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Hardware MP4 Calibration Clip", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Generate a real 720p H.264 MP4 test pattern on-device to test Sharpen, Glow & 4K Export immediately.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Button(
                        onClick = onGenerateSampleAndOpen,
                        enabled = !isGeneratingSample,
                        colors = ButtonDefaults.buttonColors(containerColor = StudioViolet, contentColor = Color.White),
                        modifier = Modifier.testTag("home_generate_sample_button")
                    ) {
                        if (isGeneratingSample) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Movie, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Create MP4")
                        }
                    }
                }
            }
        }

        // Hardware GPU & RAM Telemetry
        item {
            Text("GPU & Hardware Pipeline", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TelemetryTile(
                    icon = Icons.Default.Speed,
                    title = "GPU Engine",
                    value = hardwareReport?.gpuRenderer?.take(22) ?: "Probing EGL...",
                    sub = hardwareReport?.vulkanVersionLabel ?: "OpenGL ES 3.0",
                    accent = ElectricCyan,
                    modifier = Modifier.weight(1f)
                )
                TelemetryTile(
                    icon = Icons.Default.Memory,
                    title = "Memory & Encode",
                    value = if (hardwareReport != null) {
                        String.format(Locale.US, "%.1f GB RAM", hardwareReport.totalRamGb)
                    } else "-- GB",
                    sub = "Max ${hardwareReport?.maxSupportedResolution?.label ?: "1080p"}",
                    accent = EmeraldReady,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Quick Shader Categories
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Professional FX Racks", style = MaterialTheme.typography.titleLarge)
                StudioBadge(text = "32 SHADERS", accentColor = KeyframeAmber)
            }
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(EffectCategory.entries) { category ->
                    Surface(
                        color = StudioSurfaceElevated,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, StudioBorder),
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onOpenEffectsCategory(category) }
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                            StudioBadge(text = category.badgeCode, accentColor = ElectricCyan)
                            Spacer(Modifier.height(8.dp))
                            Text(category.displayName, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }

        // Recent Projects
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Recent Sequences", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "View All",
                    style = MaterialTheme.typography.labelLarge,
                    color = ElectricCyan,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onNavigateProjects() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        if (projects.isEmpty()) {
            item {
                Surface(
                    color = StudioSurface,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, StudioBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "No saved sequences yet. Import a video or create a calibration MP4 to start editing.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(projects.take(4), key = { it.id }) { proj ->
                Card(
                    onClick = { onOpenProject(proj.id) },
                    colors = CardDefaults.cardColors(containerColor = StudioSurface),
                    border = BorderStroke(1.dp, StudioBorder),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(proj.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = "${proj.clipCount} clips • ${formatTimecode(proj.durationMs)} • ${proj.resolutionLabel}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.Default.AutoAwesome, contentDescription = "Open sequence", tint = ElectricCyan)
                    }
                }
            }
        }

        // Plugins Quick Link
        item {
            Card(
                onClick = onNavigatePlugins,
                colors = CardDefaults.cardColors(containerColor = StudioSurfaceElevated),
                border = BorderStroke(1.dp, StudioViolet.copy(alpha = 0.45f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Extension, contentDescription = null, tint = StudioViolet, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(".aifx Plugin Architecture", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "$installedPluginCount installed • SHA-256 verified GLSL sandbox",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    StudioBadge(text = "STORE", accentColor = StudioViolet)
                }
            }
        }
    }
}

@Composable
private fun TelemetryTile(
    icon: ImageVector,
    title: String,
    value: String,
    sub: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = StudioSurface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, StudioBorder),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(sub, style = MaterialTheme.typography.labelSmall, color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
