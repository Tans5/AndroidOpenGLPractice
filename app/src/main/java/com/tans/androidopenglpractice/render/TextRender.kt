package com.tans.androidopenglpractice.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.opengl.GLES31
import android.opengl.GLUtils
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.max

class TextRender : IShapeRender {

    override val isActive: AtomicBoolean = AtomicBoolean(false)
    override var width: Int = 0
    override var height: Int = 0
    override val logTag: String = "TextRender"

    private val charPaint: Paint by lazy {
        Paint().apply {
            isAntiAlias = true
            color = Color.BLACK
            textAlign = Paint.Align.LEFT
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
    }

    private val initData: AtomicReference<InitData?> by lazy {
        AtomicReference(null)
    }

    override fun onSurfaceCreated(owner: MyOpenGLView, gl: GL10, config: EGLConfig) {
        super.onSurfaceCreated(owner, gl, config)
        val program = compileShaderFromAssets(owner.context, "text.vert", "text.frag")
        if (program != null) {
            val chars = hashSetOf<Char>()
            for (c in 'a' .. 'z') {
                chars.add(c)
            }
            for (c in 'A' .. 'Z') {
                chars.add(c)
            }
            for (c in '0' .. '9') {
                chars.add(c)
            }
            chars.add(' ')
            chars.add(',')
            chars.add('.')
            val charTextures = mutableMapOf<Char, CharTexture>()
            for (c in chars) {
                charTextures[c] = createCharTexture(c, 128, 128)
            }
            val vao = glGenVertexArrays()
            val vbo = glGenBuffers()
            GLES31.glBindVertexArray(vao)
            GLES31.glBindBuffer(GLES31.GL_ARRAY_BUFFER, vbo)
            GLES31.glBufferData(GLES31.GL_ARRAY_BUFFER, 4 * 4 * 4, null, GLES31.GL_STREAM_DRAW)
            GLES31.glVertexAttribPointer(0, 4,  GLES31.GL_FLOAT, false, 4 * 4, 0)
            GLES31.glEnableVertexAttribArray(0)
            GLES31.glEnable(GLES31.GL_BLEND)
            GLES31.glBlendFunc(GLES31.GL_SRC_ALPHA, GLES31.GL_ONE_MINUS_SRC_ALPHA)
            initData.set(
                InitData(
                    charTextures = charTextures,
                    program = program,
                    vao = vao,
                    vbo = vbo
                )
            )
        }

    }
    override fun onDrawFrame(owner: MyOpenGLView, gl: GL10) {
        val initData = initData.get()
        if (initData != null) {
            GLES31.glClear(GLES31.GL_COLOR_BUFFER_BIT)
            GLES31.glUseProgram(initData.program)
            drawText("Hello, Android.", initData)
        }
    }

    private fun drawText(text: String, initData: InitData) {
        val singleCharTex = initData.charTextures['a']
        if (singleCharTex != null) {
            val charWidthInScreenPercent = singleCharTex.width.toFloat() / width.toFloat()
            val charHeightInScreenPercent = singleCharTex.height.toFloat() / height.toFloat()
            val charWidthGLStep = charWidthInScreenPercent / 2.0f
            val charHeightGLStep = charHeightInScreenPercent / 2.0f
            var renderWidthStart = -1.0f
            var renderHeightStart = 0.0f
            for (c in text.toCharArray()) {
                val charTexture = initData.charTextures[c]
                if (charTexture != null) {
                    val widthStart = renderWidthStart
                    val widthEnd = widthStart + charWidthGLStep
                    val heightStart = renderHeightStart
                    val heightEnd = heightStart - charHeightGLStep
                    renderWidthStart += charWidthGLStep
                    val vertices = floatArrayOf(
                        // 坐标(position 0)              // 纹理坐标
                        widthStart, heightStart,        0.0f, 0.0f,    // 左上角
                        widthEnd, heightStart,          1.0f, 0.0f,   // 右上角
                        widthEnd, heightEnd,            1.0f, 1.0f,   // 右下角
                        widthStart, heightEnd,          0.0f, 1.0f,   // 左下角
                    )
                    GLES31.glBindVertexArray(initData.vao)
                    GLES31.glBindBuffer(GLES31.GL_ARRAY_BUFFER, initData.vbo)
                    GLES31.glBufferSubData(GLES31.GL_ARRAY_BUFFER, 0, vertices.size * 4, vertices.toGlBuffer())
                    GLES31.glActiveTexture(GLES31.GL_TEXTURE0)
                    GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, charTexture.texture)
                    GLES31.glDrawArrays(GLES31.GL_TRIANGLE_FAN, 0, 4)
                }
            }
        }
    }

    private fun createCharTexture(c: Char, width: Int, height: Int): CharTexture {
        val texture = glGenTextureAndSetDefaultParams()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8)
        val canvas = Canvas(bitmap)
        charPaint.textSize = max(width, height).toFloat() * 1.1f
        val metrics = charPaint.fontMetrics
        val charWidth = charPaint.measureText(c.toString())
        val x = max((width - charWidth) / 2.0f, 0.0f)
        val y = - metrics.top / (metrics.bottom - metrics.top) * height
        canvas.drawText(c.toString(), x, y, charPaint)
        GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, texture)
        GLUtils.texImage2D(GLES31.GL_TEXTURE_2D, 0, bitmap, 0)
        bitmap.recycle()
        return CharTexture(texture, width, height)
    }

    override fun onViewDestroyed(owner: MyOpenGLView) {
        super.onViewDestroyed(owner)
        val initData = initData.get()
        if (initData != null) {
            GLES31.glDeleteProgram(initData.program)
            val charTextures = initData.charTextures.values.map { it.texture }.toIntArray()
            GLES31.glDeleteTextures(charTextures.size, charTextures, 0)
            GLES31.glDeleteBuffers(0, intArrayOf(initData.vbo), 0)
        }
    }

    companion object {
        data class CharTexture(
            val texture: Int,
            val width: Int,
            val height: Int
        )

        data class InitData(
            val charTextures: Map<Char, CharTexture>,
            val program: Int,
            val vao: Int,
            val vbo: Int
        )
    }
}