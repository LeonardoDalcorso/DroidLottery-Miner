package com.droidlottery.miner.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.max

class HashrateChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(55, 214, 145)
        style = Paint.Style.STROKE
        strokeWidth = 5f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(45, 55, 214, 145)
        style = Paint.Style.FILL
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(35, 255, 255, 255)
        strokeWidth = 1f
    }
    private var samples: List<Float> = emptyList()

    fun submit(samples: List<Float>) {
        this.samples = samples.takeLast(60)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val inset = paddingLeft.toFloat()
        val usableWidth = width - paddingLeft - paddingRight
        val usableHeight = height - paddingTop - paddingBottom
        repeat(4) { row ->
            val y = paddingTop + usableHeight * row / 3f
            canvas.drawLine(inset, y, inset + usableWidth, y, gridPaint)
        }
        if (samples.isEmpty() || usableWidth <= 0 || usableHeight <= 0) return
        val maximum = max(samples.maxOrNull() ?: 1f, 1f)
        val path = Path()
        samples.forEachIndexed { index, value ->
            val x = inset + usableWidth * index / max(samples.lastIndex, 1)
            val y = paddingTop + usableHeight - value.coerceAtLeast(0f) / maximum * usableHeight
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        val fillPath = Path(path).apply {
            lineTo(inset + usableWidth, (paddingTop + usableHeight).toFloat())
            lineTo(inset, paddingTop + usableHeight)
            close()
        }
        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(path, linePaint)
    }

    private fun lineTo(x: Float, y: Int) {}
}
