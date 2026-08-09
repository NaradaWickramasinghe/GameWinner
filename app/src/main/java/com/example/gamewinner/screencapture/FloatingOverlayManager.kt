package com.example.gamewinner.screencapture

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.example.gamewinner.R
import com.example.gamewinner.model.Answer

/**
 * Manages the floating button and answer card overlays displayed over other apps.
 *
 * Uses WindowManager to add views with TYPE_APPLICATION_OVERLAY so they float
 * above all activities. The button is draggable.
 */
class FloatingOverlayManager(private val context: Context) {

    private val TAG = "FloatingOverlay"

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var buttonView: View? = null
    private var answerView: View? = null

    private var onCaptureClick: (() -> Unit)? = null
    private var onCloseClick: (() -> Unit)? = null

    private var isAnswerVisible = false

    /**
     * Shows the floating capture button.
     */
    @SuppressLint("ClickableViewAccessibility")
    fun show(
        onCaptureClick: () -> Unit,
        onCloseClick: () -> Unit
    ) {
        this.onCaptureClick = onCaptureClick
        this.onCloseClick = onCloseClick

        if (buttonView != null) return

        val inflater = LayoutInflater.from(context)
        buttonView = inflater.inflate(R.layout.floating_button, null)

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 300
        }

        // Make the button draggable
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        buttonView?.findViewById<View>(R.id.btnCaptureCircle)?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (dx * dx + dy * dy > 100) { // threshold to distinguish drag from tap
                        isDragging = true
                    }
                    params.x = initialX + dx.toInt()
                    params.y = initialY + dy.toInt()
                    try {
                        windowManager.updateViewLayout(buttonView, params)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error updating button layout", e)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        // It was a tap — trigger capture
                        this.onCaptureClick?.invoke()
                    }
                    true
                }
                else -> false
            }
        }

        // Close button to stop service
        buttonView?.findViewById<View>(R.id.btnCloseCapture)?.setOnClickListener {
            this.onCloseClick?.invoke()
        }

        try {
            windowManager.addView(buttonView, params)
            Log.d(TAG, "Floating button added")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add floating button", e)
        }
    }

    /**
     * Shows a status message in the floating answer card.
     */
    fun showStatus(message: String) {
        ensureAnswerView()
        answerView?.let { view ->
            view.findViewById<TextView>(R.id.tvFloatingAnswer)?.text = "⏳"
            view.findViewById<TextView>(R.id.tvFloatingConfidence)?.visibility = View.GONE
            view.findViewById<TextView>(R.id.tvFloatingReason)?.visibility = View.GONE
            view.findViewById<TextView>(R.id.tvFloatingOcr)?.visibility = View.GONE

            val statusView = view.findViewById<TextView>(R.id.tvFloatingStatus)
            statusView?.text = message
            statusView?.visibility = View.VISIBLE
        }
    }

    /**
     * Shows raw OCR text in the floating answer card.
     */
    fun showOcrText(text: String) {
        answerView?.let { view ->
            val ocrView = view.findViewById<TextView>(R.id.tvFloatingOcr)
            ocrView?.text = "OCR: $text"
            ocrView?.visibility = View.VISIBLE
        }
    }

    /**
     * Shows the AI answer in the floating card.
     */
    fun showAnswer(answer: Answer) {
        ensureAnswerView()
        answerView?.let { view ->
            // Answer
            view.findViewById<TextView>(R.id.tvFloatingAnswer)?.text = answer.answerText

            // Confidence
            val confView = view.findViewById<TextView>(R.id.tvFloatingConfidence)
            confView?.text = "${answer.confidence}% confidence"
            confView?.visibility = View.VISIBLE

            val confColor = when {
                answer.confidence >= 80 -> 0xFF4CAF50.toInt() // green
                answer.confidence >= 50 -> 0xFFFF9800.toInt() // orange
                else -> 0xFFF44336.toInt() // red
            }
            confView?.setTextColor(confColor)

            // Reason
            val reasonView = view.findViewById<TextView>(R.id.tvFloatingReason)
            reasonView?.text = answer.reason
            reasonView?.visibility = if (answer.reason.isNotBlank()) View.VISIBLE else View.GONE

            // Hide status
            view.findViewById<TextView>(R.id.tvFloatingStatus)?.visibility = View.GONE
        }
    }

    /**
     * Creates and adds the answer overlay view if it doesn't exist.
     */
    private fun ensureAnswerView() {
        if (answerView != null) {
            if (!isAnswerVisible) {
                showAnswerView()
            }
            return
        }

        val inflater = LayoutInflater.from(context)
        answerView = inflater.inflate(R.layout.floating_answer, null)

        answerView?.findViewById<TextView>(R.id.btnCloseAnswer)?.setOnClickListener {
            hideAnswerView()
        }

        var isMinimized = false
        answerView?.findViewById<TextView>(R.id.btnMinimizeAnswer)?.setOnClickListener {
            val contentLayout = answerView?.findViewById<View>(R.id.llAnswerContent)
            val btnMinimize = it as TextView
            
            isMinimized = !isMinimized
            if (isMinimized) {
                contentLayout?.visibility = View.GONE
                btnMinimize.text = "+"
            } else {
                contentLayout?.visibility = View.VISIBLE
                btnMinimize.text = "−"
            }
        }

        showAnswerView()
    }

    private fun showAnswerView() {
        if (isAnswerVisible) return

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100 // margin from the left edge
            y = 200 // margin from the top edge
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        answerView?.findViewById<View>(R.id.tvDragHandle)?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    
                    // Gravity.START -> moving finger right (dx > 0) increases x
                    params.x = initialX + dx.toInt()
                    params.y = initialY + dy.toInt()
                    
                    try {
                        windowManager.updateViewLayout(answerView, params)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error updating answer layout", e)
                    }
                    true
                }
                else -> false
            }
        }

        var initialWidth = 0
        answerView?.findViewById<View>(R.id.tvResizeHandle)?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialWidth = answerView?.width ?: 0
                    initialTouchX = event.rawX
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val newWidth = initialWidth + dx.toInt()
                    
                    // Constrain width to reasonable bounds
                    params.width = Math.max(300, Math.min(newWidth, 1500))
                    
                    try {
                        windowManager.updateViewLayout(answerView, params)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error updating answer layout size", e)
                    }
                    true
                }
                else -> false
            }
        }

        try {
            windowManager.addView(answerView, params)
            isAnswerVisible = true
            Log.d(TAG, "Answer overlay added")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add answer overlay", e)
        }
    }

    private fun hideAnswerView() {
        if (!isAnswerVisible) return
        try {
            windowManager.removeView(answerView)
            isAnswerVisible = false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove answer overlay", e)
        }
    }

    /**
     * Removes all floating views.
     */
    fun hide() {
        try {
            buttonView?.let { windowManager.removeView(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing button", e)
        }
        buttonView = null

        try {
            if (isAnswerVisible) {
                answerView?.let { windowManager.removeView(it) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing answer overlay", e)
        }
        answerView = null
        isAnswerVisible = false
    }
}
