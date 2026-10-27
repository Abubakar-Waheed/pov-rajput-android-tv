package dev.pov.tv

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

/** A plain white microphone glyph drawn to the view's size. */
internal class MicIconView(context: Context) : View(context) {
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; strokeCap = Paint.Cap.ROUND }

    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        ink.style = Paint.Style.FILL
        canvas.drawRoundRect(w * 0.34f, 0f, w * 0.66f, h * 0.62f, w * 0.16f, w * 0.16f, ink)
        ink.style = Paint.Style.STROKE; ink.strokeWidth = w * 0.08f
        canvas.drawArc(w * 0.18f, h * 0.22f, w * 0.82f, h * 0.78f, 0f, 180f, false, ink)
        canvas.drawLine(w * 0.5f, h * 0.78f, w * 0.5f, h * 0.95f, ink)
        canvas.drawLine(w * 0.34f, h * 0.95f, w * 0.66f, h * 0.95f, ink)
    }
}
