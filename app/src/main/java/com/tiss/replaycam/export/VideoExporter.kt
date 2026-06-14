package com.tiss.replaycam.export

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import com.tiss.replaycam.camera.TimestampedFrame
import com.tiss.replaycam.store.ClipStore
import java.io.File
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VideoExporter(private val context: Context) {

    private val width = 1080
    private val height = 1920
    private val fps = 30
    private val bitrate = 5_000_000

    suspend fun export(frames: List<TimestampedFrame>): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val dir = ClipStore.getInstance(context).clipsDirectory
        val outputFile = File(dir, "ReplayCam_$timestamp.mp4")

        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val surface = codec.createInputSurface()
        codec.start()

        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var trackIndex = -1
        var muxerStarted = false

        val bufferInfo = MediaCodec.BufferInfo()
        var presentationTimeUs = 0L
        val frameIntervalUs = 1_000_000L / fps

        frames.forEachIndexed { _, frame ->
            val c = surface.lockCanvas(null) ?: return@forEachIndexed
            try {
                c.drawBitmap(
                    android.graphics.Bitmap.createScaledBitmap(frame.bitmap, width, height, true),
                    0f, 0f, null
                )
            } finally {
                surface.unlockCanvasAndPost(c)
            }

            drainEncoder(codec, bufferInfo, muxer, { trackIndex }, { idx ->
                trackIndex = idx
            }, { muxerStarted }, { muxerStarted = true }, presentationTimeUs)
            presentationTimeUs += frameIntervalUs
        }

        codec.signalEndOfInputStream()
        drainEncoder(codec, bufferInfo, muxer, { trackIndex }, { idx ->
            trackIndex = idx
        }, { muxerStarted }, { muxerStarted = true }, presentationTimeUs, endOfStream = true)

        codec.stop()
        codec.release()
        surface.release()
        if (muxerStarted) muxer.stop()
        muxer.release()

        return outputFile
    }

    private fun drainEncoder(
        codec: MediaCodec,
        bufferInfo: MediaCodec.BufferInfo,
        muxer: MediaMuxer,
        getTrackIndex: () -> Int,
        setTrackIndex: (Int) -> Unit,
        getMuxerStarted: () -> Boolean,
        setMuxerStarted: () -> Unit,
        presentationTimeUs: Long,
        endOfStream: Boolean = false
    ) {
        val timeoutUs = if (endOfStream) 10_000L else 0L
        var done = false
        while (!done) {
            val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
            when {
                outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val newFormat = codec.outputFormat
                    val idx = muxer.addTrack(newFormat)
                    setTrackIndex(idx)
                    muxer.start()
                    setMuxerStarted()
                }
                outputBufferIndex >= 0 -> {
                    val outputBuffer: ByteBuffer = codec.getOutputBuffer(outputBufferIndex) ?: run {
                        codec.releaseOutputBuffer(outputBufferIndex, false)
                        return
                    }
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        bufferInfo.size = 0
                    }
                    if (bufferInfo.size != 0 && getMuxerStarted()) {
                        bufferInfo.presentationTimeUs = presentationTimeUs
                        muxer.writeSampleData(getTrackIndex(), outputBuffer, bufferInfo)
                    }
                    codec.releaseOutputBuffer(outputBufferIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        done = true
                    }
                }
                else -> if (!endOfStream) done = true
            }
        }
    }
}
