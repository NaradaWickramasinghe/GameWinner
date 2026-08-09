package com.example.gamewinner.activities

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.core.content.ContextCompat
import com.example.gamewinner.ai.GeminiClient
import com.example.gamewinner.cache.AnswerCache
import com.example.gamewinner.camera.CameraManager
import com.example.gamewinner.databinding.ActivityCameraBinding
import com.example.gamewinner.model.Answer
import com.example.gamewinner.ocr.OCRProcessor
import com.example.gamewinner.ocr.TextCleaner
import java.nio.ByteBuffer

/**
 * Full-screen camera activity that orchestrates the complete processing pipeline:
 *
 *   Manual High-Res Capture → OCR → Text Cleaning → Cache Check → Gemini API → Overlay
 */
class CameraActivity : AppCompatActivity() {

    private val TAG = "CameraActivity"

    private lateinit var binding: ActivityCameraBinding
    private lateinit var cameraManager: CameraManager
    private lateinit var ocrProcessor: OCRProcessor
    private lateinit var geminiClient: GeminiClient

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isProcessing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Full-screen immersive mode
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initializeComponents()
        setupUI()
        startCamera()
    }

    private fun initializeComponents() {
        ocrProcessor = OCRProcessor()
        
        val userApiKey = com.example.gamewinner.utils.UserPreferences.getGeminiApiKey(this)
        geminiClient = GeminiClient(userApiKey)

        cameraManager = CameraManager(
            lifecycleOwner = this,
            previewView = binding.previewView
        )
    }

    private fun setupUI() {
        updateStatus("Point camera and tap to capture", StatusType.READY)

        binding.answerOverlay.setOnClickListener {
            binding.answerOverlay.hideAnswer()
        }

        binding.btnClose.setOnClickListener {
            finish()
        }

        // Set up Zoom Slider
        binding.zoomSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val zoomLinear = progress / 100f
                    cameraManager.setLinearZoom(zoomLinear)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.btnCapture.setOnClickListener {
            if (isProcessing) return@setOnClickListener
            
            val capture = cameraManager.imageCapture
            if (capture == null) {
                updateStatus("Camera not ready...", StatusType.ERROR)
                return@setOnClickListener
            }
            
            isProcessing = true
            updateStatus("Capturing photo...", StatusType.PROCESSING)
            binding.tvOcrText.visibility = View.GONE
            
            capture.takePicture(
                ContextCompat.getMainExecutor(this),
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        val bitmap = imageProxyToBitmap(image)
                        image.close()
                        processCameraFrame(bitmap)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Log.e(TAG, "Photo capture failed", exception)
                        updateStatus("Capture failed", StatusType.ERROR)
                        isProcessing = false
                    }
                }
            )
        }
    }

    /**
     * Converts an ImageProxy to a Bitmap, handling rotation.
     */
    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val planeProxy = image.planes[0]
        val buffer: ByteBuffer = planeProxy.buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        
        val rotationDegrees = image.imageInfo.rotationDegrees.toFloat()
        if (rotationDegrees != 0f) {
            val matrix = Matrix()
            matrix.postRotate(rotationDegrees)
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
        return bitmap
    }

    private fun startCamera() {
        cameraManager.startCamera {
            // Called when camera is ready
            updateStatus("Camera ready", StatusType.READY)

            // Observe zoom state so that pinch-to-zoom updates the slider automatically
            cameraManager.camera?.cameraInfo?.zoomState?.observe(this) { state ->
                val linearZoom = state.linearZoom
                // Convert 0.0-1.0 to 0-100 for SeekBar without triggering the user listener recursively
                binding.zoomSlider.progress = (linearZoom * 100).toInt()
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  PROCESSING PIPELINE
    // ═══════════════════════════════════════════════════════════════

    private fun processCameraFrame(bitmap: Bitmap) {
        updateStatus("Extracting text...", StatusType.PROCESSING)

        ocrProcessor.processImage(
            bitmap = bitmap,
            onResult = { text, _ ->
                if (text.isEmpty()) {
                    mainHandler.post {
                        updateStatus("No text detected", StatusType.READY)
                        isProcessing = false
                    }
                    return@processImage
                }

                // Show raw OCR text on screen
                mainHandler.post {
                    binding.tvOcrText.text = text
                    binding.tvOcrText.visibility = View.VISIBLE
                }

                processExtractedText(text)
            },
            onError = { e ->
                Log.e(TAG, "OCR error", e)
                mainHandler.post {
                    updateStatus("OCR error — try again", StatusType.ERROR)
                    isProcessing = false
                }
            }
        )
    }

    private fun processExtractedText(rawText: String) {
        val question = TextCleaner.clean(rawText)

        if (question == null) {
            Log.d(TAG, "Text too short or invalid")
            mainHandler.post {
                updateStatus("Text too short or garbled", StatusType.READY)
                isProcessing = false
            }
            return
        }

        // Cache check
        val cachedAnswer = AnswerCache.get(question.hash)
        if (cachedAnswer != null) {
            mainHandler.post {
                showAnswer(cachedAnswer)
                updateStatus("Answer (cached)", StatusType.READY)
                isProcessing = false
            }
            return
        }

        mainHandler.post {
            updateStatus("Asking AI...", StatusType.PROCESSING)
        }

        val customPrompt = com.example.gamewinner.utils.UserPreferences.getCustomPrompt(this)

        geminiClient.getAnswer(
            question = question,
            customPrompt = customPrompt,
            onResult = { answer ->
                AnswerCache.put(question.hash, answer)
                mainHandler.post {
                    showAnswer(answer)
                    updateStatus("Answer found!", StatusType.READY)
                    isProcessing = false
                }
            },
            onError = { e ->
                Log.e(TAG, "Gemini API error", e)
                mainHandler.post {
                    val errorAnswer = Answer.error(e.message ?: "AI error")
                    showAnswer(errorAnswer)
                    updateStatus("AI error — tap to retry", StatusType.ERROR)
                    isProcessing = false
                }
            }
        )
    }

    private fun showAnswer(answer: Answer) {
        binding.answerOverlay.showAnswer(answer)
    }

    private enum class StatusType { READY, PROCESSING, ERROR }

    private fun updateStatus(message: String, type: StatusType) {
        binding.tvStatus.text = message
        binding.progressIndicator.visibility = when (type) {
            StatusType.PROCESSING -> View.VISIBLE
            else -> View.INVISIBLE
        }

        val statusColor = when (type) {
            StatusType.READY -> 0xFF4CAF50.toInt()
            StatusType.PROCESSING -> 0xFFFF9800.toInt()
            StatusType.ERROR -> 0xFFF44336.toInt()
        }
        binding.statusDot.setBackgroundColor(statusColor)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraManager.shutdown()
        ocrProcessor.close()
        geminiClient.shutdown()
    }
}
