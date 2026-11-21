package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.data.db.PluginDao
import com.example.data.db.PluginEntity
import com.example.model.AifxCompatibility
import com.example.model.AifxManifest
import com.example.model.AifxParameter
import com.example.model.AifxPreset
import com.example.model.InstalledPlugin
import com.example.model.PluginCategory
import com.example.model.StorePluginListing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

sealed class PluginInstallResult {
    data class Success(val plugin: InstalledPlugin) : PluginInstallResult()
    data class Failure(val reason: String) : PluginInstallResult()
}

class PluginRepository(
    private val context: Context,
    private val pluginDao: PluginDao
) {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    private val forbiddenExtensions = setOf(".so", ".dex", ".jar", ".apk", ".sh", ".elf", ".bin", ".exe", ".dll")

    val installedPlugins: Flow<List<InstalledPlugin>> =
        pluginDao.observeInstalledPlugins().map { list ->
            list.mapNotNull { entity ->
                runCatching {
                    val manifest = json.decodeFromString<AifxManifest>(entity.manifestJson)
                    InstalledPlugin(
                        pluginId = entity.pluginId,
                        manifest = manifest,
                        fragmentShaderCode = entity.fragmentShaderCode,
                        signatureSha256 = entity.signatureSha256,
                        isSignatureVerified = entity.isSignatureVerified,
                        isEnabled = entity.isEnabled,
                        installPath = entity.installPath,
                        availableUpdateVersion = entity.availableUpdateVersion
                    )
                }.getOrNull()
            }
        }

    fun getStoreCatalog(installedIds: Set<String>, installedVersions: Map<String, Int>): List<StorePluginListing> {
        return bundledCatalog.map { item ->
            val installedCode = installedVersions[item.manifest.pluginId]
            StorePluginListing(
                manifest = item.manifest,
                fragmentShaderCode = item.fragmentShaderCode,
                badge = item.badge,
                isInstalled = installedIds.contains(item.manifest.pluginId),
                hasUpdate = installedCode != null && item.manifest.versionCode > installedCode
            )
        }
    }

    suspend fun installFromStore(listing: StorePluginListing): PluginInstallResult = withContext(Dispatchers.IO) {
        val manifestJson = json.encodeToString(listing.manifest)
        val presetsJson = json.encodeToString(listing.manifest.presets)
        val signature = computeSha256(manifestJson + listing.fragmentShaderCode + presetsJson)
        val validationError = validateGlslSandbox(listing.fragmentShaderCode)
        if (validationError != null) {
            return@withContext PluginInstallResult.Failure(validationError)
        }

        val pluginDir = File(context.filesDir, "plugins/${listing.manifest.pluginId}").apply { mkdirs() }
        File(pluginDir, "manifest.json").writeText(manifestJson)
        File(pluginDir, "shader.frag").writeText(listing.fragmentShaderCode)
        File(pluginDir, "presets.json").writeText(presetsJson)
        File(pluginDir, "signature.sha256").writeText(signature)

        val entity = PluginEntity(
            pluginId = listing.manifest.pluginId,
            manifestJson = manifestJson,
            fragmentShaderCode = listing.fragmentShaderCode,
            signatureSha256 = signature,
            isSignatureVerified = true,
            isEnabled = true,
            installPath = pluginDir.absolutePath,
            availableUpdateVersion = null
        )
        pluginDao.upsertPlugin(entity)
        PluginInstallResult.Success(
            InstalledPlugin(
                pluginId = entity.pluginId,
                manifest = listing.manifest,
                fragmentShaderCode = listing.fragmentShaderCode,
                signatureSha256 = signature,
                isSignatureVerified = true,
                isEnabled = true,
                installPath = entity.installPath
            )
        )
    }

    suspend fun importAifxPackage(uri: Uri): PluginInstallResult = withContext(Dispatchers.IO) {
        try {
            val entries = mutableMapOf<String, ByteArray>()
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ZipInputStream(stream).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val name = entry.name.lowercase()
                        // Security policy: NEVER accept or unpack native executable code or path traversal
                        if (name.contains("..") || forbiddenExtensions.any { name.endsWith(it) }) {
                            return@withContext PluginInstallResult.Failure(
                                "Security block: .aifx contains disallowed executable/binary entry '${entry.name}'. Native code execution is prohibited."
                            )
                        }
                        if (!entry.isDirectory) {
                            val bytes = zis.readBytes()
                            if (bytes.size > 128 * 1024) {
                                return@withContext PluginInstallResult.Failure("Package entry '${entry.name}' exceeds 128KB sandbox limit.")
                            }
                            entries[entry.name.substringAfterLast('/')] = bytes
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            } ?: return@withContext PluginInstallResult.Failure("Unable to open selected .aifx package.")

            val manifestBytes = entries["manifest.json"]
                ?: return@withContext PluginInstallResult.Failure("Invalid .aifx package: missing manifest.json.")
            val manifestText = manifestBytes.decodeToString()
            val manifest = json.decodeFromString<AifxManifest>(manifestText)

            val shaderBytes = entries[manifest.shaderEntry] ?: entries["shader.frag"]
                ?: return@withContext PluginInstallResult.Failure("Invalid .aifx package: missing GLSL shader '${manifest.shaderEntry}'.")
            val shaderCode = shaderBytes.decodeToString()

            val presetsText = entries["presets.json"]?.decodeToString()
                ?: json.encodeToString(manifest.presets)

            val glslError = validateGlslSandbox(shaderCode)
            if (glslError != null) {
                return@withContext PluginInstallResult.Failure(glslError)
            }

            val expectedDigest = computeSha256(manifestText + shaderCode + presetsText)
            val embeddedSig = entries["signature.sha256"]?.decodeToString()?.trim()
            val isVerified = embeddedSig != null && embeddedSig.equals(expectedDigest, ignoreCase = true)

            val pluginDir = File(context.filesDir, "plugins/${manifest.pluginId}").apply { mkdirs() }
            File(pluginDir, "manifest.json").writeText(manifestText)
            File(pluginDir, "shader.frag").writeText(shaderCode)
            File(pluginDir, "presets.json").writeText(presetsText)
            File(pluginDir, "signature.sha256").writeText(embeddedSig ?: expectedDigest)

            val entity = PluginEntity(
                pluginId = manifest.pluginId,
                manifestJson = manifestText,
                fragmentShaderCode = shaderCode,
                signatureSha256 = embeddedSig ?: expectedDigest,
                isSignatureVerified = isVerified,
                isEnabled = true,
                installPath = pluginDir.absolutePath
            )
            pluginDao.upsertPlugin(entity)

            PluginInstallResult.Success(
                InstalledPlugin(
                    pluginId = manifest.pluginId,
                    manifest = manifest,
                    fragmentShaderCode = shaderCode,
                    signatureSha256 = entity.signatureSha256,
                    isSignatureVerified = isVerified,
                    isEnabled = true,
                    installPath = pluginDir.absolutePath
                )
            )
        } catch (e: Exception) {
            PluginInstallResult.Failure("Failed to parse .aifx package: ${e.message ?: "Malformed archive"}")
        }
    }

    suspend fun exportSampleAifxPackage(): File = withContext(Dispatchers.IO) {
        val sample = bundledCatalog.first()
        val manifestText = json.encodeToString(sample.manifest)
        val presetsText = json.encodeToString(sample.manifest.presets)
        val sig = computeSha256(manifestText + sample.fragmentShaderCode + presetsText)

        val dir = File(context.filesDir, "plugins").apply { mkdirs() }
        val outFile = File(dir, "${sample.manifest.pluginId}_v${sample.manifest.version}.aifx")
        ZipOutputStream(FileOutputStream(outFile)).use { zos ->
            fun putText(name: String, content: String) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
            putText("manifest.json", manifestText)
            putText("shader.frag", sample.fragmentShaderCode)
            putText("presets.json", presetsText)
            putText("signature.sha256", sig)
        }
        outFile
    }

    suspend fun setPluginEnabled(pluginId: String, enabled: Boolean) {
        pluginDao.setPluginEnabled(pluginId, enabled)
    }

    suspend fun removePlugin(pluginId: String) = withContext(Dispatchers.IO) {
        pluginDao.deleteById(pluginId)
        File(context.filesDir, "plugins/$pluginId").deleteRecursively()
    }

    private fun validateGlslSandbox(shaderCode: String): String? {
        if (shaderCode.isBlank()) return "Empty GLSL shader payload."
        if (shaderCode.length > 65536) return "Shader exceeds 64KB instruction safety limit."
        if (!shaderCode.contains("applyPluginEffect")) {
            return "Shader missing required sandbox entry point 'vec3 applyPluginEffect(vec3 color, vec2 uv, float intensity, float time)'."
        }
        val openBraces = shaderCode.count { it == '{' }
        val closeBraces = shaderCode.count { it == '}' }
        if (openBraces == 0 || openBraces != closeBraces) {
            return "GLSL syntax error: unbalanced braces."
        }
        return null
    }

    private fun computeSha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private val bundledCatalog: List<StorePluginListing> = listOf(
        StorePluginListing(
            badge = "VERIFIED PRO",
            manifest = AifxManifest(
                pluginId = "com.aistudio.fx.halation_pro",
                name = "Anamorphic Halation 35mm",
                description = "Authentic 35mm celluloid red-orange halation scatter around bright specular highlights with cyan shadow rolloff.",
                author = "AI STUDIO Optics Lab",
                version = "1.4.0",
                versionCode = 14,
                category = PluginCategory.OPTICAL_GLOW,
                parameters = listOf(
                    AifxParameter("intensity", "Halation Scatter", 0f, 1.5f, 0.8f),
                    AifxParameter("threshold", "Highlight Knee", 0f, 1f, 0.55f)
                ),
                presets = listOf(
                    AifxPreset("Kodak 2383 Warm", mapOf("intensity" to 0.85f, "threshold" to 0.55f)),
                    AifxPreset("Night Neon", mapOf("intensity" to 1.2f, "threshold" to 0.42f))
                ),
                compatibility = AifxCompatibility(28, "3.0", true)
            ),
            fragmentShaderCode = """
                vec3 applyPluginEffect(vec3 color, vec2 uv, float intensity, float time) {
                    float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
                    float highlight = smoothstep(0.45, 0.95, luma);
                    vec3 halation = vec3(1.0, 0.34, 0.14) * highlight * intensity * 0.55;
                    vec3 shadowTint = mix(color, color * vec3(0.88, 0.98, 1.06), (1.0 - luma) * intensity * 0.35);
                    return clamp(shadowTint + halation, 0.0, 1.0);
                }
            """.trimIndent()
        ),
        StorePluginListing(
            badge = "NEW 2.1",
            manifest = AifxManifest(
                pluginId = "com.aistudio.fx.cyber_prism",
                name = "Cyber Prism Dispersion",
                description = "Spectral radial refraction dispersing cyan, magenta, and amber fringes across frame edges.",
                author = "Prism Shader Works",
                version = "2.1.0",
                versionCode = 21,
                category = PluginCategory.DISTORTION_FX,
                parameters = listOf(
                    AifxParameter("intensity", "Dispersion", 0f, 1.5f, 0.75f)
                ),
                presets = listOf(
                    AifxPreset("Subtle Glass", mapOf("intensity" to 0.4f)),
                    AifxPreset("Hyper Prism", mapOf("intensity" to 1.1f))
                ),
                compatibility = AifxCompatibility(28, "3.0", true)
            ),
            fragmentShaderCode = """
                vec3 applyPluginEffect(vec3 color, vec2 uv, float intensity, float time) {
                    vec2 centered = uv - 0.5;
                    float dist = length(centered);
                    float pulse = 0.5 + 0.5 * sin(time * 2.0 + dist * 10.0);
                    vec3 prism = vec3(
                        color.r * (1.0 + dist * 0.45 * intensity),
                        color.g * (1.0 - dist * 0.10 * intensity),
                        color.b * (1.0 + dist * 0.60 * intensity * pulse)
                    );
                    return clamp(mix(color, prism, clamp(intensity, 0.0, 1.0)), 0.0, 1.0);
                }
            """.trimIndent()
        ),
        StorePluginListing(
            badge = "4GB SAFE",
            manifest = AifxManifest(
                pluginId = "com.aistudio.fx.deconvolve_sharp",
                name = "Micro-Edge Deconvolver",
                description = "Zero-halo micro-contrast enhancer engineered for 4GB RAM mobile GPUs with single-pass edge precision.",
                author = "AI STUDIO Core",
                version = "1.2.0",
                versionCode = 12,
                category = PluginCategory.SHARPEN_DETAIL,
                parameters = listOf(
                    AifxParameter("intensity", "Micro-Contrast", 0f, 1.5f, 0.85f)
                ),
                presets = listOf(
                    AifxPreset("Crisp 4K", mapOf("intensity" to 0.85f)),
                    AifxPreset("Broadcast", mapOf("intensity" to 0.55f))
                ),
                compatibility = AifxCompatibility(28, "2.0", true)
            ),
            fragmentShaderCode = """
                vec3 applyPluginEffect(vec3 color, vec2 uv, float intensity, float time) {
                    float luma = dot(color, vec3(0.299, 0.587, 0.114));
                    float sCurve = luma * luma * (3.0 - 2.0 * luma);
                    float boost = (sCurve - luma) * intensity * 1.4;
                    return clamp(color + vec3(boost), 0.0, 1.0);
                }
            """.trimIndent()
        ),
        StorePluginListing(
            badge = "COLORIST",
            manifest = AifxManifest(
                pluginId = "com.aistudio.fx.teal_orange_lut",
                name = "Cinema Teal & Amber 2383",
                description = "Procedural split-tone film print emulation separating deep ocean shadows and warm skin/highlight tones.",
                author = "Studio Color Lab",
                version = "2.0.0",
                versionCode = 20,
                category = PluginCategory.CINEMATIC_GRADE,
                parameters = listOf(
                    AifxParameter("intensity", "Grade Mix", 0f, 1.2f, 0.8f)
                ),
                presets = listOf(
                    AifxPreset("Blockbuster", mapOf("intensity" to 0.85f)),
                    AifxPreset("Documentary", mapOf("intensity" to 0.45f))
                ),
                compatibility = AifxCompatibility(28, "2.0", true)
            ),
            fragmentShaderCode = """
                vec3 applyPluginEffect(vec3 color, vec2 uv, float intensity, float time) {
                    float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
                    vec3 shadows = vec3(0.06, 0.22, 0.28);
                    vec3 highs = vec3(1.06, 0.90, 0.72);
                    vec3 graded = mix(color * 0.85 + shadows * 0.35, color * highs, smoothstep(0.22, 0.82, luma));
                    return clamp(mix(color, graded, clamp(intensity, 0.0, 1.0)), 0.0, 1.0);
                }
            """.trimIndent()
        ),
        StorePluginListing(
            badge = "VFX LIGHT",
            manifest = AifxManifest(
                pluginId = "com.aistudio.fx.golden_flare",
                name = "Anamorphic Golden Streaks",
                description = "Horizontal cinema anamorphic lens flare streak synced to frame luminance and playback time.",
                author = "AI STUDIO Optics Lab",
                version = "1.3.0",
                versionCode = 13,
                category = PluginCategory.LIGHTING_VFX,
                parameters = listOf(
                    AifxParameter("intensity", "Streak Gain", 0f, 1.5f, 0.75f)
                ),
                presets = listOf(
                    AifxPreset("Sci-Fi Cyan", mapOf("intensity" to 0.75f)),
                    AifxPreset("Sunset Gold", mapOf("intensity" to 0.95f))
                ),
                compatibility = AifxCompatibility(28, "3.0", true)
            ),
            fragmentShaderCode = """
                vec3 applyPluginEffect(vec3 color, vec2 uv, float intensity, float time) {
                    float band = exp(-abs(uv.y - 0.5) * 18.0);
                    float sweep = 0.65 + 0.35 * sin(uv.x * 6.2831 + time * 1.5);
                    vec3 streak = vec3(0.25, 0.82, 1.0) * band * sweep * intensity * 0.45;
                    return clamp(color + streak, 0.0, 1.0);
                }
            """.trimIndent()
        )
    )
}
