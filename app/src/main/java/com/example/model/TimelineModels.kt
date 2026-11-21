package com.example.model

import kotlinx.serialization.Serializable
import java.util.UUID

enum class TrackType(val label: String) {
    VIDEO_PRIMARY("V1"),
    VIDEO_OVERLAY("V2"),
    AUDIO("A1"),
    FX("FX")
}

enum class KeyframeProperty(val displayName: String, val unit: String) {
    OPACITY("Opacity", "%"),
    SCALE("Scale", "x"),
    ROTATION("Rotation", "°"),
    POS_X("Position X", "%"),
    POS_Y("Position Y", "%"),
    FX_INTENSITY("FX Intensity", "%")
}

@Serializable
data class KeyframePoint(
    val id: String = UUID.randomUUID().toString(),
    val timeMs: Long,
    val property: KeyframeProperty,
    val value: Float
)

@Serializable
data class CropBounds(
    val left: Float = -1f,   // Normalized Media3 NDC (-1..1)
    val right: Float = 1f,
    val bottom: Float = -1f,
    val top: Float = 1f,
    val presetName: String = "Original"
)

@Serializable
data class TimelineClip(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val sourceUri: String,
    val trackType: TrackType,
    val timelineStartMs: Long,
    val sourceStartMs: Long,
    val sourceEndMs: Long,
    val totalSourceDurationMs: Long,
    val speed: Float = 1.0f,
    val volume: Float = 1.0f,
    val isMuted: Boolean = false,
    val rotationDegrees: Float = 0f,
    val crop: CropBounds = CropBounds(),
    val transitionIn: TransitionType = TransitionType.NONE,
    val keyframes: List<KeyframePoint> = emptyList(),
    val effectParams: ShaderEffectParams = ShaderEffectParams()
) {
    val effectiveDurationMs: Long
        get() = ((sourceEndMs - sourceStartMs).coerceAtLeast(100L) / speed.coerceIn(0.25f, 4.0f)).toLong()

    val timelineEndMs: Long
        get() = timelineStartMs + effectiveDurationMs
}

@Serializable
data class TimelineProjectData(
    val projectName: String = "Untitled Sequence",
    val clips: List<TimelineClip> = emptyList(),
    val masterEffects: ShaderEffectParams = ShaderEffectParams(),
    val aspectRatioLabel: String = "16:9",
    val sequenceFps: Int = 30
) {
    val totalDurationMs: Long
        get() = clips.maxOfOrNull { it.timelineEndMs } ?: 0L
}

enum class ExportResolution(val label: String, val width: Int, val height: Int, val defaultBitrateMbps: Int) {
    P720("720p HD", 1280, 720, 8),
    P1080("1080p FHD", 1920, 1080, 16),
    P1440("1440p QHD", 2560, 1440, 28),
    UHD_4K("4K UHD", 3840, 2160, 48)
}

enum class ExportCodec(val label: String, val mimeType: String) {
    H264("H.264 / AVC", "video/avc"),
    HEVC("H.265 / HEVC", "video/hevc")
}

@Serializable
data class ExportConfig(
    val resolution: ExportResolution = ExportResolution.P1080,
    val fps: Int = 30,
    val bitrateMbps: Int = 16,
    val codec: ExportCodec = ExportCodec.H264,
    val qualityPreset: String = "High"
)
