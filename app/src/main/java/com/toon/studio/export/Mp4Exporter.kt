
package com.toon.studio.export

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.view.Surface
import com.toon.studio.command.plan.ProjectKind
import com.toon.studio.command.plan.SharedScenePlan
import java.io.File
import kotlin.math.ceil

object Mp4Exporter {

    /**
     * Exports a real 2D SharedScenePlan to an H.264 MP4 video.
     *
     * This overload uses PlanBasedVideoRenderer.
     * It does not use the hardcoded sample rabbit.
     *
     * 3D projects are rejected until a real 3D renderer is connected.
     * Audio is not included in this export yet.
     */
    fun export(
        plan: SharedScenePlan,
        output: File,
        width: Int? = null,
        height: Int? = null,
        fps: Int? = null,
        onProgress: (Float) -> Unit = {}
    ): PlanBasedVideoRenderer.RenderResult {

        require(plan.project.kind != ProjectKind.ANIMATION_3D) {
            "3D export bado haijaunganishwa. Mradi huu unahitaji 3D renderer."
        }

        require(plan.scenes.isNotEmpty()) {
            "Hakuna scenes za ku-export."
        }

        require((width == null) == (height == null)) {
            "Width na height lazima zitolewe pamoja."
        }

        val duration = PlanBasedVideoRenderer.durationSeconds(plan)

        require(duration.isFinite() && duration > 0.0) {
            "Muda wa video si sahihi au ni sifuri."
        }

        val size = PlanBasedVideoRenderer.recommendedSize(
            plan = plan,
            width = width,
            height = height
        )

        require(size.width > 0 && size.height > 0) {
            "Video dimensions si sahihi."
        }

        require(size.width % 2 == 0 && size.height % 2 == 0) {
            "Video width na height lazima ziwe namba zinazogawanyika kwa 2."
        }

        val frameRate = fps
            ?: plan.render.fps?.takeIf { it > 0 }
            ?: plan.project.fps.takeIf { it > 0 }
            ?: plan.timeline.fps.takeIf { it > 0 }
            ?: 30

        require(frameRate in 1..60) {
            "FPS lazima iwe kati ya 1 na 60."
        }

        val frameCount = ceil(duration * frameRate).toLong()

        require(frameCount in 1..Int.MAX_VALUE.toLong()) {
            "Idadi ya frames imezidi kiwango kinachoruhusiwa."
        }

        val parent = output.absoluteFile.parentFile
        if (parent != null && !parent.exists()) {
            check(parent.mkdirs()) {
                "Imeshindwa kutengeneza folder la kuhifadhi video."
            }
        }

        if (output.exists()) {
            check(output.delete()) {
                "Imeshindwa kufuta video ya zamani yenye jina hili."
            }
        }

        val bitRate = (
            size.width.toLong() *
                size.height.toLong() *
                frameRate / 6L
            ).coerceIn(2_000_000L, 20_000_000L).toInt()

        val format = MediaFormat.createVideoFormat(
            MediaFormat.MIMETYPE_VIDEO_AVC,
            size.width,
            size.height
        ).apply {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
            )
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        var codec: MediaCodec? = null
        var inputSurface: Surface? = null
        var muxer: MediaMuxer? = null

        var codecStarted = false
        var muxerStarted = false
        var completed = false

        try {
            val activeCodec = MediaCodec.createEncoderByType(
                MediaFormat.MIMETYPE_VIDEO_AVC
            )
            codec = activeCodec

            activeCodec.configure(
                format,
                null,
                null,
                MediaCodec.CONFIGURE_FLAG_ENCODE
            )

            val surface = activeCodec.createInputSurface()
            inputSurface = surface

            val activeMuxer = MediaMuxer(
                output.absolutePath,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            )
            muxer = activeMuxer

            activeCodec.start()
            codecStarted = true

            var videoTrack = -1
            var writtenFrames = 0L

            val bufferInfo = MediaCodec.BufferInfo()

            /**
             * Collects encoded H.264 output and writes it to the MP4 muxer.
             */
            fun drainEncoder(endOfStream: Boolean) {
                if (endOfStream) {
                    activeCodec.signalEndOfInputStream()
                }

                var idleAttempts = 0
                var endReached = false

                while (!endReached) {
                    val index = activeCodec.dequeueOutputBuffer(
                        bufferInfo,
                        10_000
                    )

                    when {
                        index == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            if (!endOfStream) {
                                return
                            }

                            idleAttempts++

                            check(idleAttempts < 6000) {
                                "Encoder haikukamilisha video kwa muda unaotarajiwa."
                            }
                        }

                        index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            check(!muxerStarted) {
                                "Encoder imebadilisha output format zaidi ya mara moja."
                            }

                            videoTrack = activeMuxer.addTrack(
                                activeCodec.outputFormat
                            )

                            activeMuxer.start()
                            muxerStarted = true
                        }

                        index >= 0 -> {
                            val flags = bufferInfo.flags
                            val bufferSize = bufferInfo.size
                            val encodedBuffer = activeCodec.getOutputBuffer(index)

                            if (
                                encodedBuffer != null &&
                                bufferSize > 0 &&
                                (flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0
                            ) {
                                check(muxerStarted && videoTrack >= 0) {
                                    "MP4 muxer haijaanza."
                                }

                                bufferInfo.presentationTimeUs =
                                    writtenFrames * 1_000_000L / frameRate

                                encodedBuffer.position(bufferInfo.offset)
                                encodedBuffer.limit(
                                    bufferInfo.offset + bufferSize
                                )

                                activeMuxer.writeSampleData(
                                    videoTrack,
                                    encodedBuffer,
                                    bufferInfo
                                )

                                writtenFrames++
                            }

                            activeCodec.releaseOutputBuffer(index, false)

                            if (
                                (flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                            ) {
                                endReached = true
                            }
                        }
                    }
                }
            }

            /**
             * Render every frame from the complete project timeline.
             */
            for (frameIndex in 0 until frameCount.toInt()) {
                val timeSeconds = frameIndex.toFloat() / frameRate

                val canvas = surface.lockCanvas(null)

                try {
                    val rendered = PlanBasedVideoRenderer.draw(
                        canvas = canvas,
                        plan = plan,
                        timeSeconds = timeSeconds,
                        width = size.width.toFloat(),
                        height = size.height.toFloat()
                    )

                    check(rendered) {
                        "Frame ya ${timeSeconds}s haikuweza kuchorwa. " +
                            "Kagua muda na mpangilio wa scenes."
                    }
                } finally {
                    surface.unlockCanvasAndPost(canvas)
                }

                drainEncoder(endOfStream = false)

                onProgress(
                    0.99f * (frameIndex + 1).toFloat() /
                        frameCount.toFloat()
                )
            }

            drainEncoder(endOfStream = true)

            check(muxerStarted && writtenFrames > 0L) {
                "Hakuna video frames zilizohifadhiwa kwenye MP4."
            }

            completed = true
            onProgress(1f)

            return PlanBasedVideoRenderer.RenderResult(
                success = true,
                message = "MP4 imetengenezwa kutoka kwenye SharedScenePlan.",
                durationSeconds = duration,
                frameRate = frameRate,
                size = size
            )

        } finally {
            try {
                if (codecStarted) {
                    codec?.stop()
                }
            } catch (_: Exception) {
                // Continue releasing the remaining resources.
            }

            try {
                codec?.release()
            } catch (_: Exception) {
            }

            try {
                inputSurface?.release()
            } catch (_: Exception) {
            }

            try {
                if (muxerStarted) {
                    muxer?.stop()
                }
            } catch (_: Exception) {
                // Continue with release.
            }

            try {
                muxer?.release()
            } catch (_: Exception) {
            }

            if (!completed && output.exists()) {
                output.delete()
            }
        }
    }

