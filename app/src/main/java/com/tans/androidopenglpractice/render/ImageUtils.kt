package com.tans.androidopenglpractice.render

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

//fun yuv420888toNv21(image: ImageProxy, nv21: ByteArray) {
//    if (image.format != ImageFormat.YUV_420_888) {
//        error("Image format is not YUV_420_888.")
//    }
//    val yPlane = image.planes[0]
//    val uPlane = image.planes[1]
//    val vPlane = image.planes[2]
//    val yBuffer = yPlane.buffer
//    val uBuffer = uPlane.buffer
//    val vBuffer = vPlane.buffer
//    yBuffer.rewind()
//    uBuffer.rewind()
//    vBuffer.rewind()
//    val ySize = yBuffer.remaining()
//    var position = 0
//
//    // Add the full y buffer to the array. If rowStride > 1, some padding may be skipped.
//    for (row in 0 until image.height) {
//        yBuffer[nv21, position, image.width]
//        position += image.width
//        yBuffer.position(
//            ySize.coerceAtMost(yBuffer.position() - image.width + yPlane.rowStride)
//        )
//    }
//    val chromaHeight = image.height / 2
//    val chromaWidth = image.width / 2
//    val vRowStride = vPlane.rowStride
//    val uRowStride = uPlane.rowStride
//    val vPixelStride = vPlane.pixelStride
//    val uPixelStride = uPlane.pixelStride
//
//    // Interleave the u and v frames, filling up the rest of the buffer. Use two line buffers to
//    // perform faster bulk gets from the byte buffers.
//    val vLineBuffer = ByteArray(vRowStride)
//    val uLineBuffer = ByteArray(uRowStride)
//    for (row in 0 until chromaHeight) {
//        vBuffer[vLineBuffer, 0, vRowStride.coerceAtMost(vBuffer.remaining())]
//        uBuffer[uLineBuffer, 0, uRowStride.coerceAtMost(uBuffer.remaining())]
//        var vLineBufferPosition = 0
//        var uLineBufferPosition = 0
//        for (col in 0 until chromaWidth) {
//            nv21[position++] = vLineBuffer[vLineBufferPosition]
//            nv21[position++] = uLineBuffer[uLineBufferPosition]
//            vLineBufferPosition += vPixelStride
//            uLineBufferPosition += uPixelStride
//        }
//    }
//}
//
//fun nv21ToJpeg(
//    nv21: ByteArray, width: Int, height: Int,
//    cropRect: Rect?, @IntRange(from = 1, to = 100) jpegQuality: Int
//): ByteArray {
//    val out = ByteArrayOutputStream()
//    val yuv = YuvImage(nv21, ImageFormat.NV21, width, height, null)
//    val success = yuv.compressToJpeg(
//        cropRect ?: Rect(0, 0, width, height),
//        jpegQuality, out
//    )
//    if (!success) {
//        error("YuvImage failed to encode jpeg.")
//    }
//    return out.toByteArray()
//}
//
//fun nv21ToRgb(rgb: ByteArray, nv21: ByteArray, width: Int, height: Int) {
//    val frameSize = width * height
//    var j = 0
//    var yp = 0
//    var position = 0
//    while (j < height) {
//        var uvp = frameSize + (j shr 1) * width
//        var u = 0
//        var v = 0
//        var i = 0
//        while (i < width) {
//            var y = (0xff and nv21[yp].toInt()) - 16
//            if (y < 0) y = 0
//            if (i and 1 == 0) {
//                v = (0xff and nv21[uvp++].toInt()) - 128
//                u = (0xff and nv21[uvp++].toInt()) - 128
//            }
//            val y1192 = 1192 * y
//            var r = y1192 + 1634 * v
//            var g = y1192 - 833 * v - 400 * u
//            var b = y1192 + 2066 * u
//            if (r < 0) r = 0 else if (r > 262143) r = 262143
//            if (g < 0) g = 0 else if (g > 262143) g = 262143
//            if (b < 0) b = 0 else if (b > 262143) b = 262143
//            rgb[position ++] = (r and 0x00_00_00_FF).toByte()
//            rgb[position ++] = (g and 0x00_00_00_FF).toByte()
//            rgb[position ++] = (b and 0x00_00_00_FF).toByte()
//            i++
//            yp++
//        }
//        j++
//    }
//}

fun ImageProxy.toBitmap(applyRotation: Boolean = false): Bitmap {
    val bitmap = when (format) {
        ImageFormat.YUV_420_888 -> yuv4208888ToBitmap(this)
        // It's unclear why PixelFormat is used here instead of ImageFormat, but this is documented behavior
        PixelFormat.RGBA_8888 -> rgba8888ToBitmap(this)
        else -> throw IllegalStateException("Unsupported image format: $format. Please check the documentation of Android ImageAnalysis API for supported formats.")
    }

    return if (applyRotation) {
        val rotatedBitmap = bitmap.rotate(imageInfo.rotationDegrees.toFloat())
        rotatedBitmap
    } else {
        bitmap
    }
}

fun rgba8888ToBitmap(image: ImageProxy): Bitmap {
    val encodedImage = image.planes[0]
    val pixelStride = encodedImage.pixelStride
    val rowStride = encodedImage.rowStride
    val rowPadding = rowStride - pixelStride * image.width
    val bitmap = Bitmap.createBitmap(
        image.width + rowPadding / pixelStride,
        image.height, Bitmap.Config.ARGB_8888
    )
    bitmap.copyPixelsFromBuffer(encodedImage.buffer)
    return bitmap
}

