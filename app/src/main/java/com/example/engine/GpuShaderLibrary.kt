package com.example.engine

import com.example.model.EffectsCatalog
import com.example.model.MaskShape

/**
 * Generates authentic OpenGL ES 2.0/3.0 GLSL vertex & fragment shaders
 * for pixel-level GPU processing inside both Media3 ExoPlayer preview
 * and Media3 Transformer hardware export, plus standalone GLSurfaceView preview.
 */
object GpuShaderLibrary {

    val STANDARD_VERTEX_SHADER = """
        attribute vec4 aFramePosition;
        varying vec2 vTexSamplingCoord;
        void main() {
            gl_Position = aFramePosition;
            vTexSamplingCoord = (aFramePosition.xy + 1.0) * 0.5;
        }
    """.trimIndent()

    fun buildFragmentShader(customPluginFunctionGlsl: String? = null): String {
        val pluginFn = customPluginFunctionGlsl?.takeIf { it.contains("applyPluginEffect") }
            ?: """
                vec3 applyPluginEffect(vec3 color, vec2 uv, float intensity, float time) {
                    return color;
                }
            """.trimIndent()

        return """
            precision highp float;
            varying vec2 vTexSamplingCoord;
            uniform sampler2D uTexSampler;

            uniform vec2 uTexelSize;
            uniform float uTimeSec;
            uniform int uEffectMode;
            uniform float uEffectIntensity;

            // Independent Master Pixel Controls
            uniform float uSharpenAmount;
            uniform float uRadius;
            uniform float uThreshold;
            uniform float uGlowIntensity;
            uniform float uHighlight;
            uniform float uShadow;
            uniform vec3 uRgbGain;
            uniform float uSaturation;
            uniform float uTexture;
            uniform float uContrast;
            uniform float uBlurAmount;
            uniform float uDistortionAmount;
            uniform float uLightingAmount;
            uniform float uOpacity;

            // Masking
            uniform int uMaskShape; // 0=None,1=Radial,2=Linear,3=Rect,4=Ellipse
            uniform float uMaskFeather;
            uniform float uMaskRadius;
            uniform int uMaskInvert;

            // Chroma Key
            uniform int uChromaEnabled;
            uniform vec3 uChromaColor;
            uniform float uChromaSimilarity;
            uniform float uChromaSmoothness;
            uniform float uChromaSpill;

            // Before/After Compare
            uniform int uSplitEnabled;
            uniform float uSplitPos;
            uniform int uPluginActive;

            $pluginFn

            float luminance(vec3 c) {
                return dot(c, vec3(0.2126, 0.7152, 0.0722));
            }

            vec3 rgb2hsl(vec3 c) {
                float maxC = max(max(c.r, c.g), c.b);
                float minC = min(min(c.r, c.g), c.b);
                float l = (maxC + minC) * 0.5;
                float s = 0.0;
                float h = 0.0;
                if (maxC != minC) {
                    float d = maxC - minC;
                    s = l > 0.5 ? d / (2.0 - maxC - minC) : d / (maxC + minC);
                    if (maxC == c.r) h = (c.g - c.b) / d + (c.g < c.b ? 6.0 : 0.0);
                    else if (maxC == c.g) h = (c.b - c.r) / d + 2.0;
                    else h = (c.r - c.g) / d + 4.0;
                    h /= 6.0;
                }
                return vec3(h, s, l);
            }

            float hue2rgb(float p, float q, float t) {
                if (t < 0.0) t += 1.0;
                if (t > 1.0) t -= 1.0;
                if (t < 1.0/6.0) return p + (q - p) * 6.0 * t;
                if (t < 0.5) return q;
                if (t < 2.0/3.0) return p + (q - p) * (2.0/3.0 - t) * 6.0;
                return p;
            }

            vec3 hsl2rgb(vec3 hsl) {
                if (hsl.y == 0.0) return vec3(hsl.z);
                float q = hsl.z < 0.5 ? hsl.z * (1.0 + hsl.y) : hsl.z + hsl.y - hsl.z * hsl.y;
                float p = 2.0 * hsl.z - q;
                return vec3(
                    hue2rgb(p, q, hsl.x + 1.0/3.0),
                    hue2rgb(p, q, hsl.x),
                    hue2rgb(p, q, hsl.x - 1.0/3.0)
                );
            }

            vec3 sampleClamped(vec2 uv) {
                return texture2D(uTexSampler, clamp(uv, vec2(0.001), vec2(0.999))).rgb;
            }

            // True zero-DC detail sharpen (never increases flat-area brightness)
            vec3 applyTrueSharpen(vec3 center, vec2 uv, float amount, float radiusPx, float thresh, int mode) {
                vec2 stepUv = uTexelSize * max(radiusPx, 0.5);
                vec3 n  = sampleClamped(uv + vec2(0.0, stepUv.y));
                vec3 s  = sampleClamped(uv - vec2(0.0, stepUv.y));
                vec3 e  = sampleClamped(uv + vec2(stepUv.x, 0.0));
                vec3 w  = sampleClamped(uv - vec2(stepUv.x, 0.0));
                vec3 ne = sampleClamped(uv + vec2(stepUv.x, stepUv.y));
                vec3 nw = sampleClamped(uv + vec2(-stepUv.x, stepUv.y));
                vec3 se = sampleClamped(uv + vec2(stepUv.x, -stepUv.y));
                vec3 sw = sampleClamped(uv + vec2(-stepUv.x, -stepUv.y));

                vec3 blur9 = (n + s + e + w) * 0.15 + (ne + nw + se + sw) * 0.10;
                vec3 detail = center - blur9; // Strictly zero-mean high-pass edge residual

                float edgeEnergy = length(detail);
                float gate = smoothstep(thresh * 0.22, thresh * 0.22 + 0.05, edgeEnergy);

                if (mode == ${EffectsCatalog.MODE_EDGE_SHARPEN}) {
                    // Sobel gradient directional edge weighting
                    vec3 gx = (ne + 2.0 * e + se) - (nw + 2.0 * w + sw);
                    vec3 gy = (nw + 2.0 * n + ne) - (sw + 2.0 * s + se);
                    float sobel = clamp(length(gx) + length(gy), 0.0, 1.5);
                    return clamp(center + detail * amount * 2.4 * sobel * gate, 0.0, 1.0);
                } else if (mode == ${EffectsCatalog.MODE_HIGH_PASS_SHARPEN}) {
                    // High-pass overlay keeping average luminance locked
                    vec3 hp = clamp(vec3(0.5) + detail * 1.8, 0.0, 1.0);
                    vec3 overlaid = mix(
                        2.0 * center * hp,
                        1.0 - 2.0 * (1.0 - center) * (1.0 - hp),
                        step(vec3(0.5), center)
                    );
                    return clamp(mix(center, overlaid, clamp(amount * 0.65 * gate, 0.0, 1.0)), 0.0, 1.0);
                } else if (mode == ${EffectsCatalog.MODE_TEXTURE_SHARPEN}) {
                    // Mid-frequency micro-contrast compression (suppresses extreme clipping)
                    vec3 softDetail = detail / (1.0 + abs(detail) * 4.0);
                    return clamp(center + softDetail * amount * 3.0 * gate, 0.0, 1.0);
                } else {
                    // Professional Sharpen / Unsharp Mask / Detail Enhancement
                    return clamp(center + detail * amount * 2.2 * gate, 0.0, 1.0);
                }
            }

            vec3 applyBlurKernel(vec2 uv, float strength, float radiusPx, int mode) {
                float r = max(radiusPx, 1.0) * strength * 2.5;
                vec3 acc = vec3(0.0);
                if (mode == ${EffectsCatalog.MODE_DIRECTIONAL_BLUR} || mode == ${EffectsCatalog.MODE_MOTION_BLUR}) {
                    vec2 dir = (mode == ${EffectsCatalog.MODE_MOTION_BLUR}) ? vec2(1.0, 0.25) : vec2(1.0, 0.0);
                    for (int i = -4; i <= 4; i++) {
                        acc += sampleClamped(uv + dir * uTexelSize * r * float(i) * 0.5);
                    }
                    return acc / 9.0;
                } else if (mode == ${EffectsCatalog.MODE_ZOOM_BLUR} || mode == ${EffectsCatalog.MODE_RADIAL_BLUR}) {
                    vec2 center = vec2(0.5, 0.5);
                    vec2 delta = uv - center;
                    for (int i = 0; i < 8; i++) {
                        float fi = float(i) / 7.0;
                        vec2 offset = (mode == ${EffectsCatalog.MODE_RADIAL_BLUR})
                            ? vec2(-delta.y, delta.x) * (fi - 0.5) * 0.12 * strength
                            : -delta * fi * 0.14 * strength;
                        acc += sampleClamped(uv + offset);
                    }
                    return acc / 8.0;
                } else {
                    // 9-tap Gaussian Blur
                    vec2 off = uTexelSize * r;
                    acc += sampleClamped(uv) * 0.20;
                    acc += sampleClamped(uv + vec2(off.x, 0.0)) * 0.12;
                    acc += sampleClamped(uv - vec2(off.x, 0.0)) * 0.12;
                    acc += sampleClamped(uv + vec2(0.0, off.y)) * 0.12;
                    acc += sampleClamped(uv - vec2(0.0, off.y)) * 0.12;
                    acc += sampleClamped(uv + vec2(off.x, off.y)) * 0.08;
                    acc += sampleClamped(uv + vec2(-off.x, off.y)) * 0.08;
                    acc += sampleClamped(uv + vec2(off.x, -off.y)) * 0.08;
                    acc += sampleClamped(uv + vec2(-off.x, -off.y)) * 0.08;
                    return acc;
                }
            }

            vec3 applyGlowKernel(vec3 baseColor, vec2 uv, float glowAmt, float radiusPx, float thresh, int mode) {
                vec2 off = uTexelSize * max(radiusPx, 1.5) * 2.2;
                vec3 bloom = vec3(0.0);
                for (int x = -1; x <= 1; x++) {
                    for (int y = -1; y <= 1; y++) {
                        vec3 s = sampleClamped(uv + vec2(float(x), float(y)) * off);
                        float l = luminance(s);
                        float mask = smoothstep(thresh, min(thresh + 0.35, 1.0), l);
                        bloom += s * mask;
                    }
                }
                bloom /= 9.0;
                if (mode == ${EffectsCatalog.MODE_NEON_GLOW}) {
                    bloom *= vec3(0.75, 1.25, 1.35);
                } else if (mode == ${EffectsCatalog.MODE_AURA_GLOW}) {
                    bloom *= vec3(1.15, 0.90, 1.30);
                } else if (mode == ${EffectsCatalog.MODE_DEEP_GLOW}) {
                    bloom = pow(bloom, vec3(0.82)) * 1.25;
                }
                return clamp(baseColor + bloom * glowAmt, 0.0, 1.0);
            }

            float computeMaskAlpha(vec2 uv) {
                if (uMaskShape == 0) return 1.0;
                vec2 p = uv - vec2(0.5);
                float dist = 0.0;
                if (uMaskShape == 1) { // Radial
                    dist = length(p) / max(uMaskRadius * 0.7, 0.05);
                } else if (uMaskShape == 2) { // Linear
                    dist = abs(p.y) / max(uMaskRadius * 0.5, 0.05);
                } else if (uMaskShape == 3) { // Rectangle
                    vec2 d = abs(p) / max(uMaskRadius * 0.5, 0.05);
                    dist = max(d.x, d.y);
                } else { // Ellipse
                    vec2 d = vec2(p.x * 0.85, p.y * 1.15) / max(uMaskRadius * 0.5, 0.05);
                    dist = length(d);
                }
                float feather = max(uMaskFeather * 0.6, 0.01);
                float alpha = 1.0 - smoothstep(1.0 - feather, 1.0 + feather, dist);
                return (uMaskInvert == 1) ? (1.0 - alpha) : alpha;
            }

            void main() {
                vec2 uv = vTexSamplingCoord;
                vec3 original = sampleClamped(uv);

                // Split-screen Before/After comparison (left = original, right = GPU processed)
                if (uSplitEnabled == 1 && uv.x < uSplitPos) {
                    if (abs(uv.x - uSplitPos) < uTexelSize.x * 2.0) {
                        gl_FragColor = vec4(0.0, 0.9, 1.0, 1.0);
                    } else {
                        gl_FragColor = vec4(original, 1.0);
                    }
                    return;
                }

                // 1. Distortion UV sampling
                vec2 workUv = uv;
                vec3 color = original;
                float distAmt = uDistortionAmount;
                if (uEffectMode >= 40 && uEffectMode <= 49) {
                    distAmt = max(distAmt, uEffectIntensity);
                }
                if (distAmt > 0.001) {
                    if (uEffectMode == ${EffectsCatalog.MODE_WAVE_WARP}) {
                        workUv.x += sin(uv.y * 18.0 + uTimeSec * 4.0) * 0.025 * distAmt;
                        workUv.y += cos(uv.x * 14.0 + uTimeSec * 3.0) * 0.015 * distAmt;
                        color = sampleClamped(workUv);
                    } else if (uEffectMode == ${EffectsCatalog.MODE_LENS_DISTORTION}) {
                        vec2 centered = uv - 0.5;
                        float r2 = dot(centered, centered);
                        workUv = 0.5 + centered * (1.0 + r2 * 0.85 * distAmt);
                        color = sampleClamped(workUv);
                    } else if (uEffectMode == ${EffectsCatalog.MODE_GLITCH}) {
                        float band = floor(uv.y * 28.0);
                        float jitter = fract(sin(band * 91.7 + floor(uTimeSec * 14.0) * 17.3) * 43758.5453);
                        float shift = (jitter > 0.72) ? (jitter - 0.85) * 0.14 * distAmt : 0.0;
                        float r = sampleClamped(vec2(uv.x + shift + 0.012 * distAmt, uv.y)).r;
                        float g = sampleClamped(vec2(uv.x + shift, uv.y)).g;
                        float b = sampleClamped(vec2(uv.x + shift - 0.012 * distAmt, uv.y)).b;
                        color = vec3(r, g, b);
                    } else {
                        // RGB Split / Chromatic Aberration
                        vec2 dir = (uEffectMode == ${EffectsCatalog.MODE_CHROMATIC_ABERRATION}) ? (uv - 0.5) * 0.035 : vec2(0.018, 0.006);
                        float r = sampleClamped(uv + dir * distAmt).r;
                        float g = sampleClamped(uv).g;
                        float b = sampleClamped(uv - dir * distAmt).b;
                        color = vec3(r, g, b);
                    }
                }

                // 2. Blur Pass
                float effBlur = uBlurAmount;
                if (uEffectMode >= 20 && uEffectMode <= 29) {
                    effBlur = max(effBlur, uEffectIntensity);
                }
                if (effBlur > 0.001) {
                    color = mix(color, applyBlurKernel(workUv, effBlur, uRadius, uEffectMode), clamp(effBlur, 0.0, 1.0));
                }

                // 3. True Edge Sharpen & Texture Micro-Contrast Pass
                float effSharp = uSharpenAmount;
                if (uEffectMode >= 1 && uEffectMode <= 9) {
                    effSharp = max(effSharp, uEffectIntensity);
                }
                if (effSharp > 0.001 || abs(uTexture) > 0.001) {
                    float combinedSharp = max(0.0, effSharp + max(0.0, uTexture) * 0.75);
                    if (combinedSharp > 0.001) {
                        int sMode = (uEffectMode >= 1 && uEffectMode <= 9) ? uEffectMode : ${EffectsCatalog.MODE_PRO_SHARPEN};
                        color = applyTrueSharpen(color, workUv, combinedSharp, uRadius, uThreshold, sMode);
                    }
                    if (uTexture < -0.001) {
                        vec3 smoothSkin = applyBlurKernel(workUv, -uTexture * 0.35, 1.5, ${EffectsCatalog.MODE_GAUSSIAN_BLUR});
                        color = mix(color, smoothSkin, clamp(-uTexture * 0.6, 0.0, 0.7));
                    }
                }

                // 4. Independent Color Grading Controls (Highlight, Shadow, RGB, Saturation, Contrast)
                float luma = luminance(color);
                if (abs(uShadow) > 0.001) {
                    float shadowMask = 1.0 - smoothstep(0.0, 0.55, luma);
                    color += vec3(uShadow * 0.25 * shadowMask);
                }
                if (abs(uHighlight) > 0.001) {
                    float highMask = smoothstep(0.45, 1.0, luma);
                    color += vec3(uHighlight * 0.25 * highMask);
                }
                color *= uRgbGain;
                color = (color - 0.5) * uContrast + 0.5;
                float newLuma = luminance(clamp(color, 0.0, 1.0));
                color = mix(vec3(newLuma), color, uSaturation);

                // Category D Color Modes
                if (uEffectMode >= 30 && uEffectMode <= 39 && uEffectIntensity > 0.001) {
                    if (uEffectMode == ${EffectsCatalog.MODE_HSL}) {
                        vec3 hsl = rgb2hsl(clamp(color, 0.0, 1.0));
                        hsl.y = clamp(hsl.y * (1.0 + uEffectIntensity * 0.5), 0.0, 1.0);
                        color = hsl2rgb(hsl);
                    } else if (uEffectMode == ${EffectsCatalog.MODE_COLOR_BALANCE}) {
                        vec3 warmCold = mix(color * vec3(0.90, 0.98, 1.12), color * vec3(1.10, 1.01, 0.90), smoothstep(0.2, 0.8, luma));
                        color = mix(color, warmCold, uEffectIntensity);
                    } else if (uEffectMode == ${EffectsCatalog.MODE_CURVES} || uEffectMode == ${EffectsCatalog.MODE_HDR_GRADING}) {
                        vec3 c01 = clamp(color, 0.0, 1.0);
                        vec3 filmic = c01 * c01 * (3.0 - 2.0 * c01);
                        if (uEffectMode == ${EffectsCatalog.MODE_HDR_GRADING}) {
                            filmic = filmic / (filmic + vec3(0.18)) * 1.18;
                        }
                        color = mix(color, filmic, uEffectIntensity);
                    } else {
                        vec3 vibrant = mix(vec3(newLuma), color, 1.0 + uEffectIntensity * 0.55);
                        color = vibrant;
                    }
                }
                color = clamp(color, 0.0, 1.0);

                // 5. Glow & Bloom Pass
                float effGlow = uGlowIntensity;
                if (uEffectMode >= 10 && uEffectMode <= 19) {
                    effGlow = max(effGlow, uEffectIntensity);
                }
                if (effGlow > 0.001) {
                    color = applyGlowKernel(color, workUv, effGlow, uRadius, max(uThreshold, 0.25), uEffectMode);
                }

                // 6. Lighting Pass (Light Rays, Lens Flare, Light Leaks, God Rays)
                float effLight = uLightingAmount;
                if (uEffectMode >= 50 && uEffectMode <= 59) {
                    effLight = max(effLight, uEffectIntensity);
                }
                if (effLight > 0.001) {
                    vec2 lightOrigin = vec2(0.72, 0.78);
                    vec2 toCenter = lightOrigin - workUv;
                    if (uEffectMode == ${EffectsCatalog.MODE_LIGHT_LEAKS}) {
                        float leak = smoothstep(0.75, 0.0, length(workUv - vec2(0.15, 0.85)));
                        color = clamp(color + vec3(1.0, 0.42, 0.12) * leak * effLight * 0.65, 0.0, 1.0);
                    } else if (uEffectMode == ${EffectsCatalog.MODE_LENS_FLARE}) {
                        float streak = exp(-abs(workUv.y - 0.55) * 16.0) * (1.0 - abs(workUv.x - 0.5) * 1.4);
                        float orb = exp(-length(workUv - vec2(0.32, 0.45)) * 7.0);
                        color = clamp(color + (vec3(0.2, 0.85, 1.0) * max(streak, 0.0) + vec3(1.0, 0.65, 0.25) * orb) * effLight * 0.7, 0.0, 1.0);
                    } else {
                        // Volumetric Light Rays / God Rays
                        vec3 rays = vec3(0.0);
                        for (int i = 0; i < 6; i++) {
                            vec2 rayUv = workUv + toCenter * (float(i) / 6.0) * 0.35;
                            vec3 rs = sampleClamped(rayUv);
                            rays += rs * smoothstep(0.55, 1.0, luminance(rs));
                        }
                        rays /= 6.0;
                        color = clamp(color + rays * vec3(1.0, 0.94, 0.82) * effLight * 0.85, 0.0, 1.0);
                    }
                }

                // 7. Sandboxed .aifx Plugin Shader Pass
                if (uPluginActive == 1) {
                    color = applyPluginEffect(color, workUv, max(uEffectIntensity, 0.5), uTimeSec);
                }

                // 8. Chroma Key Pass
                if (uChromaEnabled == 1) {
                    float dist = distance(color, uChromaColor);
                    float keyAlpha = smoothstep(uChromaSimilarity, uChromaSimilarity + max(uChromaSmoothness, 0.01), dist);
                    // Spill suppression on green/blue fringe
                    float maxRB = max(color.r, color.b);
                    if (uChromaColor.g > 0.5 && color.g > maxRB) {
                        color.g = mix(color.g, maxRB, uChromaSpill * (1.0 - keyAlpha * 0.5));
                    }
                    // Checkerboard/obsidian matte composite when keyed out
                    vec3 matte = vec3(0.03, 0.04, 0.06);
                    color = mix(matte, color, keyAlpha);
                }

                // 9. Geometric Masking & Keyframed Opacity
                float maskAlpha = computeMaskAlpha(workUv) * clamp(uOpacity, 0.0, 1.0);
                color *= maskAlpha;

                // Split line indicator
                if (uSplitEnabled == 1 && abs(uv.x - uSplitPos) < uTexelSize.x * 2.0) {
                    color = vec3(0.0, 0.9, 1.0);
                }

                gl_FragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
            }
        """.trimIndent()
    }
}
