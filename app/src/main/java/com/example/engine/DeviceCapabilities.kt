package com.example.engine

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import android.os.Build
import android.os.PowerManager
import com.example.model.ExportResolution
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DeviceHardwareReport(
    val totalRamGb: Float,
    val availableRamMb: Long,
    val isLowRam4GbProfile: Boolean,
    val gpuRenderer: String,
    val gpuVendor: String,
    val glEsVersion: String,
    val supportsVulkan: Boolean,
    val vulkanVersionLabel: String,
    val maxSupportedResolution: ExportResolution,
    val supportedFpsOptions: List<Int>,
    val supportsHevcEncode: Boolean,
    val thermalStatusLabel: String,
    val thermalLevel: Int
)

object DeviceCapabilities {

    suspend fun inspect(context: Context): DeviceHardwareReport = withContext(Dispatchers.Default) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)

        val totalRamGb = memInfo.totalMem / (1024f * 1024f * 1024f)
        val availRamMb = memInfo.availMem / (1024L * 1024L)
        val isLowRam = am.isLowRamDevice || totalRamGb <= 4.3f

        val glInfo = queryEglInfo()

        val pm = context.packageManager
        val hasVulkan = pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL)
        val vulkanLabel = if (hasVulkan) "Vulkan 1.1+ Hardware" else "OpenGL ES 3.0 Fallback"

        val codecCaps = queryVideoEncoderCaps()

        val thermalLevel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            power.currentThermalStatus
        } else {
            0
        }
        val thermalLabel = when (thermalLevel) {
            0 -> "Nominal (Optimal)"
            1 -> "Light Load"
            2 -> "Moderate (Throttling Ready)"
            3 -> "Severe (Thermal Protection)"
            else -> "Nominal"
        }

        DeviceHardwareReport(
            totalRamGb = totalRamGb,
            availableRamMb = availRamMb,
            isLowRam4GbProfile = isLowRam,
            gpuRenderer = glInfo.first,
            gpuVendor = glInfo.second,
            glEsVersion = glInfo.third,
            supportsVulkan = hasVulkan,
            vulkanVersionLabel = vulkanLabel,
            maxSupportedResolution = codecCaps.first,
            supportedFpsOptions = codecCaps.second,
            supportsHevcEncode = codecCaps.third,
            thermalStatusLabel = thermalLabel,
            thermalLevel = thermalLevel
        )
    }

    private fun queryEglInfo(): Triple<String, String, String> {
        var dpy = EGL14.EGL_NO_DISPLAY
        var ctx = EGL14.EGL_NO_CONTEXT
        var surf = EGL14.EGL_NO_SURFACE
        return try {
            dpy = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (dpy == EGL14.EGL_NO_DISPLAY) {
                return Triple("Hardware GPU", "Android EGL", "OpenGL ES 3.0")
            }
            val vers = IntArray(2)
            if (!EGL14.eglInitialize(dpy, vers, 0, vers, 1)) {
                return Triple("Hardware GPU", "Android EGL", "OpenGL ES 3.0")
            }
            val configAttrib = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfig = IntArray(1)
            if (!EGL14.eglChooseConfig(dpy, configAttrib, 0, configs, 0, 1, numConfig, 0) || numConfig[0] <= 0) {
                return Triple("Hardware GPU", "Android EGL", "OpenGL ES 3.0")
            }
            val config = configs[0] ?: return Triple("Hardware GPU", "Android EGL", "OpenGL ES 3.0")
            val ctxAttrib = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            ctx = EGL14.eglCreateContext(dpy, config, EGL14.EGL_NO_CONTEXT, ctxAttrib, 0)
            if (ctx == EGL14.EGL_NO_CONTEXT) {
                return Triple("Hardware GPU", "Android EGL", "OpenGL ES 3.0")
            }
            val surfAttrib = intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE)
            surf = EGL14.eglCreatePbufferSurface(dpy, config, surfAttrib, 0)
            if (surf == EGL14.EGL_NO_SURFACE) {
                return Triple("Hardware GPU", "Android EGL", "OpenGL ES 3.0")
            }
            if (!EGL14.eglMakeCurrent(dpy, surf, surf, ctx) ||
                EGL14.eglGetCurrentContext() == EGL14.EGL_NO_CONTEXT
            ) {
                return Triple("Hardware GPU", "Android EGL", "OpenGL ES 3.0")
            }

            val renderer = GLES20.glGetString(GLES20.GL_RENDERER) ?: "Mobile GPU"
            val vendor = GLES20.glGetString(GLES20.GL_VENDOR) ?: "GPU Vendor"
            val version = GLES20.glGetString(GLES20.GL_VERSION) ?: "OpenGL ES 3.0"
            Triple(renderer, vendor, version)
        } catch (_: Throwable) {
            Triple("Hardware GPU", "Android EGL", "OpenGL ES 3.0")
        } finally {
            if (dpy != EGL14.EGL_NO_DISPLAY) {
                runCatching {
                    EGL14.eglMakeCurrent(dpy, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                }
                if (surf != EGL14.EGL_NO_SURFACE) runCatching { EGL14.eglDestroySurface(dpy, surf) }
                if (ctx != EGL14.EGL_NO_CONTEXT) runCatching { EGL14.eglDestroyContext(dpy, ctx) }
            }
        }
    }

    private fun queryVideoEncoderCaps(): Triple<ExportResolution, List<Int>, Boolean> {
        var maxRes = ExportResolution.P1080
        var maxFps = 60
        var hevcSupported = false
        try {
            val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            for (info in codecList.codecInfos) {
                if (!info.isEncoder) continue
                for (type in info.supportedTypes) {
                    if (type.equals("video/hevc", ignoreCase = true)) {
                        hevcSupported = true
                    }
                    if (type.equals("video/avc", ignoreCase = true) || type.equals("video/hevc", ignoreCase = true)) {
                        val caps = runCatching { info.getCapabilitiesForType(type).videoCapabilities }.getOrNull()
                        if (caps != null) {
                            if (caps.isSizeSupported(3840, 2160)) {
                                maxRes = ExportResolution.UHD_4K
                            } else if (caps.isSizeSupported(2560, 1440) && maxRes.width < 2560) {
                                maxRes = ExportResolution.P1440
                            }
                            val upperFps = caps.supportedFrameRates.upper
                            if (upperFps >= 120) maxFps = 120
                        }
                    }
                }
            }
        } catch (_: Throwable) {
            // Keep safe defaults
        }
        val fpsList = buildList {
            add(30)
            add(60)
            if (maxFps >= 120) add(120)
        }
        return Triple(maxRes, fpsList, hevcSupported)
    }

    fun queryLiveRamMb(context: Context): Pair<Long, Long> {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return (info.availMem / (1024L * 1024L)) to (info.totalMem / (1024L * 1024L))
    }
}
