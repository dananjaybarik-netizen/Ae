package com.example.ui.screens

import android.graphics.Matrix
import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FilterCenterFocus
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Crop
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.engine.AiStudioGlEffect
import com.example.engine.DeviceHardwareReport
import com.example.engine.ExportJobState
import com.example.engine.LiveShaderState
import com.example.engine.interpolateKeyframe
import com.example.model.CropBounds
import com.example.model.ExportConfig
import com.example.model.InstalledPlugin
import com.example.model.KeyframeProperty
import com.example.model.MaskShape
import com.example.model.ShaderEffectParams
import com.example.model.TimelineClip
import com.example.model.TrackType
import com.example.model.TransitionType
import com.example.ui.components.ExportBottomSheet
import com.example.ui.components.IndependentPixelControlsPanel
import com.example.ui.components.PrecisionParameterSlider
import com.example.ui.components.StudioBadge
import com.example.ui.components.formatTimecode
import com.example.ui.theme.AudioTrackColor
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.FxTrackColor
import com.example.ui.theme.KeyframeAmber
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.OverlayTrackColor
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioSurfaceHighest
import com.example.ui.theme.StudioViolet
import com.example.ui.theme.VideoTrackColor
import com.example.viewmodel.EditorUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Locale

private enum class EditorToolPanel(val label: String) {
    NONE("Inspector"),
    TRIM("Trim"),
    CROP("Crop"),
    SPEED("Speed"),
    KEYFRAME("Keyframes"),
    MASK("Masking"),
    CHROMA("Chroma Key"),
    TRANSITION("Transitions"),
    GPU_FX("GPU Shaders")
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun EditorScreen(
    uiState: EditorUiState,
    liveShaderState: LiveShaderState,
    installedPlugins: List<InstalledPlugin>,
    hardwareReport: DeviceHardwareReport?,
    onImportVideo: (Uri, TrackType) -> Unit,
    onGenerateSample: () -> Unit,
    onSelectClip: (String?) -> Unit,
    onUpdatePlayhead: (Long) -> Unit,
    onSetPlaying: (Boolean) -> Unit,
    onSetZoom: (Float) -> Unit,
    onTrimClip: (Long, Long) -> Unit,
    onSplitAtPlayhead: () -> Unit,
    onDeleteClip: () -> Unit,
    onSetSpeed: (Float) -> Unit,
    onRotateClip: (Float) -> Unit,
    onSetCrop: (CropBounds) -> Unit,
    onToggleMute: () -> Unit,
    onSetTransition: (TransitionType) -> Unit,
    onAddKeyframe: (KeyframeProperty, Float) -> Unit,
    onRemoveKeyframe: (String) -> Unit,
    onUpdateShaderParams: (Boolean, (ShaderEffectParams) -> ShaderEffectParams) -> Unit,
    onResetShaders: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSaveProject: () -> Unit,
    onUpdateExportConfig: ((ExportConfig) -> ExportConfig) -> Unit,
    onStartExport: () -> Unit,
    onCancelExport: () -> Unit,
    onResetExport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var activePanel by remember { mutableStateOf(EditorToolPanel.NONE) }
    var showExportSheet by remember { mutableStateOf(false) }
    var importTargetTrack by remember { mutableStateOf(TrackType.VIDEO_PRIMARY) }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) onImportVideo(uri, importTargetTrack)
    }

    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onImportVideo(uri, TrackType.AUDIO)
    }

    val selectedClip = uiState.project.clips.firstOrNull { it.id == uiState.selectedClipId }
        ?: uiState.project.clips.firstOrNull()

    val activePlugin = uiState.project.masterEffects.activePluginId?.let { pid ->
        installedPlugins.firstOrNull { it.pluginId == pid && it.isEnabled }
    }

    // Real Media3 ExoPlayer instance with GPU GLSL Effect pipeline
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ALL
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    // Rebuild Media3 playlist + GlEffect chain when clips, crop, rotation, or active plugin change
    val primaryClips = remember(uiState.project.clips) {
        uiState.project.clips.filter { it.trackType == TrackType.VIDEO_PRIMARY }.sortedBy { it.timelineStartMs }
    }

    val pipelineSignature = remember(primaryClips, activePlugin?.pluginId) {
        primaryClips.joinToString("|") {
            "${it.id}:${it.sourceUri}:${it.sourceStartMs}:${it.sourceEndMs}:${it.rotationDegrees}:${it.crop}:${it.keyframes.size}:${it.isMuted}"
        } + "::" + (activePlugin?.pluginId ?: "builtin")
    }

    LaunchedEffect(pipelineSignature) {
        if (primaryClips.isEmpty()) {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            return@LaunchedEffect
        }

        val mediaItems = primaryClips.map { clip ->
            MediaItem.Builder()
                .setUri(Uri.parse(clip.sourceUri))
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(clip.sourceStartMs.coerceAtLeast(0L))
                        .setEndPositionMs(clip.sourceEndMs.coerceAtLeast(clip.sourceStartMs + 100L))
                        .build()
                )
                .build()
        }

        val effects = mutableListOf<Effect>()
        val targetClip = selectedClip ?: primaryClips.first()
        if (targetClip.crop.left > -0.99f || targetClip.crop.right < 0.99f || targetClip.crop.bottom > -0.99f || targetClip.crop.top < 0.99f) {
            effects.add(Crop(targetClip.crop.left, targetClip.crop.right, targetClip.crop.bottom, targetClip.crop.top))
        }
        val hasTransformKf = targetClip.keyframes.any {
            it.property == KeyframeProperty.SCALE || it.property == KeyframeProperty.ROTATION
        }
        if (hasTransformKf) {
            effects.add(MatrixTransformation { timeUs ->
                val ms = timeUs / 1000L
                val scale = interpolateKeyframe(targetClip.keyframes, KeyframeProperty.SCALE, ms, 1f)
                val rot = interpolateKeyframe(targetClip.keyframes, KeyframeProperty.ROTATION, ms, targetClip.rotationDegrees)
                Matrix().apply {
                    postScale(scale, scale)
                    postRotate(rot)
                }
            })
        } else if (kotlin.math.abs(targetClip.rotationDegrees) > 0.1f) {
            effects.add(ScaleAndRotateTransformation.Builder().setRotationDegrees(targetClip.rotationDegrees).build())
        }
        effects.add(AiStudioGlEffect(liveShaderState, activePlugin?.fragmentShaderCode, isExportMode = false))

        exoPlayer.setVideoEffects(effects)
        exoPlayer.setMediaItems(mediaItems)
        exoPlayer.prepare()
    }

    LaunchedEffect(selectedClip?.speed, selectedClip?.isMuted) {
        val spd = (selectedClip?.speed ?: 1f).coerceIn(0.25f, 4.0f)
        exoPlayer.playbackParameters = PlaybackParameters(spd)
        exoPlayer.volume = if (selectedClip?.isMuted == true) 0f else 1f
    }

    LaunchedEffect(uiState.isPlaying) {
        if (uiState.isPlaying && primaryClips.isNotEmpty()) {
            exoPlayer.play()
        } else {
            exoPlayer.pause()
        }
    }

    // Sync timeline playhead while playing
    LaunchedEffect(uiState.isPlaying, primaryClips) {
        while (isActive && uiState.isPlaying) {
            val idx = exoPlayer.currentMediaItemIndex.coerceIn(0, (primaryClips.size - 1).coerceAtLeast(0))
            val clipStart = primaryClips.getOrNull(idx)?.timelineStartMs ?: 0L
            onUpdatePlayhead(clipStart + exoPlayer.currentPosition)
            delay(80L)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianVoid)
    ) {
        // Top NLE Header Bar
        Surface(color = StudioSurface, tonalElevation = 2.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = uiState.project.projectName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${uiState.project.clips.size} clips • ${uiState.liveRamLabel}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onUndo, enabled = uiState.canUndo, modifier = Modifier.testTag("editor_undo_button")) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                    }
                    IconButton(onClick = onRedo, enabled = uiState.canRedo, modifier = Modifier.testTag("editor_redo_button")) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                    }
                    IconButton(onClick = onSaveProject, modifier = Modifier.testTag("editor_save_button")) {
                        Icon(Icons.Default.Save, contentDescription = "Save Project", tint = ElectricCyan)
                    }
                    Button(
                        onClick = { showExportSheet = true },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = Color.Black),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("editor_export_button")
                    ) {
                        Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Export", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        // Video Preview Viewport (Real Media3 ExoPlayer + GLSL Shader Surface)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(215.dp)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (primaryClips.isEmpty()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text("No Video in Timeline", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Import a video from your device or generate a calibration H.264 MP4.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                importTargetTrack = TrackType.VIDEO_PRIMARY
                                videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = Color.Black),
                            modifier = Modifier.testTag("editor_empty_import_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Import Video")
                        }
                        OutlinedButton(
                            onClick = onGenerateSample,
                            enabled = !uiState.isGeneratingSample,
                            modifier = Modifier.testTag("editor_empty_sample_button")
                        ) {
                            if (uiState.isGeneratingSample) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Movie, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Sample MP4")
                            }
                        }
                    }
                }
            } else {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Overlay Status Badges
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (uiState.project.masterEffects.hasAnyActivePixelProcessing()) {
                        StudioBadge(text = "GPU FX LIVE", accentColor = ElectricCyan)
                    }
                    if (activePlugin != null) {
                        StudioBadge(text = activePlugin.manifest.name, accentColor = StudioViolet)
                    }
                }
            }
        }

        // Transport Controls Bar
        Surface(color = StudioSurfaceElevated) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${formatTimecode(uiState.playheadMs)} / ${formatTimecode(uiState.project.totalDurationMs)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = ElectricCyan
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            val next = !uiState.isPlaying
                            onSetPlaying(next)
                        },
                        enabled = primaryClips.isNotEmpty(),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("editor_play_pause_button")
                    ) {
                        Icon(
                            imageVector = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (uiState.isPlaying) "Pause" else "Play",
                            tint = ElectricCyan,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ZoomIn, contentDescription = "Timeline zoom", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    Slider(
                        value = uiState.timelineZoom,
                        onValueChange = onSetZoom,
                        valueRange = 0.5f..3f,
                        modifier = Modifier.width(88.dp),
                        colors = SliderDefaults.colors(thumbColor = ElectricCyan, activeTrackColor = ElectricCyan)
                    )
                }
            }
        }

        // Playhead Scrubber
        if (uiState.project.totalDurationMs > 0L) {
            Slider(
                value = uiState.playheadMs.toFloat().coerceIn(0f, uiState.project.totalDurationMs.toFloat()),
                onValueChange = { targetMs ->
                    val ms = targetMs.toLong()
                    onUpdatePlayhead(ms)
                    val targetClipIdx = primaryClips.indexOfFirst { ms in it.timelineStartMs..it.timelineEndMs }.coerceAtLeast(0)
                    val clip = primaryClips.getOrNull(targetClipIdx)
                    if (clip != null) {
                        exoPlayer.seekTo(targetClipIdx, (ms - clip.timelineStartMs).coerceAtLeast(0L))
                    }
                },
                valueRange = 0f..uiState.project.totalDurationMs.toFloat().coerceAtLeast(1000f),
                colors = SliderDefaults.colors(thumbColor = ElectricCyan, activeTrackColor = ElectricCyan, inactiveTrackColor = StudioSurfaceHighest),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .height(28.dp)
            )
        }

        // Multi-Track Timeline (V1, V2, A1, FX)
        MultiTrackTimelineView(
            clips = uiState.project.clips,
            selectedClipId = selectedClip?.id,
            playheadMs = uiState.playheadMs,
            totalDurationMs = uiState.project.totalDurationMs.coerceAtLeast(5000L),
            zoom = uiState.timelineZoom,
            onSelectClip = onSelectClip,
            modifier = Modifier
                .fillMaxWidth()
                .weight(if (activePanel == EditorToolPanel.NONE) 1f else 0.42f)
        )

        // Contextual Editing Inspector Panel
        if (activePanel != EditorToolPanel.NONE) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.58f)
                    .background(StudioSurface)
                    .border(1.dp, StudioBorder)
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp)
            ) {
                when (activePanel) {
                    EditorToolPanel.TRIM -> TrimInspector(selectedClip, onTrimClip)
                    EditorToolPanel.CROP -> CropRotateInspector(selectedClip, onSetCrop, onRotateClip)
                    EditorToolPanel.SPEED -> SpeedAudioInspector(selectedClip, onSetSpeed, onToggleMute)
                    EditorToolPanel.KEYFRAME -> KeyframeInspector(selectedClip, uiState.playheadMs, onAddKeyframe, onRemoveKeyframe)
                    EditorToolPanel.MASK -> MaskingInspector(uiState.project.masterEffects) { update ->
                        onUpdateShaderParams(false, update)
                    }
                    EditorToolPanel.CHROMA -> ChromaKeyInspector(uiState.project.masterEffects) { update ->
                        onUpdateShaderParams(false, update)
                    }
                    EditorToolPanel.TRANSITION -> TransitionInspector(selectedClip, onSetTransition)
                    EditorToolPanel.GPU_FX -> IndependentPixelControlsPanel(
                        params = uiState.project.masterEffects,
                        onUpdate = { update -> onUpdateShaderParams(false, update) },
                        onResetAll = onResetShaders
                    )
                    EditorToolPanel.NONE -> Unit
                }
            }
        }

        // Bottom Touch-Friendly Editing Tool Strip
        Surface(color = StudioSurfaceElevated, border = BorderStroke(1.dp, StudioBorder)) {
            val tools = listOf(
                Triple("Add V1", Icons.Default.Add) {
                    importTargetTrack = TrackType.VIDEO_PRIMARY
                    videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                },
                Triple("Add V2", Icons.Default.Layers) {
                    importTargetTrack = TrackType.VIDEO_OVERLAY
                    videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                },
                Triple("Audio", Icons.Default.VolumeUp) {
                    audioPicker.launch(arrayOf("audio/*"))
                },
                Triple("Sample", Icons.Default.Movie) { onGenerateSample() },
                Triple("Trim", Icons.Default.ContentCut) {
                    activePanel = if (activePanel == EditorToolPanel.TRIM) EditorToolPanel.NONE else EditorToolPanel.TRIM
                },
                Triple("Split", Icons.Default.SwapHoriz) { onSplitAtPlayhead() },
                Triple("Crop", Icons.Default.Crop) {
                    activePanel = if (activePanel == EditorToolPanel.CROP) EditorToolPanel.NONE else EditorToolPanel.CROP
                },
                Triple("Rotate", Icons.Default.RotateRight) { onRotateClip(90f) },
                Triple("Speed", Icons.Default.FastForward) {
                    activePanel = if (activePanel == EditorToolPanel.SPEED) EditorToolPanel.NONE else EditorToolPanel.SPEED
                },
                Triple("Keyframe", Icons.Default.Diamond) {
                    activePanel = if (activePanel == EditorToolPanel.KEYFRAME) EditorToolPanel.NONE else EditorToolPanel.KEYFRAME
                },
                Triple("Mask", Icons.Default.FilterCenterFocus) {
                    activePanel = if (activePanel == EditorToolPanel.MASK) EditorToolPanel.NONE else EditorToolPanel.MASK
                },
                Triple("Chroma", Icons.Default.AutoFixHigh) {
                    activePanel = if (activePanel == EditorToolPanel.CHROMA) EditorToolPanel.NONE else EditorToolPanel.CHROMA
                },
                Triple("Transition", Icons.Default.Speed) {
                    activePanel = if (activePanel == EditorToolPanel.TRANSITION) EditorToolPanel.NONE else EditorToolPanel.TRANSITION
                },
                Triple("GPU FX", Icons.Default.Tune) {
                    activePanel = if (activePanel == EditorToolPanel.GPU_FX) EditorToolPanel.NONE else EditorToolPanel.GPU_FX
                },
                Triple("Delete", Icons.Default.Delete) { onDeleteClip() }
            )

            LazyRow(
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(tools) { (label, icon, action) ->
                    val isSelected = when (label) {
                        "Trim" -> activePanel == EditorToolPanel.TRIM
                        "Crop" -> activePanel == EditorToolPanel.CROP
                        "Speed" -> activePanel == EditorToolPanel.SPEED
                        "Keyframe" -> activePanel == EditorToolPanel.KEYFRAME
                        "Mask" -> activePanel == EditorToolPanel.MASK
                        "Chroma" -> activePanel == EditorToolPanel.CHROMA
                        "Transition" -> activePanel == EditorToolPanel.TRANSITION
                        "GPU FX" -> activePanel == EditorToolPanel.GPU_FX
                        else -> false
                    }
                    ToolStripButton(label = label, icon = icon, isActive = isSelected, onClick = action)
                }
            }
        }
    }

    if (showExportSheet) {
        ExportBottomSheet(
            config = uiState.exportConfig,
            exportState = uiState.exportState,
            hardwareReport = hardwareReport,
            onConfigChange = onUpdateExportConfig,
            onStartExport = onStartExport,
            onCancelExport = onCancelExport,
            onDismiss = {
                if (uiState.exportState !is ExportJobState.Running) {
                    onResetExport()
                    showExportSheet = false
                }
            }
        )
    }
}

