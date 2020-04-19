package com.my.ugame

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

object PermissionUtils {

    const val PERMISSION_REQUEST_CODE = 100
    const val REQUEST_PERMISSION = Manifest.permission.CAMERA
    const val PERMISSION_SETTING_CODE = 101

    private var permissionExplainDialog: AlertDialog? = null
    private var permissionSettingDialog: AlertDialog? = null


    fun checkPermission(
        activity: AppCompatActivity,
        callBack: Runnable
    ) {
        if (ContextCompat.checkSelfPermission(
                activity,
                REQUEST_PERMISSION
            ) == PackageManager.PERMISSION_GRANTED
        ) callBack.run()
        else startRequestPermission(activity)
    }

    /**
     * 如果用户之前拒绝过，展示需要权限的提示框，否则直接请求相关权限
     */
    private fun startRequestPermission(activity: AppCompatActivity) {

        if (ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                REQUEST_PERMISSION
            )
        ) {
            showPermissionExplainDialog(activity)
        } else {
            requestPermission(activity)
        }
    }


    private fun requestPermission(activity: AppCompatActivity) {
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(REQUEST_PERMISSION),
            PERMISSION_REQUEST_CODE
        )
    }


    /**
     * 展示一个对话框，解释为什么需要此权限
     */
    private fun showPermissionExplainDialog(
        activity: AppCompatActivity
    ) {
        if (permissionExplainDialog == null) {
            permissionExplainDialog = AlertDialog.Builder(activity).setTitle("权限申请")
                .setMessage(
                    "您刚才拒绝了相关权限，开始游戏需要重新申请权限"
                )
                .setPositiveButton("申请权限") { dialog, _ ->
                    requestPermission(activity)
                    dialog.cancel()
                }
                .setNegativeButton("退出游戏") { dialog, _ ->
                    dialog.cancel()
                    activity.finish()
                }
                .create()
        }

        permissionExplainDialog?.let {
            if (!it.isShowing) {
                it.show()
            }
        }
    }

    fun showPermissionSettingDialog(activity: AppCompatActivity) {
        if (permissionSettingDialog == null) {
            permissionSettingDialog = AlertDialog.Builder(activity)
                .setTitle("权限设置")
                .setMessage("您刚才拒绝了相关的权限，请到应用设置页面更改应用的权限")
                .setPositiveButton("确定") { dialog, _ ->
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    val uri = Uri.fromParts("package", activity.packageName, null)
                    intent.data = uri
                    activity.startActivityForResult(intent, PERMISSION_SETTING_CODE)
                    dialog.cancel()
                }
                .setNegativeButton("取消") { dialog, _ ->
                    dialog.cancel()
                    activity.finish()
                }
                .create()

        }

        permissionSettingDialog?.let {
            if (!it.isShowing) {
                it.show()
            }
        }
    }
}