    /**
     * Legacy sample-video export.
     *
     * Kept temporarily so the existing SampleVideoScreen does not break.
     * Command-based projects should use export(plan, output, ...).
     */
    fun export(
        output: File,
        width: Int,
        height: Int,
        fps: Int = 30,
        onProgress: (Float) -> Unit = {}
    ) {
        require(width > 0 && height > 0) {
            "Video dimensions si sahihi."
        }

        require(width % 2 == 0 && height % 2 == 0) {
            "Video width na height lazima zigawanyike kwa 2."
        }

        require(fps in 1..60) {
            "FPS lazima iwe kati ya 1 na 60."
        }

        val parent = output.absoluteFile.parentFile
        if (parent != null && !parent.exists()) {
            check(parent.mkdirs()) {
                "Imeshindwa kutengeneza folder la kuhifadhi video."
            }
        }

        val format = MediaFormat.createVideoFormat(
            MediaFormat.MIMETYPE_VIDEO_AVC,
            width,
            height
        ).apply {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
            )
            setInteger(MediaFormat.KEY_BIT_RATE, 4_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        val encoder = MediaCodec.createEncoderByType(
            MediaFormat.MIMETYPE_VIDEO_AVC
        )

        var surface: Surface? = null
        var muxer: MediaMuxer? = null
        var encoderStarted = false
        var muxerStarted = false

        try {
            encoder.configure(
                format,
                null,
                null,
                MediaCodec.CONFIGURE_FLAG_ENCODE
            )

            surface = encoder.createInputSurface()

            if (output.exists()) {
                check(output.delete()) {
                    "Imeshindwa kufuta video ya zamani."
                }
            }

            muxer = MediaMuxer(
                output.absolutePath,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            )

            encoder.start()
            encoderStarted = true

            var track = -1
            var written = 0L

            val info = MediaCodec.BufferInfo()
            val totalFrames = SampleVideoRenderer.DURATION_SECONDS * fps

            fun drain(endOfStream: Boolean) {
                if (endOfStream) {
                    encoder.signalEndOfInputStream()
                }

                var idleAttempts = 0
                var endReached = false

                while (!endReached) {
                    val index = encoder.dequeueOutputBuffer(info, 10_000)

                    when {
                        index == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            if (!endOfStream) return

                            idleAttempts++
                            check(idleAttempts < 6000) {
                                "Sample encoder haikukamilisha video."
                            }
                        }

                        index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            check(!muxerStarted) {
                                "Sample encoder imebadilisha format zaidi ya mara moja."
                            }

                            track = muxer.addTrack(encoder.outputFormat)
                            muxer.start()
                            muxerStarted = true
                        }

                        index >= 0 -> {
                            val flags = info.flags
                            val data = encoder.getOutputBuffer(index)

                            if (
                                data != null &&
                                info.size > 0 &&
                                (flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0
                            ) {
                                check(muxerStarted && track >= 0) {
                                    "Sample MP4 muxer haijaanza."
                                }

                                info.presentationTimeUs =
                                    written * 1_000_000L / fps

                                data.position(info.offset)
                                data.limit(info.offset + info.size)

                                muxer.writeSampleData(track, data, info)
                                written++
                            }

                            encoder.releaseOutputBuffer(index, false)

                            if (
                                (flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                            ) {
                                endReached = true
                            }
                        }
                    }
                }
            }

            for (i in 0 until totalFrames) {
                val frameCanvas = surface.lockCanvas(null)

                try {
                    SampleVideoRenderer.draw(
                        frameCanvas,
                        i / fps.toFloat(),
                        width.toFloat(),
                        height.toFloat()
                    )
                } finally {
                    surface.unlockCanvasAndPost(frameCanvas)
                }

                drain(false)
                onProgress((i + 1).toFloat() / totalFrames)
            }

            drain(true)

            check(muxerStarted && written > 0L) {
                "Sample MP4 haikuwa na frames."
            }

        } finally {
            try {
                if (encoderStarted) encoder.stop()
            } catch (_: Exception) {
            }

            try {
                encoder.release()
            } catch (_: Exception) {
            }

            try {
                surface?.release()
            } catch (_: Exception) {
            }

            try {
                if (muxerStarted) muxer?.stop()
            } catch (_: Exception) {
            }

            try {
                muxer?.release()
            } catch (_: Exception) {
            }
        }
    }
}
