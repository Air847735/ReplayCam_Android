package com.tiss.replaycam.camera

import android.graphics.Bitmap

data class TimestampedFrame(
    val bitmap: Bitmap,
    val timestampNanos: Long
) {
    val timestampSeconds: Double get() = timestampNanos / 1_000_000_000.0
}
