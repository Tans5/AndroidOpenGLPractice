package com.tans.androidopenglpractice.render

import android.content.Context
import android.opengl.GLES31
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

interface IShapeRender {

    val isActive: AtomicBoolean
    var width: Int
    var height: Int

    val logTag: String

    fun onSurfaceCreated(owner: MyOpenGLView, gl: GL10, config: EGLConfig) {
        isActive.set(true)
    }

    fun onSurfaceChanged(owner: MyOpenGLView, gl: GL10, width: Int, height: Int) {
        this.width = width
        this.height = height
    }

    fun onDrawFrame(owner: MyOpenGLView, gl: GL10)

    fun onViewDestroyed(owner: MyOpenGLView) {
        isActive.set(false)
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
            Log.e(logTag, "Load vertex shader source file $vertexShaderFile fail: ${e.message}", e)
            return null
        }
        val fragmentRender = try {
            context.assets.open(fragmentShaderFile).use {
                String(it.readBytes(), Charsets.UTF_8)
            }
        } catch (e: Throwable) {
            Log.e(logTag, "Load fragment shader source file $vertexShaderFile fail: ${e.message}", e)
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
            Log.e(logTag, "Compile vertex shader fail: $log")
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
            Log.e(logTag, "Compile fragment shader fail: $log")
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
            Log.e(logTag, "Link program fail: $log")
            return null
        }
        Log.d(logTag, "Compile program success!!")
        return shaderProgram
    }
}