package com.my.ugame.cam

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.hardware.camera2.params.Face
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.widget.Toast
import java.util.*
import kotlin.collections.ArrayList


fun Context.toast(msg: String) {
    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}

class CameraHelper(val mActivity: Activity, private val mTextureView: AutoFitTextureView) {

    private lateinit var mCameraManager: CameraManager
    private var mCameraDevice: CameraDevice? = null
    private var mCameraCaptureSession: CameraCaptureSession? = null

    private var mCameraId = "0"
    private lateinit var mCameraCharacteristics: CameraCharacteristics

    private var mCameraSensorOrientation = 0                                            //摄像头方向
    private var mCameraFacing = CameraCharacteristics.LENS_FACING_BACK             //默认使用前置摄像头
    private var mFaceDetectMode = CaptureResult.STATISTICS_FACE_DETECT_MODE_OFF     //人脸检测模式

    private var canExchangeCamera = false                                               //是否可以切换摄像头
    private var mFaceDetectMatrix = Matrix()                                            //人脸检测坐标转换矩阵
    private var mFacesRect = ArrayList<RectF>()                                         //保存人脸坐标信息
    private var mFaceDetectListener: FaceDetectListener? = null                         //人脸检测回调

    private var mCameraHandler: Handler
    private val handlerThread = HandlerThread("CameraThread")

    private lateinit var mPreviewSize: Size

    interface FaceDetectListener {
        fun onFaceDetect(faces: Array<Face>, facesRect: ArrayList<RectF>)
    }

    fun setFaceDetectListener(listener: FaceDetectListener) {
        this.mFaceDetectListener = listener
    }

    init {
        handlerThread.start()
        mCameraHandler = Handler(handlerThread.looper)

        mTextureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureSizeChanged(
                surface: SurfaceTexture?,
                width: Int,
                height: Int
            ) {
                configureTransform(width, height)
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) {
            }

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture?): Boolean {
                releaseCamera()
                return true
            }

