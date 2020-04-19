package com.my.ugame

import android.graphics.RectF
import android.hardware.camera2.params.Face
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.*
import com.my.ugame.bg.BackgroundView
import com.my.ugame.bg.FIRST_APPEAR_DELAY_MILLIS
import com.my.ugame.cam.AutoFitTextureView
import com.my.ugame.cam.CameraHelper
import com.my.ugame.fg.ForegroundView

/**
 * 游戏控制类
 */
class GameController(
    private val activity: AppCompatActivity,
    private val textureView: AutoFitTextureView,
    private val bg: BackgroundView,
    private val fg: ForegroundView
) {
    init {
        activity.lifecycle.addObserver(object : LifecycleObserver {

            @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            fun onDestroy() {
                cameraHelper?.releaseCamera()
                cameraHelper?.releaseThread()
            }
        })
    }

    private val handler by lazy {
        android.os.Handler(activity.mainLooper)
    }

    private var cameraHelper: CameraHelper? = null

    private var _score: Long = 0L

    /**
     * 游戏停止
     */
    fun stop() {
        bg.stop()
        fg.stop()
        _state.value = GameState.Over(_score)
        _score = 0L
    }

    /**
     * 游戏再开
     */
    fun start() {
        initCamera()
        fg.start()
        bg.start()
        _state.value = GameState.Start
        handler.postDelayed({
            startScoring()
        }, FIRST_APPEAR_DELAY_MILLIS)
    }

    /**
     * 开始计分
     */
    private fun startScoring() {
        handler.postDelayed(
            {
                fg.boat?.run {
                    bg.barsList.flatMap { listOf(it.up, it.down) }
                        .forEach { bar ->
                            if (isCollision(
                                    bar.x, bar.y, bar.w, bar.h,
                                    this.x, this.y, this.w, this.h
                                )
                            ) {
                                stop()
                                return@postDelayed
                            }
                        }
                }
                _score++
                _state.value = GameState.Score(_score)
                startScoring()
            }, 100
        )
    }

    /**
     * 碰撞检测
     */
    private fun isCollision(
        x1: Float,
        y1: Float,
        w1: Float,
        h1: Float,
        x2: Float,
        y2: Float,
        w2: Float,
        h2: Float
    ): Boolean {
        if (x1 > x2 + w2 || x1 + w1 < x2 || y1 > y2 + h2 || y1 + h1 < y2) {
            return false
        }
        return true
    }

    /**
     * 相机初始化
     */
    private fun initCamera() {
        cameraHelper ?: run {
            cameraHelper = CameraHelper(activity, textureView).apply {
                setFaceDetectListener(object : CameraHelper.FaceDetectListener {
                    override fun onFaceDetect(faces: Array<Face>, facesRect: ArrayList<RectF>) {
                        if (facesRect.isNotEmpty()) {
                            fg.onFaceDetect(faces, facesRect)
                        }
                    }
                })
            }
        }
    }

    /**
     * 切换摄像头
     */
    fun switchCamera() {
        cameraHelper?.exchangeCamera()
    }

    /**
     * 游戏状态
     */
    private val _state = MutableLiveData<GameState>()
    internal val gameState: LiveData<GameState>
        get() = _state
}


sealed class GameState(open val score: Long) {

    object Start : GameState(0)

    data class Over(override val score: Long) : GameState(score)

    data class Score(override val score: Long) : GameState(score)

}