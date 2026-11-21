package com.example.model

import kotlinx.serialization.Serializable

enum class EffectCategory(val displayName: String, val badgeCode: String) {
    SHARPEN("Sharpen", "SHP"),
    GLOW("Glow", "GLW"),
    BLUR("Blur", "BLR"),
    COLOR("Color", "CLR"),
    DISTORTION("Distortion", "DST"),
    LIGHTING("Lighting", "LGT")
}

enum class MaskShape(val displayName: String) {
    NONE("None"),
    RADIAL("Radial"),
    LINEAR("Linear"),
    RECTANGLE("Rectangle"),
    ELLIPSE("Ellipse")
}

enum class TransitionType(val displayName: String, val defaultDurationMs: Long = 600L) {
    NONE("None", 0L),
    CROSS_DISSOLVE("Cross Dissolve", 600L),
    DIP_TO_BLACK("Dip to Black", 700L),
    WIPE_LEFT("Wipe Left", 500L),
    ZOOM_SLIDE("Zoom Slide", 650L)
}

@Serializable
data class StudioEffectDefinition(
    val id: String,
    val name: String,
    val category: EffectCategory,
    val description: String,
    val shaderModeCode: Int,
    val defaultIntensity: Float = 0.65f,
    val supportsRadius: Boolean = true,
    val supportsThreshold: Boolean = true
)

@Serializable
data class ShaderEffectParams(
    val activeEffectId: String = "pro_sharpen",
    val activePluginId: String? = null,
    val effectIntensity: Float = 0.0f,
    // Independent Master Controls (Required by specification)
    val sharpenAmount: Float = 0.0f,     // 0.0 .. 2.0
    val radius: Float = 1.5f,            // 0.5 .. 8.0 px
    val threshold: Float = 0.08f,        // 0.0 .. 1.0
    val glowIntensity: Float = 0.0f,     // 0.0 .. 2.0
    val highlight: Float = 0.0f,         // -1.0 .. 1.0
    val shadow: Float = 0.0f,            // -1.0 .. 1.0
    val redGain: Float = 1.0f,           // 0.0 .. 2.0
    val greenGain: Float = 1.0f,         // 0.0 .. 2.0
    val blueGain: Float = 1.0f,          // 0.0 .. 2.0
    val saturation: Float = 1.0f,        // 0.0 .. 2.0
    val texture: Float = 0.0f,           // -1.0 .. 1.0
    val contrast: Float = 1.0f,          // 0.2 .. 2.0
    val blurAmount: Float = 0.0f,        // 0.0 .. 1.0
    val distortionAmount: Float = 0.0f,  // 0.0 .. 1.0
    val lightingAmount: Float = 0.0f,    // 0.0 .. 1.0
    // Masking
    val maskShape: MaskShape = MaskShape.NONE,
    val maskFeather: Float = 0.2f,       // 0.0 .. 1.0
    val maskRadius: Float = 0.65f,       // 0.1 .. 1.5
    val maskInvert: Boolean = false,
    // Chroma Key
    val chromaKeyEnabled: Boolean = false,
    val chromaKeyRed: Float = 0.0f,
    val chromaKeyGreen: Float = 1.0f,
    val chromaKeyBlue: Float = 0.0f,
    val chromaSimilarity: Float = 0.32f,
    val chromaSmoothness: Float = 0.12f,
    val chromaSpillSuppression: Float = 0.45f,
    // Before / After Wipe
    val splitCompareEnabled: Boolean = false,
    val splitComparePosition: Float = 0.5f,
    // Keyframed or static opacity
    val opacity: Float = 1.0f
) {
    fun hasAnyActivePixelProcessing(): Boolean {
        return effectIntensity > 0.001f ||
            sharpenAmount > 0.001f ||
            glowIntensity > 0.001f ||
            kotlin.math.abs(highlight) > 0.001f ||
            kotlin.math.abs(shadow) > 0.001f ||
            kotlin.math.abs(redGain - 1.0f) > 0.001f ||
            kotlin.math.abs(greenGain - 1.0f) > 0.001f ||
            kotlin.math.abs(blueGain - 1.0f) > 0.001f ||
            kotlin.math.abs(saturation - 1.0f) > 0.001f ||
            kotlin.math.abs(texture) > 0.001f ||
            kotlin.math.abs(contrast - 1.0f) > 0.001f ||
            blurAmount > 0.001f ||
            distortionAmount > 0.001f ||
            lightingAmount > 0.001f ||
            maskShape != MaskShape.NONE ||
            chromaKeyEnabled ||
            activePluginId != null ||
            opacity < 0.999f
    }
}

