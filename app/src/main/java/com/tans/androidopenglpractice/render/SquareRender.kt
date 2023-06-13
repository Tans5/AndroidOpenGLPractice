package com.tans.androidopenglpractice.render

import android.opengl.GLES31
import android.opengl.Matrix
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicBoolean
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class SquareRender : IShapeRender {

    override val isActive: AtomicBoolean = AtomicBoolean(false)
    override var width: Int = 0
    override var height: Int = 0
    override val logTag: String = "SquareRender"

    private var initData: InitData? = null

    override fun onSurfaceCreated(gl: GL10, config: EGLConfig) {
        super.onSurfaceCreated(gl, config)
        val program = compileShaderProgram(squareVertexRender, squareFragmentRender)
        if (program != null) {
            val vertices = floatArrayOf(
                // 坐标(position 0)   // 纹理坐标
                0.5f, 0.5f, 0.0f,    1.0f, 0.0f,   // 右上角
                0.5f, -0.5f, 0.0f,   1.0f, 1.0f,   // 右下角
                -0.5f, -0.5f, 0.0f,  0.0f, 1.0f,   // 左下角
                -0.5f, 0.5f, 0.0f,   0.0f, 0.0f    // 左上角
            )
            val indices = intArrayOf(
                0, 1, 2, // 第一个三角形
                0, 2, 3 // 第二个三角形
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
            val EBO = glGenBuffers()
            GLES31.glBindBuffer(GLES31.GL_ELEMENT_ARRAY_BUFFER, EBO)
            GLES31.glBufferData(GLES31.GL_ELEMENT_ARRAY_BUFFER, indices.size * 4, indices.toGlBuffer(), GLES31.GL_STATIC_DRAW)
            initData = InitData(
                VAO = VAO,
                VBO = VBO,
                EBO = EBO,
                program = program
            )
        }
    }

    override fun onDrawFrame(gl: GL10) {
        val initData = this.initData
        if (initData != null) {
            GLES31.glUseProgram(initData.program)
            val ratio = width.toFloat() / height.toFloat()

            // Projection
            val projectionMatrix = newGlFloatMatrix()
            Matrix.perspectiveM(projectionMatrix, 0, 45.0f, ratio, 0.1f, 100f,)
            GLES31.glUniformMatrix4fv(GLES31.glGetUniformLocation(initData.program, "projection"), 1, false, projectionMatrix, 0)

            // View
            val viewMatrix = newGlFloatMatrix()
            Matrix.translateM(viewMatrix, 0, 0f, 0f, -3f)
            GLES31.glUniformMatrix4fv(GLES31.glGetUniformLocation(initData.program, "view"), 1, false, viewMatrix, 0)

            // model
            val modelMatrix = newGlFloatMatrix()
            GLES31.glUniformMatrix4fv(GLES31.glGetUniformLocation(initData.program, "model"), 1, false, modelMatrix, 0)

            // transform
            val transformMatrix = newGlFloatMatrix()
            Matrix.rotateM(transformMatrix, 0, ((SystemClock.uptimeMillis() / 10) % 360).toFloat(), 1f, 0f, 0f)
            GLES31.glUniformMatrix4fv(GLES31.glGetUniformLocation(initData.program, "transform"), 1, false, transformMatrix, 0)

            GLES31.glBindBuffer(GLES31.GL_ELEMENT_ARRAY_BUFFER, initData.EBO)
            GLES31.glDrawElements(GLES31.GL_TRIANGLES, 6, GLES31.GL_UNSIGNED_INT, 0)
        }
    }

    override fun onViewDestroyed() {
        super.onViewDestroyed()
    }


    companion object {

        private data class InitData(
            val VAO: Int,
            val VBO: Int,
            val EBO: Int,
            val program: Int
        )

        private const val squareVertexRender = """#version 310 es
            layout (location = 0) in vec3 aPos;
            layout (location = 1) in vec2 aTexCoord;
            uniform mat4 transform;
            uniform mat4 model;
            uniform mat4 view;
            uniform mat4 projection;
            void main() {
                gl_Position = projection * view * model * transform * vec4(aPos, 1.0);
            }
        """

        private const val squareFragmentRender = """#version 310 es
            precision highp float; // Define float precision
            out vec4 FragColor;
            void main() {
                FragColor = vec4(1.0, 0.0, 0.0, 1.0);
            }
        """
    }
}