package com.example.gamewinner.screencapture

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.example.gamewinner.R
import com.example.gamewinner.ai.GeminiClient
import com.example.gamewinner.cache.AnswerCache
import com.example.gamewinner.model.Answer
import com.example.gamewinner.ocr.OCRProcessor
import com.example.gamewinner.ocr.TextCleaner
import com.example.gamewinner.utils.UserPreferences

/**
 * Foreground service that manages screen capture via MediaProjection.
 *
 * Takes screenshots on demand, processes them through OCR → Gemini pipeline,
 * and sends results to the FloatingOverlayManager for display.
 */
class ScreenCaptureService : Service() {

    private val TAG = "ScreenCaptureService"
    private val CHANNEL_ID = "screen_capture_channel"
    private val NOTIFICATION_ID = 1001

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private lateinit var ocrProcessor: OCRProcessor
    private lateinit var geminiClient: GeminiClient
    private lateinit var floatingOverlay: FloatingOverlayManager

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isProcessing = false

    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0

    companion object {
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"

        private var instance: ScreenCaptureService? = null

        fun isRunning(): Boolean = instance != null

        fun stopService(context: Context) {
            instance?.stopSelf()
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        ocrProcessor = OCRProcessor()
        
        val userApiKey = UserPreferences.getGeminiApiKey(this)
        geminiClient = GeminiClient(userApiKey)
        
        floatingOverlay = FloatingOverlayManager(this)

        // Get screen metrics
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDensity = metrics.densityDpi

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Start as foreground service immediately
        startForeground(NOTIFICATION_ID, buildNotification())

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            ?: Activity.RESULT_CANCELED
        val resultData = intent?.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)

        if (resultCode == Activity.RESULT_OK && resultData != null) {
            setupMediaProjection(resultCode, resultData)
            floatingOverlay.show(
                onCaptureClick = { captureScreen() },
                onCloseClick = { stopSelf() }
            )
        } else {
            Log.e(TAG, "Invalid result code or data")
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun setupMediaProjection(resultCode: Int, resultData: Intent) {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)

        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                Log.d(TAG, "MediaProjection stopped")
                cleanupVirtualDisplay()
            }
        }, mainHandler)

        setupImageReader()
    }

    @SuppressLint("WrongConstant")
    private fun setupImageReader() {
        imageReader = ImageReader.newInstance(
            screenWidth, screenHeight,
            PixelFormat.RGBA_8888, 2
        )

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "GameWinnerCapture",
            screenWidth, screenHeight, screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface,
            null, mainHandler
        )

        Log.d(TAG, "VirtualDisplay created: ${screenWidth}x${screenHeight}")
    }

    /**
     * Captures the current screen and processes it through the OCR → AI pipeline.
     */
    fun captureScreen() {
        if (isProcessing) {
            Log.d(TAG, "Already processing, skipping capture")
            return
        }

        isProcessing = true
        floatingOverlay.showStatus("Capturing...")

        // Small delay to let the floating button animation finish
        mainHandler.postDelayed({
            val image = imageReader?.acquireLatestImage()
            if (image == null) {
                Log.e(TAG, "Failed to acquire image")
                floatingOverlay.showStatus("Capture failed")
                isProcessing = false
                return@postDelayed
            }

            try {
                val plane = image.planes[0]
                val buffer = plane.buffer
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding = rowStride - pixelStride * screenWidth

                val bitmap = Bitmap.createBitmap(
                    screenWidth + rowPadding / pixelStride,
                    screenHeight,
                    Bitmap.Config.ARGB_8888
                )
                bitmap.copyPixelsFromBuffer(buffer)

                // Crop to actual screen size (remove padding)
                val croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)
                if (croppedBitmap != bitmap) {
                    bitmap.recycle()
                }

                image.close()
                processScreenshot(croppedBitmap)

            } catch (e: Exception) {
                Log.e(TAG, "Error processing screenshot", e)
                image.close()
                floatingOverlay.showStatus("Error: ${e.message}")
                isProcessing = false
            }
        }, 150)
    }

    private fun processScreenshot(bitmap: Bitmap) {
        floatingOverlay.showStatus("Extracting text...")

        ocrProcessor.processImage(
            bitmap = bitmap,
            onResult = { text, _ ->
                bitmap.recycle()

                if (text.isEmpty()) {
                    mainHandler.post {
                        floatingOverlay.showStatus("No text detected")
                        isProcessing = false
                    }
                    return@processImage
                }

                mainHandler.post {
                    floatingOverlay.showOcrText(text)
                }

                processExtractedText(text)
            },
            onError = { e ->
                bitmap.recycle()
                Log.e(TAG, "OCR error", e)
                mainHandler.post {
                    floatingOverlay.showStatus("OCR error")
                    isProcessing = false
                }
            }
        )
    }

    private fun processExtractedText(rawText: String) {
        val question = TextCleaner.clean(rawText)

        if (question == null) {
            mainHandler.post {
                floatingOverlay.showStatus("Text too short")
                isProcessing = false
            }
            return
        }

        // Cache check
        val cachedAnswer = AnswerCache.get(question.hash)
        if (cachedAnswer != null) {
            mainHandler.post {
                floatingOverlay.showAnswer(cachedAnswer)
                isProcessing = false
            }
            return
        }

        mainHandler.post {
            floatingOverlay.showStatus("Asking AI...")
        }

        val customPrompt = UserPreferences.getCustomPrompt(this)

        geminiClient.getAnswer(
            question = question,
            customPrompt = customPrompt,
            onResult = { answer ->
                AnswerCache.put(question.hash, answer)
                mainHandler.post {
                    floatingOverlay.showAnswer(answer)
                    isProcessing = false
                }
            },
            onError = { e ->
                Log.e(TAG, "Gemini API error", e)
                mainHandler.post {
                    floatingOverlay.showAnswer(Answer.error(e.message ?: "AI error"))
                    isProcessing = false
                }
            }
        )
    }

    private fun cleanupVirtualDisplay() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Screen Capture",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "GameWinner screen capture service"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GameWinner Active")
            .setContentText("Tap the floating button to capture screen")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        floatingOverlay.hide()
        cleanupVirtualDisplay()
        mediaProjection?.stop()
        mediaProjection = null
        ocrProcessor.close()
        geminiClient.shutdown()
        Log.d(TAG, "ScreenCaptureService destroyed")
    }
}
