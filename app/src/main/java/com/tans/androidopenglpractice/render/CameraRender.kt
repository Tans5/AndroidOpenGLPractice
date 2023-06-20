package com.tans.androidopenglpractice.render

import android.opengl.GLES31
import android.opengl.GLUtils
import android.opengl.Matrix
import androidx.camera.core.ImageProxy
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

    private var owner: MyOpenGLView? = null

    var scaleType: ScaleType = ScaleType.CenterCrop

    override fun onSurfaceCreated(owner: MyOpenGLView, gl: GL10, config: EGLConfig) {
        super.onSurfaceCreated(owner, gl, config)
        this.owner = owner
        val program = compileShaderProgram(cameraVertexRender, cameraFragmentRender)
        if (program != null) {
            val VAO = glGenVertexArrays()
            GLES31.glBindVertexArray(VAO)
            val VBO = glGenBuffers()
            GLES31.glBindBuffer(GLES31.GL_ARRAY_BUFFER, VBO)
            val EBO = glGenBuffers()

            // 纹理
            val texture = glGenTexture()
            GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, texture)
            GLES31.glTexParameteri(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_WRAP_S, GLES31.GL_REPEAT)
            GLES31.glTexParameteri(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_WRAP_T, GLES31.GL_REPEAT)
            GLES31.glTexParameteri(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_MIN_FILTER, GLES31.GL_LINEAR)
            GLES31.glTexParameteri(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_MAG_FILTER, GLES31.GL_LINEAR)
            GLES31.glGenerateMipmap(GLES31.GL_TEXTURE_2D)

            initData = InitData(
                VAO = VAO,
                VBO = VBO,
                EBO = EBO,
                program = program,
                texture = texture
            )
        }
    }

    override fun onDrawFrame(owner: MyOpenGLView, gl: GL10) {
        val initData = this.initData
        val imageData = pendingRenderFrames.pollFirst()
        if (initData != null && imageData != null) {
            GLES31.glClear(GLES31.GL_COLOR_BUFFER_BIT)
            GLES31.glUseProgram(initData.program)
            val (imageWidth, imageHeight) = when (imageData.rotation % 360) {
                in 0 until  90 -> imageData.width to imageData.height
                in 90 until  180 ->imageData.height to imageData.width
                in 180 until 270 -> imageData.width to imageData.height
                in 270 until  360 -> imageData.height to imageData.width
                else ->  imageData.width to imageData.height
            }
            val imageRatio = imageWidth.toFloat() / imageHeight.toFloat()
            val textureTransform = android.graphics.Matrix()
            textureTransform.setRotate(- imageData.rotation.toFloat(), 0.5f, 0.5f)
            val textureTopLeft = floatArrayOf(0.0f, 0.0f)
            val textureBottomLeft = floatArrayOf(0.0f, 1.0f)
            val textureTopRight = floatArrayOf(1.0f, 0.0f)
            val textureBottomRight = floatArrayOf(1.0f, 1.0f)
            textureTransform.mapPoints(textureTopLeft)
            textureTransform.mapPoints(textureBottomLeft)
            textureTransform.mapPoints(textureTopRight)
            textureTransform.mapPoints(textureBottomRight)
            val xMin = if (imageRatio < 1.0f) (-1f * imageRatio) else -1f
            val xMax = if (imageRatio < 1.0f) (1f * imageRatio) else 1f
            val yMin = if (imageRatio < 1.0f) (-1f * imageRatio) else -1f
            val yMax = if (imageRatio < 1.0f) (1f * imageRatio) else 1f
            val vertices = floatArrayOf(
                // 坐标           // 纹理坐标
                // 坐标(position 0)   // 纹理坐标
                xMin, yMax, 0.0f,   textureTopLeft[0], textureTopLeft[1],    // 左上角
                xMax, yMax, 0.0f,    textureTopRight[0], textureTopRight[1],   // 右上角
                xMax, yMin, 0.0f,   textureBottomRight[0], textureBottomRight[1],   // 右下角
                xMin, yMin, 0.0f,  textureBottomLeft[0], textureBottomLeft[1],   // 左下角
                // 多加一个废弃点.
                0.0f
            )
            GLES31.glBindVertexArray(initData.VAO)
            GLES31.glBindBuffer(GLES31.GL_ARRAY_BUFFER, initData.VBO)
            GLES31.glBufferData(GLES31.GL_ARRAY_BUFFER, vertices.size * 4, vertices.toGlBuffer(), GLES31.GL_STREAM_DRAW)
            GLES31.glVertexAttribPointer(0, 3, GLES31.GL_FLOAT, false, 5 * 4, 0)
            GLES31.glEnableVertexAttribArray(0)
            GLES31.glVertexAttribPointer(1, 3, GLES31.GL_FLOAT, false, 5 * 4, 3 * 4)
            GLES31.glEnableVertexAttribArray(1)

            GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, initData.texture)
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

            val renderRatio = width.toFloat() / height.toFloat()
            // View
            val viewMatrix = newGlFloatMatrix()
            Matrix.scaleM(viewMatrix, 0, 1 / renderRatio, 1.0f, 1.0f)
//            when (scaleType) {
//                ScaleType.CenterFit -> {
//                    if (renderRatio < positionRatio) {
//                        // width < height
//                        Matrix.scaleM(
//                            viewMatrix,
//                            0,
//                            renderRatio / positionRatio,
//                            renderRatio / positionRatio,
//                            1.0f
//                        )
//                    }
//                }
//                ScaleType.CenterCrop -> {
//                    if (renderRatio > positionRatio) {
//                        // width > height
//                        Matrix.scaleM(
//                            viewMatrix,
//                            0,
//                            renderRatio / positionRatio,
//                            renderRatio / positionRatio,
//                            1.0f
//                        )
//                    }
//                }
//            }

            // 镜像显示
            Matrix.rotateM(viewMatrix, 0, 180f, 0f, 1f, 0f)
            GLES31.glUniformMatrix4fv(GLES31.glGetUniformLocation(initData.program, "view"), 1, false, viewMatrix, 0)

            // model
            val modelMatrix = newGlFloatMatrix()
            GLES31.glUniformMatrix4fv(GLES31.glGetUniformLocation(initData.program, "model"), 1, false, modelMatrix, 0)

            // transform
            val transformMatrix = newGlFloatMatrix()
            GLES31.glUniformMatrix4fv(GLES31.glGetUniformLocation(initData.program, "transform"), 1, false, transformMatrix, 0)

            val indices = intArrayOf(
                0, 1, 2, // 第一个三角形
                2, 3, 0 // 第二个三角形
            )
            GLES31.glBindBuffer(GLES31.GL_ELEMENT_ARRAY_BUFFER, initData.EBO)
            GLES31.glBufferData(GLES31.GL_ELEMENT_ARRAY_BUFFER, indices.size * 4, indices.toGlBuffer(), GLES31.GL_STREAM_DRAW)
            GLES31.glDrawElements(GLES31.GL_TRIANGLES, 6, GLES31.GL_UNSIGNED_INT, 0)
        }
    }

    override fun onViewDestroyed(owner: MyOpenGLView) {
        super.onViewDestroyed(owner)
        initData = null
        this.owner = null
    }

    fun cameraReady(imageData: ImageData) {
        this.owner?.let {
            pendingRenderFrames.put(imageData)
            it.requestRender()
        }
    }

    companion object {

        enum class ImageType {
            NV21, RGBA
        }

        data class ImageData(
            val image: ByteArray,
            val width: Int,
            val height: Int,
            val rotation: Int,
            val imageType: ImageType,
            val imageProxy: ImageProxy
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
            val VAO: Int,
            val VBO: Int,
            val EBO: Int,
            val program: Int,
            val texture: Int
        )

        private const val cameraVertexRender = """#version 310 es
            layout (location = 0) in vec3 aPos;
            layout (location = 1) in vec2 aTexCoord;
            uniform mat4 transform;
            uniform mat4 model;
            uniform mat4 view;
            out vec2 TexCoord;
            void main() {
                gl_Position = view * model * transform * vec4(aPos, 1.0);
                TexCoord = aTexCoord;
            }
        """

        private const val cameraFragmentRender = """#version 310 es
            precision highp float; // Define float precision
            uniform sampler2D Texture;
            in vec2 TexCoord;
            out vec4 FragColor;
            void main() {
                FragColor = texture(Texture, TexCoord);
                // FragColor = vec4(1.0, 0.0, 0.0, 0.0);
            }
        """
    }
}