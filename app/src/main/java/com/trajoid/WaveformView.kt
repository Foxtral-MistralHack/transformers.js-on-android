package com.trajoid

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class WaveformView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#BB86FC") // Primary theme color
        strokeWidth = 5f
        strokeCap = Paint.Cap.ROUND
    }

    // Keep 5 seconds of data. Assuming we get updates at roughly 10Hz, that's 50 samples.
    private val maxSamples = 50
    private val samples = FloatArray(maxSamples)
    private var headIndex = 0

    fun addSample(amplitude: Float) {
        samples[headIndex] = amplitude
        headIndex = (headIndex + 1) % maxSamples
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        val centerY = h / 2f

        val spacing = w / maxSamples

        for (i in 0 until maxSamples) {
            // Read from oldest to newest
            val index = (headIndex + i) % maxSamples
            var s = samples[index]

            // Normalize slightly (amplitudes can vary wildly, typical speech might be between 0.0 - 0.5)
            // Clamp it visually
            if (s > 1.0f) s = 1.0f
            if (s < 0.0f) s = 0.0f

            // Minimum bar height
            var barHeight = s * h * 2f
            if (barHeight < 5f) barHeight = 5f

            val x = i * spacing
            val top = centerY - (barHeight / 2f)
            val bottom = centerY + (barHeight / 2f)

            canvas.drawLine(x, top, x, bottom, paint)
        }
    }
}
