package com.tans.androidopenglpractice.render

import android.opengl.GLES31
import java.util.concurrent.atomic.AtomicBoolean
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class SimpleTriangleRender : IShapeRender {
    private var initData: InitData? = null

    override val isActive: AtomicBoolean = AtomicBoolean(false)

    override var width: Int = 0
    override var height: Int = 0

    override val logTag: String = "SimpleTriangleRender"

    override fun onSurfaceCreated(gl: GL10, config: EGLConfig) {
        super.onSurfaceCreated(gl, config)
        val program = compileShaderProgram(triangleVertexShader, triangleFragmentShader)
        if (program != null) {
            val VBO = glGenBuffers()
            val VAO = glGenVertexArrays()
            val vertices = floatArrayOf(
                // 坐标               // 颜色
                -0.5f, 0.5f, 0.0f,   1.0f, 0.0f, 0.0f,
                0.5f, 0.5f, 0.0f,    0.0f, 1.0f, 0.0f,
                -0.5f, -0.5f, 0.0f,  0.0f, 0.0f, 1.0f,
            )
            GLES31.glBindVertexArray(VAO)
            GLES31.glBindBuffer(GLES31.GL_ARRAY_BUFFER, VBO)
            GLES31.glBufferData(GLES31.GL_ARRAY_BUFFER, vertices.size * 4, vertices.toGlBuffer(), GLES31.GL_STATIC_DRAW)

            // 设置顶点属性
            GLES31.glVertexAttribPointer(0, 3, GLES31.GL_FLOAT, false, 24, 0)
            GLES31.glEnableVertexAttribArray(0)
            GLES31.glVertexAttribPointer(1, 3, GLES31.GL_FLOAT, false, 24, 12)
            GLES31.glEnableVertexAttribArray(1)

            initData = InitData(
                VAO = VAO,
                VBO = VBO,
                program = program
            )
        }
    }

    override fun onDrawFrame(gl: GL10) {
        val initData = this.initData
        if (initData != null) {
            GLES31.glUseProgram(initData.program)
            GLES31.glBindVertexArray(initData.VBO)
            GLES31.glDrawArrays(GLES31.GL_TRIANGLES, 0, 3)
        }
    }

    override fun onViewDestroyed() {
        super.onViewDestroyed()
    }

    companion object {

        private data class InitData(
            val VAO: Int,
            val VBO: Int,
            val program: Int
        )

        private const val triangleVertexShader = """#version 310 es
            layout (location = 0) in vec3 aPos;
            layout (location = 1) in vec3 aColor;
            out vec3 pColor;
            void main() {
                gl_Position = vec4(aPos, 1.0);
                pColor = aColor;
            }
        """

        private const val triangleFragmentShader = """#version 310 es 
            precision highp float; // Define float precision
            in vec3 pColor;
            out vec4 FragColor;
            void main() {
                FragColor = vec4(pColor, 1.0);
            }
        """

    }
}