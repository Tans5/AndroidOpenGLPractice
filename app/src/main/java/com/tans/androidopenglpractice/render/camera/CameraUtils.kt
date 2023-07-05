package com.tans.androidopenglpractice.render.camera

import androidx.camera.core.ImageProxy
import kotlin.math.sqrt

data class Point(
    val x: Float,
    val y: Float
)

data class Oval(
    val center: Point,
    val a: Float,
    val b: Float
)

data class FaceData(
    val timestamp: Long,
    // 4 point
    val faceFrame: Array<Point>,
    // 69 个点
    // 0 - 36: 左颧骨 -> 额头 -> 右颧骨 (37 个点)
    // 37 - 53: 下巴 -> 左颧骨 (16 个点)
    // 54 - 69： 下巴 -> 右颧骨 (16 个点)
    val check: Array<Point>,
    // 16 个点
    val leftEyebrow: Array<Point>,
    // 16 个点
    val rightEyebrow: Array<Point>,
    // 16 个点
    val leftEye: Array<Point>,
    // 16 个点
    val rightEye: Array<Point>,
    // 5 个点
    val leftEyeIris: Array<Point>,
    // 15 个点, 第 1 个点和第 7 个点构成椭圆的长轴，第 13 个点为短轴上部分的点，下部分的点需要计算.
    val leftEyeIrisF: Array<Point>,
    // 5 个点
    val rightEyeIris: Array<Point>,
    // 15 个点
    val rightEyeIrisF: Array<Point>,
    // 47 个点
    // 0 - 15 右鼻翼 (16 个点)
    // 16 - 31 左鼻翼 (16 个点)
    // 32 - 46 鼻对称线 (15 个点)
    val nose: Array<Point>,
    // 16 个点
    val upLip: Array<Point>,
    // 16 个点
    val downLip: Array<Point>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FaceData

        if (timestamp != other.timestamp) return false
        if (!faceFrame.contentEquals(other.faceFrame)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = timestamp.hashCode()
        result = 31 * result + faceFrame.contentHashCode()
        return result
    }
}

enum class ImageType {
    NV21, RGBA
}

data class ImageData(
    val image: ByteArray,
    val width: Int,
    val height: Int,
    val rotation: Int,
    val imageType: ImageType,
    val imageProxy: ImageProxy,
    val timestamp: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ImageData

        if (!image.contentEquals(other.image)) return false
        if (width != other.width) return false
        if (height != other.height) return false
        if (imageType != other.imageType) return false

        return true
    }

    override fun hashCode(): Int {
        var result = image.contentHashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + imageType.hashCode()
        return result
    }
}

enum class ScaleType {
    CenterFit,
    CenterCrop
}

data class InitData(
    val cameraProgram: Int,
    val cameraVAO: Int,
    val cameraVBO: Int,
    val cameraEBO: Int,
    val cameraTexture: Int,
    val faceProgram: Int,
    val faceVAO: Int,
    val faceVBO: Int
)

data class ThinFaceData(
    val leftFaceThinCenter: Point,
    val rightFaceThinCenter: Point,
    val thinRadius: Float,
    val stretchCenter: Point
)

fun FaceData.computeThinFaceData(degree: Int, cx: Float = 0.5f, cy: Float = 0.5f): ThinFaceData {
    val leftFaceThinCenter = check[45].rotate(degree.toFloat(), cx, cy)
    val rightFaceThinCenter = check[62].rotate(degree.toFloat(), cx, cy)
    val thinRadius = leftEyeIris[0].distance(rightEyeIris[0]) / 2.0f
    val stretchCenter = nose[43].rotate(degree.toFloat(), cx, cy)
    return ThinFaceData(
        leftFaceThinCenter = leftFaceThinCenter,
        rightFaceThinCenter = rightFaceThinCenter,
        thinRadius = thinRadius,
        stretchCenter = stretchCenter
    )
}

fun Array<Point>.toGlFacePoints(
    xMin: Float,
    xMax: Float,
    yMin: Float,
    yMax: Float,
    colorR: Float,
    colorG: Float,
    colorB: Float): FloatArray {
    return map { it.toGlPoint(xMin, xMax, yMin, yMax) }
        .map {
            it + floatArrayOf(0f, colorR, colorG, colorB)
        }
        .fold(floatArrayOf()) { old, new ->
            old + new
        }
}

fun Point.distance(targetPoint: Point): Float {
    val dx = x - targetPoint.x
    val dy = y - targetPoint.y
    return sqrt(dx * dx + dy * dy)
}