fun yuv4208888ToBitmap(image: ImageProxy): Bitmap {
    val nv21 = yuv420888ToNv21(image)
    val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
    return yuvImage.toBitmap()
}
fun YuvImage.toBitmap(): Bitmap {
    val out = ByteArrayOutputStream()
    val ok = compressToJpeg(Rect(0, 0, width, height), 100, out)
    check(ok) { "Something gone wrong during conversion of YUV image to jpeg format" }

    val imageBytes: ByteArray = out.toByteArray()
    return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
}
fun yuv420888ToNv21(image: ImageProxy): ByteArray {
    val pixelCount = image.cropRect.width() * image.cropRect.height()
    val pixelSizeBits = ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888)
    val outputBuffer = ByteArray(pixelCount * pixelSizeBits / 8)
    yuv420888ToNv21(image, outputBuffer, pixelCount)
    return outputBuffer
}
fun yuv420888ToNv21(image: ImageProxy, outputBuffer: ByteArray, pixelCount: Int) {
    assert(image.format == ImageFormat.YUV_420_888)

    val imageCrop = image.cropRect
    val imagePlanes = image.planes

    imagePlanes.forEachIndexed { planeIndex, plane ->
        // How many values are read in input for each output value written
        // Only the Y plane has a value for every pixel, U and V have half the resolution i.e.
        //
        // Y Plane            U Plane    V Plane
        // ===============    =======    =======
        // Y Y Y Y Y Y Y Y    U U U U    V V V V
        // Y Y Y Y Y Y Y Y    U U U U    V V V V
        // Y Y Y Y Y Y Y Y    U U U U    V V V V
        // Y Y Y Y Y Y Y Y    U U U U    V V V V
        // Y Y Y Y Y Y Y Y
        // Y Y Y Y Y Y Y Y
        // Y Y Y Y Y Y Y Y
        val outputStride: Int

        // The index in the output buffer the next value will be written at
        // For Y it's zero, for U and V we start at the end of Y and interleave them i.e.
        //
        // First chunk        Second chunk
        // ===============    ===============
        // Y Y Y Y Y Y Y Y    V U V U V U V U
        // Y Y Y Y Y Y Y Y    V U V U V U V U
        // Y Y Y Y Y Y Y Y    V U V U V U V U
        // Y Y Y Y Y Y Y Y    V U V U V U V U
        // Y Y Y Y Y Y Y Y
        // Y Y Y Y Y Y Y Y
        // Y Y Y Y Y Y Y Y
        var outputOffset: Int

        when (planeIndex) {
            0 -> {
                outputStride = 1
                outputOffset = 0
            }
            1 -> {
                outputStride = 2
                // For NV21 format, U is in odd-numbered indices
                outputOffset = pixelCount + 1
            }
            2 -> {
                outputStride = 2
                // For NV21 format, V is in even-numbered indices
                outputOffset = pixelCount
            }
            else -> {
                // Image contains more than 3 planes, something strange is going on
                return@forEachIndexed
            }
        }

        val planeBuffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride

        // We have to divide the width and height by two if it's not the Y plane
        val planeCrop = if (planeIndex == 0) {
            imageCrop
        } else {
            Rect(
                imageCrop.left / 2,
                imageCrop.top / 2,
                imageCrop.right / 2,
                imageCrop.bottom / 2
            )
        }

        val planeWidth = planeCrop.width()
        val planeHeight = planeCrop.height()

        // Intermediate buffer used to store the bytes of each row
        val rowBuffer = ByteArray(plane.rowStride)

        // Size of each row in bytes
        val rowLength = if (pixelStride == 1 && outputStride == 1) {
            planeWidth
        } else {
            // Take into account that the stride may include data from pixels other than this
            // particular plane and row, and that could be between pixels and not after every
            // pixel:
            //
            // |---- Pixel stride ----|                    Row ends here --> |
            // | Pixel 1 | Other Data | Pixel 2 | Other Data | ... | Pixel N |
            //
            // We need to get (N-1) * (pixel stride bytes) per row + 1 byte for the last pixel
            (planeWidth - 1) * pixelStride + 1
        }

        for (row in 0 until planeHeight) {
            // Move buffer position to the beginning of this row
            planeBuffer.position(
                (row + planeCrop.top) * rowStride + planeCrop.left * pixelStride
            )

            if (pixelStride == 1 && outputStride == 1) {
                // When there is a single stride value for pixel and output, we can just copy
                // the entire row in a single step
                planeBuffer.get(outputBuffer, outputOffset, rowLength)
                outputOffset += rowLength
            } else {
                // When either pixel or output have a stride > 1 we must copy pixel by pixel
                planeBuffer.get(rowBuffer, 0, rowLength)
                for (col in 0 until planeWidth) {
                    outputBuffer[outputOffset] = rowBuffer[col * pixelStride]
                    outputOffset += outputStride
                }
            }
        }
    }
}

fun Bitmap.rotate(degrees: Float): Bitmap {
    if (degrees == 0f) return this
    val matrix = Matrix().apply { postRotate(degrees) }
    val new =  Bitmap.createBitmap(this, 0, 0, this.width, this.height, matrix, true)
    this.recycle()
    return new
}