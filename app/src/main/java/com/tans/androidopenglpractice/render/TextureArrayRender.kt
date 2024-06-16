package com.tans.androidopenglpractice.render

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLES30
import android.opengl.GLES31
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class TextureArrayRender : IShapeRender {

    override val isActive: AtomicBoolean = AtomicBoolean(false)
    override var width: Int = 0
    override var height: Int = 0
    override val logTag: String = "TextureArrayRender"

    private val renderData: AtomicReference<RenderData?> by lazy {
        AtomicReference(null)
    }

    override fun onSurfaceCreated(owner: MyOpenGLView, gl: GL10, config: EGLConfig) {
        super.onSurfaceCreated(owner, gl, config)
        val program = compileShaderFromAssets(owner.context, "tex_array.vert", "tex_array.frag")
        if (program != null) {
            val vao = glGenVertexArrays()
            val vbo = glGenBuffers()
            GLES31.glBindVertexArray(vao)
            GLES31.glBindBuffer(GLES31.GL_ARRAY_BUFFER, vbo)
            GLES31.glBufferData(GLES31.GL_ARRAY_BUFFER, 5 * 4 * 4, null, GLES31.GL_STREAM_DRAW)
            GLES31.glVertexAttribPointer(0, 2, GLES31.GL_FLOAT, false, 5 * 4, 0)
            GLES31.glEnableVertexAttribArray(0)
            GLES31.glVertexAttribPointer(1, 3, GLES31.GL_FLOAT, false, 5 * 4, 2 * 4)
            GLES31.glEnableVertexAttribArray(1)

            val textureArray = glGenTexture()
            GLES31.glBindTexture(GLES31.GL_TEXTURE_2D_ARRAY, textureArray)
            GLES31.glTexStorage3D(GLES31.GL_TEXTURE_2D_ARRAY, 1, GLES31.GL_RGBA8, 512, 512, 2)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D_ARRAY, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_REPEAT)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D_ARRAY, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_REPEAT)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D_ARRAY, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D_ARRAY, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D_ARRAY)
            fun loadBitmapToTexture(i: Int, name: String) {
                val bitmap = owner.context.assets.open(name).use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    })
                }!!
                val buffer = ByteBuffer.allocate(bitmap.width * bitmap.height * 4)
                bitmap.copyPixelsToBuffer(buffer)
                buffer.position(0)
                GLES31.glTexSubImage3D(GLES31.GL_TEXTURE_2D_ARRAY, 0, 0, 0, i, bitmap.width, bitmap.height,
                    1, GLES31.GL_RGBA, GLES31.GL_UNSIGNED_BYTE, buffer)
                bitmap.recycle()
            }
            loadBitmapToTexture(0, "container.png")
            loadBitmapToTexture(1, "emoji.png")
            val renderData = RenderData(
                program = program,
                textureArray = textureArray,
                vao = vao,
                vbo = vbo
            )
            this.renderData.set(renderData)
        }
    }
    override fun onDrawFrame(owner: MyOpenGLView, gl: GL10) {
        val renderData = renderData.get()
        if (renderData != null) {
            GLES31.glClear(GLES31.GL_COLOR_BUFFER_BIT)
            GLES31.glUseProgram(renderData.program)
            val vert1 = floatArrayOf(
                0.0f, 0.0f,     0.0f, 1.0f, 0.0f,
                1.0f, 0.0f,     1.0f, 1.0f, 0.0f,
                1.0f, 1.0f,     1.0f, 0.0f, 0.0f,
                0.0f, 1.0f,     0.0f, 0.0f, 0.0f
            )
            GLES31.glActiveTexture(GLES31.GL_TEXTURE0)
            GLES31.glBindTexture(GLES31.GL_TEXTURE_2D_ARRAY, renderData.textureArray)
            GLES31.glBindVertexArray(renderData.vao)
            GLES31.glBindBuffer(GLES31.GL_ARRAY_BUFFER, renderData.vbo)
            GLES31.glBufferSubData(GLES31.GL_ARRAY_BUFFER, 0, vert1.size * 4, vert1.toGlBuffer())
            GLES31.glDrawArrays(GLES31.GL_TRIANGLE_FAN, 0, 4)

            val vert2 = floatArrayOf(
                -1.0f, -1.0f,     0.0f, 1.0f, 1.0f,
                0.0f, -1.0f,     1.0f, 1.0f, 1.0f,
                0.0f, 0.0f,     1.0f, 0.0f, 1.0f,
                -1.0f, 0.0f,     0.0f, 0.0f, 1.0f
            )
            GLES31.glBindBuffer(GLES31.GL_ARRAY_BUFFER, renderData.vbo)
            GLES31.glBufferSubData(GLES31.GL_ARRAY_BUFFER, 0, vert2.size * 4, vert2.toGlBuffer())
            GLES31.glDrawArrays(GLES31.GL_TRIANGLE_FAN, 0, 4)
        }
    }

    override fun onViewDestroyed(owner: MyOpenGLView) {
        super.onViewDestroyed(owner)
        val renderData = renderData.get()
        if (renderData != null) {
            this.renderData.set(null)
            GLES31.glDeleteProgram(renderData.program)
            GLES31.glDeleteBuffers(1, intArrayOf(renderData.vbo), 0)
            GLES31.glDeleteTextures(1, intArrayOf(renderData.textureArray), 0)
        }
    }


    companion object {
        private data class RenderData(
            val program: Int,
            val textureArray: Int,
            val vao: Int,
            val vbo: Int
        )
    }
}