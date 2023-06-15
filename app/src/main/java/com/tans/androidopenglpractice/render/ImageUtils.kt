package com.tans.androidopenglpractice.render

import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.annotation.IntRange
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

fun yuv420888toNv21(image: ImageProxy, nv21: ByteArray): ByteArray {
    if (image.format != ImageFormat.YUV_420_888) {
        error("Image format is not YUV_420_888.")
    }
    val yPlane = image.planes[0]
    val uPlane = image.planes[1]
    val vPlane = image.planes[2]
    val yBuffer = yPlane.buffer
    val uBuffer = uPlane.buffer
    val vBuffer = vPlane.buffer
    yBuffer.rewind()
    uBuffer.rewind()
    vBuffer.rewind()
    val ySize = yBuffer.remaining()
    var position = 0

    // Add the full y buffer to the array. If rowStride > 1, some padding may be skipped.
    for (row in 0 until image.height) {
        yBuffer[nv21, position, image.width]
        position += image.width
        yBuffer.position(
            ySize.coerceAtMost(yBuffer.position() - image.width + yPlane.rowStride)
        )
    }
    val chromaHeight = image.height / 2
    val chromaWidth = image.width / 2
    val vRowStride = vPlane.rowStride
    val uRowStride = uPlane.rowStride
    val vPixelStride = vPlane.pixelStride
    val uPixelStride = uPlane.pixelStride

    // Interleave the u and v frames, filling up the rest of the buffer. Use two line buffers to
    // perform faster bulk gets from the byte buffers.
    val vLineBuffer = ByteArray(vRowStride)
    val uLineBuffer = ByteArray(uRowStride)
    for (row in 0 until chromaHeight) {
        vBuffer[vLineBuffer, 0, vRowStride.coerceAtMost(vBuffer.remaining())]
        uBuffer[uLineBuffer, 0, uRowStride.coerceAtMost(uBuffer.remaining())]
        var vLineBufferPosition = 0
        var uLineBufferPosition = 0
        for (col in 0 until chromaWidth) {
            nv21[position++] = vLineBuffer[vLineBufferPosition]
            nv21[position++] = uLineBuffer[uLineBufferPosition]
            vLineBufferPosition += vPixelStride
            uLineBufferPosition += uPixelStride
        }
    }
    return nv21
}

fun nv21ToJpeg(
    nv21: ByteArray, width: Int, height: Int,
    cropRect: Rect?, @IntRange(from = 1, to = 100) jpegQuality: Int
): ByteArray? {
    val out = ByteArrayOutputStream()
    val yuv = YuvImage(nv21, ImageFormat.NV21, width, height, null)
    val success = yuv.compressToJpeg(
        cropRect ?: Rect(0, 0, width, height),
        jpegQuality, out
    )
    if (!success) {
        error("YuvImage failed to encode jpeg.")
    }
    return out.toByteArray()
}

fun nv21ToRgb(rgb: ByteArray, nv21: ByteArray, width: Int, height: Int) {
    val frameSize = width * height
    var j = 0
    var yp = 0
    while (j < height) {
        var uvp = frameSize + (j shr 1) * width
        var u = 0
        var v = 0
        var i = 0
        while (i < width) {
            var y = (0xff and nv21[yp].toInt()) - 16
            if (y < 0) y = 0
            if (i and 1 == 0) {
                v = (0xff and nv21[uvp++].toInt()) - 128
                u = (0xff and nv21[uvp++].toInt()) - 128
            }
            val y1192 = 1192 * y
            var r = y1192 + 1634 * v
            var g = y1192 - 833 * v - 400 * u
            var b = y1192 + 2066 * u
            if (r < 0) r = 0 else if (r > 262143) r = 262143
            if (g < 0) g = 0 else if (g > 262143) g = 262143
            if (b < 0) b = 0 else if (b > 262143) b = 262143
            rgb[yp] = r.toByte()
            rgb[yp + 1] = g.toByte()
            rgb[yp + 2] = b.toByte()
            i++
            yp++
        }
        j++
    }
}