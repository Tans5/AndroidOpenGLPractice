package com.tans.androidopenglpractice.render

import android.content.Context
import android.opengl.GLES31
import android.opengl.GLSurfaceView
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class MyOpenGLView : GLSurfaceView {
    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?): super(context, attrs)

    private var createdCache: SurfaceCreatedCache? = null

    private var sizeCache: SurfaceSizeCache? = null

    var shapeRender: IShapeRender? = null
        set(value) {
            mainThread {
                this.shapeRender?.onViewDestroyed(this)
                field = value
                requestRender()
            }
        }

    init {
        setEGLContextClientVersion(3)
        setRenderer(MyRenderer(this))
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    private fun mainThread(callback: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            callback()
        } else {
            post { callback() }
        }
    }

    private class MyRenderer(private val owner: MyOpenGLView) : Renderer {

        override fun onSurfaceCreated(gl: GL10, config: EGLConfig) {
            GLES31.glClearColor(0.3f, 0.3f, 0.3f, 1.0f)
            val glVersion = gl.glGetString(GLES31.GL_VERSION)
            Log.d(TAG, "Support OpenGL ES version: $glVersion")
            val render = owner.shapeRender
            if (render != null) {
                render.onSurfaceCreated(owner, gl, config)
                owner.requestRender()
            }
            owner.mainThread {
                owner.createdCache = SurfaceCreatedCache(gl, config)
            }
        }

        override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
            GLES31.glViewport(0, 0, width, height)
            val render = owner.shapeRender
            if (render != null) {
                if (render.isActive.get()) {
                    render.onSurfaceChanged(owner, gl, width, height)
                } else {
                    val ca = owner.createdCache
                    if (ca != null) {
                        render.onSurfaceCreated(owner, ca.gl, ca.config)
                        render.onSurfaceChanged(owner, gl, width, height)
                    }
                }
            }
            owner.mainThread {
                owner.sizeCache = SurfaceSizeCache(gl, width, height)
            }
        }

        override fun onDrawFrame(gl: GL10) {
            val render = owner.shapeRender
            if (render != null) {
                if (render.isActive.get()) {
                    render.onDrawFrame(owner, gl)
                } else {
                    val ca = owner.createdCache
                    if (ca != null) {
                        render.onSurfaceCreated(owner, ca.gl, ca.config)
                        val sa = owner.sizeCache
                        if (sa != null) {
                            render.onSurfaceChanged(owner, sa.gl, sa.width, sa.height)
                        }
                        render.onDrawFrame(owner, gl)
                    }
                }
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        shapeRender?.onViewDestroyed(this)
        createdCache = null
        sizeCache = null
    }

    companion object {
        private data class SurfaceCreatedCache(
            val gl: GL10,
            val config: EGLConfig
        )
        private data class SurfaceSizeCache(
            val gl: GL10,
            val width: Int,
            val height: Int
        )
        private const val TAG = "MyOpenGLView"
    }
}