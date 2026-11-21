package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.opengl.EGL14
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.model.EffectsCatalog
import com.example.model.ShaderEffectParams
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Interactive OpenGL ES 2.0/3.0 GLSurfaceView for real-time GLSL pixel shader preview
 * inside the Effects Library and .aifx Plugin Store.
 * Compiles and executes the exact same GpuShaderLibrary GLSL shaders on actual RGBA texture pixels!
 */
@Composable
fun GlShaderLivePreview(
    params: ShaderEffectParams,
    pluginGlslCode: String? = null,
    modifier: Modifier = Modifier
) {
    val renderer = remember(pluginGlslCode) {
        StandaloneGlPreviewRenderer(pluginGlslCode)
    }
    val glViewHolder = remember { arrayOfNulls<GLSurfaceView>(1) }
    renderer.updateParams(params)

    DisposableEffect(Unit) {
        onDispose {
            val view = glViewHolder[0]
            view?.queueEvent { renderer.releaseOnGlThread() }
            view?.onPause()
            glViewHolder[0] = null
        }
    }

    AndroidView(
        factory = { context ->
            GLSurfaceView(context).apply {
                setEGLContextClientVersion(2)
                setRenderer(renderer)
                renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                glViewHolder[0] = this
            }
        },
        update = { view ->
            glViewHolder[0] = view
            renderer.updateParams(params)
            view.requestRender()
        },
        modifier = modifier
    )
}