object EffectsCatalog {
    // Shader Mode Codes mapped directly in GLSL fragment shader
    const val MODE_NONE = 0
    const val MODE_PRO_SHARPEN = 1
    const val MODE_UNSHARP_MASK = 2
    const val MODE_HIGH_PASS_SHARPEN = 3
    const val MODE_TEXTURE_SHARPEN = 4
    const val MODE_EDGE_SHARPEN = 5
    const val MODE_DETAIL_ENHANCE = 6

    const val MODE_SOFT_GLOW = 10
    const val MODE_DEEP_GLOW = 11
    const val MODE_NEON_GLOW = 12
    const val MODE_BLOOM = 13
    const val MODE_AURA_GLOW = 14
    const val MODE_LIGHT_GLOW = 15

    const val MODE_GAUSSIAN_BLUR = 20
    const val MODE_DIRECTIONAL_BLUR = 21
    const val MODE_MOTION_BLUR = 22
    const val MODE_RADIAL_BLUR = 23
    const val MODE_ZOOM_BLUR = 24

    const val MODE_HSL = 30
    const val MODE_RGB_MIXER = 31
    const val MODE_CHROMA_CONTROLS = 32
    const val MODE_COLOR_BALANCE = 33
    const val MODE_CURVES = 34
    const val MODE_HDR_GRADING = 35

    const val MODE_RGB_SPLIT = 40
    const val MODE_CHROMATIC_ABERRATION = 41
    const val MODE_WAVE_WARP = 42
    const val MODE_LENS_DISTORTION = 43
    const val MODE_GLITCH = 44

    const val MODE_LIGHT_RAYS = 50
    const val MODE_LENS_FLARE = 51
    const val MODE_LIGHT_LEAKS = 52
    const val MODE_GOD_RAYS = 53