            override fun onSurfaceTextureAvailable(
                surface: SurfaceTexture?,
                width: Int,
                height: Int
            ) {
                initCameraInfo()
                configureTransform(width, height)
            }
        }
    }

    /**
     * 初始化
     */
    private fun initCameraInfo() {
        mCameraManager = mActivity.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraIdList = mCameraManager.cameraIdList
        if (cameraIdList.isEmpty()) {
            mActivity.toast("没有可用相机")
            return
        }

        for (id in cameraIdList) {
            val cameraCharacteristics = mCameraManager.getCameraCharacteristics(id)
            val facing = cameraCharacteristics.get(CameraCharacteristics.LENS_FACING)

            if (facing == mCameraFacing) {
                mCameraId = id
                mCameraCharacteristics = cameraCharacteristics
            }
        }

        val supportLevel =
            mCameraCharacteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
        if (supportLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
            mActivity.toast("相机硬件不支持新特性")
        }

        //获取摄像头方向
        mCameraSensorOrientation =
            mCameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)!!
        //获取StreamConfigurationMap，它是管理摄像头支持的所有输出格式和尺寸
        val configurationMap =
            mCameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!

        val previewSize = configurationMap.getOutputSizes(SurfaceTexture::class.java) //预览尺寸

        // 当屏幕为垂直的时候需要把宽高值进行调换，保证宽大于高
        mPreviewSize = getBestSize(
            mTextureView.height,
            mTextureView.width,
            previewSize.toList()
        )

        mTextureView.surfaceTexture.setDefaultBufferSize(mPreviewSize.width, mPreviewSize.height)
        mTextureView.setAspectRatio(mPreviewSize.height, mPreviewSize.width)

        initFaceDetect()
        openCamera()
    }


    private fun getBestSize(
        targetWidth: Int,
        targetHeight: Int,
        sizeList: List<Size>
    ): Size {
        val bigEnough = ArrayList<Size>()     //比指定宽高大的Size列表
        val notBigEnough = ArrayList<Size>()  //比指定宽高小的Size列表

        for (size in sizeList) {

            //宽高比 == 目标值宽高比
            if (size.width == size.height * targetWidth / targetHeight
            ) {
                if (size.width >= targetWidth && size.height >= targetHeight)
                    bigEnough.add(size)
                else
                    notBigEnough.add(size)
            }
        }

        //选择bigEnough中最小的值  或 notBigEnough中最大的值
        return when {
            bigEnough.size > 0 -> Collections.min(bigEnough, CompareSizesByArea())
            notBigEnough.size > 0 -> Collections.max(notBigEnough, CompareSizesByArea())
            else -> sizeList[0]
        }
    }

    /**
     * 初始化人脸检测相关信息
     */
    private fun initFaceDetect() {

        val faceDetectModes =
            mCameraCharacteristics.get(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES)  //人脸检测的模式

        mFaceDetectMode = when {
            faceDetectModes!!.contains(CaptureRequest.STATISTICS_FACE_DETECT_MODE_FULL) -> CaptureRequest.STATISTICS_FACE_DETECT_MODE_FULL
            faceDetectModes!!.contains(CaptureRequest.STATISTICS_FACE_DETECT_MODE_SIMPLE) -> CaptureRequest.STATISTICS_FACE_DETECT_MODE_FULL
            else -> CaptureRequest.STATISTICS_FACE_DETECT_MODE_OFF
        }

        if (mFaceDetectMode == CaptureRequest.STATISTICS_FACE_DETECT_MODE_OFF) {
            mActivity.toast("相机硬件不支持人脸检测")
            return
        }

        val activeArraySizeRect =
            mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)!! //获取成像区域
        val scaledWidth = mPreviewSize.width / activeArraySizeRect.width().toFloat()
        val scaledHeight = mPreviewSize.height / activeArraySizeRect.height().toFloat()

        val mirror = mCameraFacing == CameraCharacteristics.LENS_FACING_FRONT

        mFaceDetectMatrix.setRotate(mCameraSensorOrientation.toFloat())
        mFaceDetectMatrix.postScale(if (mirror) -scaledHeight else scaledHeight, scaledWidth)// 注意交换width和height的位置！
        mFaceDetectMatrix.postTranslate(
            mPreviewSize.height.toFloat(),
            mPreviewSize.width.toFloat()
        )

    }

    /**
     * 打开相机
     */
    @SuppressLint("MissingPermission")
    private fun openCamera() {
        mCameraManager.openCamera(mCameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                mCameraDevice = camera
                createCaptureSession(camera)
            }

            override fun onDisconnected(camera: CameraDevice) {
            }

            override fun onError(camera: CameraDevice, error: Int) {
                mActivity.toast("打开相机失败！$error")
            }
        }, mCameraHandler)
    }

    /**
     * 创建预览会话
     */
    private fun createCaptureSession(cameraDevice: CameraDevice) {

        val captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)

        val surface = Surface(mTextureView.surfaceTexture)
        captureRequestBuilder.addTarget(surface)  // 将CaptureRequest的构建器与Surface对象绑定在一起
        captureRequestBuilder.set(
            CaptureRequest.CONTROL_AE_MODE,
            CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH
        )      // 闪光灯
        captureRequestBuilder.set(
            CaptureRequest.CONTROL_AF_MODE,
            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
        ) // 自动对焦
        if (mFaceDetectMode != CaptureRequest.STATISTICS_FACE_DETECT_MODE_OFF)
            captureRequestBuilder.set(
                CaptureRequest.STATISTICS_FACE_DETECT_MODE,
                CameraCharacteristics.STATISTICS_FACE_DETECT_MODE_SIMPLE
            )//人脸检测

        // 为相机预览，创建一个CameraCaptureSession对象
        cameraDevice.createCaptureSession(
            arrayListOf(surface),
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigureFailed(session: CameraCaptureSession) {
                    mActivity.toast("开启预览会话失败！")
                }

                override fun onConfigured(session: CameraCaptureSession) {
                    mCameraCaptureSession = session
                    session.setRepeatingRequest(
                        captureRequestBuilder.build(),
                        mCaptureCallBack,
                        mCameraHandler
                    )
                }

            },
            mCameraHandler
        )
    }

    private val mCaptureCallBack = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult
        ) {
            super.onCaptureCompleted(session, request, result)
            if (mFaceDetectMode != CaptureRequest.STATISTICS_FACE_DETECT_MODE_OFF)
                handleFaces(result)

            canExchangeCamera = true
        }


        override fun onCaptureFailed(
            session: CameraCaptureSession,
            request: CaptureRequest,
            failure: CaptureFailure
        ) {
            super.onCaptureFailed(session, request, failure)
            mActivity.toast("开启预览失败！")
        }
    }

    /**
     * 处理人脸信息
     */
    private fun handleFaces(result: TotalCaptureResult) {
        val faces = result.get(CaptureResult.STATISTICS_FACES)!!
        mFacesRect.clear()

        for (face in faces) {
            val bounds = face.bounds

            val left = bounds.left
            val top = bounds.top
            val right = bounds.right
            val bottom = bounds.bottom

            val rawFaceRect =
                RectF(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
            mFaceDetectMatrix.mapRect(rawFaceRect)

            var resultFaceRect = if (mCameraFacing == CaptureRequest.LENS_FACING_FRONT) {
                rawFaceRect
            } else {
                RectF(
                    rawFaceRect.left,
                    rawFaceRect.top - mPreviewSize.width,
                    rawFaceRect.right,
                    rawFaceRect.bottom - mPreviewSize.width
                )
            }

            mFacesRect.add(resultFaceRect)

        }

        mActivity.runOnUiThread {
            mFaceDetectListener?.onFaceDetect(faces, mFacesRect)
        }
    }

    /**
     * 切换摄像头
     */
    fun exchangeCamera() {
        if (mCameraDevice == null || !canExchangeCamera || !mTextureView.isAvailable) return

        mCameraFacing = if (mCameraFacing == CameraCharacteristics.LENS_FACING_FRONT)
            CameraCharacteristics.LENS_FACING_BACK
        else
            CameraCharacteristics.LENS_FACING_FRONT

        releaseCamera()
        initCameraInfo()
    }


    fun releaseCamera() {
        mCameraCaptureSession?.close()
        mCameraCaptureSession = null

        mCameraDevice?.close()
        mCameraDevice = null

        canExchangeCamera = false
    }

    fun releaseThread() {
        handlerThread.quitSafely()
        handlerThread.join()
    }


    /**
     * Configures the necessary [android.graphics.Matrix] transformation to `mTextureView`.
     * This method should be called after the camera preview size is determined in
     * setUpCameraOutputs and also the size of `mTextureView` is fixed.
     *
     * @param viewWidth  The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */
    private fun configureTransform(viewWidth: Int, viewHeight: Int) {
        val rotation = mActivity.windowManager.defaultDisplay.rotation
        val matrix = Matrix()
        val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        val bufferRect = RectF(0f, 0f, mPreviewSize.height.toFloat(), mPreviewSize.width.toFloat())
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
            val scale = Math.max(
                viewHeight.toFloat() / mPreviewSize.height,
                viewWidth.toFloat() / mPreviewSize.width
            )
            matrix.postScale(scale, scale, centerX, centerY)
            matrix.postRotate((90 * (rotation - 2)).toFloat(), centerX, centerY)
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180f, centerX, centerY)
        }
        mTextureView.setTransform(matrix)
    }

    private class CompareSizesByArea : Comparator<Size> {
        override fun compare(size1: Size, size2: Size): Int {
            return java.lang.Long.signum(size1.width.toLong() * size1.height - size2.width.toLong() * size2.height)
        }
    }
}