package com.tans.androidopenglpractice.render.camera

import android.content.Context
import android.opengl.GLES31
import android.util.Log
import com.tans.androidopenglpractice.render.glGenBuffers
import com.tans.androidopenglpractice.render.glGenFrameBuffer
import com.tans.androidopenglpractice.render.glGenTexture
import com.tans.androidopenglpractice.render.glGenVertexArrays
import com.tans.androidopenglpractice.render.toGlBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val TAG = "GLImageUtils"

private const val simpleImageVertSource: String = """#version 310 es
layout (location = 0) in vec3 aPos;
layout (location = 1) in vec2 aTexCoord;
out vec2 TexCoord;

void main() {
    gl_Position = vec4(aPos, 1.0);
    TexCoord = aTexCoord;
}
"""

private const val simpleImageFragSource: String = """#version 310 es
precision highp float; // Define float precision
in vec2 TexCoord;
out vec4 FragColor;

uniform sampler2D Texture;

void main() {
    FragColor = texture(Texture, TexCoord);
}
"""

private val imageVAO: Int by lazy {
    glGenVertexArrays()
}

private val imageVBO: Int by lazy {
    glGenBuffers()
}

/**
 * 获取纹理图片，格式为 RGBA.
 */
fun readGlTextureImageBytes(
    textureId: Int,
    width: Int,
    height: Int
): ByteArray? {

    // 生成帧缓冲
    val fbo = glGenFrameBuffer()
    GLES31.glBindFramebuffer(GLES31.GL_FRAMEBUFFER, fbo)

    // 生成帧缓冲的附件纹理
    val fboTexture = glGenTexture()
    GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, fboTexture)
    // 纹理的数据设置为空，大小为图片大小, 光申请内存不填充，渲染时填充
    GLES31.glTexImage2D(GLES31.GL_TEXTURE_2D, 0, GLES31.GL_RGBA,
        width, height, 0, GLES31.GL_RGBA, GLES31.GL_UNSIGNED_BYTE, null)
    GLES31.glTexParameteri(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_MIN_FILTER, GLES31.GL_LINEAR)
    GLES31.glTexParameteri(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_MAG_FILTER, GLES31.GL_LINEAR)

    // 为帧缓冲添加纹理附件(颜色)
    GLES31.glFramebufferTexture2D(GLES31.GL_FRAMEBUFFER, GLES31.GL_COLOR_ATTACHMENT0,
        GLES31.GL_TEXTURE_2D, fboTexture, 0)

    val frameBufferStatus = GLES31.glCheckFramebufferStatus(GLES31.GL_FRAMEBUFFER)
    if (frameBufferStatus != GLES31.GL_FRAMEBUFFER_COMPLETE) {
        Log.e(TAG, "Create frame buffer fail: $frameBufferStatus")
        return null
    }

    // 获取当前的 view port, 离屏渲染完成后，需要还原
    val lastViewPort = IntArray(4)
    GLES31.glGetIntegerv(GLES31.GL_VIEWPORT, lastViewPort, 0)

    // 创建离屏渲染的 view port
    GLES31.glViewport(0, 0, width, height)
    GLES31.glClearColor(0.0f, 0.0f, 0.0f, 0.0f)
    GLES31.glClear(GLES31.GL_COLOR_BUFFER_BIT)

    val program = compileShaderProgram(simpleImageVertSource, simpleImageFragSource) ?: return null

    GLES31.glUseProgram(program)

    GLES31.glActiveTexture(GLES31.GL_TEXTURE0)
    GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, textureId)

    val vertices = floatArrayOf(
        // 坐标(position 0)   // 纹理坐标
        -1.0f, 1.0f, 0.0f,   0.0f, 0.0f,    // 左上角
        1.0f, 1.0f, 0.0f,    1.0f, 0.0f,   // 右上角
        1.0f, -1.0f, 0.0f,   1.0f, 1.0f,   // 右下角
        -1.0f, -1.0f, 0.0f,   0.0f, 1.0f,   // 左下角
    )
    GLES31.glBindVertexArray(imageVAO)
    GLES31.glBindBuffer(GLES31.GL_ARRAY_BUFFER, imageVBO)
    GLES31.glVertexAttribPointer(0, 3, GLES31.GL_FLOAT, false, 20, 0)
    GLES31.glEnableVertexAttribArray(0)
    GLES31.glVertexAttribPointer(1, 2, GLES31.GL_FLOAT, false, 20, 12)
    GLES31.glEnableVertexAttribArray(1)
    GLES31.glBufferData(GLES31.GL_ARRAY_BUFFER, vertices.size * 4, vertices.toGlBuffer(), GLES31.GL_STATIC_DRAW)
    GLES31.glDrawArrays(GLES31.GL_TRIANGLE_FAN, 0, 4)
    GLES31.glBindVertexArray(GLES31.GL_NONE)
    GLES31.glBindBuffer(GLES31.GL_ARRAY_BUFFER, GLES31.GL_NONE)

    val imageBytes = ByteArray(width * height * 4)
    GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, fboTexture)
    GLES31.glReadPixels(
        0, 0,
        width, height,
        GLES31.GL_RGBA,
        GLES31.GL_UNSIGNED_BYTE,
        ByteBuffer.wrap(imageBytes)
    )

    GLES31.glUseProgram(0)
    GLES31.glDeleteProgram(program)
    GLES31.glFinish()

    GLES31.glViewport(lastViewPort[0], lastViewPort[1], lastViewPort[2], lastViewPort[3])
    GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, GLES31.GL_NONE)

    // 激活默认缓冲
    GLES31.glBindFramebuffer(GLES31.GL_FRAMEBUFFER, 0)

    GLES31.glDeleteTextures(1, intArrayOf(fboTexture), 0)
    GLES31.glDeleteFramebuffers(1, intArrayOf(fbo) , 0)

    return imageBytes
}

