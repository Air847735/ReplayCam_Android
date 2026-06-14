package com.tiss.replaycam.pose

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class PoseDetector(context: Context) {

    private val options = AccuratePoseDetectorOptions.Builder()
        .setDetectorMode(AccuratePoseDetectorOptions.SINGLE_IMAGE_MODE)
        .build()

    private val detector = PoseDetection.getClient(options)

    suspend fun detect(bitmap: Bitmap): PoseResult? = suspendCancellableCoroutine { cont ->
        val image = InputImage.fromBitmap(bitmap, 0)
        detector.process(image)
            .addOnSuccessListener { pose ->
                val landmarks = pose.allPoseLandmarks
                if (landmarks.isEmpty()) { cont.resume(null); return@addOnSuccessListener }

                val mlKitToCoco = mapOf(
                    PoseLandmark.NOSE to 0,
                    PoseLandmark.LEFT_EYE to 1,
                    PoseLandmark.RIGHT_EYE to 2,
                    PoseLandmark.LEFT_EAR to 3,
                    PoseLandmark.RIGHT_EAR to 4,
                    PoseLandmark.LEFT_SHOULDER to 5,
                    PoseLandmark.RIGHT_SHOULDER to 6,
                    PoseLandmark.LEFT_ELBOW to 7,
                    PoseLandmark.RIGHT_ELBOW to 8,
                    PoseLandmark.LEFT_WRIST to 9,
                    PoseLandmark.RIGHT_WRIST to 10,
                    PoseLandmark.LEFT_HIP to 11,
                    PoseLandmark.RIGHT_HIP to 12,
                    PoseLandmark.LEFT_KNEE to 13,
                    PoseLandmark.RIGHT_KNEE to 14,
                    PoseLandmark.LEFT_ANKLE to 15,
                    PoseLandmark.RIGHT_ANKLE to 16
                )

                val keypoints = MutableList(17) { Keypoint(0f, 0f, 0f) }
                val w = bitmap.width.toFloat()
                val h = bitmap.height.toFloat()

                for (lm in landmarks) {
                    val cocoIdx = mlKitToCoco[lm.landmarkType] ?: continue
                    val wp = lm.position3D
                    keypoints[cocoIdx] = Keypoint(
                        x = lm.position.x / w,
                        y = lm.position.y / h,
                        confidence = lm.inFrameLikelihood,
                        worldX = wp.x,
                        worldY = wp.y,
                        worldZ = wp.z
                    )
                }

                val avgConf = keypoints.map { it.confidence }.average().toFloat()
                cont.resume(PoseResult(keypoints = keypoints, confidence = avgConf))
            }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    fun close() { detector.close() }
}
