/*
 * Copyright 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.camera2basic



import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import com.example.android.camera2basic.services.CameraPermissionHelper

enum class CameraOrientation {
    LANDSCAPE, PORTRAIT
}

class CameraActivity : AppCompatActivity() {

    val MAX_HEIGHT_PIXELS: Int = 800
    val MAX_WIDTH_PIXELS: Int = 500

    lateinit var currentPhotoPath: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        // retrieve the saved photo path
        if (savedInstanceState != null) {
            if(savedInstanceState.getString(getString(R.string.saveStringToInstance)) != null) {
                currentPhotoPath = savedInstanceState.getString(getString(R.string.saveStringToInstance))
            }
        }

        createPicture()

        createTakePictureButtons()
    }

    /**
     * Saves the path to the image last taken if the activity gets destroyed, which happens
     * for example when someone switches his phone between landscape and portrait mode.
     */
    override fun onSaveInstanceState(outState: Bundle?) {
        if (::currentPhotoPath.isInitialized) {
            outState?.putString(getString(R.string.saveStringToInstance), currentPhotoPath)
        }
        super.onSaveInstanceState(outState)
    }

    /**
     * Gets called after the user is asked for permissions.
     * If the user does not allow camera or storage permissions pictures can not be taken.
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == CameraPermissionHelper.getRequestCode() && CameraPermissionHelper.hasPermissions(this)) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        } else {
            Toast.makeText(this, getString(R.string.no_camer_permission_toast), Toast.LENGTH_LONG)
                    .show()
        }
    }

     @SuppressLint("SourceLockedOrientationActivity")
     fun setOrientationToLandscape() {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    }

    @SuppressLint("SourceLockedOrientationActivity")
    fun setOrientationToPortrait() {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    fun unlockOrientation() {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER
    }

    /**
     * Creates the picture on the first page.
     */
    private fun createPicture() {
        if (::currentPhotoPath.isInitialized) {
            setPicture(currentPhotoPath)
        }
    }

    /**
     * Creates the two buttons to take pictures. One with landscape mode and one with portrait mode.
     */
    private fun createTakePictureButtons() {
        val buttonPortrait: Button = this.findViewById(R.id.picture_button) as Button
        val buttonLandscape: Button = this.findViewById(R.id.picture_button2) as Button

        buttonPortrait.setOnClickListener(){
            if (!CameraPermissionHelper.hasPermissions(this)) {
                CameraPermissionHelper.requestPermissions(this)
            } else {
                supportFragmentManager.beginTransaction()
                        .replace(R.id.container, Camera2BasicFragment.newInstance(CameraOrientation.PORTRAIT))
                        .commit()
            }
        }
        buttonLandscape.setOnClickListener(){
            if (!CameraPermissionHelper.hasPermissions(this)) {
                CameraPermissionHelper.requestPermissions(this)
            } else {
                supportFragmentManager.beginTransaction()
                        .replace(R.id.container, Camera2BasicFragment.newInstance(CameraOrientation.LANDSCAPE))
                        .commit()
            }
        }
    }

    /**
     * Sets the picture that the user took as new ImageView.
     */
    fun setPicture(currentPhotoPath: String) {
        val bMap = BitmapFactory.decodeFile(currentPhotoPath)

        // account for landscape of portrait mode to make picture either with more height or width
        val bMapScaled: Bitmap
        bMapScaled = if (bMap.width > bMap.height) {
            Bitmap.createScaledBitmap(bMap, MAX_HEIGHT_PIXELS, MAX_WIDTH_PIXELS, true)
        } else {
            Bitmap.createScaledBitmap(bMap, MAX_WIDTH_PIXELS, MAX_HEIGHT_PIXELS, true)
        }

        this.currentPhotoPath = currentPhotoPath

        val image: ImageView = this.findViewById<View>(R.id.picture_view) as ImageView
        image.setImageBitmap(bMapScaled)
    }
}
