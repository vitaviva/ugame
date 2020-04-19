package com.my.ugame.fg

import android.animation.ObjectAnimator
import android.content.Context
import android.widget.OverScroller
import androidx.appcompat.widget.AppCompatImageView
import com.my.ugame.R
import kotlin.math.atan


/**
 * 潜艇类
 */
class Boat(context: Context) {

    internal val view by lazy { BoatView(context) }

    val h
        get() = view.height.toFloat()

    val w
        get() = view.width.toFloat()

    val x
        get() = view.x

    val y
        get() = view.y

    /**
     * 移动到指定坐标
     */
    fun moveTo(x: Int, y: Int) {
        view.smoothMoveTo(x, y)
    }

}


internal class BoatView(context: Context?) : AppCompatImageView(context) {

    private val _scroller by lazy { OverScroller(context) }

    private val _res = arrayOf(
        R.mipmap.boat_000,
        R.mipmap.boat_002
    )

    private var _rotationAnimator: ObjectAnimator? = null

    private var _cnt = 0
        set(value) {
            field = if (value > 1) 0 else value
        }

    init {
        scaleType = ScaleType.FIT_CENTER
        _startFlashing()
    }

    private fun _startFlashing() {
        postDelayed({
            setImageResource(_res[_cnt++])
            _startFlashing()
        }, 500)
    }

    override fun computeScroll() {
        super.computeScroll()

        if (_scroller.computeScrollOffset()) {

            x = _scroller.currX.toFloat()
            y = _scroller.currY.toFloat()

            // Keep on drawing until the animation has finished.
            postInvalidateOnAnimation()
        }

    }

    /**
     * 移动更加顺换
     */
    internal fun smoothMoveTo(x: Int, y: Int) {
        if (!_scroller.isFinished) _scroller.abortAnimation()
        _rotationAnimator?.let { if (it.isRunning) it.cancel() }

        val curX = this.x.toInt()
        val curY = this.y.toInt()

        val dx = (x - curX)
        val dy = (y - curY)
        _scroller.startScroll(curX, curY, dx, dy, 250)

        _rotationAnimator = ObjectAnimator.ofFloat(
            this,
            "rotation",
            rotation,
            Math.toDegrees(atan((dy / 100.toDouble()))).toFloat()
        ).apply {
            duration = 100
            start()
        }

        postInvalidateOnAnimation()
    }
}
