package com.tans.androidopenglpractice

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.tans.androidopenglpractice.render.CubeRender
import com.tans.androidopenglpractice.render.MyOpenGLView
import com.tans.androidopenglpractice.render.SimpleTriangleRender
import com.tans.androidopenglpractice.render.SquareRender

class MainActivity : AppCompatActivity() {

    @SuppressLint("UnsafeOptInUsageError")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)
        val glView = findViewById<MyOpenGLView>(R.id.gl_view)
        glView.shapeRender = SimpleTriangleRender()
        findViewById<Button>(R.id.simple_triangle_bt).setOnClickListener {
            if (glView.shapeRender !is SimpleTriangleRender) {
                glView.shapeRender = SimpleTriangleRender()
            }
        }

        findViewById<Button>(R.id.square_bt).setOnClickListener {
            if (glView.shapeRender !is SquareRender) {
                glView.shapeRender = SquareRender()
            }
        }

        findViewById<Button>(R.id.cube_bt).setOnClickListener {
            if (glView.shapeRender !is CubeRender) {
                glView.shapeRender = CubeRender()
            }
        }

        findViewById<Button>(R.id.camera_bt).setOnClickListener {
            startActivity(Intent(this, CameraActivity::class.java))
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}