package com.example.gamewinner.overlay

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import com.example.gamewinner.model.Answer
import com.example.gamewinner.utils.Constants

/**
 * Custom overlay view that displays AI-generated answers on top of the camera preview.
 *
 * Features:
 * - Semi-transparent rounded rectangle background with glassmorphism effect
 * - Color-coded by confidence level (green/amber/red)
 * - Animated slide-in from bottom with fade-in
 * - Auto-dismiss after configured duration
 * - Displays: answer letter, full text, confidence %, and reasoning
 */
class AnswerOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // ── Current answer state ──
    private var currentAnswer: Answer? = null
    private var isVisible = false

    // ── Handler for auto-dismiss ──
    private val handler = Handler(Looper.getMainLooper())
    private val dismissRunnable = Runnable { hideAnswer() }

    // ── Paints ──
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * resources.displayMetrics.density
    }

    private val answerLetterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 48f * resources.displayMetrics.scaledDensity
        typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val answerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 16f * resources.displayMetrics.scaledDensity
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    private val confidencePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B0FFFFFF")
        textSize = 14f * resources.displayMetrics.scaledDensity
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }

    private val reasonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#90FFFFFF")
        textSize = 13f * resources.displayMetrics.scaledDensity
        typeface = Typeface.create("sans-serif", Typeface.ITALIC)
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#70FFFFFF")
        textSize = 11f * resources.displayMetrics.scaledDensity
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        letterSpacing = 0.15f
    }

    // ── Dimensions ──
    private val density = resources.displayMetrics.density
    private val padding = 24f * density
    private val cornerRadius = 20f * density
    private val cardMargin = 20f * density

    // ── Color definitions ──
    private val highConfidenceColors = intArrayOf(
        Color.parseColor("#CC00C853"),  // Green
        Color.parseColor("#CC00BFA5")   // Teal
    )
    private val medConfidenceColors = intArrayOf(
        Color.parseColor("#CCFF6D00"),  // Orange
        Color.parseColor("#CCFFAB00")   // Amber
    )
    private val lowConfidenceColors = intArrayOf(
        Color.parseColor("#CCD50000"),  // Red
        Color.parseColor("#CCFF1744")   // Pink-Red
    )

    /**
     * Shows an answer with animated entrance.
     */
    fun showAnswer(answer: Answer) {
        // Cancel any pending dismiss
        handler.removeCallbacks(dismissRunnable)

        currentAnswer = answer
        isVisible = true
        invalidate()

        // Animate in
        animateIn()

        // Schedule auto-dismiss
        handler.postDelayed(dismissRunnable, Constants.OVERLAY_DISPLAY_DURATION_MS)
    }

    /**
     * Hides the answer with animated exit.
     */
    fun hideAnswer() {
        if (!isVisible) return

        animateOut {
            isVisible = false
            currentAnswer = null
            invalidate()
        }
    }

    /**
     * Immediately clears the overlay without animation.
     */
    fun clear() {
        handler.removeCallbacks(dismissRunnable)
        isVisible = false
        currentAnswer = null
        alpha = 0f
        translationY = 0f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val answer = currentAnswer ?: return
        if (!isVisible) return

        val cardWidth = width - (cardMargin * 2)
        val cardHeight = 180f * density
        val cardLeft = cardMargin
        val cardTop = height - cardHeight - cardMargin - 60f * density // Above bottom
        val cardRect = RectF(cardLeft, cardTop, cardLeft + cardWidth, cardTop + cardHeight)

        // ── Draw background with gradient ──
        val colors = when (answer.confidenceLevel) {
            Answer.ConfidenceLevel.HIGH -> highConfidenceColors
            Answer.ConfidenceLevel.MEDIUM -> medConfidenceColors
            Answer.ConfidenceLevel.LOW -> lowConfidenceColors
        }

        backgroundPaint.shader = LinearGradient(
            cardRect.left, cardRect.top,
            cardRect.right, cardRect.bottom,
            colors[0], colors[1],
            Shader.TileMode.CLAMP
        )

        canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, backgroundPaint)

        // ── Draw border ──
        borderPaint.color = Color.parseColor("#30FFFFFF")
        canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, borderPaint)

        // ── Draw content ──
        val contentLeft = cardLeft + padding
        val contentRight = cardLeft + cardWidth - padding
        var yPos = cardTop + padding

        // Label: "ANSWER"
        canvas.drawText("ANSWER", contentLeft, yPos + labelPaint.textSize, labelPaint)
        yPos += labelPaint.textSize + 8f * density

        // Answer letter (large)
        val letterX = contentLeft + 30f * density
        yPos += answerLetterPaint.textSize * 0.8f
        canvas.drawText(answer.answerLetter, letterX, yPos, answerLetterPaint)

        // Answer text (to the right of the letter)
        val textStartX = letterX + 50f * density
        val answerDisplayText = if (answer.answerText.length > 1) answer.answerText else ""
        if (answerDisplayText.isNotEmpty()) {
            canvas.drawText(
                truncateText(answerDisplayText, answerTextPaint, contentRight - textStartX),
                textStartX,
                yPos - 8f * density,
                answerTextPaint
            )
        }

        // Confidence percentage (to the right of answer text)
        val confidenceText = "${answer.confidence}% confident"
        canvas.drawText(
            confidenceText,
            textStartX,
            yPos + 16f * density,
            confidencePaint
        )

        // Reason (bottom of card)
        if (answer.reason.isNotEmpty()) {
            yPos += 36f * density
            canvas.drawText(
                truncateText(answer.reason, reasonPaint, contentRight - contentLeft),
                contentLeft,
                yPos,
                reasonPaint
            )
        }
    }

    /**
     * Truncates text with ellipsis if it exceeds available width.
     */
    private fun truncateText(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text

        var end = text.length
        while (end > 0 && paint.measureText(text.substring(0, end) + "...") > maxWidth) {
            end--
        }
        return if (end > 0) text.substring(0, end) + "..." else "..."
    }

    /**
     * Slide-in + fade-in animation.
     */
    private fun animateIn() {
        alpha = 0f
        translationY = 100f * density

        val fadeIn = ObjectAnimator.ofFloat(this, ALPHA, 0f, 1f)
        val slideUp = ObjectAnimator.ofFloat(this, TRANSLATION_Y, 100f * density, 0f)

        AnimatorSet().apply {
            playTogether(fadeIn, slideUp)
            duration = Constants.OVERLAY_ANIMATION_DURATION_MS
            interpolator = OvershootInterpolator(0.8f)
            start()
        }
    }

    /**
     * Fade-out + slide-down animation.
     */
    private fun animateOut(onEnd: () -> Unit) {
        val fadeOut = ObjectAnimator.ofFloat(this, ALPHA, 1f, 0f)
        val slideDown = ObjectAnimator.ofFloat(this, TRANSLATION_Y, 0f, 80f * density)

        AnimatorSet().apply {
            playTogether(fadeOut, slideDown)
            duration = 300
            interpolator = DecelerateInterpolator()
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    onEnd()
                }
            })
            start()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacks(dismissRunnable)
    }
}
