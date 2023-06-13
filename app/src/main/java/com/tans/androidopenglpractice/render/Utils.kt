package com.tans.androidopenglpractice.render

import android.opengl.GLES31
import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.IntBuffer

fun newGlIntBuffer(): IntBuffer {
    return ByteBuffer.allocateDirect(4).let {
        it.order(ByteOrder.nativeOrder())
        it.asIntBuffer()
    }
}

fun newGlFloatMatrix(n: Int = 4): FloatArray {
    return FloatArray(n * n) {
        val x = it / n
        val y = it % n
        if (x == y) {
            1.0f
        } else {
            0.0f
        }
    }
}

fun FloatArray.toGlBuffer(): ByteBuffer {
    return ByteBuffer.allocateDirect(size * 4).let {
        it.order(ByteOrder.nativeOrder())
        it.asFloatBuffer().put(this)
        it.position(0)
        it
    }
}

fun IntArray.toGlBuffer(): ByteBuffer {
    return ByteBuffer.allocateDirect(size * 4).let {
        it.order(ByteOrder.nativeOrder())
        it.asIntBuffer().put(this)
        it.position(0)
        it
    }
}

fun glGenBuffers(): Int {
    val buffer = newGlIntBuffer()
    GLES31.glGenBuffers(1, buffer)
    buffer.position(0)
    return buffer.get()
}

fun glGenVertexArrays(): Int {
    val buffer = newGlIntBuffer()
    GLES31.glGenVertexArrays(1, buffer)
    buffer.position(0)
    return buffer.get()
}

fun glGenTexture(): Int {
    val buffer = newGlIntBuffer()
    GLES31.glGenTextures(1, buffer)
    buffer.position(0)
    return buffer.get()
}