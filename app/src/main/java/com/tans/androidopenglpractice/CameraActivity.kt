package com.tans.androidopenglpractice

import android.Manifest
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.PixelFormat
import android.os.Bundle
import android.os.SystemClock
import android.widget.Button
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.tans.androidopenglpractice.render.camera.CameraRender
import com.tans.androidopenglpractice.render.MyOpenGLView
import com.tans.androidopenglpractice.render.camera.FaceData
import com.tans.androidopenglpractice.render.camera.ImageData
import com.tans.androidopenglpractice.render.camera.ImageType
import com.tans.androidopenglpractice.render.camera.Point
import com.tans.androidopenglpractice.render.camera.ScaleType
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

class CameraActivity : AppCompatActivity(), CoroutineScope by CoroutineScope(Dispatchers.Main) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        setContentView(R.layout.camera_activity)
        val glView = findViewById<MyOpenGLView>(R.id.gl_view)
        val render = CameraRender()
        glView.shapeRender = render
        launch {
            val permissionGrant = RxPermissions(this@CameraActivity)
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
                    val timestamp = SystemClock.uptimeMillis()
                    val imageData = when (imageProxy.format) {
                        ImageFormat.YUV_420_888 -> {
                            val yuv = yuv420888ToNv21(imageProxy)
                            val width = imageProxy.cropRect.width()
                            val height = imageProxy.cropRect.height()
                            ImageData(
                                image = yuv,
                                width = width,
                                height = height,
                                rotation = imageProxy.imageInfo.rotationDegrees,
                                imageType = ImageType.NV21,
                                imageProxy = imageProxy,
                                timestamp = timestamp
                            )
                        }
                        PixelFormat.RGBA_8888 -> {
                            val width = imageProxy.width
                            val height = imageProxy.height
                            val rgba = ByteArray(width * height * 4)
                            imageProxy.toRgba(rgba)
                            ImageData(
                                image = rgba,
                                width = width,
                                height = height,
                                rotation = imageProxy.imageInfo.rotationDegrees,
                                imageType = ImageType.RGBA,
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
                                ImageType.NV21 -> ImageConfig.FaceImageFormat.YUV
                                ImageType.RGBA -> ImageConfig.FaceImageFormat.RGBA
                            }
                        }
                        val face = TengineKitSdk.getInstance().detectFace(imageConfig, faceConfig)?.getOrNull(0)
                        if (face != null) {
                            val landmark = face.landmark
                            val check = getCameraPoints(landmark, 0, 69)
                            val leftEyebrow = getCameraPoints(landmark, 69, 16)
                            val rightEyebrow = getCameraPoints(landmark, 85, 16)
                            val leftEye = getCameraPoints(landmark, 101, 16)
                            val rightEye = getCameraPoints(landmark, 117, 16)
                            val nose = getCameraPoints(landmark, 133, 47)
                            val upLip = getCameraPoints(landmark, 180, 16)
                            val downLip = getCameraPoints(landmark, 196, 16)
                            val leftEyeIris = Array(5) {
                                Point(
                                    x =  face.eyeIrisLeft[it * 3],
                                    y = face.eyeIrisLeft[it * 3 + 1]
                                )
                            }
                            val rightEyeIris = Array(5) {
                                Point(
                                    x =  face.eyeIrisRight[it * 3],
                                    y = face.eyeIrisRight[it * 3 + 1]
                                )
                            }
                            val leftEyeIrisFrame = Array(15) {
                                Point(
                                    x = face.eyeLandMarkLeft[it * 3],
                                    y = face.eyeLandMarkLeft[it * 3 + 1]
                                )
                            }
                            val rightEyeIrisFrame = Array(15) {
                                Point(
                                    x = face.eyeLandMarkRight[it * 3],
                                    y = face.eyeLandMarkRight[it * 3 + 1]
                                )
                            }
                            val faceData = FaceData(
                                timestamp = timestamp,
                                faceFrame = arrayOf(
                                    Point(face.x1, face.y1),
                                    Point(face.x2, face.y1),
                                    Point(face.x2, face.y2),
                                    Point(face.x1, face.y2)
                                ),
                                check = check,
                                leftEyebrow = leftEyebrow,
                                rightEyebrow = rightEyebrow,
                                leftEye = leftEye,
                                rightEye = rightEye,
                                leftEyeIris = leftEyeIris,
                                leftEyeIrisF = leftEyeIrisFrame,
                                rightEyeIris = rightEyeIris,
                                rightEyeIrisF = rightEyeIrisFrame,
                                nose = nose,
                                upLip = upLip,
                                downLip = downLip
                            )
                            render.faceDataReady(faceData)
                        }
                    }
                    render.cameraReady(imageData)
                }
                val cameraProvider = with(Dispatchers.IO) {
                    ProcessCameraProvider.getInstance(this@CameraActivity).get()
                }
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this@CameraActivity, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
                var cameraSelectedCamera = CameraSelector.DEFAULT_FRONT_CAMERA

                findViewById<Button>(R.id.change_camera_bt).setOnClickListener {
                    cameraProvider.unbindAll()
                    if (cameraSelectedCamera == CameraSelector.DEFAULT_FRONT_CAMERA) {
                        cameraSelectedCamera = CameraSelector.DEFAULT_BACK_CAMERA
                        cameraProvider.bindToLifecycle(this@CameraActivity, CameraSelector.DEFAULT_BACK_CAMERA, analysis)
                    } else {
                        cameraSelectedCamera = CameraSelector.DEFAULT_FRONT_CAMERA
                        cameraProvider.bindToLifecycle(this@CameraActivity, CameraSelector.DEFAULT_FRONT_CAMERA, analysis)
                    }
                }

                findViewById<Button>(R.id.cam_crop_bt).setOnClickListener {
                    render.scaleType = ScaleType.CenterCrop
                }

                findViewById<Button>(R.id.cam_fit_bt).setOnClickListener {
                    render.scaleType = ScaleType.CenterFit
                }

                findViewById<Button>(R.id.cam_mirror_bt).setOnClickListener {
                    render.mirror = !render.mirror
                }

                findViewById<Button>(R.id.face_frame_bt).setOnClickListener {
                    render.renderFaceFrame = !render.renderFaceFrame
                }
                val enlargeEyesSb = findViewById<SeekBar>(R.id.enlarge_eyes_sb)
                enlargeEyesSb.progress = render.getEnlargeEyesStrength().toInt()
                enlargeEyesSb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(
                        seekBar: SeekBar?,
                        progress: Int,
                        fromUser: Boolean
                    ) {
                        if (fromUser) {
                            render.setEnlargeEyesStrength(progress.toFloat())
                        }
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })

                val thinFaceSb = findViewById<SeekBar>(R.id.thin_face_sb)
                thinFaceSb.progress = render.getThinFaceStrength().toInt()
                thinFaceSb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(
                        seekBar: SeekBar?,
                        progress: Int,
                        fromUser: Boolean
                    ) {
                        if (fromUser) {
                            render.setThinFaceStrength(progress.toFloat())
                        }
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })

                val whiteningSb = findViewById<SeekBar>(R.id.whitening_sb)
                whiteningSb.progress = render.getWhiteningStrength().toInt()
                whiteningSb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(
                        seekBar: SeekBar?,
                        progress: Int,
                        fromUser: Boolean
                    ) {
                        if (fromUser) {
                            render.setWhiteningStrength(progress.toFloat())
                        }
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })

                val smoothSkinSb = findViewById<SeekBar>(R.id.smooth_skin_sb)
                smoothSkinSb.progress = render.getSkinSmoothStrength().toInt()
                smoothSkinSb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(
                        seekBar: SeekBar?,
                        progress: Int,
                        fromUser: Boolean
                    ) {
                        if (fromUser) {
                            render.setSkinSmoothStrength(progress.toFloat())
                        }
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })
            }
        }
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
        TengineKitSdk.getInstance()?.releaseFaceDetect()
        TengineKitSdk.getInstance()?.release()
    }

    private fun getCameraPoints(dataSource: FloatArray, offset: Int, pointSize: Int): Array<Point> {
        return Array(pointSize) { i ->
            val ix = (offset + i) * 2
            val iy = ix + 1
            Point(dataSource[ix], dataSource[iy])
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseTengine()
        cancel()
    }
}