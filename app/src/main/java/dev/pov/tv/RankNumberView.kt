package dev.pov.tv

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.view.View

/** An outlined chart rank drawn beneath the poster that overlaps it, as on Top 10 shelves. */
internal class RankNumberView(context: Context, private val rank: String, private val fill: Int, private val outline: Int) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-black", Typeface.BOLD); textAlign = Paint.Align.RIGHT; letterSpacing = -0.08f
    }

    init {
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        // Numerals this large bypass the glyph cache; a layer rasterizes them once instead of every scroll frame.
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    /** Distance from the right edge at which the numeral ends; the poster covers the rest. */
    var posterInset = 0f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        paint.textSize = height * 0.92f
        val x = width - posterInset
        val y = height - paint.descent() * 0.35f
        paint.style = Paint.Style.FILL; paint.color = fill
        canvas.drawText(rank, x, y, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = height * 0.022f; paint.color = outline
        canvas.drawText(rank, x, y, paint)
    }
}