    val allEffects: List<StudioEffectDefinition> = listOf(
        // A. Sharpen
        StudioEffectDefinition("pro_sharpen", "Professional Sharpen", EffectCategory.SHARPEN, "Threshold-gated multi-tap luminance deconvolution kernel.", MODE_PRO_SHARPEN, 0.85f),
        StudioEffectDefinition("unsharp_mask", "Unsharp Mask", EffectCategory.SHARPEN, "Classic Gaussian subtraction unsharp mask with edge threshold.", MODE_UNSHARP_MASK, 0.8f),
        StudioEffectDefinition("high_pass", "High Pass Sharpen", EffectCategory.SHARPEN, "Isolates fine high-frequency detail via overlay blend.", MODE_HIGH_PASS_SHARPEN, 0.75f),
        StudioEffectDefinition("texture_sharpen", "Texture Sharpen", EffectCategory.SHARPEN, "Boosts mid-frequency micro-contrast without halo artifacts.", MODE_TEXTURE_SHARPEN, 0.8f),
        StudioEffectDefinition("edge_sharpen", "Edge Sharpen", EffectCategory.SHARPEN, "Sobel gradient-weighted crisp edge amplification.", MODE_EDGE_SHARPEN, 0.85f),
        StudioEffectDefinition("detail_enhance", "Detail Enhancement", EffectCategory.SHARPEN, "Adaptive local detail recovery across shadow & highlight ranges.", MODE_DETAIL_ENHANCE, 0.75f),

        // B. Glow
        StudioEffectDefinition("soft_glow", "Soft Glow", EffectCategory.GLOW, "Dreamy smooth highlight diffusion halo.", MODE_SOFT_GLOW, 0.7f),
        StudioEffectDefinition("deep_glow", "Deep Glow", EffectCategory.GLOW, "Exponential multi-radius HDR glow falloff.", MODE_DEEP_GLOW, 0.75f),
        StudioEffectDefinition("neon_glow", "Neon Glow", EffectCategory.GLOW, "Saturated emissive edge neon bloom.", MODE_NEON_GLOW, 0.8f),
        StudioEffectDefinition("bloom", "Bloom", EffectCategory.GLOW, "Physically inspired thresholded specular bloom.", MODE_BLOOM, 0.7f),
        StudioEffectDefinition("aura_glow", "Aura Glow", EffectCategory.GLOW, "Chromatic-shifted wide atmospheric aura.", MODE_AURA_GLOW, 0.75f),
        StudioEffectDefinition("light_glow", "Light Glow", EffectCategory.GLOW, "Crisp cinema pro-mist halation glow.", MODE_LIGHT_GLOW, 0.65f),

        // C. Blur
        StudioEffectDefinition("gaussian_blur", "Gaussian Blur", EffectCategory.BLUR, "Weighted 2D Gaussian convolution blur.", MODE_GAUSSIAN_BLUR, 0.6f, supportsThreshold = false),
        StudioEffectDefinition("directional_blur", "Directional Blur", EffectCategory.BLUR, "Linear vector streak blur kernel.", MODE_DIRECTIONAL_BLUR, 0.65f, supportsThreshold = false),
        StudioEffectDefinition("motion_blur", "Motion Blur", EffectCategory.BLUR, "Shutter-angle directional motion blur.", MODE_MOTION_BLUR, 0.65f, supportsThreshold = false),
        StudioEffectDefinition("radial_blur", "Radial Blur", EffectCategory.BLUR, "Rotational spin blur around frame center.", MODE_RADIAL_BLUR, 0.6f, supportsThreshold = false),
        StudioEffectDefinition("zoom_blur", "Zoom Blur", EffectCategory.BLUR, "Focal radial burst blur toward center.", MODE_ZOOM_BLUR, 0.65f, supportsThreshold = false),

        // D. Color
        StudioEffectDefinition("hsl", "HSL", EffectCategory.COLOR, "Perceptual Hue, Saturation & Luminance matrix.", MODE_HSL, 0.7f, supportsRadius = false),
        StudioEffectDefinition("rgb_mixer", "RGB Mixer", EffectCategory.COLOR, "Independent Red/Green/Blue channel gain matrix.", MODE_RGB_MIXER, 0.7f, supportsRadius = false),
        StudioEffectDefinition("chroma_controls", "Chroma Controls", EffectCategory.COLOR, "Selective vibrance & chroma density shaping.", MODE_CHROMA_CONTROLS, 0.75f, supportsRadius = false),
        StudioEffectDefinition("color_balance", "Color Balance", EffectCategory.COLOR, "Lift, Gamma, and Gain shadows/highlights toning.", MODE_COLOR_BALANCE, 0.7f, supportsRadius = false),
        StudioEffectDefinition("curves", "Curves", EffectCategory.COLOR, "Filmic S-curve contrast and toe/shoulder response.", MODE_CURVES, 0.7f, supportsRadius = false),
        StudioEffectDefinition("hdr_grading", "HDR-style Color Grading", EffectCategory.COLOR, "ACES-inspired highlight roll-off & deep micro-contrast.", MODE_HDR_GRADING, 0.8f),

        // E. Distortion
        StudioEffectDefinition("rgb_split", "RGB Split", EffectCategory.DISTORTION, "Horizontal/diagonal RGB channel UV separation.", MODE_RGB_SPLIT, 0.6f),
        StudioEffectDefinition("chromatic_aberration", "Chromatic Aberration", EffectCategory.DISTORTION, "Radial optical lens dispersion fringing.", MODE_CHROMATIC_ABERRATION, 0.65f),
        StudioEffectDefinition("wave_warp", "Wave Warp", EffectCategory.DISTORTION, "Animated sinusoidal UV displacement warp.", MODE_WAVE_WARP, 0.55f),
        StudioEffectDefinition("lens_distortion", "Lens Distortion", EffectCategory.DISTORTION, "Wide-angle barrel & pincushion optical warp.", MODE_LENS_DISTORTION, 0.6f),
        StudioEffectDefinition("glitch", "Glitch", EffectCategory.DISTORTION, "Time-seeded scanline jitter & digital block shift.", MODE_GLITCH, 0.65f),

        // F. Lighting
        StudioEffectDefinition("light_rays", "Light Rays", EffectCategory.LIGHTING, "Volumetric radial luminance ray marching.", MODE_LIGHT_RAYS, 0.7f),
        StudioEffectDefinition("lens_flare", "Lens Flare", EffectCategory.LIGHTING, "Anamorphic cyan-amber optical streak flare.", MODE_LENS_FLARE, 0.65f),
        StudioEffectDefinition("light_leaks", "Light Leaks", EffectCategory.LIGHTING, "Organic warm film gate light bleed.", MODE_LIGHT_LEAKS, 0.65f),
        StudioEffectDefinition("god_rays", "God Rays", EffectCategory.LIGHTING, "Thresholded crepuscular light shafts.", MODE_GOD_RAYS, 0.75f)
    )

    fun findById(id: String): StudioEffectDefinition =
        allEffects.firstOrNull { it.id == id } ?: allEffects.first()

    fun byCategory(category: EffectCategory): List<StudioEffectDefinition> =
        allEffects.filter { it.category == category }
}
