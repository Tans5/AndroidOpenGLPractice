package com.tans.androidopenglpractice.render

import android.graphics.BitmapFactory
import android.opengl.GLES31
import android.opengl.GLUtils
import android.opengl.Matrix
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.sin

class CubeRender : IShapeRender {

    override val isActive: AtomicBoolean = AtomicBoolean(false)
    override var width: Int = 0
    override var height: Int = 0
    override val logTag: String = "CubeRender"

    private var initData: InitData? = null

    override fun onSurfaceCreated(owner: MyOpenGLView, gl: GL10, config: EGLConfig) {
        super.onSurfaceCreated(owner, gl, config)
        val program = compileShaderProgram(cubeVertexRender, cubeFragmentRender)
        if (program != null) {
            // 一个正方体 6 个面；每个面由 2 个三角形组成；每个三角形 3 个点组成；所以总共 36 个点。
            val vertices = floatArrayOf(
                -0.5f, -0.5f, -0.5f,  0.0f, 0.0f,
                0.5f, -0.5f, -0.5f,  1.0f, 0.0f,
                0.5f,  0.5f, -0.5f,  1.0f, 1.0f,
                0.5f,  0.5f, -0.5f,  1.0f, 1.0f,
                -0.5f,  0.5f, -0.5f,  0.0f, 1.0f,
                -0.5f, -0.5f, -0.5f,  0.0f, 0.0f,

                -0.5f, -0.5f,  0.5f,  0.0f, 0.0f,
                0.5f, -0.5f,  0.5f,  1.0f, 0.0f,
                0.5f,  0.5f,  0.5f,  1.0f, 1.0f,
                0.5f,  0.5f,  0.5f,  1.0f, 1.0f,
                -0.5f,  0.5f,  0.5f,  0.0f, 1.0f,
                -0.5f, -0.5f,  0.5f,  0.0f, 0.0f,

                -0.5f,  0.5f,  0.5f,  1.0f, 0.0f,
                -0.5f,  0.5f, -0.5f,  1.0f, 1.0f,
                -0.5f, -0.5f, -0.5f,  0.0f, 1.0f,
                -0.5f, -0.5f, -0.5f,  0.0f, 1.0f,
                -0.5f, -0.5f,  0.5f,  0.0f, 0.0f,
                -0.5f,  0.5f,  0.5f,  1.0f, 0.0f,

                0.5f,  0.5f,  0.5f,  1.0f, 0.0f,
                0.5f,  0.5f, -0.5f,  1.0f, 1.0f,
                0.5f, -0.5f, -0.5f,  0.0f, 1.0f,
                0.5f, -0.5f, -0.5f,  0.0f, 1.0f,
                0.5f, -0.5f,  0.5f,  0.0f, 0.0f,
                0.5f,  0.5f,  0.5f,  1.0f, 0.0f,

                -0.5f, -0.5f, -0.5f,  0.0f, 1.0f,
                0.5f, -0.5f, -0.5f,  1.0f, 1.0f,
                0.5f, -0.5f,  0.5f,  1.0f, 0.0f,
                0.5f, -0.5f,  0.5f,  1.0f, 0.0f,
                -0.5f, -0.5f,  0.5f,  0.0f, 0.0f,
                -0.5f, -0.5f, -0.5f,  0.0f, 1.0f,

                -0.5f,  0.5f, -0.5f,  0.0f, 1.0f,
                0.5f,  0.5f, -0.5f,  1.0f, 1.0f,
                0.5f,  0.5f,  0.5f,  1.0f, 0.0f,
                0.5f,  0.5f,  0.5f,  1.0f, 0.0f,
                -0.5f,  0.5f,  0.5f,  0.0f, 0.0f,
                -0.5f,  0.5f, -0.5f,  0.0f, 1.0f,

                // Android 中要多加一个点，否者纹理渲染出错，不知道什么原因。
                0f,  0f, -0f,  0f, 0f,
            )
            val VAO = glGenVertexArrays()
            GLES31.glBindVertexArray(VAO)
            val VBO = glGenBuffers()
            GLES31.glBindBuffer(GLES31.GL_ARRAY_BUFFER, VBO)
            GLES31.glBufferData(GLES31.GL_ARRAY_BUFFER, vertices.size * 4, vertices.toGlBuffer(), GLES31.GL_STATIC_DRAW)
            GLES31.glVertexAttribPointer(0, 3, GLES31.GL_FLOAT, false, 5 * 4, 0)
            GLES31.glEnableVertexAttribArray(0)
            GLES31.glVertexAttribPointer(1, 3, GLES31.GL_FLOAT, false, 5 * 4, 3 * 4)
            GLES31.glEnableVertexAttribArray(1)

            val androidContext = owner.context
            val bitmap = try {
                androidContext.assets.open("container.jpeg").use { BitmapFactory.decodeStream(it) }
            } catch (e: Throwable) {
                e.printStackTrace()
                Log.e(logTag, "Load texture bitmap fail: ${e.message}", e)
                null
            }
            var texture: Int? = null
            if (bitmap != null) {
                texture = glGenTexture()
                GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, texture)
                GLES31.glTexParameteri(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_WRAP_S, GLES31.GL_REPEAT)
                GLES31.glTexParameteri(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_WRAP_T, GLES31.GL_REPEAT)
                GLES31.glTexParameteri(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_MIN_FILTER, GLES31.GL_LINEAR)
                GLES31.glTexParameteri(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_MAG_FILTER, GLES31.GL_LINEAR)
                GLES31.glGenerateMipmap(GLES31.GL_TEXTURE_2D)
                GLUtils.texImage2D(GLES31.GL_TEXTURE_2D, 0, bitmap, 0)
                bitmap.recycle()
            }
            initData = InitData(
                VAO = VAO,
                VBO = VBO,
                program = program,
                texture = texture
            )
        }
    }

    override fun onDrawFrame(owner: MyOpenGLView, gl: GL10) {
        val initData = this.initData
        if (initData != null) {

            // Z 缓冲
            GLES31.glEnable(GLES31.GL_DEPTH_TEST)
            GLES31.glClear(GLES31.GL_DEPTH_BUFFER_BIT or GLES31.GL_COLOR_BUFFER_BIT)

            GLES31.glUseProgram(initData.program)

            val ratio = width.toFloat() / height.toFloat()
            val time = SystemClock.uptimeMillis()

            // Projection
            val projectionMatrix = newGlFloatMatrix()
            Matrix.perspectiveM(projectionMatrix, 0, 45.0f, ratio, 0.1f, 100f)
            GLES31.glUniformMatrix4fv(GLES31.glGetUniformLocation(initData.program, "projection"), 1, false, projectionMatrix, 0)

            // View
            val viewMatrix = newGlFloatMatrix()
            val radius = 10f
            val eyeX = sin(Math.toRadians(time.toDouble() / 20f)) * radius
            val eyeZ = cos(Math.toRadians(time.toDouble() / 20f)) * radius
            Matrix.setLookAtM(viewMatrix, 0, eyeX.toFloat(), 0f, eyeZ.toFloat(), 0f, 0f, 0f, 0f, 1f, 0f)
            GLES31.glUniformMatrix4fv(GLES31.glGetUniformLocation(initData.program, "view"), 1, false, viewMatrix, 0)

            // transform
            val transformMatrix = newGlFloatMatrix()
            Matrix.rotateM(transformMatrix, 0, ((time / 10) % 360).toFloat(), 0.5f, 1.0f, 0.0f)
            GLES31.glUniformMatrix4fv(GLES31.glGetUniformLocation(initData.program, "transform"), 1, false, transformMatrix, 0)

            val cubePositions = listOf<FloatArray>(
                floatArrayOf(0f, 0f, 0f),
                floatArrayOf(2f, 5f, -15f),
                floatArrayOf(-1.5f, -2.2f, -2.5f),
                floatArrayOf(-3.8f, -2.0f, -12.3f),
                floatArrayOf(2.4f, -0.4f, -3.5f),
                floatArrayOf(-1.7f,  3.0f, -7.5f),
                floatArrayOf(1.3f, -2.0f, -2.5f),
                floatArrayOf(1.5f,  2.0f, -2.5f),
                floatArrayOf(1.5f,  0.2f, -1.5f),
                floatArrayOf(-1.3f,  1.0f, -1.5f),
            )

            for ((i, p) in cubePositions.withIndex()) {
                // model
                val modelMatrix = newGlFloatMatrix()
                Matrix.translateM(modelMatrix, 0, p[0], p[1], p[2])
                val angle = 20.0f * i
                Matrix.rotateM(modelMatrix, 0, angle, 1.0f, 0.3f, 0.5f)
                GLES31.glUniformMatrix4fv(GLES31.glGetUniformLocation(initData.program, "model"), 1, false, modelMatrix, 0)

                GLES31.glBindVertexArray(initData.VAO)
                GLES31.glDrawArrays(GLES31.GL_TRIANGLES, 0, 36)
            }
        }
    }

    override fun onViewDestroyed(owner: MyOpenGLView) {
        super.onViewDestroyed(owner)
        initData = null
    }


    companion object {

        private data class InitData(
            val VAO: Int,
            val VBO: Int,
            val program: Int,
            val texture: Int?
        )

        private const val cubeVertexRender = """#version 310 es
            layout (location = 0) in vec3 aPos;
            layout (location = 1) in vec2 aTexCoord;
            uniform mat4 transform;
            uniform mat4 model;
            uniform mat4 view;
            uniform mat4 projection;
            out vec2 TexCoord;
            void main() {
                gl_Position = projection * view * model * transform * vec4(aPos, 1.0);
                TexCoord = aTexCoord;
            }
        """

        private const val cubeFragmentRender = """#version 310 es
            precision highp float; // Define float precision
            uniform sampler2D Texture;
            in vec2 TexCoord;
            out vec4 FragColor;
            void main() {
                FragColor = texture(Texture, TexCoord);
            }
        """
    }
}