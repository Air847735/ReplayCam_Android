package com.tiss.replaycam.camera

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class FrameBuffer(private val maxDurationSeconds: Double = 35.0) {

    private val lock = ReentrantLock()
    private val frames = ArrayDeque<TimestampedFrame>()

    companion object {
        private const val EARLY_CLEANUP_THRESHOLD = 1200
    }

    fun append(frame: TimestampedFrame) = lock.withLock {
        frames.addLast(frame)
        if (frames.size >= EARLY_CLEANUP_THRESHOLD) {
            evictExpired(frame.timestampNanos)
        }
    }

    fun findFrame(nearTimestampNanos: Long, toleranceNanos: Long = 100_000_000L): TimestampedFrame? =
        lock.withLock {
            frames.minByOrNull { Math.abs(it.timestampNanos - nearTimestampNanos) }
                ?.takeIf { Math.abs(it.timestampNanos - nearTimestampNanos) <= toleranceNanos }
        }

    fun framesSince(timestampNanos: Long): List<TimestampedFrame> =
        lock.withLock {
            frames.filter { it.timestampNanos >= timestampNanos }
        }

    fun frameCount(): Int = lock.withLock { frames.size }

    fun duration(): Double = lock.withLock {
        if (frames.size < 2) return@withLock 0.0
        (frames.last().timestampNanos - frames.first().timestampNanos) / 1_000_000_000.0
    }

    fun clear() = lock.withLock { frames.clear() }

    private fun evictExpired(latestTimestampNanos: Long) {
        val cutoffNanos = latestTimestampNanos - (maxDurationSeconds * 1_000_000_000.0).toLong()
        while (frames.isNotEmpty() && frames.first().timestampNanos < cutoffNanos) {
            frames.removeFirst().bitmap.recycle()
        }
    }
}