@Composable
private fun ToolStripButton(
    label: String,
    icon: ImageVector,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val tint = when {
        label == "Delete" -> CrimsonAlert
        isActive -> ElectricCyan
        else -> MaterialTheme.colorScheme.onSurface
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isActive) ElectricCyan.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag("tool_${label.lowercase().replace(" ", "_")}")
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

@Composable
private fun MultiTrackTimelineView(
    clips: List<TimelineClip>,
    selectedClipId: String?,
    playheadMs: Long,
    totalDurationMs: Long,
    zoom: Float,
    onSelectClip: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val pxPerSec = 56f * zoom
    val totalWidthDp = ((totalDurationMs / 1000f) * pxPerSec).coerceAtLeast(360f).dp
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .background(ObsidianVoid)
            .padding(vertical = 6.dp)
            .verticalScroll(rememberScrollState())
    ) {
        TrackType.entries.forEach { track ->
            val trackColor = when (track) {
                TrackType.VIDEO_PRIMARY -> VideoTrackColor
                TrackType.VIDEO_OVERLAY -> OverlayTrackColor
                TrackType.AUDIO -> AudioTrackColor
                TrackType.FX -> FxTrackColor
            }
            val trackClips = clips.filter { it.trackType == track }.sortedBy { it.timelineStartMs }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Track Label Pill
                Box(
                    modifier = Modifier
                        .width(34.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .background(StudioSurfaceElevated)
                        .border(1.dp, StudioBorder, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(track.label, style = MaterialTheme.typography.labelMedium, color = trackColor)
                }
                Spacer(Modifier.width(6.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .background(StudioSurface)
                        .horizontalScroll(scrollState)
                ) {
                    Box(modifier = Modifier.width(totalWidthDp).fillMaxHeight()) {
                        if (track == TrackType.FX) {
                            // Display keyframes from primary clips on FX track
                            val allKf = clips.flatMap { clip ->
                                clip.keyframes.map { kf -> (clip.timelineStartMs + kf.timeMs) to kf }
                            }
                            allKf.forEach { (absMs, kf) ->
                                val xDp = ((absMs / 1000f) * pxPerSec).dp
                                Icon(
                                    imageVector = Icons.Default.Diamond,
                                    contentDescription = "Keyframe ${kf.property.displayName}",
                                    tint = KeyframeAmber,
                                    modifier = Modifier
                                        .offset(x = xDp, y = 11.dp)
                                        .size(18.dp)
                                )
                            }
                        } else {
                            trackClips.forEach { clip ->
                                val startDp = ((clip.timelineStartMs / 1000f) * pxPerSec).dp
                                val widthDp = ((clip.effectiveDurationMs / 1000f) * pxPerSec).coerceAtLeast(48f).dp
                                val isSel = clip.id == selectedClipId
                                Box(
                                    modifier = Modifier
                                        .offset(x = startDp)
                                        .width(widthDp)
                                        .fillMaxHeight()
                                        .padding(2.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(trackColor.copy(alpha = if (isSel) 0.34f else 0.18f))
                                        .border(
                                            width = if (isSel) 2.dp else 1.dp,
                                            color = if (isSel) ElectricCyan else trackColor.copy(alpha = 0.6f),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { onSelectClip(clip.id) }
                                        .padding(horizontal = 8.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = clip.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (clip.speed != 1f) {
                                            Text(
                                                text = "${String.format(Locale.US, "%.1f", clip.speed)}x",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = KeyframeAmber
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Playhead Needle
                        val playheadDp = ((playheadMs / 1000f) * pxPerSec).dp
                        Box(
                            modifier = Modifier
                                .offset(x = playheadDp)
                                .width(2.dp)
                                .fillMaxHeight()
                                .background(ElectricCyan)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrimInspector(clip: TimelineClip?, onTrim: (Long, Long) -> Unit) {
    if (clip == null) {
        Text("Select a clip on the timeline to trim.", style = MaterialTheme.typography.bodyMedium)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Trim: ${clip.label}", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "In: ${formatTimecode(clip.sourceStartMs)}  •  Out: ${formatTimecode(clip.sourceEndMs)}  •  Duration: ${formatTimecode(clip.effectiveDurationMs)}",
            style = MaterialTheme.typography.labelMedium,
            color = ElectricCyan
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { onTrim(250L, 0L) }, modifier = Modifier.weight(1f)) {
                Text("In +0.25s")
            }
            OutlinedButton(onClick = { onTrim(-250L, 0L) }, modifier = Modifier.weight(1f)) {
                Text("In -0.25s")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { onTrim(0L, -250L) }, modifier = Modifier.weight(1f)) {
                Text("Out -0.25s")
            }
            OutlinedButton(onClick = { onTrim(0L, 250L) }, modifier = Modifier.weight(1f)) {
                Text("Out +0.25s")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CropRotateInspector(
    clip: TimelineClip?,
    onSetCrop: (CropBounds) -> Unit,
    onRotate: (Float) -> Unit
) {
    if (clip == null) {
        Text("Select a clip to crop or rotate.", style = MaterialTheme.typography.bodyMedium)
        return
    }
    val presets = listOf(
        CropBounds(-1f, 1f, -1f, 1f, "Original"),
        CropBounds(-0.85f, 0.85f, -0.85f, 0.85f, "1.15x Punch"),
        CropBounds(-0.56f, 0.56f, -1f, 1f, "9:16 Vertical"),
        CropBounds(-0.6f, 0.6f, -0.6f, 0.6f, "1:1 Square"),
        CropBounds(-1f, 1f, -0.78f, 0.78f, "2.39:1 Scope")
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Crop & Rotation (${clip.rotationDegrees.toInt()}°)", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.forEach { preset ->
                FilterChip(
                    selected = clip.crop.presetName == preset.presetName,
                    onClick = { onSetCrop(preset) },
                    label = { Text(preset.presetName) }
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { onRotate(90f) }) { Text("Rotate +90°") }
            OutlinedButton(onClick = { onRotate(-90f) }) { Text("Rotate -90°") }
        }
    }
}

@Composable
private fun SpeedAudioInspector(
    clip: TimelineClip?,
    onSetSpeed: (Float) -> Unit,
    onToggleMute: () -> Unit
) {
    if (clip == null) {
        Text("Select a clip to adjust speed & audio.", style = MaterialTheme.typography.bodyMedium)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PrecisionParameterSlider(
            label = "Playback Speed",
            value = clip.speed,
            valueRange = 0.25f..4.0f,
            defaultValue = 1.0f,
            valueFormat = { String.format(Locale.US, "%.2fx", it) }
        ) { onSetSpeed(it) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0.5f, 1.0f, 1.5f, 2.0f).forEach { spd ->
                FilterChip(
                    selected = kotlin.math.abs(clip.speed - spd) < 0.05f,
                    onClick = { onSetSpeed(spd) },
                    label = { Text("${spd}x") }
                )
            }
        }
        OutlinedButton(onClick = onToggleMute) {
            Icon(if (clip.isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(if (clip.isMuted) "Unmute Clip Audio" else "Mute Clip Audio")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeyframeInspector(
    clip: TimelineClip?,
    playheadMs: Long,
    onAddKeyframe: (KeyframeProperty, Float) -> Unit,
    onRemoveKeyframe: (String) -> Unit
) {
    if (clip == null) {
        Text("Select a clip to animate keyframes.", style = MaterialTheme.typography.bodyMedium)
        return
    }
    var selectedProp by remember { mutableStateOf(KeyframeProperty.OPACITY) }
    var propValue by remember { mutableStateOf(1.0f) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Keyframe Animation @ ${formatTimecode(playheadMs)}", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            KeyframeProperty.entries.forEach { prop ->
                FilterChip(
                    selected = selectedProp == prop,
                    onClick = {
                        selectedProp = prop
                        propValue = when (prop) {
                            KeyframeProperty.OPACITY -> 1.0f
                            KeyframeProperty.SCALE -> 1.2f
                            KeyframeProperty.ROTATION -> 15f
                            KeyframeProperty.POS_X, KeyframeProperty.POS_Y -> 0f
                            KeyframeProperty.FX_INTENSITY -> 0.8f
                        }
                    },
                    label = { Text(prop.displayName) }
                )
            }
        }
        val range = when (selectedProp) {
            KeyframeProperty.OPACITY -> 0f..1f
            KeyframeProperty.SCALE -> 0.25f..2.5f
            KeyframeProperty.ROTATION -> -180f..180f
            KeyframeProperty.POS_X, KeyframeProperty.POS_Y -> -1f..1f
            KeyframeProperty.FX_INTENSITY -> 0f..1.5f
        }
        PrecisionParameterSlider(
            label = selectedProp.displayName,
            value = propValue,
            valueRange = range,
            defaultValue = 1f,
            accentColor = KeyframeAmber
        ) { propValue = it }

        Button(
            onClick = { onAddKeyframe(selectedProp, propValue) },
            colors = ButtonDefaults.buttonColors(containerColor = KeyframeAmber, contentColor = Color.Black)
        ) {
            Icon(Icons.Default.Diamond, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Set Keyframe at Playhead")
        }

        if (clip.keyframes.isNotEmpty()) {
            Text("Active Keyframes (${clip.keyframes.size})", style = MaterialTheme.typography.labelLarge)
            clip.keyframes.forEach { kf ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(StudioSurfaceElevated)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${kf.property.displayName} = ${String.format(Locale.US, "%.2f", kf.value)} @ ${formatTimecode(kf.timeMs)}",
                        style = MaterialTheme.typography.labelMedium
                    )
                    IconButton(onClick = { onRemoveKeyframe(kf.id) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove keyframe", tint = CrimsonAlert, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MaskingInspector(
    params: ShaderEffectParams,
    onUpdate: ((ShaderEffectParams) -> ShaderEffectParams) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("GPU Shape Masking", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MaskShape.entries.forEach { shape ->
                FilterChip(
                    selected = params.maskShape == shape,
                    onClick = { onUpdate { it.copy(maskShape = shape) } },
                    label = { Text(shape.displayName) }
                )
            }
        }
        if (params.maskShape != MaskShape.NONE) {
            PrecisionParameterSlider("Mask Radius", params.maskRadius, 0.1f..1.5f, 0.65f) { v ->
                onUpdate { it.copy(maskRadius = v) }
            }
            PrecisionParameterSlider("Mask Feather", params.maskFeather, 0.01f..1f, 0.2f) { v ->
                onUpdate { it.copy(maskFeather = v) }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Invert Mask", style = MaterialTheme.typography.titleSmall)
                Switch(checked = params.maskInvert, onCheckedChange = { inv -> onUpdate { it.copy(maskInvert = inv) } })
            }
        }
    }
}

@Composable
private fun ChromaKeyInspector(
    params: ShaderEffectParams,
    onUpdate: ((ShaderEffectParams) -> ShaderEffectParams) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("GPU Chroma Key", style = MaterialTheme.typography.titleMedium)
            Switch(
                checked = params.chromaKeyEnabled,
                onCheckedChange = { en -> onUpdate { it.copy(chromaKeyEnabled = en) } }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                Triple("Green Screen", 0f, 1f) to 0f,
                Triple("Blue Screen", 0f, 0.25f) to 1f,
                Triple("Cyan Matte", 0f, 0.9f) to 1f
            ).forEach { (rg, b) ->
                val (label, r, g) = rg
                FilterChip(
                    selected = kotlin.math.abs(params.chromaKeyGreen - g) < 0.15f && kotlin.math.abs(params.chromaKeyBlue - b) < 0.15f,
                    onClick = { onUpdate { it.copy(chromaKeyEnabled = true, chromaKeyRed = r, chromaKeyGreen = g, chromaKeyBlue = b) } },
                    label = { Text(label) }
                )
            }
        }
        PrecisionParameterSlider("Similarity Threshold", params.chromaSimilarity, 0.05f..0.85f, 0.32f) { v ->
            onUpdate { it.copy(chromaSimilarity = v) }
        }
        PrecisionParameterSlider("Edge Smoothness", params.chromaSmoothness, 0.01f..0.5f, 0.12f) { v ->
            onUpdate { it.copy(chromaSmoothness = v) }
        }
        PrecisionParameterSlider("Spill Suppression", params.chromaSpillSuppression, 0f..1f, 0.45f) { v ->
            onUpdate { it.copy(chromaSpillSuppression = v) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TransitionInspector(
    clip: TimelineClip?,
    onSetTransition: (TransitionType) -> Unit
) {
    if (clip == null) {
        Text("Select a clip to assign an incoming transition.", style = MaterialTheme.typography.bodyMedium)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Incoming Transition: ${clip.label}", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TransitionType.entries.forEach { t ->
                FilterChip(
                    selected = clip.transitionIn == t,
                    onClick = { onSetTransition(t) },
                    label = { Text(t.displayName) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = StudioViolet.copy(alpha = 0.24f),
                        selectedLabelColor = StudioViolet
                    )
                )
            }
        }
    }
}
