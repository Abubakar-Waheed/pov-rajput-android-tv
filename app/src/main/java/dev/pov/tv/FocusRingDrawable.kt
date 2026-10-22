package dev.pov.tv

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

/** A bounded TV focus halo: visible even when the card's art is very bright. */
internal class FocusRingDrawable(private val color: Int, private val radius: Float, private val focused: Boolean, private val scale: Float) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun draw(canvas: Canvas) {
        val area = RectF(bounds)
        paint.style = Paint.Style.STROKE
        if (focused) {
            for (width in intArrayOf(9, 6, 3)) {
                paint.color = color; paint.alpha = if (width == 3) 255 else if (width == 6) 65 else 22
                paint.strokeWidth = width * scale
                val inset = paint.strokeWidth / 2
                val rect = RectF(area).apply { inset(inset, inset) }
                canvas.drawRoundRect(rect, radius, radius, paint)
            }
        } else {
            paint.color = Color.argb(65, Color.red(color), Color.green(color), Color.blue(color))
            paint.strokeWidth = scale
            area.inset(scale / 2, scale / 2)
            canvas.drawRoundRect(area, radius, radius, paint)
        }
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Drawable opacity") override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
