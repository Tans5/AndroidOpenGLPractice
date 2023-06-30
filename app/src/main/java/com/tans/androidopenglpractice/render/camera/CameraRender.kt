package com.tans.androidopenglpractice.render.camera

import android.opengl.GLES31
import android.opengl.GLUtils
import android.opengl.Matrix
import com.tans.androidopenglpractice.render.IShapeRender
import com.tans.androidopenglpractice.render.MyOpenGLView
import com.tans.androidopenglpractice.render.glGenBuffers
import com.tans.androidopenglpractice.render.glGenTexture
import com.tans.androidopenglpractice.render.glGenVertexArrays
import com.tans.androidopenglpractice.render.newGlFloatMatrix
import com.tans.androidopenglpractice.render.nv21ToBitmap
import com.tans.androidopenglpractice.render.rgbaToBitmap
import com.tans.androidopenglpractice.render.toGlBuffer
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.atomic.AtomicBoolean
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

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

    private val pointFilters: Array<KalmanPointFilter> by lazy {
        Array(256) { KalmanPointFilter() }
    }

    private var owner: MyOpenGLView? = null

    var scaleType: ScaleType = ScaleType.CenterCrop

    var mirror: Boolean = true

    var renderFaceFrame: Boolean = false

    var enlargeEyes: Boolean = true

    var whitening: Boolean = true

    var thinFace: Boolean = true

    var smoothSkin: Boolean = true

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

            // 美颜
            beautify(
                initData = initData,
                imageData = imageData,
                faceData = faceData,
                rotation = rotation
            )

            val indices = intArrayOf(
                0, 1, 2, // 第一个三角形
                2, 3, 0 // 第二个三角形
            )
            GLES31.glBindBuffer(GLES31.GL_ELEMENT_ARRAY_BUFFER, initData.cameraEBO)
            GLES31.glBufferData(GLES31.GL_ELEMENT_ARRAY_BUFFER, indices.size * 4, indices.toGlBuffer(), GLES31.GL_STREAM_DRAW)
            GLES31.glDrawElements(GLES31.GL_TRIANGLES, 6, GLES31.GL_UNSIGNED_INT, 0)

            // 绘制脸部框架
            drawFaceFrame(
                initData = initData,
                textureTl = textureTl,
                textureRb = textureRb,
                viewMatrix = viewMatrix,
                modelMatrix = modelMatrix,
                transformMatrix = transformMatrix,
                xMin = xMin,
                xMax = xMax,
                yMin = yMin,
                yMax = yMax,
                faceData = faceData
            )
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


    /**
     * 美颜
     */
    private fun beautify(initData: InitData, imageData: ImageData, faceData: FaceData?, rotation: Int) {
        // 大眼
        val leftEyeCenter = floatArrayOf(0.0f, 0.0f)
        var leftEyeAxisA = 0f
        var leftEyeAxisB = 0f
        val rightEyeCenter = floatArrayOf(0.0f, 0.0f)
        var rightEyeAxisA = 0f
        var rightEyeAxisB = 0f
        if (faceData != null && enlargeEyes) {
            val leftOval = faceData.leftEyeIrisF.computeFaceTextureOval().rotate(360 - rotation)
            leftEyeCenter[0] = leftOval.center.x
            leftEyeCenter[1] = leftOval.center.y
            leftEyeAxisA = leftOval.a * 1.1f
            leftEyeAxisB = leftOval.b * 1.1f
            val rightOval = faceData.rightEyeIrisF.computeFaceTextureOval().rotate(360 - rotation)
            rightEyeCenter[0] = rightOval.center.x
            rightEyeCenter[1] = rightOval.center.y
            rightEyeAxisA = rightOval.a * 1.1f
            rightEyeAxisB = rightOval.b * 1.1f
        }
        GLES31.glUniform2f(GLES31.glGetUniformLocation(initData.cameraProgram, "leftEyeCenter"), leftEyeCenter[0], leftEyeCenter[1])
        GLES31.glUniform1f(GLES31.glGetUniformLocation(initData.cameraProgram, "leftEyeA"), leftEyeAxisA)
        GLES31.glUniform1f(GLES31.glGetUniformLocation(initData.cameraProgram, "leftEyeB"), leftEyeAxisB)
        GLES31.glUniform2f(GLES31.glGetUniformLocation(initData.cameraProgram, "rightEyeCenter"), rightEyeCenter[0], rightEyeCenter[1])
        GLES31.glUniform1f(GLES31.glGetUniformLocation(initData.cameraProgram, "rightEyeA"), rightEyeAxisA)
        GLES31.glUniform1f(GLES31.glGetUniformLocation(initData.cameraProgram, "rightEyeB"), rightEyeAxisB)

        // 美白
        GLES31.glUniform1i(GLES31.glGetUniformLocation(initData.cameraProgram, "whiteningSwitch"), if (whitening) 1 else 0)

        // 瘦脸
        var thinRadius = 0.0f
        val stretchCenter = floatArrayOf(0.0f, 0.0f)
        val leftFaceThinCenter = floatArrayOf(0.0f, 0.0f)
        val rightFaceThinCenter = floatArrayOf(0.0f, 0.0f)
        if (faceData != null && thinFace) {
            val thinData = faceData.computeThinFaceData(360 - rotation)
            thinRadius = thinData.thinRadius
            stretchCenter[0] = thinData.stretchCenter.x
            stretchCenter[1] = thinData.stretchCenter.y
            leftFaceThinCenter[0] = thinData.leftFaceThinCenter.x
            leftFaceThinCenter[1] = thinData.leftFaceThinCenter.y
            rightFaceThinCenter[0] = thinData.rightFaceThinCenter.x
            rightFaceThinCenter[1] = thinData.rightFaceThinCenter.y
        }
        GLES31.glUniform1f(GLES31.glGetUniformLocation(initData.cameraProgram, "thinRadius"), thinRadius)
        GLES31.glUniform2f(GLES31.glGetUniformLocation(initData.cameraProgram, "stretchCenter"), stretchCenter[0], stretchCenter[1])
        GLES31.glUniform2f(GLES31.glGetUniformLocation(initData.cameraProgram, "leftFaceThinCenter"), leftFaceThinCenter[0], leftFaceThinCenter[1])
        GLES31.glUniform2f(GLES31.glGetUniformLocation(initData.cameraProgram, "rightFaceThinCenter"), rightFaceThinCenter[0], rightFaceThinCenter[1])


        // 磨皮
        GLES31.glUniform1i(GLES31.glGetUniformLocation(initData.cameraProgram, "skinSmoothSwitch"), if (smoothSkin) 1 else 0)
        GLES31.glUniform1f(GLES31.glGetUniformLocation(initData.cameraProgram, "textureWidthPixelStep"), 1.0f / imageData.width.toFloat())
        GLES31.glUniform1f(GLES31.glGetUniformLocation(initData.cameraProgram, "textureHeightPixelStep"), 1.0f / imageData.height.toFloat())
    }

    /**
     * 绘制脸部框架.
     */
    private fun drawFaceFrame(
        initData: InitData,
        textureTl: Point,
        textureRb: Point,
        viewMatrix: FloatArray,
        modelMatrix: FloatArray,
        transformMatrix: FloatArray,
        xMin: Float,
        xMax: Float,
        yMin: Float,
        yMax: Float,
        faceData: FaceData?
    ) {
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
                points =  arrayOf(
                    faceData.leftEyeIrisF[0],
                    faceData.leftEyeIrisF[6],
                    faceData.leftEyeIrisF[12],
                ),
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
                points = arrayOf(
                    faceData.rightEyeIrisF[0],
                    faceData.rightEyeIrisF[6],
                    faceData.rightEyeIrisF[12],
                ),
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

    private fun findFaceData(): FaceData? {
        val needToFix = pendingRenderFaceData.pollFirst()
        return if (needToFix == null) {
            resetPointFilters()
            null
        } else {
            needToFix.copy(
                faceFrame = needToFix.faceFrame.filterPoints(0),
                check = needToFix.check.filterPoints(4),
                leftEyebrow = needToFix.leftEyebrow.filterPoints(73),
                rightEyebrow = needToFix.rightEyebrow.filterPoints(89),
                leftEye = needToFix.leftEye.filterPoints(105),
                rightEye = needToFix.rightEye.filterPoints(121),
                leftEyeIris = needToFix.leftEyeIris.filterPoints(137),
                leftEyeIrisF = needToFix.leftEyeIrisF.filterPoints(142),
                rightEyeIris = needToFix.rightEyeIris.filterPoints(157),
                rightEyeIrisF = needToFix.rightEyeIrisF.filterPoints(162),
                nose = needToFix.nose.filterPoints(177),
                upLip = needToFix.upLip.filterPoints(224),
                downLip = needToFix.downLip.filterPoints(240)
            )
        }
    }

    private fun Array<Point>.filterPoints(filterOffset: Int): Array<Point> {
        return this.withIndex()
            .map { (i, point) ->
                pointFilters[i + filterOffset].filter(point)
            }
            .toTypedArray()
    }

    private fun resetPointFilters() {
        for (f in pointFilters) {
            f.reset()
        }
    }
}