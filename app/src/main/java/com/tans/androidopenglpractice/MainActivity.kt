package com.tans.androidopenglpractice

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.tans.androidopenglpractice.render.CubeRender
import com.tans.androidopenglpractice.render.MyOpenGLView
import com.tans.androidopenglpractice.render.SimpleTriangleRender
import com.tans.androidopenglpractice.render.SquareRender

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        window.statusBarColor = Color.TRANSPARENT
        setContentView(R.layout.main_activity)
        val glView = findViewById<MyOpenGLView>(R.id.gl_view)
        glView.shapeRender = CubeRender()

        findViewById<Button>(R.id.simple_triangle_bt).setOnClickListener {
            glView.shapeRender = SimpleTriangleRender()
        }

        findViewById<Button>(R.id.square_bt).setOnClickListener {
            glView.shapeRender = SquareRender()
        }

        findViewById<Button>(R.id.cube_bt).setOnClickListener {
            glView.shapeRender = CubeRender()
        }
    }

}