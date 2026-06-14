package com.tiss.replaycam.pose

data class Keypoint(
    val x: Float,
    val y: Float,
    val confidence: Float,
    val worldX: Float = 0f,
    val worldY: Float = 0f,
    val worldZ: Float = 0f
)

data class PoseResult(
    val keypoints: List<Keypoint>,
    val confidence: Float,
    val timestampMs: Long = 0L
)

enum class CocoKeypoint(val idx: Int) {
    NOSE(0), LEFT_EYE(1), RIGHT_EYE(2), LEFT_EAR(3), RIGHT_EAR(4),
    LEFT_SHOULDER(5), RIGHT_SHOULDER(6),
    LEFT_ELBOW(7), RIGHT_ELBOW(8),
    LEFT_WRIST(9), RIGHT_WRIST(10),
    LEFT_HIP(11), RIGHT_HIP(12),
    LEFT_KNEE(13), RIGHT_KNEE(14),
    LEFT_ANKLE(15), RIGHT_ANKLE(16)
}

val skeletonEdges = listOf(
    CocoKeypoint.NOSE to CocoKeypoint.LEFT_EYE,
    CocoKeypoint.NOSE to CocoKeypoint.RIGHT_EYE,
    CocoKeypoint.LEFT_EYE to CocoKeypoint.LEFT_EAR,
    CocoKeypoint.RIGHT_EYE to CocoKeypoint.RIGHT_EAR,
    CocoKeypoint.LEFT_SHOULDER to CocoKeypoint.RIGHT_SHOULDER,
    CocoKeypoint.LEFT_SHOULDER to CocoKeypoint.LEFT_ELBOW,
    CocoKeypoint.RIGHT_SHOULDER to CocoKeypoint.RIGHT_ELBOW,
    CocoKeypoint.LEFT_ELBOW to CocoKeypoint.LEFT_WRIST,
    CocoKeypoint.RIGHT_ELBOW to CocoKeypoint.RIGHT_WRIST,
    CocoKeypoint.LEFT_SHOULDER to CocoKeypoint.LEFT_HIP,
    CocoKeypoint.RIGHT_SHOULDER to CocoKeypoint.RIGHT_HIP,
    CocoKeypoint.LEFT_HIP to CocoKeypoint.RIGHT_HIP,
    CocoKeypoint.LEFT_HIP to CocoKeypoint.LEFT_KNEE,
    CocoKeypoint.RIGHT_HIP to CocoKeypoint.RIGHT_KNEE,
    CocoKeypoint.LEFT_KNEE to CocoKeypoint.LEFT_ANKLE,
    CocoKeypoint.RIGHT_KNEE to CocoKeypoint.RIGHT_ANKLE
)

enum class JointAngle(val label: String, val a: CocoKeypoint, val b: CocoKeypoint, val c: CocoKeypoint) {
    LEFT_ELBOW("左肘", CocoKeypoint.LEFT_SHOULDER, CocoKeypoint.LEFT_ELBOW, CocoKeypoint.LEFT_WRIST),
    RIGHT_ELBOW("右肘", CocoKeypoint.RIGHT_SHOULDER, CocoKeypoint.RIGHT_ELBOW, CocoKeypoint.RIGHT_WRIST),
    LEFT_KNEE("左膝", CocoKeypoint.LEFT_HIP, CocoKeypoint.LEFT_KNEE, CocoKeypoint.LEFT_ANKLE),
    RIGHT_KNEE("右膝", CocoKeypoint.RIGHT_HIP, CocoKeypoint.RIGHT_KNEE, CocoKeypoint.RIGHT_ANKLE),
    LEFT_HIP("左髖", CocoKeypoint.LEFT_SHOULDER, CocoKeypoint.LEFT_HIP, CocoKeypoint.LEFT_KNEE),
    RIGHT_HIP("右髖", CocoKeypoint.RIGHT_SHOULDER, CocoKeypoint.RIGHT_HIP, CocoKeypoint.RIGHT_KNEE),
    LEFT_SHOULDER("左肩", CocoKeypoint.LEFT_ELBOW, CocoKeypoint.LEFT_SHOULDER, CocoKeypoint.LEFT_HIP),
    RIGHT_SHOULDER("右肩", CocoKeypoint.RIGHT_ELBOW, CocoKeypoint.RIGHT_SHOULDER, CocoKeypoint.RIGHT_HIP)
}

fun calcAngle(a: Keypoint, b: Keypoint, c: Keypoint): Float {
    val v1x = a.x - b.x; val v1y = a.y - b.y
    val v2x = c.x - b.x; val v2y = c.y - b.y
    val dot = v1x * v2x + v1y * v2y
    val mag1 = kotlin.math.sqrt((v1x * v1x + v1y * v1y).toDouble())
    val mag2 = kotlin.math.sqrt((v2x * v2x + v2y * v2y).toDouble())
    if (mag1 == 0.0 || mag2 == 0.0) return 0f
    val cos = (dot / (mag1 * mag2)).coerceIn(-1.0, 1.0)
    return Math.toDegrees(kotlin.math.acos(cos)).toFloat()
}

fun calcAngle3D(a: Keypoint, b: Keypoint, c: Keypoint): Float {
    val v1x = a.worldX - b.worldX; val v1y = a.worldY - b.worldY; val v1z = a.worldZ - b.worldZ
    val v2x = c.worldX - b.worldX; val v2y = c.worldY - b.worldY; val v2z = c.worldZ - b.worldZ
    val dot = v1x * v2x + v1y * v2y + v1z * v2z
    val mag1 = kotlin.math.sqrt((v1x * v1x + v1y * v1y + v1z * v1z).toDouble())
    val mag2 = kotlin.math.sqrt((v2x * v2x + v2y * v2y + v2z * v2z).toDouble())
    if (mag1 == 0.0 || mag2 == 0.0) return calcAngle(a, b, c)
    val cos = (dot / (mag1 * mag2)).coerceIn(-1.0, 1.0)
    return Math.toDegrees(kotlin.math.acos(cos)).toFloat()
}