fun Point.rotate(degree: Float, cx: Float = 0.5f, cy: Float = 0.5f): Point {
    val transform = android.graphics.Matrix()
    transform.setRotate(degree, cx, cy)
    val array = floatArrayOf(x, y)
    transform.mapPoints(array)
    return Point(array[0], array[1])
}


fun centerCropTextureRect(targetRatio: Float, topLeftPoint: Point, bottomRightPoint: Point): Pair<Point, Point> {
    val oldRectWidth = bottomRightPoint.x - topLeftPoint.x
    val oldRectHeight = bottomRightPoint.y - topLeftPoint.y
    val oldRectRatio = oldRectWidth / oldRectHeight
    return when  {
        oldRectRatio - targetRatio > 0.00001 -> {
            // 裁剪 x
            val d = (oldRectWidth - oldRectHeight * targetRatio) / 2.0f
            val newTopLeftX = topLeftPoint.x + d
            val newBottomRightX = bottomRightPoint.x - d
            Point(x = newTopLeftX, y = topLeftPoint.y) to Point(x = newBottomRightX, y = bottomRightPoint.y)
        }

        targetRatio - oldRectRatio > 0.00001 -> {
            // 裁剪 y
            val d = (oldRectHeight - oldRectWidth / targetRatio) / 2.0f
            val newTopLeftY = topLeftPoint.y + d
            val newBottomRightY = bottomRightPoint.y - d
            Point(x = topLeftPoint.x, y = newTopLeftY) to Point(x = bottomRightPoint.x, y = newBottomRightY)
        }

        else -> {
            topLeftPoint to bottomRightPoint
        }
    }
}

fun centerCropPositionRect(targetRatio: Float, topLeftPoint: Point, bottomRightPoint: Point): Pair<Point, Point> {
    val oldRectWidth = bottomRightPoint.x - topLeftPoint.x
    val oldRectHeight = topLeftPoint.y - bottomRightPoint.y
    val oldRectRatio = oldRectWidth / oldRectHeight
    return when  {
        oldRectRatio - targetRatio > 0.00001 -> {
            // 裁剪 x
            val d = (oldRectWidth - oldRectHeight * targetRatio) / 2.0f
            val newTopLeftX = topLeftPoint.x + d
            val newBottomRightX = bottomRightPoint.x - d
            Point(x = newTopLeftX, y = topLeftPoint.y) to Point(x = newBottomRightX, y = bottomRightPoint.y)
        }

        targetRatio - oldRectRatio > 0.00001 -> {
            // 裁剪 y
            val d = (oldRectHeight - oldRectWidth / targetRatio) / 2.0f
            val newTopLeftY = topLeftPoint.y - d
            val newBottomRightY = bottomRightPoint.y + d
            Point(x = topLeftPoint.x, y = newTopLeftY) to Point(x = bottomRightPoint.x, y = newBottomRightY)
        }

        else -> {
            topLeftPoint to bottomRightPoint
        }
    }
}

fun centerPoint(topLeftPoint: Point, bottomRightPoint: Point): Point {
    return Point(x = (topLeftPoint.x + bottomRightPoint.x) / 2.0f, y = (topLeftPoint.y + bottomRightPoint.y) / 2.0f)
}

fun Array<Point>.computeFaceTextureOval(): Oval {
    val leftPoint = get(0)
    val rightPoint = get(6)
    val topPoint = get(12)
    val centerPoint = Point((leftPoint.x + rightPoint.x) / 2.0f, (leftPoint.y + rightPoint.y) / 2.0f)
    val a = leftPoint.distance(rightPoint) / 2.0f * 1.1f
    val b = topPoint.distance(centerPoint) * 2.0f
    return Oval(
        center = centerPoint,
        a = a,
        b = b
    )
}

fun Oval.rotate(degree: Int, cx: Float = 0.5f, cy: Float = 0.5f): Oval {
    val newCenter = center.rotate(degree.toFloat(), cx, cy)
    val (newA, newB) = when (degree) {
        in 0 until  90 -> a to b
        in 90 until  180 -> b to a
        in 180 until 270 -> a to b
        in 270 until  360 -> b to a
        else ->  a to b
    }
    return Oval(
        center = newCenter,
        a = newA,
        b = newB
    )
}

fun Point.toGlPoint(xMin: Float, xMax: Float, yMin: Float, yMax: Float): FloatArray {
    val scaleX = xMax - xMin
    val scaleY = yMax - yMin
    val dX = xMax - xMin
    val dY = yMax - yMin
    return floatArrayOf(x * scaleX - dX / 2f, y * - scaleY + dY / 2f)
}

infix operator fun Point.plus(b: Point): Point {
    return Point(x + b.x, y + b.y)
}

infix operator fun Point.minus(b: Point): Point {
    return Point(x - b.x, y - b.y)
}