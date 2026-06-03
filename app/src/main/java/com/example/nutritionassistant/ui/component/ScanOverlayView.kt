package com.example.nutritionassistant.ui.component

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.animation.LinearInterpolator

class ScanOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // 扫描线画笔：亮绿色细线，20px 宽
    private val linePaint = Paint().apply {
        color = Color.parseColor("#00E676")   // 微信风格的亮绿色
        alpha = 220
        strokeWidth = 3f                       // 更细
        style = Paint.Style.STROKE
    }

    // 上光晕画笔
    private val shaderTopPaint = Paint().apply {
        shader = LinearGradient(
            0f, 0f, 0f, 15f,   // 向上渐变
            intArrayOf(
                Color.parseColor("#5500E676"),
                Color.TRANSPARENT
            ),
            null,
            Shader.TileMode.CLAMP
        )
    }

    // 下光晕画笔
    private val shaderBottomPaint = Paint().apply {
        shader = LinearGradient(
            0f, 0f, 0f, 15f,   // 向下渐变
            intArrayOf(
                Color.TRANSPARENT,
                Color.parseColor("#5500E676")
            ),
            null,
            Shader.TileMode.CLAMP
        )
    }

    private var scanLineY = 0f
    private var animator: ValueAnimator? = null
    private var isAnimating = false

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        Log.d("ScanOverlay", "尺寸: w=$w, h=$h")
        if (isAnimating) {
            stopAnimation()
            startAnimationInternal(h)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 画水平扫描线
        canvas.drawLine(0f, scanLineY, width.toFloat(), scanLineY, linePaint)

        // 画上方光晕（从 scanLineY-15 到 scanLineY，颜色从浅到深）
        canvas.drawRect(0f, scanLineY - 15f, width.toFloat(), scanLineY, shaderTopPaint)

        // 画下方光晕（从 scanLineY 到 scanLineY+15，颜色从深到浅）
        canvas.drawRect(0f, scanLineY, width.toFloat(), scanLineY + 15f, shaderBottomPaint)
    }
    fun startScanning() {
        isAnimating = true
        if (height > 0) {
            startAnimationInternal(height)
        }
    }

    private fun startAnimationInternal(viewHeight: Int) {
        stopAnimation()
        val lineTop = 30f
        val lineBottom = viewHeight - 30f
        scanLineY = lineTop
        Log.d("ScanOverlay", "动画范围: $lineTop -> $lineBottom")

        animator = ValueAnimator.ofFloat(lineTop, lineBottom).apply {
            duration = 2000                     // 与微信一致的动画速度
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = LinearInterpolator()
            addUpdateListener { animation ->
                scanLineY = animation.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun stopScanning() {
        stopAnimation()
        isAnimating = false
    }

    private fun stopAnimation() {
        animator?.cancel()
        animator = null
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimation()
    }
}