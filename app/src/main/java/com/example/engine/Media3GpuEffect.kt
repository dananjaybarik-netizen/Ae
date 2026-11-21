package com.example.engine

import android.content.Context
import android.opengl.GLES20
import androidx.annotation.OptIn
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import com.example.model.EffectsCatalog
import com.example.model.KeyframePoint
import com.example.model.KeyframeProperty
import com.example.model.ShaderEffectParams
import java.util.concurrent.atomic.AtomicReference

/**
 * Shared thread-safe live parameter holder so UI sliders mutate shader uniforms
 * at 60fps without rebuilding ExoPlayer's media pipeline on every drag tick.
 */
class LiveShaderState(
    initialParams: ShaderEffectParams = ShaderEffectParams(),
    initialKeyframes: List<KeyframePoint> = emptyList()
) {
    val paramsRef = AtomicReference(initialParams)
    val keyframesRef = AtomicReference(initialKeyframes)
}

@OptIn(UnstableApi::class)
class AiStudioGlEffect(
    private val liveState: LiveShaderState,
    private val pluginGlslCode: String? = null,
    private val isExportMode: Boolean = false
) : GlEffect {

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
        return AiStudioShaderProgram(
            useHdr = useHdr,
            liveState = liveState,
            pluginGlslCode = pluginGlslCode
        )
    }

    override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean {
        if (!isExportMode) return false
        return !liveState.paramsRef.get().hasAnyActivePixelProcessing() &&
            liveState.keyframesRef.get().isEmpty()
    }
}

@OptIn(UnstableApi::class)
private class AiStudioShaderProgram(
    useHdr: Boolean,
    private val liveState: LiveShaderState,
    pluginGlslCode: String?
) : BaseGlShaderProgram(useHdr, /* texturePoolCapacity= */ 1) {

    private var glProgram: GlProgram? = null
    private var texWidth = 1280
    private var texHeight = 720
    private val fragmentGlsl = GpuShaderLibrary.buildFragmentShader(pluginGlslCode)

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        texWidth = inputWidth.coerceAtLeast(1)
        texHeight = inputHeight.coerceAtLeast(1)
        if (glProgram == null) {
            try {
                glProgram = GlProgram(GpuShaderLibrary.STANDARD_VERTEX_SHADER, fragmentGlsl).apply {
                    setBufferAttribute(
                        "aFramePosition",
                        GlUtil.getNormalizedCoordinateBounds(),
                        GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE
                    )
                }
            } catch (e: Exception) {
                throw VideoFrameProcessingException(e)
            }
        }
        return Size(texWidth, texHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        val program = glProgram ?: return
        val params = liveState.paramsRef.get()
        val keyframes = liveState.keyframesRef.get()
        val timeMs = presentationTimeUs / 1000L

        val keyframedOpacity = interpolateKeyframe(keyframes, KeyframeProperty.OPACITY, timeMs, params.opacity)
        val keyframedIntensity = interpolateKeyframe(keyframes, KeyframeProperty.FX_INTENSITY, timeMs, params.effectIntensity)

        val def = EffectsCatalog.findById(params.activeEffectId)
        val modeCode = if (params.hasAnyActivePixelProcessing()) def.shaderModeCode else EffectsCatalog.MODE_NONE

        try {
            program.use()
            program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            program.setFloatsUniform("uTexelSize", floatArrayOf(1f / texWidth.toFloat(), 1f / texHeight.toFloat()))
            program.setFloatUniform("uTimeSec", presentationTimeUs / 1_000_000f)
            program.setIntUniform("uEffectMode", modeCode)
            program.setFloatUniform("uEffectIntensity", keyframedIntensity)

            program.setFloatUniform("uSharpenAmount", params.sharpenAmount)
            program.setFloatUniform("uRadius", params.radius)
            program.setFloatUniform("uThreshold", params.threshold)
            program.setFloatUniform("uGlowIntensity", params.glowIntensity)
            program.setFloatUniform("uHighlight", params.highlight)
            program.setFloatUniform("uShadow", params.shadow)
            program.setFloatsUniform("uRgbGain", floatArrayOf(params.redGain, params.greenGain, params.blueGain))
            program.setFloatUniform("uSaturation", params.saturation)
            program.setFloatUniform("uTexture", params.texture)
            program.setFloatUniform("uContrast", params.contrast)
            program.setFloatUniform("uBlurAmount", params.blurAmount)
            program.setFloatUniform("uDistortionAmount", params.distortionAmount)
            program.setFloatUniform("uLightingAmount", params.lightingAmount)
            program.setFloatUniform("uOpacity", keyframedOpacity)

            program.setIntUniform("uMaskShape", params.maskShape.ordinal)
            program.setFloatUniform("uMaskFeather", params.maskFeather)
            program.setFloatUniform("uMaskRadius", params.maskRadius)
            program.setIntUniform("uMaskInvert", if (params.maskInvert) 1 else 0)

            program.setIntUniform("uChromaEnabled", if (params.chromaKeyEnabled) 1 else 0)
            program.setFloatsUniform("uChromaColor", floatArrayOf(params.chromaKeyRed, params.chromaKeyGreen, params.chromaKeyBlue))
            program.setFloatUniform("uChromaSimilarity", params.chromaSimilarity)
            program.setFloatUniform("uChromaSmoothness", params.chromaSmoothness)
            program.setFloatUniform("uChromaSpill", params.chromaSpillSuppression)

            program.setIntUniform("uSplitEnabled", if (params.splitCompareEnabled) 1 else 0)
            program.setFloatUniform("uSplitPos", params.splitComparePosition)
            program.setIntUniform("uPluginActive", if (params.activePluginId != null) 1 else 0)

            program.bindAttributesAndUniforms()
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GlUtil.checkGlError()
        } catch (e: Exception) {
            throw VideoFrameProcessingException(e)
        }
    }

    override fun release() {
        super.release()
        runCatching { glProgram?.delete() }
        glProgram = null
    }
}

fun interpolateKeyframe(
    keyframes: List<KeyframePoint>,
    property: KeyframeProperty,
    timeMs: Long,
    fallback: Float
): Float {
    val points = keyframes.filter { it.property == property }.sortedBy { it.timeMs }
    if (points.isEmpty()) return fallback
    if (timeMs <= points.first().timeMs) return points.first().value
    if (timeMs >= points.last().timeMs) return points.last().value
    for (i in 0 until points.size - 1) {
        val a = points[i]
        val b = points[i + 1]
        if (timeMs in a.timeMs..b.timeMs) {
            val span = (b.timeMs - a.timeMs).coerceAtLeast(1L).toFloat()
            val t = ((timeMs - a.timeMs).toFloat() / span).coerceIn(0f, 1f)
            return a.value + (b.value - a.value) * t
        }
    }
    return fallback
}
