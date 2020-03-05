package com.example.android.camera2basic.services

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat

/** Helper to ask camera permission.  */
object CameraPermissionHelper {
    private const val CAMERA_PERMISSION_CODE = 222
    private const val CAMERA_PERMISSION = Manifest.permission.CAMERA
    private const val WRITING_STORAGE_PERMISSION = Manifest.permission.WRITE_EXTERNAL_STORAGE

    /** Check to see we have the necessary permissions for this app.  */
    fun hasPermissions(activity: Activity): Boolean {
        return ContextCompat.checkSelfPermission(activity, CAMERA_PERMISSION) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(activity, WRITING_STORAGE_PERMISSION) == PackageManager.PERMISSION_GRANTED
    }

    /** Check to see we have the necessary permissions for this app, and ask for them if we don't.  */
    fun requestPermissions(activity: Activity) {
        ActivityCompat.requestPermissions(
                activity, arrayOf(CAMERA_PERMISSION, WRITING_STORAGE_PERMISSION), CAMERA_PERMISSION_CODE)
    }

    fun getRequestCode (): Int {
        return CAMERA_PERMISSION_CODE
    }

    /** Check to see if we need to show the rationale for this permission.  */
    fun shouldShowRequestPermissionRationale(activity: Activity): Boolean {
        return ActivityCompat.shouldShowRequestPermissionRationale(activity, CAMERA_PERMISSION) ||
                ActivityCompat.shouldShowRequestPermissionRationale(activity, WRITING_STORAGE_PERMISSION)
    }

    /** Launch Application Setting to grant permission.  */
    fun launchPermissionSettings(activity: Activity) {
        val intent = Intent()
        intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        intent.data = Uri.fromParts("package", activity.packageName, null)
        activity.startActivity(intent)
    }
}