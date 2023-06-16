package com.tans.androidopenglpractice

import android.Manifest
import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Size
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import com.tans.androidopenglpractice.render.CameraRender
import com.tans.androidopenglpractice.render.CubeRender
import com.tans.androidopenglpractice.render.MyOpenGLView
import com.tans.androidopenglpractice.render.SimpleTriangleRender
import com.tans.androidopenglpractice.render.SquareRender
import com.tbruyelle.rxpermissions3.RxPermissions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx3.await
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), CoroutineScope by CoroutineScope(Dispatchers.Main) {

    @SuppressLint("UnsafeOptInUsageError")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)
        val glView = findViewById<MyOpenGLView>(R.id.gl_view)
        glView.shapeRender = CameraRender()
        launch {
            val permissionGrant = RxPermissions(this@MainActivity)
                .request(Manifest.permission.CAMERA)
                .firstOrError()
                .await()
            if (permissionGrant) {
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setImageQueueDepth(10)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .setTargetResolution(Size(640, 480))
                    .setBackgroundExecutor(Dispatchers.IO.asExecutor())
                    .build()
                analysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                    val render = glView.shapeRender
                    if (render is CameraRender) {
                        render.cameraReady(imageProxy)
                    } else {
                        imageProxy.close()
                    }
                }
                val cameraProvider = with(Dispatchers.IO) {
                    ProcessCameraProvider.getInstance(this@MainActivity).get()
                }
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this@MainActivity, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)

                findViewById<Button>(R.id.camera_bt).setOnClickListener {
                    glView.shapeRender = CameraRender()
                }

                findViewById<Button>(R.id.cam_crop_bt).setOnClickListener {
                    val render = glView.shapeRender
                    if (render is CameraRender) {
                        render.scaleType = CameraRender.Companion.ScaleType.CenterCrop
                    }
                }

                findViewById<Button>(R.id.cam_fit_bt).setOnClickListener {
                    val render = glView.shapeRender
                    if (render is CameraRender) {
                        render.scaleType = CameraRender.Companion.ScaleType.CenterFit
                    }
                }
            }
        }

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

    override fun onDestroy() {
        super.onDestroy()
        cancel()
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}