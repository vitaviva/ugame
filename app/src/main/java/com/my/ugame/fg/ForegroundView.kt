package com.my.ugame.fg

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.hardware.camera2.params.Face
import android.util.AttributeSet
import android.util.TypedValue
import android.widget.FrameLayout
import androidx.annotation.MainThread
import androidx.core.animation.doOnEnd
import com.my.ugame.cam.CameraHelper


/**
 * 前景容器类
 */
class ForegroundView(context: Context, attrs: AttributeSet?) : FrameLayout(context, attrs),
    CameraHelper.FaceDetectListener {

    private var _isStop: Boolean = false

    internal var boat: Boat? = null

    private val _width by lazy { (width * BOAT_WIDTH_FACTOR).toInt() }

    private val _widthOffset by lazy { dip2px(context, 50F) }

    private val _heightOffset by lazy { dip2px(context, 150F) }

    /**
     * 游戏停止，潜艇不再移动
     */
    @MainThread
    fun stop() {
        _isStop = true
    }

    /**
     * 游戏开始时通过动画进入
     */
    @MainThread
    fun start() {
        _isStop = false
        if (boat == null) {
            boat = Boat(context).also {
                post {
                    addView(it.view, _width, _width)
                    AnimatorSet().apply {
                        play(
                            ObjectAnimator.ofFloat(
                                it.view,
                                "y",
                                0F,
                                this@ForegroundView.height / 2f
                            )
                        ).with(
                            ObjectAnimator.ofFloat(it.view, "rotation", 0F, 360F)
                        )
                        doOnEnd { _ -> it.view.rotation = 0F }
                        duration = 1000
                    }.start()
                }
            }
        }
    }

    /**
     * 接受人脸识别的回调，移动位置
     */
    override fun onFaceDetect(faces: Array<Face>, facesRect: ArrayList<RectF>) {
        if (_isStop) return
        if (facesRect.isNotEmpty()) {
            boat?.run {
                val face = facesRect.first()
                val x = (face.left - _widthOffset).toInt()
                val y = (face.top + _heightOffset).toInt()
                moveTo(x, y)
            }
            _face = facesRect.first()
        }
    }


    //debug
    private val _paint by lazy {
        Paint().apply {
            color = Color.parseColor("#42ed45")
            style = Paint.Style.STROKE
            strokeWidth = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                1f,
                context.resources.displayMetrics
            )
            isAntiAlias = true
        }
    }

    private var _face: RectF? = null
        set(value) {
            field = value
            invalidate()
        }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        _face?.let {
            canvas.drawRect(it, _paint)
        }
    }

}

private const val BOAT_WIDTH_FACTOR = 0.2F

private fun dip2px(context: Context, dp: Float): Float {
    val scale = context.resources.displayMetrics.density
    return dp * scale * 0.5f
}