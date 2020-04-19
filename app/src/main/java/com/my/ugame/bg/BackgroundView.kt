package com.my.ugame.bg

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import androidx.annotation.UiThread
import androidx.core.animation.doOnEnd
import java.util.*

class BackgroundView(context: Context, attrs: AttributeSet?) : FrameLayout(context, attrs) {

    private lateinit var _timer: Timer

    private var _random = Random()

    private val _anims = mutableListOf<ValueAnimator>()

    private val _barWidth by lazy { width * BAR_WIDTH_FACTOR } // 障碍物默认宽度

    private val _barHeight by lazy { height * BAR_HEIGHT_FACTOR } // 障碍物默认高度

    private val _gap by lazy { height * BAR_GAP_FACTOR } // 障碍物空洞间隙

    private val _step by lazy { height * 0.1F } // 高度调整步进单位

    internal val barsList = mutableListOf<Bars>()

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        barsList.flatMap { listOf(it.up, it.down) }.forEach {
            val w = it.view.measuredWidth
            val h = it.view.measuredHeight
            when (it) {
                is UpBar -> it.view.layout(0, 0, w, h)
                else -> it.view.layout(0, height - h, w, height)
            }
        }
    }

    @UiThread
    fun stop() {
        _timer.cancel()
        _anims.forEach { it.cancel() }
        _anims.clear()
    }

    @UiThread
    fun start() {
        _clearBars()
        Timer().also { _timer = it }.schedule(object : TimerTask() {
            override fun run() {
                post {
                    _createBars(context, barsList.lastOrNull()).let {
                        _addBars(it)
                        _moveBars(it)
                    }
                }
            }

        },
            FIRST_APPEAR_DELAY_MILLIS,
            BAR_APPEAR_INTERVAL_MILLIS
        )
    }

    private fun _createBars(context: Context, pre: Bars?) = run {
        val up = UpBar(context, this).apply {
            h = pre?.let {
                val step = when {
                    it.up.h >= height - _gap - _step -> -_step
                    it.up.h <= _step -> _step
                    _random.nextBoolean() -> _step
                    else -> -_step
                }
                it.up.h + step
            } ?: _barHeight
            w = _barWidth
        }

        val down = DnBar(context, this).apply {
            h = height - up.h - _gap
            w = _barWidth
        }

        Bars(up, down)

    }


    private fun _clearBars() {
        barsList.clear()
        removeAllViews()
    }

    private fun _addBars(bars: Bars) {
        barsList.add(bars)
        bars.asArray().forEach {
            addView(
                it.view,
                ViewGroup.LayoutParams(
                    it.w.toInt(),
                    it.h.toInt()
                )
            )
        }
    }

    private fun _moveBars(bars: Bars) {
        _anims.add(
            ValueAnimator.ofFloat(width.toFloat(), -_barWidth)
                .apply {
                    addUpdateListener {
                        bars.asArray().forEach { bar ->
                            bar.x = it.animatedValue as Float
                            if (bar.x + bar.w <= 0) {
                                post { removeView(bar.view) }
                            }
                        }
                    }

                    duration = BAR_MOVE_DURATION_MILLIS
                    interpolator = LinearInterpolator()
                    start()
                })
    }

}


internal data class Bars(val up: Bar, val down: Bar)

private fun Bars.asArray() = arrayOf(up, down)

private const val BAR_WIDTH_FACTOR = 0.22F

private const val BAR_HEIGHT_FACTOR = 0.35F

private const val BAR_GAP_FACTOR = BAR_HEIGHT_FACTOR

private const val BAR_MOVE_DURATION_MILLIS = 4000L

private const val BAR_APPEAR_INTERVAL_MILLIS = (BAR_MOVE_DURATION_MILLIS / 2.2).toLong()

internal const val FIRST_APPEAR_DELAY_MILLIS = 3000L