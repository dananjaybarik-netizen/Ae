package com.example.engine

import android.content.Context
import android.graphics.Matrix
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Crop
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.Presentation
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.effect.SpeedChangeEffect
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.example.model.ExportConfig
import com.example.model.KeyframeProperty
import com.example.model.TimelineClip
import com.example.model.TimelineProjectData
import com.example.model.TrackType
import java.io.File

sealed class ExportJobState {
    data object Idle : ExportJobState()
    data class Running(val progressPercent: Int, val stageLabel: String) : ExportJobState()
    data class Completed(val outputFile: File, val fileSizeBytes: Long, val durationMs: Long) : ExportJobState()
    data class Failed(val message: String) : ExportJobState()
}

@OptIn(UnstableApi::class)
class VideoExportEngine(private val context: Context) {

    private var activeTransformer: Transformer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var progressRunnable: Runnable? = null

    fun startExport(
        project: TimelineProjectData,
        config: ExportConfig,
        pluginGlslCode: String?,
        onStateChanged: (ExportJobState) -> Unit
    ) {
        val videoClips = project.clips
            .filter { it.trackType == TrackType.VIDEO_PRIMARY }
            .sortedBy { it.timelineStartMs }

        if (videoClips.isEmpty()) {
            onStateChanged(ExportJobState.Failed("No video clips in timeline to export."))
            return
        }

        val exportDir = File(context.filesDir, "exports").apply { mkdirs() }
        val safeName = project.projectName.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifBlank { "AI_STUDIO" }
        val outputFile = File(exportDir, "${safeName}_${config.resolution.label.replace(" ", "_")}_${System.currentTimeMillis()}.mp4")

        val editedItems = videoClips.map { clip ->
            buildEditedMediaItem(clip, project, config, pluginGlslCode)
        }

        val sequence = EditedMediaItemSequence.Builder(editedItems).build()
        val composition = Composition.Builder(listOf(sequence)).build()

        val videoEncoderSettings = VideoEncoderSettings.Builder()
            .setBitrate(config.bitrateMbps * 1_000_000)
            .build()

        val encoderFactory = DefaultEncoderFactory.Builder(context)
            .setRequestedVideoEncoderSettings(videoEncoderSettings)
            .setEnableFallback(true)
            .build()

        val transformer = Transformer.Builder(context)
            .setVideoMimeType(config.codec.mimeType)
            .setEncoderFactory(encoderFactory)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    stopPolling()
                    onStateChanged(
                        ExportJobState.Completed(
                            outputFile = outputFile,
                            fileSizeBytes = outputFile.length(),
                            durationMs = exportResult.durationMs
                        )
                    )
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    stopPolling()
                    onStateChanged(
                        ExportJobState.Failed(exportException.localizedMessage ?: "Export pipeline error.")
                    )
                }
            })
            .build()

        activeTransformer = transformer
        onStateChanged(ExportJobState.Running(0, "Initializing hardware encoder (${config.resolution.label} @ ${config.fps}fps)..."))
        transformer.start(composition, outputFile.absolutePath)

        val holder = ProgressHolder()
        val poll = object : Runnable {
            override fun run() {
                val state = transformer.getProgress(holder)
                if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                    onStateChanged(
                        ExportJobState.Running(
                            holder.progress.coerceIn(0, 99),
                            "GPU Shader Pass + Hardware Encode (${config.codec.label})"
                        )
                    )
                }
                if (state != Transformer.PROGRESS_STATE_NOT_STARTED) {
                    mainHandler.postDelayed(this, 250L)
                }
            }
        }
        progressRunnable = poll
        mainHandler.postDelayed(poll, 250L)
    }

    fun cancelExport() {
        stopPolling()
        runCatching { activeTransformer?.cancel() }
        activeTransformer = null
    }

    private fun stopPolling() {
        progressRunnable?.let { mainHandler.removeCallbacks(it) }
        progressRunnable = null
    }

    private fun buildEditedMediaItem(
        clip: TimelineClip,
        project: TimelineProjectData,
        config: ExportConfig,
        pluginGlslCode: String?
    ): EditedMediaItem {
        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(clip.sourceUri))
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(clip.sourceStartMs.coerceAtLeast(0L))
                    .setEndPositionMs(clip.sourceEndMs.coerceAtLeast(clip.sourceStartMs + 100L))
                    .build()
            )
            .build()

        val videoEffects = mutableListOf<Effect>()

        // Crop
        if (clip.crop.left > -0.99f || clip.crop.right < 0.99f || clip.crop.bottom > -0.99f || clip.crop.top < 0.99f) {
            videoEffects.add(Crop(clip.crop.left, clip.crop.right, clip.crop.bottom, clip.crop.top))
        }

        // Keyframed Scale / Position / Rotation MatrixTransformation
        val hasTransformKeyframes = clip.keyframes.any {
            it.property == KeyframeProperty.SCALE ||
                it.property == KeyframeProperty.ROTATION ||
                it.property == KeyframeProperty.POS_X ||
                it.property == KeyframeProperty.POS_Y
        }
        if (hasTransformKeyframes) {
            videoEffects.add(MatrixTransformation { timeUs ->
                val ms = timeUs / 1000L
                val scale = interpolateKeyframe(clip.keyframes, KeyframeProperty.SCALE, ms, 1f)
                val rot = interpolateKeyframe(clip.keyframes, KeyframeProperty.ROTATION, ms, clip.rotationDegrees)
                val posX = interpolateKeyframe(clip.keyframes, KeyframeProperty.POS_X, ms, 0f)
                val posY = interpolateKeyframe(clip.keyframes, KeyframeProperty.POS_Y, ms, 0f)
                Matrix().apply {
                    postScale(scale, scale)
                    postRotate(rot)
                    postTranslate(posX, posY)
                }
            })
        } else if (kotlin.math.abs(clip.rotationDegrees) > 0.1f) {
            videoEffects.add(
                ScaleAndRotateTransformation.Builder()
                    .setRotationDegrees(clip.rotationDegrees)
                    .build()
            )
        }

        // Speed control
        if (kotlin.math.abs(clip.speed - 1.0f) > 0.01f) {
            videoEffects.add(SpeedChangeEffect(clip.speed.coerceIn(0.25f, 4.0f)))
        }

        // Real GPU Pixel Shader Pass (merged project master + clip params)
        val mergedParams = project.masterEffects
        val liveState = LiveShaderState(mergedParams, clip.keyframes)
        videoEffects.add(AiStudioGlEffect(liveState, pluginGlslCode, isExportMode = true))

        // Target Resolution Scaling
        videoEffects.add(Presentation.createForHeight(config.resolution.height))

        val audioProcessors = mutableListOf<androidx.media3.common.audio.AudioProcessor>()
        if (kotlin.math.abs(clip.speed - 1.0f) > 0.01f && !clip.isMuted) {
            audioProcessors.add(SonicAudioProcessor().apply { setSpeed(clip.speed.coerceIn(0.25f, 4.0f)) })
        }

        return EditedMediaItem.Builder(mediaItem)
            .setRemoveAudio(clip.isMuted)
            .setEffects(Effects(audioProcessors, videoEffects))
            .build()
    }
}
