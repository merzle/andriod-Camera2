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
import android.content.Context
import android.content.res.Configuration
import android.graphics.*
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.media.Image
import android.os.Bundle
import android.os.Environment
import android.support.v4.app.ActivityCompat
import android.support.v4.app.Fragment
import android.util.Log
import android.util.Size
import android.view.*
import android.widget.FrameLayout
import android.widget.ImageView
import com.example.android.camera2basic.services.*
import com.example.android.camera2basic.services.Camera
import com.example.android.camera2basic.services.ImageSaver
import com.example.android.camera2basic.ui.AutoFitTextureView
import com.example.android.camera2basic.ui.ErrorDialog
import com.example.android.camera2basic.ui.FocusView
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

enum class CameraMode {
    AUTO_FIT, FULL_SCREEN
}

class Camera2BasicFragment : Fragment(), View.OnClickListener,
        ActivityCompat.OnRequestPermissionsResultCallback {
    /**
     * [TextureView.SurfaceTextureListener] handles several lifecycle events on a
     * [TextureView].
     */
    private var surfaceTextureListener = object : TextureView.SurfaceTextureListener {

        override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                openCamera(width, height)
        }

        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
           configureTransform(width, height)
        }

        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
            closeCameraPreview()
            return true
        }

        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit

    }

    /**
     * An [AutoFitTextureView] for camera preview.
     */
    private lateinit var textureView: AutoFitTextureView

    /**
     * SurfaceView to render camera preview
     */
    private var previewSurface: SurfaceTexture? = null

    /**
     * The [android.util.Size] of camera preview.
     */
    private lateinit var previewSize: Size

    /**
     * The ImageSaver to save the image.
     */
    private lateinit var  imageSaver: ImageSaver

    /**
     * This is the path to the picture taken.
     */
    private lateinit var currentPhotoPath: String

    /**
     * Whether the current camera device supports Flash or not.
     */
    private var flashSupported = false

    /**
     * The imageView of the flash symbol.
     */
   private lateinit var flash: ImageView

    /**
     * Camera module
     */
    private var camera: Camera? = null

    private val cameraMode: CameraMode = CameraMode.FULL_SCREEN

    private lateinit var focus: FocusView

    private lateinit var cameraOrientation: CameraOrientation

    override fun onCreateView(inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_camera2_basic, container, false)


    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        //read the camera orientation from the arguments
        arguments?.getSerializable(stringArgument)?.let {
            cameraOrientation = it as CameraOrientation
        }

        view.findViewById<View>(R.id.picture).setOnClickListener(this)

        flash = view.findViewById<View>(R.id.flash) as ImageView
        flash.setOnClickListener(this)


        focus = view.findViewById(R.id.focus_view)
        textureView = view.findViewById(R.id.texture)

        textureView.setOnTouchListener { _, event ->
            camera?.manualFocus(
                    event.x,
                    event.y,
                    textureView.width,
                    textureView.height)
            focus.showFocus(event.x.toInt(), event.y.toInt())
            return@setOnTouchListener true
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        val manager = activity!!.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        camera = Camera.initInstance(manager)
        setCameraOrientation(cameraOrientation)
    }

    override fun onResume() {
        super.onResume()
        // When the screen is turned off and turned back on, the SurfaceTexture is already
        // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
        // a camera and start preview from here (otherwise, we wait until the surface is ready in
        // the SurfaceTextureListener).
        if (textureView.isAvailable) {
                openCamera(textureView.width, textureView.height)
        } else {
            textureView.surfaceTextureListener = surfaceTextureListener
        }
    }

    override fun onPause() {
        camera?.close()
        super.onPause()
    }


    /**
     * Sets the given camera orientation and fixes the phone in that orientation.
     * Additionally, sets the rotation for the camera in order to have to right one for
     * taking and saving pictures.
     *
     * @param cameraOrientation  The orientation the camera should have.
     */
    private fun setCameraOrientation(cameraOrientation: CameraOrientation) {
        if (cameraOrientation == CameraOrientation.LANDSCAPE) {
            (activity as CameraActivity).setOrientationToLandscape()
            camera?.deviceRotation = activity?.windowManager?.defaultDisplay?.rotation!!
        }
        if (cameraOrientation == CameraOrientation.PORTRAIT) {
            (activity as CameraActivity).setOrientationToPortrait()
            camera?.deviceRotation  = activity?.windowManager?.defaultDisplay?.rotation!!
        }
    }

    /**
     * Sets up member variables related to camera.
     *
     * @param width  The width of available size for camera preview
     * @param height The height of available size for camera preview
     */
    private fun setUpCameraOutputs(width: Int, height: Int, camera: Camera, mode: CameraMode) {
        try {
            // val largest = camera.getCaptureSize()
            // For preview, we want to make sure camera fits to screen size

            val largest: Size = when(mode) {
                CameraMode.AUTO_FIT -> {
                    // we want to make sure captured image fits to screen size,
                    // so choose the largest one we can get from supported capture sizes
                    camera.getCaptureSize()
                }
                CameraMode.FULL_SCREEN -> {
                    // In this example, opengl is also full screen.
                    // When full screen, we choose the largest from supported surface view sizes
                    val realSize = Point()
                    activity?.windowManager?.defaultDisplay?.getRealSize(realSize)
                    val aspectRatio = realSize.x.toFloat()/ realSize.y.toFloat()
                    Log.d(TAG, "====== aspect ratio $aspectRatio")
                    camera.getPreviewSize(aspectRatio)
                }
                else -> camera.getCaptureSize()
            }

            // Find out if we need to swap dimension to get the preview size relative to sensor
            // coordinate.
            val displayRotation = activity?.windowManager?.defaultDisplay?.rotation ?: return

            val sensorOrientation = camera.getSensorOrientation()

            val swappedDimensions = areDimensionsSwapped(sensorOrientation, displayRotation)

            val displaySize = Point()
            activity?.windowManager?.defaultDisplay?.getSize(displaySize)

            Log.d(TAG, "===== display size ${displaySize.x} ${displaySize.y} ${largest} ")
            // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
            // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
            // garbage capture data.
            previewSize = if (swappedDimensions) {
                camera.chooseOptimalSize(
                        height,
                        width,
                        displaySize.y,
                        displaySize.x,
                        largest)
            } else {
                camera.chooseOptimalSize(
                        width,
                        height,
                        displaySize.x,
                        displaySize.y,
                        largest)
            }

            if(mode == CameraMode.AUTO_FIT) {
                // We fit the aspect ratio of TextureView to the size of preview we picked.
                if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    textureView.setAspectRatio(previewSize.width, previewSize.height)
                } else {
                    textureView.setAspectRatio(previewSize.height, previewSize.width)
                }
            }
            // Check if the flash is supported.
            flashSupported = camera.getFlashSupported()
            if(flashSupported){
                if (flash != null) {
                    flash.visibility = View.VISIBLE
                }
                checkFlashIconColor()
            }

        } catch (e: CameraAccessException) {
            Log.e(TAG, e.toString())
        } catch (e: NullPointerException) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            ErrorDialog.newInstance(getString(R.string.camera_error))
                .show(childFragmentManager, FRAGMENT_DIALOG)
        }

    }

    /**
     * Checks whether the flash is set to on or off and adapts the icon accordingly.
     */
    private fun checkFlashIconColor() {
        if (camera?.wbMode == WBMode.FLASH){
            flash.setImageResource(R.drawable.ic_flash_yellow)
            flash.setTag(R.drawable.ic_flash_yellow);
        } else {
            flash.setTag(R.drawable.ic_flash_total_white);
        }
    }

    /**
     * Determines if the dimensions are swapped given the phone's current rotation.
     *
     * @param sensorOrientation The current sensor orientation
     * @param displayRotation The current rotation of the display
     * @return true if the dimensions are swapped, false otherwise.
     */
    private fun areDimensionsSwapped(sensorOrientation: Int, displayRotation: Int): Boolean {
        var swappedDimensions = false
        when (displayRotation) {
            Surface.ROTATION_0, Surface.ROTATION_180 -> {
                if (sensorOrientation == 90 || sensorOrientation == 270) {
                    swappedDimensions = true
                }
            }
            Surface.ROTATION_90, Surface.ROTATION_270 -> {
                if (sensorOrientation == 0 || sensorOrientation == 180) {
                    swappedDimensions = true
                }
            }
            else -> {
                Log.e(TAG, "Display rotation is invalid: $displayRotation")
            }
        }
        return swappedDimensions
    }

    /**
     * Opens the camera specified by [Camera2BasicFragment.cameraId].
     */
    private fun openCamera(width: Int, height: Int) {
        if (activity == null) {
            Log.e(TAG, "activity is not ready!")
            return
        }

        try {
            camera?.let {
                setUpCameraOutputs(width, height, it, cameraMode)
                configureTransform(width, height)
                it.open()
                val texture = textureView.surfaceTexture
                texture.setDefaultBufferSize(previewSize.width, previewSize.height)
                it.start(Surface(texture))
            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, e.toString())
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera opening.", e)
        }
    }


    /**
     * Configures the necessary [android.graphics.Matrix] transformation to `textureView`.
     * This method should be called after the camera preview size is determined in
     * setUpCameraOutputs and also the size of `textureView` is fixed.
     *
     * @param viewWidth  The width of `textureView`
     * @param viewHeight The height of `textureView`
     */
    private fun configureTransform(viewWidth: Int, viewHeight: Int) {
        activity ?: return
        val rotation = activity!!.windowManager.defaultDisplay.rotation
        val matrix = Matrix()
        val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        val bufferRect = RectF(0f, 0f, previewSize.height.toFloat(), previewSize.width.toFloat())
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()

        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
            val scale = Math.max(
                    viewHeight.toFloat() / previewSize.height,
                    viewWidth.toFloat() / previewSize.width)
            with(matrix) {
                setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
                postScale(scale, scale, centerX, centerY)
                postRotate((90 * (rotation - 2)).toFloat(), centerX, centerY)
            }
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180f, centerX, centerY)
        }
        textureView.setTransform(matrix)

    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.picture -> { camera?.takePicture(object : ImageHandler {
                override fun handleImage(image: Image): Runnable {
                    imageSaver = ImageSaver(image, createImageFile())
                    return imageSaver
                }
            })
                handleTakenPicture()
            }
            R.id.flash -> {
                camera?.close()
                if(setPictureOFFlashButton()) {
                    camera?.wbMode = WBMode.FLASH
                } else {
                    camera?.wbMode = WBMode.FLASHOFF
                }
                openCamera(textureView.width, textureView.height)
            }
        }
    }

    /**
     * Sets the icon color of the flash icon according to whether the user selected it or not.
     */
    private fun setPictureOFFlashButton(): Boolean {
        val imageName: String = flash.tag.toString()
        return if (imageName == R.drawable.ic_flash_total_white.toString()) {
            flash.setImageResource(R.drawable.ic_flash_yellow)
            flash.tag = R.drawable.ic_flash_yellow;
            true
        } else {
            flash.setImageResource(R.drawable.ic_flash_total_white)
            flash.tag = R.drawable.ic_flash_total_white;
            false
        }
    }



    //Files you save in the directories provided by getExternalFilesDir() or getFilesDir()
    // are deleted when the user uninstalls your app.
    // https://developer.android.com/training/camera/photobasics
    /**
     * Creates an image file in the internal app storage to save the taken picture.
     */
    @Throws(IOException::class)
    private fun createImageFile(): File {
        // Create an image file name
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val storageDir: File? = activity?.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
                "JPEG_${timeStamp}_", /* prefix */
                ".jpg", /* suffix */
                storageDir /* directory */
        ).apply {
            // Save a file: path for use with ACTION_VIEW intents
            currentPhotoPath = absolutePath
        }
    }

    /**
     * Sets the taken picture and closes the camera view.
     */
    private fun handleTakenPicture() {
        // ImageSaver is handled on a separate thread. As a result, we have to wait until this
        // thread is finished before setting the new picture.
        while (!::imageSaver.isInitialized || !imageSaver.getIsFinished()){
        }
        (activity as CameraActivity).setPicture(currentPhotoPath)

        closeCameraPreview()
    }

    /**
     * Closes the camera and the corresponding preview.
     */
    private fun closeCameraPreview() {
        previewSurface?.release()
        camera?.close()
        (activity as CameraActivity).unlockOrientation()
        fragmentManager?.beginTransaction()?.remove(this)?.commitAllowingStateLoss()
    }

    companion object {

        private const val FRAGMENT_DIALOG = "dialog"
        /**
         * Tag for the [Log].
         */
        private const val TAG = "Camera2BasicFragment"

        private const val stringArgument: String = "CameraFragment"

        @JvmStatic
        fun newInstance(cameraLayout: CameraOrientation): Camera2BasicFragment = Camera2BasicFragment().apply {
            arguments = Bundle().apply {
                putSerializable(stringArgument, cameraLayout)
            }
        }
    }
}
