package com.tans.androidopenglpractice.render

import android.opengl.GLES31
import android.opengl.GLUtils
import android.opengl.Matrix
import androidx.camera.core.ImageProxy
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.atomic.AtomicBoolean
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.sqrt

class CameraRender : IShapeRender {

    private var initData: InitData? = null

    override val isActive: AtomicBoolean = AtomicBoolean(false)

    override var width: Int = 0

    override var height: Int = 0
    override val logTag: String = "CameraRender"

    private val pendingRenderFrames: LinkedBlockingDeque<ImageData> by lazy {
        LinkedBlockingDeque()
    }

    private val pendingRenderFaceData: LinkedBlockingDeque<FaceData> by lazy {
        LinkedBlockingDeque()
    }

    private var owner: MyOpenGLView? = null

    var scaleType: ScaleType = ScaleType.CenterCrop

    var mirror: Boolean = true

    var renderFaceFrame: Boolean = true

    override fun onSurfaceCreated(owner: MyOpenGLView, gl: GL10, config: EGLConfig) {
        super.onSurfaceCreated(owner, gl, config)
        this.owner = owner
        val cameraProgram = compileShaderFromAssets(owner.context, "camera.vert", "camera.frag")
        val faceProgram = compileShaderFromAssets(owner.context, "face_frame.vert", "face_frame.frag")
        if (cameraProgram != null && faceProgram != null) {
            val cameraVAO = glGenVertexArrays()
            val cameraVBO = glGenBuffers()
            val cameraEBO = glGenBuffers()

            // 纹理
            val cameraTexture = glGenTexture()
            GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, cameraTexture)
            GLES31.glTexParameteri(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_WRAP_S, GLES31.GL_REPEAT)
            GLES31.glTexParameteri(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_WRAP_T, GLES31.GL_REPEAT)
            GLES31.glTexParameteri(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_MIN_FILTER, GLES31.GL_LINEAR)
            GLES31.glTexParameteri(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_MAG_FILTER, GLES31.GL_LINEAR)
            GLES31.glGenerateMipmap(GLES31.GL_TEXTURE_2D)

            val faceVAO = glGenVertexArrays()
            val faceVBO = glGenBuffers()

            initData = InitData(
                cameraProgram = cameraProgram,
                cameraVAO = cameraVAO,
                cameraVBO = cameraVBO,
                cameraEBO = cameraEBO,
                cameraTexture = cameraTexture,
                faceProgram = faceProgram,
                faceVAO = faceVAO,
                faceVBO = faceVBO
            )
        }
    }

    override fun onDrawFrame(owner: MyOpenGLView, gl: GL10) {
        val initData = this.initData
        val imageData = pendingRenderFrames.pollFirst()
        if (initData != null && imageData != null) {
            GLES31.glClear(GLES31.GL_COLOR_BUFFER_BIT)
            GLES31.glUseProgram(initData.cameraProgram)
            val rotation = imageData.rotation % 360
            val (imageWidth, imageHeight) = when (rotation) {
                in 0 until  90 -> imageData.width to imageData.height
                in 90 until  180 ->imageData.height to imageData.width
                in 180 until 270 -> imageData.width to imageData.height
                in 270 until  360 -> imageData.height to imageData.width
                else ->  imageData.width to imageData.height
            }
            val imageRatio = imageWidth.toFloat() / imageHeight.toFloat()
            val renderRatio = width.toFloat() / height.toFloat()
            val scaleType = this.scaleType

            val (textureTl, textureRb) = when (scaleType) {
                ScaleType.CenterFit -> {
                    Point(0.0f, 0.0f) to Point(1.0f, 1.0f)
                }
                ScaleType.CenterCrop -> {
                    centerCropTextureRect(
                        targetRatio = renderRatio / imageRatio,
                        topLeftPoint = Point(0.0f, 0.0f),
                        bottomRightPoint = Point(1.0f, 1.0f)
                    )
                }
            }

            val (positionTl, positionRb) = when (scaleType) {
                ScaleType.CenterFit -> {
                    centerCropPositionRect(
                        targetRatio = imageRatio,
                        topLeftPoint = Point(-1.0f * renderRatio, 1.0f),
                        bottomRightPoint = Point(1.0f * renderRatio, -1.0f)
                    )
                }

                ScaleType.CenterCrop -> {
                    Point(-1.0f * renderRatio, 1.0f) to Point(1.0f * renderRatio, -1.0f)
                }
            }

            val textureTopLeft = floatArrayOf(textureTl.x, textureTl.y)
            val textureBottomLeft = floatArrayOf(textureTl.x, textureRb.y)
            val textureTopRight = floatArrayOf(textureRb.x, textureTl.y)
            val textureBottomRight = floatArrayOf(textureRb.x, textureRb.y)

            val textureTransform = android.graphics.Matrix()
            val rotateCenter = centerPoint(textureTl, textureRb)
            textureTransform.setRotate(360f - rotation.toFloat(), rotateCenter.x, rotateCenter.y)
            textureTransform.mapPoints(textureTopLeft)
            textureTransform.mapPoints(textureBottomLeft)
            textureTransform.mapPoints(textureTopRight)
            textureTransform.mapPoints(textureBottomRight)
            val xMin = positionTl.x
            val xMax = positionRb.x
            val yMin = positionRb.y
            val yMax = positionTl.y
            val cameraVertices = floatArrayOf(
                // 坐标(position 0)   // 纹理坐标
                xMin, yMax, 0.0f,   textureTopLeft[0], textureTopLeft[1],    // 左上角
                xMax, yMax, 0.0f,    textureTopRight[0], textureTopRight[1],   // 右上角
                xMax, yMin, 0.0f,   textureBottomRight[0], textureBottomRight[1],   // 右下角
                xMin, yMin, 0.0f,  textureBottomLeft[0], textureBottomLeft[1],   // 左下角
                0.0f
            )
            GLES31.glBindVertexArray(initData.cameraVAO)
            GLES31.glBindBuffer(GLES31.GL_ARRAY_BUFFER, initData.cameraVBO)
            GLES31.glBufferData(GLES31.GL_ARRAY_BUFFER, cameraVertices.size * 4, cameraVertices.toGlBuffer(), GLES31.GL_STREAM_DRAW)
            GLES31.glVertexAttribPointer(0, 3, GLES31.GL_FLOAT, false, 5 * 4, 0)
            GLES31.glEnableVertexAttribArray(0)
            GLES31.glVertexAttribPointer(1, 3, GLES31.GL_FLOAT, false, 5 * 4, 3 * 4)
            GLES31.glEnableVertexAttribArray(1)

            GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, initData.cameraTexture)
            val bitmap = when (imageData.imageType) {
                ImageType.NV21 -> imageData.image.nv21ToBitmap(imageData.width, imageData.height)
                ImageType.RGBA -> imageData.image.rgbaToBitmap(imageData.width, imageData.height)
            }
            GLUtils.texImage2D(GLES31.GL_TEXTURE_2D, 0, bitmap, 0)
            bitmap.recycle()
            imageData.imageProxy.close()
//            val rgbaBytes = ByteArray(imageWidth * imageHeight * 4)
//            GLES31.glTexImage2D(GLES31.GL_TEXTURE_2D, 0, GLES31.GL_RGBA, imageWidth, imageHeight, 0,
//            GLES31.GL_RGBA, GLES31.GL_UNSIGNED_BYTE, ByteBuffer.wrap(rgbaBytes))

            // View
            val viewMatrix = newGlFloatMatrix()
            Matrix.scaleM(viewMatrix, 0, 1 / renderRatio, 1.0f, 1.0f)

            if (mirror) {
                // 镜像显示
                Matrix.rotateM(viewMatrix, 0, 180f, 0f, 1f, 0f)
            }
            GLES31.glUniformMatrix4fv(GLES31.glGetUniformLocation(initData.cameraProgram, "view"), 1, false, viewMatrix, 0)

            // model
            val modelMatrix = newGlFloatMatrix()
            GLES31.glUniformMatrix4fv(GLES31.glGetUniformLocation(initData.cameraProgram, "model"), 1, false, modelMatrix, 0)

            // transform
            val transformMatrix = newGlFloatMatrix()
            GLES31.glUniformMatrix4fv(GLES31.glGetUniformLocation(initData.cameraProgram, "transform"), 1, false, transformMatrix, 0)

            val faceData = findFaceData()
            val leftEyeIris = floatArrayOf(0.0f, 0.0f)
            var leftEyeRadius = 0f
            if (faceData != null) {
                val p = faceData.leftEyeIris[0]
                val fixed = p.rotate(360.0f - rotation)
                leftEyeIris[0] = fixed.x
                leftEyeIris[1] = fixed.y
                leftEyeRadius = p.distance(faceData.leftEyeIris[1])
            }
            GLES31.glUniform2f(GLES31.glGetUniformLocation(initData.cameraProgram, "leftEyeIris"), leftEyeIris[0], leftEyeIris[1])
            GLES31.glUniform1f(GLES31.glGetUniformLocation(initData.cameraProgram, "leftEyeIrisRadius"), leftEyeRadius)

            val indices = intArrayOf(
                0, 1, 2, // 第一个三角形
                2, 3, 0 // 第二个三角形
            )
            GLES31.glBindBuffer(GLES31.GL_ELEMENT_ARRAY_BUFFER, initData.cameraEBO)
            GLES31.glBufferData(GLES31.GL_ELEMENT_ARRAY_BUFFER, indices.size * 4, indices.toGlBuffer(), GLES31.GL_STREAM_DRAW)
            GLES31.glDrawElements(GLES31.GL_TRIANGLES, 6, GLES31.GL_UNSIGNED_INT, 0)
            if (faceData != null && renderFaceFrame) {
                /**
                 * 绘制 face frame
                 */
                GLES31.glUseProgram(initData.faceProgram)
                GLES31.glBindVertexArray(initData.faceVAO)
                GLES31.glBindBuffer(GLES31.GL_ARRAY_BUFFER, initData.faceVBO)
                GLES31.glVertexAttribPointer(0, 3, GLES31.GL_FLOAT, false, 6 * 4, 0)
                GLES31.glEnableVertexAttribArray(0)
                GLES31.glVertexAttribPointer(1, 3, GLES31.GL_FLOAT, false, 6 * 4, 3 * 4)
                GLES31.glEnableVertexAttribArray(1)
                val textureRatio = (textureRb.x - textureTl.x) / (textureRb.y - textureTl.y)
                Matrix.scaleM(viewMatrix, 0, 1 / textureRatio, 1.0f, 1.0f)
                GLES31.glUniformMatrix4fv(GLES31.glGetUniformLocation(initData.faceProgram, "view"), 1, false, viewMatrix, 0)
                GLES31.glUniformMatrix4fv(GLES31.glGetUniformLocation(initData.faceProgram, "model"), 1, false, modelMatrix, 0)
                GLES31.glUniformMatrix4fv(GLES31.glGetUniformLocation(initData.faceProgram, "transform"), 1, false, transformMatrix, 0)
                GLES31.glLineWidth(3f)

                // 绘制Frame
                GLES31.glBindVertexArray(initData.faceVAO)
                GLES31.glBindBuffer(GLES31.GL_ARRAY_BUFFER, initData.faceVBO)
                val faceFrameVertices = faceData.faceFrame.toGlFacePoints(xMin, xMax, yMin, yMax, 1.0f,  0.0f, 0.0f)
                GLES31.glBufferData(GLES31.GL_ARRAY_BUFFER, faceFrameVertices.size * 4, faceFrameVertices.toGlBuffer(), GLES31.GL_STREAM_DRAW)
                GLES31.glDrawArrays(GLES31.GL_LINE_LOOP, 0, faceFrameVertices.size / 6)

                // 绘制脸颊
                drawFacePoints(
                    initData = initData,
                    points = faceData.check,
                    xMin = xMin,
                    xMax = xMax,
                    yMin = yMin,
                    yMax = yMax,
                    colorR = 0.0f,
                    colorG = 1.0f,
                    colorB = 0.0f
                )

                // 左眉毛
                drawFacePoints(
                    initData = initData,
                    points = faceData.leftEyebrow,
                    xMin = xMin,
                    xMax = xMax,
                    yMin = yMin,
                    yMax = yMax,
                    colorR = 0.0f,
                    colorG = 1.0f,
                    colorB = 0.0f
                )

                // 右眉毛
                drawFacePoints(
                    initData = initData,
                    points = faceData.rightEyebrow,
                    xMin = xMin,
                    xMax = xMax,
                    yMin = yMin,
                    yMax = yMax,
                    colorR = 0.0f,
                    colorG = 1.0f,
                    colorB = 0.0f
                )

                // 左眼
                drawFacePoints(
                    initData = initData,
                    points = faceData.leftEye,
                    xMin = xMin,
                    xMax = xMax,
                    yMin = yMin,
                    yMax = yMax,
                    colorR = 0.0f,
                    colorG = 1.0f,
                    colorB = 0.0f
                )

                // 右眼
                drawFacePoints(
                    initData = initData,
                    points = faceData.rightEye,
                    xMin = xMin,
                    xMax = xMax,
                    yMin = yMin,
                    yMax = yMax,
                    colorR = 0.0f,
                    colorG = 1.0f,
                    colorB = 0.0f
                )

                // 左眼虹膜
                drawFacePoints(
                    initData = initData,
                    points = faceData.leftEyeIris,
                    xMin = xMin,
                    xMax = xMax,
                    yMin = yMin,
                    yMax = yMax,
                    colorR = 1.0f,
                    colorG = 0.0f,
                    colorB = 0.0f
                )

                drawFacePoints(
                    initData = initData,
                    points = faceData.leftEyeIrisF,
                    xMin = xMin,
                    xMax = xMax,
                    yMin = yMin,
                    yMax = yMax,
                    colorR = 0.0f,
                    colorG = 0.0f,
                    colorB = 1.0f
                )

                // 右眼虹膜
                drawFacePoints(
                    initData = initData,
                    points = faceData.rightEyeIris,
                    xMin = xMin,
                    xMax = xMax,
                    yMin = yMin,
                    yMax = yMax,
                    colorR = 1.0f,
                    colorG = 0.0f,
                    colorB = 0.0f
                )

                drawFacePoints(
                    initData = initData,
                    points = faceData.rightEyeIrisF,
                    xMin = xMin,
                    xMax = xMax,
                    yMin = yMin,
                    yMax = yMax,
                    colorR = 0.0f,
                    colorG = 0.0f,
                    colorB = 1.0f
                )

                // 鼻子
                drawFacePoints(
                    initData = initData,
                    points = faceData.nose,
                    xMin = xMin,
                    xMax = xMax,
                    yMin = yMin,
                    yMax = yMax,
                    colorR = 0.0f,
                    colorG = 1.0f,
                    colorB = 0.0f
                )

                // 上嘴唇
                drawFacePoints(
                    initData = initData,
                    points = faceData.upLip,
                    xMin = xMin,
                    xMax = xMax,
                    yMin = yMin,
                    yMax = yMax,
                    colorR = 0.0f,
                    colorG = 1.0f,
                    colorB = 0.0f
                )

                // 下嘴唇
                drawFacePoints(
                    initData = initData,
                    points = faceData.downLip,
                    xMin = xMin,
                    xMax = xMax,
                    yMin = yMin,
                    yMax = yMax,
                    colorR = 0.0f,
                    colorG = 1.0f,
                    colorB = 0.0f
                )
            }
        } else {
            imageData?.imageProxy?.close()
        }
    }

    override fun onViewDestroyed(owner: MyOpenGLView) {
        super.onViewDestroyed(owner)
        for (f in pendingRenderFrames) {
            f.imageProxy.close()
        }
        pendingRenderFrames.clear()
        pendingRenderFaceData.clear()
        initData = null
        this.owner = null
    }

    fun cameraReady(imageData: ImageData) {
        this.owner?.let {
            pendingRenderFrames.put(imageData)
            it.requestRender()
        } ?: imageData.imageProxy.close()
    }

    fun faceDataReady(faceData: FaceData) {
        this.owner?.let {
            pendingRenderFaceData.put(faceData)
        }
    }


    private fun drawFacePoints(
        initData: InitData,
        points: Array<Point>,
        xMin: Float,
        xMax: Float,
        yMin: Float,
        yMax: Float,
        colorR: Float,
        colorG: Float,
        colorB: Float) {
        GLES31.glBindVertexArray(initData.faceVAO)
        GLES31.glBindBuffer(GLES31.GL_ARRAY_BUFFER, initData.faceVBO)
        val vertices = points.toGlFacePoints(xMin, xMax, yMin, yMax, colorR,  colorG, colorB)
        GLES31.glBufferData(GLES31.GL_ARRAY_BUFFER, vertices.size * 4, vertices.toGlBuffer(), GLES31.GL_STREAM_DRAW)
        GLES31.glDrawArrays(GLES31.GL_POINTS, 0, vertices.size / 6)
    }

    private fun Point.toGlPoint(xMin: Float, xMax: Float, yMin: Float, yMax: Float): FloatArray {
        val scaleX = xMax - xMin
        val scaleY = yMax - yMin
        val dX = xMax - xMin
        val dY = yMax - yMin
        return floatArrayOf(x * scaleX - dX / 2f, y * - scaleY + dY / 2f)
    }

    private fun findFaceData(): FaceData? {
        return pendingRenderFaceData.pollFirst()
    }

    private fun Array<Point>.toGlFacePoints(
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

    private fun Point.distance(targetPoint: Point): Float {
        val dx = x - targetPoint.x
        val dy = y - targetPoint.y
        return sqrt(dx * dx + dy * dy)
    }

    private fun Point.rotate(degree: Float, cx: Float = 0.5f, cy: Float = 0.5f): Point {
        val transform = android.graphics.Matrix()
        transform.setRotate(degree, cx, cy)
        val array = floatArrayOf(x, y)
        transform.mapPoints(array)
        return Point(array[0], array[1])
    }


    private fun centerCropTextureRect(targetRatio: Float, topLeftPoint: Point, bottomRightPoint: Point): Pair<Point, Point> {
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

    private fun centerCropPositionRect(targetRatio: Float, topLeftPoint: Point, bottomRightPoint: Point): Pair<Point, Point> {
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

    private fun centerPoint(topLeftPoint: Point, bottomRightPoint: Point): Point {
        return Point(x = (topLeftPoint.x + bottomRightPoint.x) / 2.0f, y = (topLeftPoint.y + bottomRightPoint.y) / 2.0f)
    }

    companion object {

        data class Point(
            val x: Float,
            val y: Float
        )

        data class FaceData(
            val timestamp: Long,
            // 4 point
            val faceFrame: Array<Point>,
            // 69 个点
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
            // 15 个点
            val leftEyeIrisF: Array<Point>,
            // 5 个点
            val rightEyeIris: Array<Point>,
            // 15 个点
            val rightEyeIrisF: Array<Point>,
            // 47 个点
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

        private data class InitData(
            val cameraProgram: Int,
            val cameraVAO: Int,
            val cameraVBO: Int,
            val cameraEBO: Int,
            val cameraTexture: Int,
            val faceProgram: Int,
            val faceVAO: Int,
            val faceVBO: Int
        )
    }
}