private class StandaloneGlPreviewRenderer(
    private val pluginGlslCode: String?
) : GLSurfaceView.Renderer {

    @Volatile
    private var currentParams = ShaderEffectParams()

    private var programId = 0
    private var textureId = 0
    private var viewWidth = 640
    private var viewHeight = 360
    private var startTimeNs = System.nanoTime()

    private val quadVertices = floatArrayOf(
        -1f, -1f, 0f, 1f,
         1f, -1f, 0f, 1f,
        -1f,  1f, 0f, 1f,
         1f,  1f, 0f, 1f
    )
    private val vertexBuffer: FloatBuffer = ByteBuffer
        .allocateDirect(quadVertices.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .put(quadVertices)
        .apply { position(0) }

    fun updateParams(params: ShaderEffectParams) {
        currentParams = params
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        val fragSource = GpuShaderLibrary.buildFragmentShader(pluginGlslCode)
        programId = compileProgram(GpuShaderLibrary.STANDARD_VERTEX_SHADER, fragSource)
        textureId = uploadReferenceCinemaBitmap()
        startTimeNs = System.nanoTime()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewWidth = width.coerceAtLeast(1)
        viewHeight = height.coerceAtLeast(1)
        GLES20.glViewport(0, 0, viewWidth, viewHeight)
    }

    override fun onDrawFrame(gl: GL10?) {
        if (programId == 0) return
        val params = currentParams
        val def = EffectsCatalog.findById(params.activeEffectId)
        val modeCode = if (params.hasAnyActivePixelProcessing()) def.shaderModeCode else EffectsCatalog.MODE_NONE
        val elapsedSec = (System.nanoTime() - startTimeNs) / 1_000_000_000f

        GLES20.glClearColor(0.03f, 0.04f, 0.06f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        GLES20.glUseProgram(programId)

        val posAttr = GLES20.glGetAttribLocation(programId, "aFramePosition")
        if (posAttr >= 0) {
            vertexBuffer.position(0)
            GLES20.glEnableVertexAttribArray(posAttr)
            GLES20.glVertexAttribPointer(posAttr, 4, GLES20.GL_FLOAT, false, 16, vertexBuffer)
        }

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        setUniform1i("uTexSampler", 0)
        setUniform2f("uTexelSize", 1f / 640f, 1f / 360f)
        setUniform1f("uTimeSec", elapsedSec)
        setUniform1i("uEffectMode", modeCode)
        setUniform1f("uEffectIntensity", params.effectIntensity)

        setUniform1f("uSharpenAmount", params.sharpenAmount)
        setUniform1f("uRadius", params.radius)
        setUniform1f("uThreshold", params.threshold)
        setUniform1f("uGlowIntensity", params.glowIntensity)
        setUniform1f("uHighlight", params.highlight)
        setUniform1f("uShadow", params.shadow)
        setUniform3f("uRgbGain", params.redGain, params.greenGain, params.blueGain)
        setUniform1f("uSaturation", params.saturation)
        setUniform1f("uTexture", params.texture)
        setUniform1f("uContrast", params.contrast)
        setUniform1f("uBlurAmount", params.blurAmount)
        setUniform1f("uDistortionAmount", params.distortionAmount)
        setUniform1f("uLightingAmount", params.lightingAmount)
        setUniform1f("uOpacity", params.opacity)

        setUniform1i("uMaskShape", params.maskShape.ordinal)
        setUniform1f("uMaskFeather", params.maskFeather)
        setUniform1f("uMaskRadius", params.maskRadius)
        setUniform1i("uMaskInvert", if (params.maskInvert) 1 else 0)

        setUniform1i("uChromaEnabled", if (params.chromaKeyEnabled) 1 else 0)
        setUniform3f("uChromaColor", params.chromaKeyRed, params.chromaKeyGreen, params.chromaKeyBlue)
        setUniform1f("uChromaSimilarity", params.chromaSimilarity)
        setUniform1f("uChromaSmoothness", params.chromaSmoothness)
        setUniform1f("uChromaSpill", params.chromaSpillSuppression)

        setUniform1i("uSplitEnabled", if (params.splitCompareEnabled) 1 else 0)
        setUniform1f("uSplitPos", params.splitComparePosition)
        setUniform1i("uPluginActive", if (params.activePluginId != null) 1 else 0)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        if (posAttr >= 0) GLES20.glDisableVertexAttribArray(posAttr)
    }

    fun releaseOnGlThread() {
        if (EGL14.eglGetCurrentContext() == EGL14.EGL_NO_CONTEXT) {
            programId = 0
            textureId = 0
            return
        }
        if (programId != 0) {
            runCatching { GLES20.glDeleteProgram(programId) }
            programId = 0
        }
        if (textureId != 0) {
            runCatching { GLES20.glDeleteTextures(1, intArrayOf(textureId), 0) }
            textureId = 0
        }
    }

    private fun setUniform1i(name: String, v: Int) {
        val loc = GLES20.glGetUniformLocation(programId, name)
        if (loc >= 0) GLES20.glUniform1i(loc, v)
    }

    private fun setUniform1f(name: String, v: Float) {
        val loc = GLES20.glGetUniformLocation(programId, name)
        if (loc >= 0) GLES20.glUniform1f(loc, v)
    }

    private fun setUniform2f(name: String, x: Float, y: Float) {
        val loc = GLES20.glGetUniformLocation(programId, name)
        if (loc >= 0) GLES20.glUniform2f(loc, x, y)
    }

    private fun setUniform3f(name: String, x: Float, y: Float, z: Float) {
        val loc = GLES20.glGetUniformLocation(programId, name)
        if (loc >= 0) GLES20.glUniform3f(loc, x, y, z)
    }

    private fun compileProgram(vert: String, frag: String): Int {
        val vs = loadShader(GLES20.GL_VERTEX_SHADER, vert)
        val fs = loadShader(GLES20.GL_FRAGMENT_SHADER, frag)
        val prog = GLES20.glCreateProgram()
        GLES20.glAttachShader(prog, vs)
        GLES20.glAttachShader(prog, fs)
        GLES20.glLinkProgram(prog)
        return prog
    }

    private fun loadShader(type: Int, code: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, code)
        GLES20.glCompileShader(shader)
        return shader
    }

    private fun uploadReferenceCinemaBitmap(): Int {
        val w = 640
        val h = 360
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(
            0f, 0f, w.toFloat(), h.toFloat(),
            intArrayOf(Color.rgb(16, 26, 48), Color.rgb(34, 22, 68), Color.rgb(12, 48, 58)),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.shader = null

        // High-frequency architectural skyline & radial lens target so Sharpen/Glow/Blur are immediately visible
        p.color = Color.rgb(0, 229, 255)
        canvas.drawCircle(w * 0.68f, h * 0.36f, 54f, p)
        p.color = Color.rgb(255, 185, 65)
        canvas.drawCircle(w * 0.28f, h * 0.42f, 36f, p)
        p.color = Color.WHITE
        p.strokeWidth = 2f
        for (i in 0..16) {
            val x = i * (w / 16f)
            canvas.drawLine(x, h * 0.6f, x, h.toFloat(), p)
        }
        // Fine micro-detail stripes
        p.color = Color.rgb(140, 110, 255)
        for (i in 0..14) {
            canvas.drawRect(32f + i * 14f, h * 0.65f, 38f + i * 14f, h * 0.90f, p)
        }
        p.color = Color.WHITE
        p.textSize = 26f
        p.isFakeBoldText = true
        canvas.drawText("GPU SHADER BENCH", 32f, 64f, p)

        val texIds = IntArray(1)
        GLES20.glGenTextures(1, texIds, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texIds[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
        bmp.recycle()
        return texIds[0]
    }
}
