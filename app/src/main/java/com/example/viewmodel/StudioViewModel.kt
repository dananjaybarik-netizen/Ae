package com.example.viewmodel

import android.app.Application
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.AiStudioApplication
import com.example.data.preferences.PreviewResolutionPreset
import com.example.data.preferences.StudioSettingsState
import com.example.data.repository.PluginInstallResult
import com.example.data.repository.StudioProjectSummary
import com.example.engine.DeviceCapabilities
import com.example.engine.DeviceHardwareReport
import com.example.engine.ExportJobState
import com.example.engine.LiveShaderState
import com.example.engine.SampleVideoGenerator
import com.example.model.CropBounds
import com.example.model.EffectsCatalog
import com.example.model.ExportCodec
import com.example.model.ExportConfig
import com.example.model.ExportResolution
import com.example.model.InstalledPlugin
import com.example.model.KeyframePoint
import com.example.model.KeyframeProperty
import com.example.model.MaskShape
import com.example.model.ShaderEffectParams
import com.example.model.StorePluginListing
import com.example.model.TimelineClip
import com.example.model.TimelineProjectData
import com.example.model.TrackType
import com.example.model.TransitionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class EditorUiState(
    val projectId: Long? = null,
    val project: TimelineProjectData = TimelineProjectData(),
    val selectedClipId: String? = null,
    val playheadMs: Long = 0L,
    val isPlaying: Boolean = false,
    val timelineZoom: Float = 1.0f,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val statusMessage: String? = null,
    val isGeneratingSample: Boolean = false,
    val exportState: ExportJobState = ExportJobState.Idle,
    val exportConfig: ExportConfig = ExportConfig(),
    val liveRamLabel: String = "-- MB free"
)

class StudioViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as AiStudioApplication
    private val projectRepo = app.projectRepository
    private val pluginRepo = app.pluginRepository
    private val prefs = app.studioPreferences
    private val exportEngine = app.exportEngine

    val liveShaderState = LiveShaderState()

    private val _editorState = MutableStateFlow(EditorUiState())
    val editorState: StateFlow<EditorUiState> = _editorState.asStateFlow()

    private val _hardwareReport = MutableStateFlow<DeviceHardwareReport?>(null)
    val hardwareReport: StateFlow<DeviceHardwareReport?> = _hardwareReport.asStateFlow()

    private val _cacheSizeLabel = MutableStateFlow("0 KB")
    val cacheSizeLabel: StateFlow<String> = _cacheSizeLabel.asStateFlow()

    private val _pluginBanner = MutableStateFlow<String?>(null)
    val pluginBanner: StateFlow<String?> = _pluginBanner.asStateFlow()

    private val undoStack = ArrayDeque<TimelineProjectData>()
    private val redoStack = ArrayDeque<TimelineProjectData>()

    val projects: StateFlow<List<StudioProjectSummary>> =
        projectRepo.allProjects.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val installedPlugins: StateFlow<List<InstalledPlugin>> =
        pluginRepo.installedPlugins.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val storeCatalog: StateFlow<List<StorePluginListing>> =
        installedPlugins.combine(MutableStateFlow(Unit)) { installed, _ ->
            val ids = installed.map { it.pluginId }.toSet()
            val versions = installed.associate { it.pluginId to it.manifest.versionCode }
            pluginRepo.getStoreCatalog(ids, versions)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<StudioSettingsState> =
        prefs.settingsFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StudioSettingsState())

    init {
        refreshHardwareAndCache()
    }

    fun refreshHardwareAndCache() {
        viewModelScope.launch {
            val report = DeviceCapabilities.inspect(app)
            _hardwareReport.value = report
            val ram = DeviceCapabilities.queryLiveRamMb(app)
            _editorState.update {
                it.copy(
                    liveRamLabel = "${ram.first} MB free / ${ram.second} MB",
                    exportConfig = it.exportConfig.copy(
                        resolution = settings.value.defaultExportResolution,
                        fps = settings.value.defaultExportFps
                    )
                )
            }
            _cacheSizeLabel.value = computeCacheSizeString()
        }
    }

    fun clearStatusMessage() {
        _editorState.update { it.copy(statusMessage = null) }
    }

    fun clearPluginBanner() {
        _pluginBanner.value = null
    }

    // --- Project Management ---

    fun createNewProject(title: String = "Studio Cut ${System.currentTimeMillis() % 1000}") {
        undoStack.clear()
        redoStack.clear()
        val fresh = TimelineProjectData(projectName = title)
        liveShaderState.paramsRef.set(fresh.masterEffects)
        liveShaderState.keyframesRef.set(emptyList())
        _editorState.value = EditorUiState(
            projectId = null,
            project = fresh,
            statusMessage = "New sequence initialized"
        )
    }

    fun openProject(projectId: Long) {
        viewModelScope.launch {
            val loaded = projectRepo.loadProject(projectId)
            if (loaded != null) {
                undoStack.clear()
                redoStack.clear()
                val (_, timeline) = loaded
                val firstClip = timeline.clips.firstOrNull()
                liveShaderState.paramsRef.set(timeline.masterEffects)
                liveShaderState.keyframesRef.set(firstClip?.keyframes ?: emptyList())
                _editorState.update {
                    it.copy(
                        projectId = projectId,
                        project = timeline,
                        selectedClipId = firstClip?.id,
                        playheadMs = 0L,
                        canUndo = false,
                        canRedo = false,
                        statusMessage = "Loaded '${timeline.projectName}'"
                    )
                }
            }
        }
    }

    fun saveCurrentProject() {
        viewModelScope.launch {
            val current = _editorState.value
            val id = projectRepo.saveProject(current.projectId, current.project)
            _editorState.update {
                it.copy(
                    projectId = id,
                    statusMessage = "Saved '${current.project.projectName}'"
                )
            }
        }
    }

    fun renameProject(id: Long, newTitle: String) {
        viewModelScope.launch {
            projectRepo.renameProject(id, newTitle)
            if (_editorState.value.projectId == id) {
                _editorState.update {
                    it.copy(project = it.project.copy(projectName = newTitle))
                }
            }
        }
    }

    fun duplicateProject(id: Long) {
        viewModelScope.launch { projectRepo.duplicateProject(id) }
    }

    fun deleteProject(id: Long) {
        viewModelScope.launch {
            projectRepo.deleteProject(id)
            if (_editorState.value.projectId == id) {
                createNewProject()
            }
        }
    }

    // --- Media Import & Calibration Clip ---

    fun importVideoUri(uri: Uri, trackType: TrackType = TrackType.VIDEO_PRIMARY) {
        viewModelScope.launch {
            val durationMs = queryMediaDurationMs(uri).coerceAtLeast(2000L)
            val label = queryMediaDisplayName(uri) ?: "Clip ${_editorState.value.project.clips.size + 1}"
            pushUndoSnapshot()
            _editorState.update { state ->
                val trackClips = state.project.clips.filter { it.trackType == trackType }
                val startMs = trackClips.maxOfOrNull { it.timelineEndMs } ?: 0L
                val newClip = TimelineClip(
                    label = label,
                    sourceUri = uri.toString(),
                    trackType = trackType,
                    timelineStartMs = startMs,
                    sourceStartMs = 0L,
                    sourceEndMs = durationMs,
                    totalSourceDurationMs = durationMs
                )
                val updated = state.project.copy(clips = state.project.clips + newClip)
                liveShaderState.keyframesRef.set(newClip.keyframes)
                state.copy(
                    project = updated,
                    selectedClipId = newClip.id,
                    canUndo = undoStack.isNotEmpty(),
                    canRedo = redoStack.isNotEmpty(),
                    statusMessage = "Imported '$label' (${durationMs / 1000f}s)"
                )
            }
        }
    }

    fun generateCalibrationClipAndLoad() {
        if (_editorState.value.isGeneratingSample) return
        viewModelScope.launch {
            _editorState.update { it.copy(isGeneratingSample = true, statusMessage = "Encoding H.264 calibration MP4...") }
            try {
                val file = SampleVideoGenerator.generateCalibrationMp4(app)
                importVideoUri(Uri.fromFile(file), TrackType.VIDEO_PRIMARY)
            } catch (e: Exception) {
                _editorState.update { it.copy(statusMessage = "Sample encode failed: ${e.message}") }
            } finally {
                _editorState.update { it.copy(isGeneratingSample = false) }
            }
        }
    }

    // --- Timeline Editing Operations (Trim, Split, Crop, Rotate, Speed, Keyframe, Mask, Chroma, Transition) ---

    fun selectClip(clipId: String?) {
        val clip = _editorState.value.project.clips.firstOrNull { it.id == clipId }
        liveShaderState.keyframesRef.set(clip?.keyframes ?: emptyList())
        _editorState.update { it.copy(selectedClipId = clipId) }
    }

    fun updatePlayhead(ms: Long) {
        val clamped = ms.coerceIn(0L, _editorState.value.project.totalDurationMs.coerceAtLeast(1000L))
        _editorState.update { it.copy(playheadMs = clamped) }
    }

    fun setPlaying(playing: Boolean) {
        _editorState.update { it.copy(isPlaying = playing) }
    }

    fun setTimelineZoom(zoom: Float) {
        _editorState.update { it.copy(timelineZoom = zoom.coerceIn(0.5f, 3.0f)) }
    }

    fun trimSelectedClip(trimStartDeltaMs: Long, trimEndDeltaMs: Long) {
        val clipId = _editorState.value.selectedClipId ?: return
        mutateProject("Trimmed clip") { project ->
            val updated = project.clips.map { clip ->
                if (clip.id != clipId) clip else {
                    val newStart = (clip.sourceStartMs + trimStartDeltaMs).coerceIn(0L, clip.sourceEndMs - 300L)
                    val newEnd = (clip.sourceEndMs + trimEndDeltaMs).coerceIn(newStart + 300L, clip.totalSourceDurationMs)
                    clip.copy(sourceStartMs = newStart, sourceEndMs = newEnd)
                }
            }
            project.copy(clips = recomputeTrackPositions(updated))
        }
    }

    fun splitSelectedClipAtPlayhead() {
        val state = _editorState.value
        val clip = state.project.clips.firstOrNull { it.id == state.selectedClipId } ?: return
        val splitOffsetTimeline = state.playheadMs - clip.timelineStartMs
        if (splitOffsetTimeline <= 200L || splitOffsetTimeline >= clip.effectiveDurationMs - 200L) {
            _editorState.update { it.copy(statusMessage = "Move playhead inside the selected clip to split.") }
            return
        }
        val splitSourceMs = clip.sourceStartMs + (splitOffsetTimeline * clip.speed).toLong()
        if (splitSourceMs <= clip.sourceStartMs + 150L || splitSourceMs >= clip.sourceEndMs - 150L) return

        mutateProject("Split '${clip.label}'") { project ->
            val firstHalf = clip.copy(
                label = "${clip.label} (A)",
                sourceEndMs = splitSourceMs
            )
            val secondHalf = clip.copy(
                id = java.util.UUID.randomUUID().toString(),
                label = "${clip.label} (B)",
                sourceStartMs = splitSourceMs,
                timelineStartMs = firstHalf.timelineEndMs
            )
            val newClips = project.clips.flatMap {
                if (it.id == clip.id) listOf(firstHalf, secondHalf) else listOf(it)
            }
            project.copy(clips = recomputeTrackPositions(newClips))
        }
    }

    fun deleteSelectedClip() {
        val clipId = _editorState.value.selectedClipId ?: return
        mutateProject("Deleted clip") { project ->
            val remaining = project.clips.filterNot { it.id == clipId }
            project.copy(clips = recomputeTrackPositions(remaining))
        }
        _editorState.update { it.copy(selectedClipId = it.project.clips.firstOrNull()?.id) }
    }

    fun setSelectedClipSpeed(speed: Float) {
        val clipId = _editorState.value.selectedClipId ?: return
        val clamped = speed.coerceIn(0.25f, 4.0f)
        mutateProject("Speed ${String.format("%.2fx", clamped)}") { project ->
            val updated = project.clips.map {
                if (it.id == clipId) it.copy(speed = clamped) else it
            }
            project.copy(clips = recomputeTrackPositions(updated))
        }
    }

    fun rotateSelectedClip(deltaDegrees: Float = 90f) {
        val clipId = _editorState.value.selectedClipId ?: return
        mutateProject("Rotated clip") { project ->
            project.copy(clips = project.clips.map {
                if (it.id == clipId) it.copy(rotationDegrees = (it.rotationDegrees + deltaDegrees) % 360f) else it
            })
        }
    }

    fun setSelectedClipCrop(crop: CropBounds) {
        val clipId = _editorState.value.selectedClipId ?: return
        mutateProject("Crop ${crop.presetName}") { project ->
            project.copy(clips = project.clips.map {
                if (it.id == clipId) it.copy(crop = crop) else it
            })
        }
    }

    fun toggleSelectedClipMute() {
        val clipId = _editorState.value.selectedClipId ?: return
        mutateProject("Audio toggled") { project ->
            project.copy(clips = project.clips.map {
                if (it.id == clipId) it.copy(isMuted = !it.isMuted) else it
            })
        }
    }

    fun setSelectedClipTransition(transition: TransitionType) {
        val clipId = _editorState.value.selectedClipId ?: return
        mutateProject("Transition: ${transition.displayName}") { project ->
            project.copy(clips = project.clips.map {
                if (it.id == clipId) it.copy(transitionIn = transition) else it
            })
        }
    }

    fun addKeyframeAtPlayhead(property: KeyframeProperty, value: Float) {
        val state = _editorState.value
        val clip = state.project.clips.firstOrNull { it.id == state.selectedClipId }
            ?: state.project.clips.firstOrNull() ?: return
        val localMs = (state.playheadMs - clip.timelineStartMs).coerceIn(0L, clip.effectiveDurationMs)

        mutateProject("Added ${property.displayName} keyframe") { project ->
            val newClips = project.clips.map { c ->
                if (c.id != clip.id) c else {
                    val filtered = c.keyframes.filterNot { it.property == property && kotlin.math.abs(it.timeMs - localMs) < 120L }
                    val updatedKf = (filtered + KeyframePoint(timeMs = localMs, property = property, value = value)).sortedBy { it.timeMs }
                    liveShaderState.keyframesRef.set(updatedKf)
                    c.copy(keyframes = updatedKf)
                }
            }
            project.copy(clips = newClips)
        }
    }

    fun removeKeyframe(keyframeId: String) {
        val clipId = _editorState.value.selectedClipId ?: return
        mutateProject("Removed keyframe") { project ->
            project.copy(clips = project.clips.map { c ->
                if (c.id != clipId) c else {
                    val updatedKf = c.keyframes.filterNot { it.id == keyframeId }
                    liveShaderState.keyframesRef.set(updatedKf)
                    c.copy(keyframes = updatedKf)
                }
            })
        }
    }

    // --- GPU Shader Effects Controls ---

    fun updateMasterEffectParams(recordUndo: Boolean = false, transform: (ShaderEffectParams) -> ShaderEffectParams) {
        val current = _editorState.value.project
        val updatedParams = transform(current.masterEffects)
        liveShaderState.paramsRef.set(updatedParams)
        if (recordUndo) {
            pushUndoSnapshot()
        }
        _editorState.update { state ->
            state.copy(
                project = state.project.copy(masterEffects = updatedParams),
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
    }

    fun selectBuiltinEffect(effectId: String) {
        val def = EffectsCatalog.findById(effectId)
        updateMasterEffectParams(recordUndo = true) { prev ->
            prev.copy(
                activeEffectId = def.id,
                activePluginId = null,
                effectIntensity = def.defaultIntensity
            )
        }
        _editorState.update { it.copy(statusMessage = "GPU Shader Active: ${def.name}") }
    }

    fun activatePluginShader(plugin: InstalledPlugin) {
        if (!plugin.isEnabled) {
            _editorState.update { it.copy(statusMessage = "Enable '${plugin.manifest.name}' in Plugins first.") }
            return
        }
        updateMasterEffectParams(recordUndo = true) { prev ->
            prev.copy(
                activePluginId = plugin.pluginId,
                effectIntensity = plugin.manifest.parameters.firstOrNull()?.defaultValue ?: 0.8f
            )
        }
        _editorState.update { it.copy(statusMessage = "Plugin Shader Active: ${plugin.manifest.name}") }
    }

    fun resetAllEffects() {
        updateMasterEffectParams(recordUndo = true) { ShaderEffectParams() }
        _editorState.update { it.copy(statusMessage = "Reset all GPU shader parameters") }
    }

    // --- Undo / Redo ---

    fun undo() {
        if (undoStack.isEmpty()) return
        val current = _editorState.value.project
        redoStack.addLast(current)
        val prev = undoStack.removeLast()
        liveShaderState.paramsRef.set(prev.masterEffects)
        liveShaderState.keyframesRef.set(prev.clips.firstOrNull { it.id == _editorState.value.selectedClipId }?.keyframes ?: emptyList())
        _editorState.update {
            it.copy(
                project = prev,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
                statusMessage = "Undo"
            )
        }
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val current = _editorState.value.project
        undoStack.addLast(current)
        val next = redoStack.removeLast()
        liveShaderState.paramsRef.set(next.masterEffects)
        liveShaderState.keyframesRef.set(next.clips.firstOrNull { it.id == _editorState.value.selectedClipId }?.keyframes ?: emptyList())
        _editorState.update {
            it.copy(
                project = next,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
                statusMessage = "Redo"
            )
        }
    }

    private fun pushUndoSnapshot() {
        if (undoStack.size >= 30) undoStack.removeFirst()
        undoStack.addLast(_editorState.value.project)
        redoStack.clear()
    }

    private fun mutateProject(message: String, mutator: (TimelineProjectData) -> TimelineProjectData) {
        pushUndoSnapshot()
        _editorState.update { state ->
            state.copy(
                project = mutator(state.project),
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
                statusMessage = message
            )
        }
    }

    private fun recomputeTrackPositions(clips: List<TimelineClip>): List<TimelineClip> {
        val result = mutableListOf<TimelineClip>()
        for (track in TrackType.entries) {
            var cursor = 0L
            clips.filter { it.trackType == track }.sortedBy { it.timelineStartMs }.forEach { clip ->
                val positioned = clip.copy(timelineStartMs = cursor)
                cursor = positioned.timelineEndMs
                result.add(positioned)
            }
        }
        return result
    }

    // --- Export Engine ---

    fun updateExportConfig(transform: (ExportConfig) -> ExportConfig) {
        _editorState.update { it.copy(exportConfig = transform(it.exportConfig)) }
    }

    fun startExport() {
        val state = _editorState.value
        val activePlugin = state.project.masterEffects.activePluginId?.let { pid ->
            installedPlugins.value.firstOrNull { it.pluginId == pid && it.isEnabled }
        }
        exportEngine.startExport(
            project = state.project,
            config = state.exportConfig,
            pluginGlslCode = activePlugin?.fragmentShaderCode
        ) { jobState ->
            _editorState.update { it.copy(exportState = jobState) }
        }
    }

    fun cancelExport() {
        exportEngine.cancelExport()
        _editorState.update { it.copy(exportState = ExportJobState.Idle) }
    }

    fun resetExportState() {
        _editorState.update { it.copy(exportState = ExportJobState.Idle) }
    }

    // --- .aifx Plugin Architecture ---

    fun installStorePlugin(listing: StorePluginListing) {
        viewModelScope.launch {
            when (val res = pluginRepo.installFromStore(listing)) {
                is PluginInstallResult.Success -> _pluginBanner.value = "Installed '${res.plugin.manifest.name}' v${res.plugin.manifest.version} (SHA-256 Verified)"
                is PluginInstallResult.Failure -> _pluginBanner.value = res.reason
            }
        }
    }

    fun importAifxFile(uri: Uri) {
        viewModelScope.launch {
            when (val res = pluginRepo.importAifxPackage(uri)) {
                is PluginInstallResult.Success -> {
                    val trust = if (res.plugin.isSignatureVerified) "Signature Verified" else "Unsigned Sandboxed GLSL"
                    _pluginBanner.value = "Imported '${res.plugin.manifest.name}' ($trust)"
                }
                is PluginInstallResult.Failure -> _pluginBanner.value = res.reason
            }
        }
    }

    fun togglePluginEnabled(pluginId: String, enabled: Boolean) {
        viewModelScope.launch {
            pluginRepo.setPluginEnabled(pluginId, enabled)
            if (!enabled && _editorState.value.project.masterEffects.activePluginId == pluginId) {
                updateMasterEffectParams { it.copy(activePluginId = null) }
            }
        }
    }

    fun removePlugin(pluginId: String) {
        viewModelScope.launch {
            pluginRepo.removePlugin(pluginId)
            if (_editorState.value.project.masterEffects.activePluginId == pluginId) {
                updateMasterEffectParams { it.copy(activePluginId = null) }
            }
            _pluginBanner.value = "Removed plugin from library"
        }
    }

    fun createSampleAifxFile(onReady: (File) -> Unit) {
        viewModelScope.launch {
            val file = pluginRepo.exportSampleAifxPackage()
            _pluginBanner.value = "Packaged ${file.name} (.aifx ZIP)"
            onReady(file)
        }
    }

    // --- Settings & Performance Optimization ---

    fun setLowRamSafeMode(enabled: Boolean) = viewModelScope.launch { prefs.setLowRamSafeMode(enabled) }
    fun setPreviewResolution(preset: PreviewResolutionPreset) = viewModelScope.launch { prefs.setPreviewResolution(preset) }
    fun setEffectCacheEnabled(enabled: Boolean) = viewModelScope.launch { prefs.setEffectCacheEnabled(enabled) }
    fun setThermalThrottling(enabled: Boolean) = viewModelScope.launch { prefs.setThermalThrottling(enabled) }
    fun setPreferVulkan(enabled: Boolean) = viewModelScope.launch { prefs.setPreferVulkan(enabled) }
    fun setDefaultExportResolution(res: ExportResolution) = viewModelScope.launch { prefs.setDefaultExportResolution(res) }
    fun setDefaultExportFps(fps: Int) = viewModelScope.launch { prefs.setDefaultExportFps(fps) }

    fun clearShaderAndMediaCache() {
        viewModelScope.launch(Dispatchers.IO) {
            app.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
            _cacheSizeLabel.value = computeCacheSizeString()
            refreshHardwareAndCache()
        }
    }

    private fun computeCacheSizeString(): String {
        val bytes = app.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        return when {
            bytes < 1024L -> "$bytes B"
            bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
            else -> String.format("%.1f MB", bytes / (1024f * 1024f))
        }
    }

    private suspend fun queryMediaDurationMs(uri: Uri): Long = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(app, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 5000L
        } catch (_: Exception) {
            5000L
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun queryMediaDisplayName(uri: Uri): String? {
        return uri.lastPathSegment?.substringAfterLast('/')?.take(24)
    }
}
