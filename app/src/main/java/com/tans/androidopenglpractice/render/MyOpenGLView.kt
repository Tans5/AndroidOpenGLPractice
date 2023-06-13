package com.tans.androidopenglpractice.render

import android.content.Context
import android.opengl.GLES31
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.util.Log
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class MyOpenGLView : GLSurfaceView {
    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?): super(context, attrs)

    private val myRenderer: MyRenderer

    private val shapeRender: IShapeRender

    init {
        setEGLContextClientVersion(3)
        shapeRender = SquareRender()
        myRenderer = MyRenderer(shapeRender, this)
        setRenderer(myRenderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    internal class MyRenderer(private val shapeRender: IShapeRender, private val view: MyOpenGLView) : Renderer {

        override fun onSurfaceCreated(gl: GL10, config: EGLConfig) {
            GLES31.glClearColor(0.3f, 0.3f, 0.3f, 1.0f)
            val glVersion = gl.glGetString(GLES31.GL_VERSION)
            Log.d(TAG, "Support OpenGL ES version: $glVersion")
            shapeRender.onSurfaceCreated(gl, config)
            view.requestRender()
        }

        override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
            GLES31.glViewport(0, 0, width, height)
            if (shapeRender.isActive.get()) {
                shapeRender.onSurfaceChanged(gl, width, height)
            }
        }

        override fun onDrawFrame(gl: GL10) {
            GLES31.glClear(GLES31.GL_COLOR_BUFFER_BIT)
            if (shapeRender.isActive.get()) {
                shapeRender.onDrawFrame(gl)
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        shapeRender.onViewDestroyed()
    }

    companion object {
        private const val TAG = "MyOpenGLView"
    }
}