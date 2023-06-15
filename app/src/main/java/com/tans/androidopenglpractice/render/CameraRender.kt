package com.tans.androidopenglpractice.render

import android.graphics.BitmapFactory
import android.opengl.GLES31
import android.opengl.GLUtils
import android.opengl.Matrix
import androidx.camera.core.ImageProxy
import androidx.camera.core.internal.utils.ImageUtil
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

    private val pendingRenderFrames: LinkedBlockingDeque<ImageProxy> by lazy {
        LinkedBlockingDeque()
    }

    private var owner: MyOpenGLView? = null

    override fun onSurfaceCreated(owner: MyOpenGLView, gl: GL10, config: EGLConfig) {
        super.onSurfaceCreated(owner, gl, config)
        this.owner = owner
        val program = compileShaderProgram(cameraVertexRender, cameraFragmentRender)
        if (program != null) {
            val VAO = glGenVertexArrays()
            GLES31.glBindVertexArray(VAO)
            val VBO = glGenBuffers()
            GLES31.glBindBuffer(GLES31.GL_ARRAY_BUFFER, VBO)
//            GLES31.glVertexAttribPointer(0, 3, GLES31.GL_FLOAT, false, 5 * 4, 0)
//            GLES31.glEnableVertexAttribArray(0)
//            GLES31.glVertexAttribPointer(1, 3, GLES31.GL_FLOAT, false, 5 * 4, 3 * 4)
//            GLES31.glEnableVertexAttribArray(1)
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
        val imageProxy = pendingRenderFrames.pollFirst()
        if (initData != null && imageProxy != null) {
            GLES31.glClear(GLES31.GL_COLOR_BUFFER_BIT)
            GLES31.glUseProgram(initData.program)
            val imageWidth = imageProxy.width
            val imageHeight = imageProxy.height
            val imageRatio = imageWidth.toFloat() / imageHeight.toFloat()
            val xMin = imageRatio * -1f
            val xMax = imageRatio * 1f
            val yMin = -1f
            val yMax = 1f
            val vertices = floatArrayOf(
                // 坐标           // 纹理坐标
                // 坐标(position 0)   // 纹理坐标
                xMax, yMax, 0.0f,    1.0f, 0.0f,   // 右上角
                xMax, yMin, 0.0f,   1.0f, 1.0f,   // 右下角
                xMin, yMin, 0.0f,  0.0f, 1.0f,   // 左下角
                xMin, yMax, 0.0f,   0.0f, 0.0f    // 左上角
            )
            GLES31.glBindVertexArray(initData.VAO)
            GLES31.glBindBuffer(GLES31.GL_ARRAY_BUFFER, initData.VBO)
            GLES31.glBufferData(GLES31.GL_ARRAY_BUFFER, vertices.size * 4, vertices.toGlBuffer(), GLES31.GL_STATIC_DRAW)
            GLES31.glVertexAttribPointer(0, 3, GLES31.GL_FLOAT, false, 5 * 4, 0)
            GLES31.glEnableVertexAttribArray(0)
            GLES31.glVertexAttribPointer(1, 3, GLES31.GL_FLOAT, false, 5 * 4, 3 * 4)
            GLES31.glEnableVertexAttribArray(1)

            GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, initData.texture)
            val jpegByteArray = ImageUtil.yuvImageToJpegByteArray(imageProxy, null , 50)
            val bitmap = BitmapFactory.decodeByteArray(jpegByteArray, 0, jpegByteArray.size)
            GLUtils.texImage2D(GLES31.GL_TEXTURE_2D, 0, bitmap, 0)
            bitmap.recycle()
            imageProxy.close()

            val renderRatio = width.toFloat() / height.toFloat()
            // View
            val viewMatrix = newGlFloatMatrix()
            Matrix.scaleM(viewMatrix, 0, renderRatio, 1.0f, 1.0f)
            GLES31.glUniformMatrix4fv(GLES31.glGetUniformLocation(initData.program, "view"), 1, false, viewMatrix, 0)

            // model
            val modelMatrix = newGlFloatMatrix()
            GLES31.glUniformMatrix4fv(GLES31.glGetUniformLocation(initData.program, "model"), 1, false, modelMatrix, 0)

            // transform
            val transformMatrix = newGlFloatMatrix()
            GLES31.glUniformMatrix4fv(GLES31.glGetUniformLocation(initData.program, "transform"), 1, false, transformMatrix, 0)

            val indices = intArrayOf(
                0, 1, 2, // 第一个三角形
                0, 2, 3 // 第二个三角形
            )
            GLES31.glBindBuffer(GLES31.GL_ELEMENT_ARRAY_BUFFER, initData.EBO)
            GLES31.glBufferData(GLES31.GL_ELEMENT_ARRAY_BUFFER, indices.size * 4, indices.toGlBuffer(), GLES31.GL_STATIC_DRAW)
            GLES31.glDrawElements(GLES31.GL_TRIANGLES, 6, GLES31.GL_UNSIGNED_INT, 0)
        }
    }

    override fun onViewDestroyed(owner: MyOpenGLView) {
        super.onViewDestroyed(owner)
        initData = null
        this.owner = null
    }

    fun cameraReady(imageProxy: ImageProxy) {
        this.owner?.let {
            pendingRenderFrames.put(imageProxy)
            it.requestRender()
        } ?: imageProxy.close()
    }

    companion object {

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