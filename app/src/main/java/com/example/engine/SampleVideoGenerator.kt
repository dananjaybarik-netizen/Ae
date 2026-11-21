package com.example.engine

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

/**
 * Generates a real playable H.264 MP4 calibration clip using MediaCodec + MediaMuxer
 * so users can immediately import, scrub, trim, split, run GPU shaders, and export
 * actual video frames even on a device with an empty gallery.
 */
object SampleVideoGenerator {

    suspend fun generateCalibrationMp4(
        context: Context,
        width: Int = 1280,
        height: Int = 720,
        durationSec: Int = 6,
        fps: Int = 30
    ): File = withContext(Dispatchers.IO) {
        val mediaDir = File(context.filesDir, "media").apply { mkdirs() }
        val outputFile = File(mediaDir, "ai_studio_calibration_${width}x${height}.mp4")
        if (outputFile.exists() && outputFile.length() > 10_000L) {
            return@withContext outputFile
        }

        val mime = MediaFormat.MIMETYPE_VIDEO_AVC
        val format = MediaFormat.createVideoFormat(mime, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, 4_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        val encoder = MediaCodec.createEncoderByType(mime)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val inputSurface = encoder.createInputSurface()
        encoder.start()

        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var trackIndex = -1
        var muxerStarted = false

        val bufferInfo = MediaCodec.BufferInfo()
        val totalFrames = durationSec * fps
        val frameDurationUs = 1_000_000L / fps

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(65, 255, 255, 255)
            strokeWidth = 2f
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = height * 0.055f
            isFakeBoldText = true
        }
        val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0, 229, 255)
            textSize = height * 0.032f
        }

        fun drainEncoder(endOfStream: Boolean) {
            if (endOfStream) {
                encoder.signalEndOfInputStream()
            }
            while (true) {
                val outIndex = encoder.dequeueOutputBuffer(bufferInfo, 10_000L)
                if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    if (!endOfStream) break
                } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    trackIndex = muxer.addTrack(encoder.outputFormat)
                    muxer.start()
                    muxerStarted = true
                } else if (outIndex >= 0) {
                    val encodedData = encoder.getOutputBuffer(outIndex)
                    if (encodedData != null && (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && bufferInfo.size > 0 && muxerStarted) {
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(trackIndex, encodedData, bufferInfo)
                    }
                    encoder.releaseOutputBuffer(outIndex, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                }
            }
        }

        try {
            for (frameIdx in 0 until totalFrames) {
                val t = frameIdx.toFloat() / fps.toFloat()
                val canvas: Canvas = inputSurface.lockCanvas(null)
                try {
                    val bgShader = LinearGradient(
                        0f, 0f, width.toFloat(), height.toFloat(),
                        intArrayOf(
                            Color.rgb(12, 18, 34),
                            Color.rgb(22, 16, 48),
                            Color.rgb(8, 32, 42)
                        ),
                        null,
                        Shader.TileMode.CLAMP
                    )
                    paint.shader = bgShader
                    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
                    paint.shader = null

                    // Crisp high-frequency calibration grid (ideal for testing Sharpen & Blur)
                    val step = width / 16f
                    var x = 0f
                    while (x < width) {
                        canvas.drawLine(x, 0f, x, height.toFloat(), gridPaint)
                        x += step
                    }
                    var y = 0f
                    while (y < height) {
                        canvas.drawLine(0f, y, width.toFloat(), y, gridPaint)
                        y += step
                    }

                    // Moving luminous cinema orbs (ideal for testing Glow & Lighting)
                    val cx = width * 0.5f + cos(t * 1.4f) * width * 0.25f
                    val cy = height * 0.48f + sin(t * 2.1f) * height * 0.18f
                    paint.color = Color.rgb(0, 229, 255)
                    canvas.drawCircle(cx, cy, height * 0.12f, paint)

                    paint.color = Color.rgb(140, 110, 255)
                    canvas.drawCircle(width - cx, height - cy, height * 0.09f, paint)

                    // Sharp checkerboard detail patch for edge-sharpen verification
                    val boxSize = height * 0.025f
                    val startX = width * 0.08f
                    val startY = height * 0.62f
                    for (r in 0 until 6) {
                        for (c in 0 until 10) {
                            paint.color = if ((r + c) % 2 == 0) Color.WHITE else Color.rgb(25, 30, 44)
                            canvas.drawRect(
                                startX + c * boxSize,
                                startY + r * boxSize,
                                startX + (c + 1) * boxSize,
                                startY + (r + 1) * boxSize,
                                paint
                            )
                        }
                    }

                    // Color bar strip for Color & Chroma testing
                    val barColors = intArrayOf(
                        Color.rgb(255, 75, 75),
                        Color.rgb(255, 195, 50),
                        Color.rgb(40, 220, 120),
                        Color.rgb(0, 229, 255),
                        Color.rgb(140, 110, 255),
                        Color.WHITE
                    )
                    val barW = (width * 0.45f) / barColors.size
                    barColors.forEachIndexed { idx, col ->
                        paint.color = col
                        canvas.drawRect(
                            width * 0.48f + idx * barW,
                            height * 0.72f,
                            width * 0.48f + (idx + 1) * barW - 4f,
                            height * 0.84f,
                            paint
                        )
                    }

                    canvas.drawText("AI STUDIO // GPU CALIBRATION", width * 0.08f, height * 0.16f, textPaint)
                    canvas.drawText(
                        String.format("TC %02d:%02d | %dx%d @ %dfps", frameIdx / fps, frameIdx % fps, width, height, fps),
                        width * 0.08f,
                        height * 0.24f,
                        subTextPaint
                    )
                } finally {
                    inputSurface.unlockCanvasAndPost(canvas)
                }
                // Note: Surface input timestamps follow presentation via drain
                drainEncoder(false)
            }
            drainEncoder(true)
        } finally {
            runCatching { encoder.stop() }
            runCatching { encoder.release() }
            runCatching { inputSurface.release() }
            if (muxerStarted) runCatching { muxer.stop() }
            runCatching { muxer.release() }
        }
        outputFile
    }
}
