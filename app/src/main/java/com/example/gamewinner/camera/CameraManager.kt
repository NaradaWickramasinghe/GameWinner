package com.example.gamewinner.camera

import android.annotation.SuppressLint
import android.util.Log
import android.view.ScaleGestureDetector
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner

/**
 * Manages CameraX initialization, preview binding, image capture setup, and pinch-to-zoom.
 */
class CameraManager(
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView
) {
    private val TAG = "CameraManager"

    private var cameraProvider: ProcessCameraProvider? = null
    var camera: Camera? = null
        private set

    // High quality image capture use case
    var imageCapture: ImageCapture? = null
        private set

    /**
     * Starts the camera with preview and high-quality image capture use cases.
     */
    fun startCamera(onCameraReady: () -> Unit = {}) {
        val context = previewView.context
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindUseCases()
                onCameraReady()
            } catch (e: Exception) {
                Log.e(TAG, "Camera initialization failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Binds Preview and ImageCapture use cases to the camera lifecycle.
     */
    private fun bindUseCases() {
        val provider = cameraProvider ?: return
        provider.unbindAll()

        // Preview use case
        val preview = Preview.Builder()
            .build()
            .also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

        // ImageCapture use case for high-quality photos
        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
            camera = provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageCapture
            )
            setupZoom()
            Log.d(TAG, "Camera use cases bound successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind camera use cases", e)
        }
    }

    /**
     * Sets linear zoom (0.0 to 1.0)
     */
    fun setLinearZoom(zoom: Float) {
        camera?.cameraControl?.setLinearZoom(zoom)
    }

    /**
     * Attaches a pinch-to-zoom gesture listener to the preview view.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun setupZoom() {
        val cam = camera ?: return
        val scaleGestureDetector = ScaleGestureDetector(previewView.context, 
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val currentZoomRatio = cam.cameraInfo.zoomState.value?.zoomRatio ?: 1f
                    val delta = detector.scaleFactor
                    cam.cameraControl.setZoomRatio(currentZoomRatio * delta)
                    return true
                }
            })
            
        previewView.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)
            return@setOnTouchListener true
        }
    }

    /**
     * Stops the camera and releases resources.
     */
    fun stopCamera() {
        cameraProvider?.unbindAll()
    }

    fun shutdown() {
        stopCamera()
    }
}
