package com.tans.androidopenglpractice.render.camera

import jama.Matrix
import jkalman.JKalman

class KalmanPointFilter {

    private val kalman: JKalman by lazy {
        JKalman(4, 2)
    }

    private val inputMatrix: Matrix by lazy {
        Matrix(2, 1)
    }

    init {
        reset()
    }

    fun reset() {
        val tr: Array<DoubleArray> = arrayOf(
            doubleArrayOf(1.0, 0.0, 1.0, 0.0),
            doubleArrayOf(0.0, 1.0, 0.0, 1.0),
            doubleArrayOf(0.0, 0.0, 1.0, 0.0),
            doubleArrayOf(0.0, 0.0, 0.0, 1.0)
        )
        kalman.transition_matrix = Matrix(tr)
        kalman.error_cov_post = kalman.error_cov_post.identity()
    }

    fun filter(input: Point): Point {
        return try {
            val predict = kalman.Predict()
            inputMatrix.set(0, 0, input.x.toDouble())
            inputMatrix.set(1, 0, input.y.toDouble())
            kalman.Correct(inputMatrix)
            Point(
                x = predict.get(0, 0).toFloat(),
                y = predict.get(1, 0).toFloat()
            )
        }  catch (e: Throwable) {
            e.printStackTrace()
            input
        }
    }
}