fun compileShaderFromAssets(
    context: Context,
    vertexShaderFile: String,
    fragmentShaderFile: String
): Int? {
    val vertexRender = try {
        context.assets.open(vertexShaderFile).use {
            String(it.readBytes(), Charsets.UTF_8)
        }
    } catch (e: Throwable) {
        Log.e(TAG, "Load vertex shader source file $vertexShaderFile fail: ${e.message}", e)
        return null
    }
    val fragmentRender = try {
        context.assets.open(fragmentShaderFile).use {
            String(it.readBytes(), Charsets.UTF_8)
        }
    } catch (e: Throwable) {
        Log.e(TAG, "Load fragment shader source file $vertexShaderFile fail: ${e.message}", e)
        return null
    }
    return compileShaderProgram(vertexRender, fragmentRender)
}

fun compileShaderProgram(
    vertexShaderSource: String,
    fragmentShaderSource: String): Int? {

    /**
     * 编译顶点着色器
     */
    val vertexShader = GLES31.glCreateShader(GLES31.GL_VERTEX_SHADER)
    GLES31.glShaderSource(vertexShader, vertexShaderSource)
    GLES31.glCompileShader(vertexShader)
    val vertexCompileState = ByteBuffer.allocateDirect(4).let {
        it.order(ByteOrder.nativeOrder())
        it.asIntBuffer()
    }
    GLES31.glGetShaderiv(vertexShader, GLES31.GL_COMPILE_STATUS, vertexCompileState)
    vertexCompileState.position(0)
    if (vertexCompileState.get() <= 0) {
        val log = GLES31.glGetShaderInfoLog(vertexShader)
        GLES31.glDeleteShader(vertexShader)
        Log.e(TAG, "Compile vertex shader fail: $log")
        return null
    }

    /**
     * 编译片段着色器
     */
    val fragmentShader = GLES31.glCreateShader(GLES31.GL_FRAGMENT_SHADER)
    GLES31.glShaderSource(fragmentShader, fragmentShaderSource)
    GLES31.glCompileShader(fragmentShader)
    val fragmentCompileState = ByteBuffer.allocateDirect(4).let {
        it.order(ByteOrder.nativeOrder())
        it.asIntBuffer()
    }
    GLES31.glGetShaderiv(fragmentShader, GLES31.GL_COMPILE_STATUS, fragmentCompileState)
    fragmentCompileState.position(0)
    if (fragmentCompileState.get() <= 0) {
        val log = GLES31.glGetShaderInfoLog(fragmentShader)
        GLES31.glDeleteShader(vertexShader)
        GLES31.glDeleteShader(fragmentShader)
        Log.e(TAG, "Compile fragment shader fail: $log")
        return null
    }

    /**
     * 链接着色器程序
     */
    val shaderProgram = GLES31.glCreateProgram()
    GLES31.glAttachShader(shaderProgram, vertexShader)
    GLES31.glAttachShader(shaderProgram, fragmentShader)
    GLES31.glLinkProgram(shaderProgram)
    GLES31.glDeleteShader(vertexShader)
    GLES31.glDeleteShader(fragmentShader)
    val linkProgramState = ByteBuffer.allocateDirect(4).let {
        it.order(ByteOrder.nativeOrder())
        it.asIntBuffer()
    }
    GLES31.glGetProgramiv(shaderProgram, GLES31.GL_LINK_STATUS, linkProgramState)
    linkProgramState.position(0)
    if (linkProgramState.get() <= 0) {
        val log = GLES31.glGetProgramInfoLog(shaderProgram)
        GLES31.glDeleteProgram(shaderProgram)
        Log.e(TAG, "Link program fail: $log")
        return null
    }
    Log.d(TAG, "Compile program success!!")
    return shaderProgram
}