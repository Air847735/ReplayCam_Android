package com.tiss.replaycam.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import com.tiss.replaycam.pose.PoseDetector
import com.tiss.replaycam.pose.PoseResult
import com.tiss.replaycam.pose.skeletonEdges
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

object SkeletonVideoExporter {

    suspend fun export(context: Context, inputPath: String, outputDir: File): File =
        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(inputPath)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val frameRate = 30
            val frameCount = (durationMs * frameRate / 1000).toInt().coerceAtLeast(1)
            val width = 1080
            val height = 1920

            val outputFile = File(outputDir, "skeleton_${System.currentTimeMillis()}.mp4")
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, 5_000_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            val surface = codec.createInputSurface().also { codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE) }
            val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            codec.start()

            val detector = PoseDetector(context)
            var muxerTrack = -1
            val info = MediaCodec.BufferInfo()
            val frameDurationUs = 1_000_000L / frameRate

            fun drainEncoder(endOfStream: Boolean) {
                if (endOfStream) codec.signalEndOfInputStream()
                while (true) {
                    val idx = codec.dequeueOutputBuffer(info, 10_000L)
                    if (idx == MediaCodec.INFO_TRY_AGAIN_LATER) { if (!endOfStream) break else continue }
                    if (idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        muxerTrack = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        continue
                    }
                    if (idx >= 0) {
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                            codec.releaseOutputBuffer(idx, false); continue
                        }
                        val buf = codec.getOutputBuffer(idx)!!
                        if (muxerTrack >= 0 && info.size > 0) muxer.writeSampleData(muxerTrack, buf, info)
                        codec.releaseOutputBuffer(idx, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
            }

            val bonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; strokeWidth = 6f; style = Paint.Style.STROKE }
            val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }

            for (i in 0 until frameCount) {
                val timeUs = i * 1_000_000L / frameRate
                val bmp = retriever.getFrameAtTime(timeUs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: continue

                val pose = runCatching { detector.detect(bmp) }.getOrNull()

                val canvas = surface.lockCanvas(null) ?: continue
                canvas.drawBitmap(
                    Bitmap.createScaledBitmap(bmp, width, height, true),
                    0f, 0f, null
                )

                pose?.let { drawSkeleton(canvas, it, width.toFloat(), height.toFloat(), bonePaint, dotPaint) }
                surface.unlockCanvasAndPost(canvas)
                drainEncoder(false)
                bmp.recycle()
            }

            drainEncoder(true)
            detector.close()
            retriever.release()
            codec.stop(); codec.release()
            muxer.stop(); muxer.release()
            outputFile
        }

    private fun drawSkeleton(canvas: Canvas, pose: PoseResult, w: Float, h: Float, bonePaint: Paint, dotPaint: Paint) {
        skeletonEdges.forEach { (from, to) ->
            val a = pose.keypoints.getOrNull(from.idx) ?: return@forEach
            val b = pose.keypoints.getOrNull(to.idx) ?: return@forEach
            if (a.confidence < 0.3f || b.confidence < 0.3f) return@forEach
            canvas.drawLine(a.x * w, a.y * h, b.x * w, b.y * h, bonePaint)
        }
        pose.keypoints.forEach { kp ->
            if (kp.confidence < 0.3f) return@forEach
            canvas.drawCircle(kp.x * w, kp.y * h, 10f, dotPaint)
        }
    }
}
