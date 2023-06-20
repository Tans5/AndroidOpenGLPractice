package com.tans.androidopenglpractice

import android.Manifest
import android.annotation.SuppressLint
import android.graphics.ImageFormat
import android.graphics.PixelFormat
import android.os.Bundle
import android.os.SystemClock
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
import com.tans.androidopenglpractice.render.toRgba
import com.tans.androidopenglpractice.render.yuv420888ToNv21
import com.tbruyelle.rxpermissions3.RxPermissions
import com.tenginekit.engine.core.ImageConfig
import com.tenginekit.engine.core.SdkConfig
import com.tenginekit.engine.core.TengineKitSdk
import com.tenginekit.engine.face.FaceConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx3.await
import kotlinx.coroutines.withContext
import java.io.File

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
                withContext(Dispatchers.IO) {
                    initTengine()
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setImageQueueDepth(1)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    // .setDefaultResolution(Size(640, 480))
                    .setBackgroundExecutor(Dispatchers.IO.asExecutor())
                    .build()
                analysis.setAnalyzer(Dispatchers.IO.asExecutor()) { imageProxy ->
                    val render = glView.shapeRender
                    if (render !is CameraRender) {
                        imageProxy.close()
                        return@setAnalyzer
                    }
                    val timestamp = SystemClock.uptimeMillis()
                    val imageData = when (imageProxy.format) {
                        ImageFormat.YUV_420_888 -> {
                            val yuv = yuv420888ToNv21(imageProxy)
                            val width = imageProxy.cropRect.width()
                            val height = imageProxy.cropRect.height()
                            CameraRender.Companion.ImageData(
                                image = yuv,
                                width = width,
                                height = height,
                                rotation = imageProxy.imageInfo.rotationDegrees,
                                imageType = CameraRender.Companion.ImageType.NV21,
                                imageProxy = imageProxy,
                                timestamp = timestamp
                            )
                        }
                        PixelFormat.RGBA_8888 -> {
                            val width = imageProxy.width
                            val height = imageProxy.height
                            val rgba = ByteArray(width * height * 4)
                            imageProxy.toRgba(rgba)
                            CameraRender.Companion.ImageData(
                                image = rgba,
                                width = width,
                                height = height,
                                rotation = imageProxy.imageInfo.rotationDegrees,
                                imageType = CameraRender.Companion.ImageType.RGBA,
                                imageProxy = imageProxy,
                                timestamp = timestamp
                            )
                        }
                        else -> {
                            imageProxy.close()
                            return@setAnalyzer
                        }
                    }
                    glView.queueEvent {
                        val faceConfig = FaceConfig().apply {
                            detect = true
                            landmark2d = true
                            attribute = true
                            eyeIris = true
                            maxFaceNum = 1
                        }
                        val imageConfig = ImageConfig().apply {
                            this.data = imageData.image
                            this.degree = imageData.rotation
                            this.mirror = false
                            this.width = imageData.width
                            this.height = imageData.height
                            this.format = when (imageData.imageType) {
                                CameraRender.Companion.ImageType.NV21 -> ImageConfig.FaceImageFormat.YUV
                                CameraRender.Companion.ImageType.RGBA -> ImageConfig.FaceImageFormat.RGBA
                            }
                        }
                        val face = TengineKitSdk.getInstance().detectFace(imageConfig, faceConfig)?.getOrNull(0)
                        if (face != null) {
                            val faceData = CameraRender.Companion.FaceData(
                                timestamp = timestamp,
                                faceFrame = arrayOf(
                                    CameraRender.Companion.Point(face.x1, face.y1),
                                    CameraRender.Companion.Point(face.x2, face.y1),
                                    CameraRender.Companion.Point(face.x2, face.y2),
                                    CameraRender.Companion.Point(face.x1, face.y2)
                                )
                            )
                            render.faceDataReady(faceData)
                        }
                    }
                    render.cameraReady(imageData)
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
        releaseTengine()
    }

    private fun initTengine() {
        val modelDir = this.filesDir
        val modelNames = assets.list("model") ?: emptyArray()
        for (name in modelNames) {
            val modelFile = File(modelDir, name)
            if (modelFile.isFile) {
                continue
            }
            modelFile.createNewFile()
            modelFile.outputStream().use { os ->
                assets.open("model/$name").use {
                    it.copyTo(os)
                }
            }
        }
        val config = SdkConfig()
        TengineKitSdk.getInstance().initSdk(modelDir.toString(), config, this)
        TengineKitSdk.getInstance().initFaceDetect()
    }

    private fun releaseTengine() {
        TengineKitSdk.getInstance().releaseFaceDetect()
        TengineKitSdk.getInstance().release()
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}