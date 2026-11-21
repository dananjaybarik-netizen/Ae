package com.example

import com.example.engine.GpuShaderLibrary
import com.example.engine.interpolateKeyframe
import com.example.model.EffectCategory
import com.example.model.EffectsCatalog
import com.example.model.KeyframePoint
import com.example.model.KeyframeProperty
import com.example.model.ShaderEffectParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiStudioEngineTest {

    @Test
    fun `effects catalog includes all 6 categories and 32 effects`() {
        assertEquals(6, EffectCategory.entries.size)
        assertEquals(32, EffectsCatalog.allEffects.size)
        assertEquals(6, EffectsCatalog.byCategory(EffectCategory.SHARPEN).size)
        assertEquals(6, EffectsCatalog.byCategory(EffectCategory.GLOW).size)
        assertEquals(5, EffectsCatalog.byCategory(EffectCategory.BLUR).size)
        assertEquals(6, EffectsCatalog.byCategory(EffectCategory.COLOR).size)
        assertEquals(5, EffectsCatalog.byCategory(EffectCategory.DISTORTION).size)
        assertEquals(4, EffectsCatalog.byCategory(EffectCategory.LIGHTING).size)
    }

    @Test
    fun `glsl sharpen shader uses zero-DC residual detail and independent uniforms`() {
        val frag = GpuShaderLibrary.buildFragmentShader()
        assertTrue(frag.contains("applyTrueSharpen"))
        assertTrue(frag.contains("vec3 detail = center - blur9;"))
        assertTrue(frag.contains("uniform float uSharpenAmount;"))
        assertTrue(frag.contains("uniform float uGlowIntensity;"))
        assertTrue(frag.contains("uniform float uThreshold;"))
        assertTrue(frag.contains("uniform float uRadius;"))
        assertTrue(frag.contains("uniform float uHighlight;"))
        assertTrue(frag.contains("uniform float uShadow;"))
        assertTrue(frag.contains("uniform vec3 uRgbGain;"))
        assertTrue(frag.contains("uniform float uSaturation;"))
        assertTrue(frag.contains("uniform float uTexture;"))
        assertTrue(frag.contains("uniform float uContrast;"))
    }

    @Test
    fun `custom plugin shader function injects cleanly into fragment pipeline`() {
        val customFn = """
            vec3 applyPluginEffect(vec3 color, vec2 uv, float intensity, float time) {
                return clamp(color * (1.0 + intensity * 0.2), 0.0, 1.0);
            }
        """.trimIndent()
        val frag = GpuShaderLibrary.buildFragmentShader(customFn)
        assertTrue(frag.contains("return clamp(color * (1.0 + intensity * 0.2), 0.0, 1.0);"))
    }

    @Test
    fun `keyframe interpolation smoothly interpolates opacity over time`() {
        val kfs = listOf(
            KeyframePoint(timeMs = 0L, property = KeyframeProperty.OPACITY, value = 0.0f),
            KeyframePoint(timeMs = 1000L, property = KeyframeProperty.OPACITY, value = 1.0f)
        )
        assertEquals(0.0f, interpolateKeyframe(kfs, KeyframeProperty.OPACITY, 0L, 1f), 0.001f)
        assertEquals(0.5f, interpolateKeyframe(kfs, KeyframeProperty.OPACITY, 500L, 1f), 0.001f)
        assertEquals(1.0f, interpolateKeyframe(kfs, KeyframeProperty.OPACITY, 1000L, 1f), 0.001f)
    }

    @Test
    fun `shader params detects active pixel processing accurately`() {
        val neutral = ShaderEffectParams()
        assertFalse(neutral.hasAnyActivePixelProcessing())

        val sharpened = neutral.copy(sharpenAmount = 0.65f)
        assertTrue(sharpened.hasAnyActivePixelProcessing())
    }
}
