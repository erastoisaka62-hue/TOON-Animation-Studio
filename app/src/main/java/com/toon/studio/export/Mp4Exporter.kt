package com.toon.studio.export

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File

object Mp4Exporter {

    /**
     * Renders SampleVideoRenderer frame by frame and encodes them into an
     * H.264 MP4 file. Runs on the calling thread, so call it from a
     * background thread. Throws an exception if the device cannot encode.
     */
    fun export(
        output: File,
        width: Int,
        height: Int,
        fps: Int = 30,
        onProgress: (Float) -> Unit = {}
    ) {
        val format = MediaFormat.createVideoFormat(
            MediaFormat.MIMETYPE_VIDEO_AVC, width, height
        ).apply {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
            )
            setInteger(MediaFormat.KEY_BIT_RATE, 4_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val surface = encoder.createInputSurface()
        encoder.start()

        if (output.exists()) output.delete()
        val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        var track = -1
        var muxerStarted = false
        var written = 0L
        val info = MediaCodec.BufferInfo()
        val totalFrames = SampleVideoRenderer.DURATION_SECONDS * fps

        fun drain(endOfStream: Boolean) {
            if (endOfStream) encoder.signalEndOfInputStream()
            while (true) {
                val index = encoder.dequeueOutputBuffer(info, 10_000)
                if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    if (!endOfStream) return
                } else if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    track = muxer.addTrack(encoder.outputFormat)
                    muxer.start()
                    muxerStarted = true
                } else if (index >= 0) {
                    val data = encoder.getOutputBuffer(index)
                    val isConfig = (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                    if (data != null && info.size > 0 && !isConfig && muxerStarted) {
                        info.presentationTimeUs = written * 1_000_000L / fps
                        written++
                        data.position(info.offset)
                        data.limit(info.offset + info.size)
                        muxer.writeSampleData(track, data, info)
                    }
                    encoder.releaseOutputBuffer(index, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return
                }
            }
        }

        try {
            for (i in 0 until totalFrames) {
                val canvas = surface.lockCanvas(null)
                try {
                    SampleVideoRenderer.draw(
                        canvas,
                        i / fps.toFloat(),
                        width.toFloat(),
                        height.toFloat()
                    )
                } finally {
                    surface.unlockCanvasAndPost(canvas)
                }
                drain(false)
                onProgress((i + 1) / totalFrames.toFloat())
            }
            drain(true)
        } finally {
            try {
                encoder.stop()
            } catch (ignored: Exception) {
            }
            encoder.release()
            surface.release()
            try {
                if (muxerStarted) muxer.stop()
            } catch (ignored: Exception) {
            }
            muxer.release()
        }
    }
}
