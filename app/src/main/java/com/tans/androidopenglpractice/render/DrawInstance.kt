package com.tans.androidopenglpractice.render

import android.graphics.BitmapFactory
import android.opengl.GLES31
import android.opengl.GLUtils
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class DrawInstance : IShapeRender {

    override val isActive: AtomicBoolean = AtomicBoolean(false)
    override var width: Int = 0
    override var height: Int = 0
    override val logTag: String = "DrawInstance"

    private val renderData: AtomicReference<RenderData?> by lazy {
        AtomicReference(null)
    }

    override fun onSurfaceCreated(owner: MyOpenGLView, gl: GL10, config: EGLConfig) {
        super.onSurfaceCreated(owner, gl, config)
        val program = compileShaderFromAssets(owner.context, "draw_instant.vert", "draw_instant.frag")
        if (program != null) {
            val vao = glGenVertexArrays()
            val posVbo = glGenBuffers()
            val offsetVbo = glGenBuffers()
            val vert = floatArrayOf(
                -0.5f, 0.5f,    0.0f, 0.0f,
                0.5f, 0.5f,     1.0f, 0.0f,
                0.5f, -0.5f,    1.0f, 1.0f,
                -0.5f, -0.5f,   0.0f, 1.0f
            )
            GLES31.glBindVertexArray(vao)
            GLES31.glBindBuffer(GLES31.GL_ARRAY_BUFFER, posVbo)
            GLES31.glVertexAttribPointer(0, 4, GLES31.GL_FLOAT, false, 4 * 4, 0)
            GLES31.glEnableVertexAttribArray(0)
            GLES31.glBufferData(GLES31.GL_ARRAY_BUFFER, vert.size * 4, vert.toGlBuffer(), GLES31.GL_STATIC_DRAW)

            GLES31.glBindBuffer(GLES31.GL_ARRAY_BUFFER, offsetVbo)
            GLES31.glVertexAttribPointer(1, 2, GLES31.GL_FLOAT, false, 4 * 2, 0)
            GLES31.glBufferData(GLES31.GL_ARRAY_BUFFER, 16, null, GLES31.GL_STATIC_DRAW)
            GLES31.glEnableVertexAttribArray(1)
            GLES31.glVertexAttribDivisor(1, 1)


            val bitmap = owner.context.assets.open("emoji.png").use {
                BitmapFactory.decodeStream(it)
            }
            val texture = glGenTextureAndSetDefaultParams()
            GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, texture)
            GLUtils.texImage2D(GLES31.GL_TEXTURE_2D, 0, GLES31.GL_RGBA, bitmap, 0)
            bitmap.recycle()
            this.renderData.set(
                RenderData(
                    program = program,
                    vao = vao,
                    posVbo = posVbo,
                    offsetVbo = offsetVbo,
                    texture = texture
                )
            )
        }
    }

    override fun onDrawFrame(owner: MyOpenGLView, gl: GL10) {
        val renderData = this.renderData.get()
        if (renderData != null) {
            GLES31.glClear(GLES31.GL_COLOR_BUFFER_BIT)
            GLES31.glUseProgram(renderData.program)
            GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, renderData.texture)
            val offset = floatArrayOf(
                -0.5f, 0.5f,
                0.5f, -0.5f
            )
            GLES31.glBindVertexArray(renderData.vao)
            GLES31.glBindBuffer(GLES31.GL_ARRAY_BUFFER, renderData.offsetVbo)
            GLES31.glBufferData(GLES31.GL_ARRAY_BUFFER, offset.size * 4, offset.toGlBuffer(), GLES31.GL_STATIC_DRAW)
            GLES31.glDrawArraysInstanced(GLES31.GL_TRIANGLE_FAN, 0, 4, 2)
        }
    }

    override fun onViewDestroyed(owner: MyOpenGLView) {
        super.onViewDestroyed(owner)
        val renderData = this.renderData.get()
        if (renderData != null) {
            GLES31.glDeleteProgram(renderData.program)
            GLES31.glDeleteBuffers(2, intArrayOf(renderData.posVbo, renderData.offsetVbo), 0)
            GLES31.glDeleteTextures(1, intArrayOf(renderData.texture), 0)
        }
    }


     companion object {
         private data class RenderData(
             val program: Int,
             val vao: Int,
             val posVbo: Int,
             val offsetVbo: Int,
             val texture: Int
         )
     }

}