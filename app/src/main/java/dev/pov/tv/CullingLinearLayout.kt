package dev.pov.tv

import android.content.Context
import android.graphics.Canvas
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView

/**
 * A scrolling list that only draws children near its viewport. Android otherwise records and syncs
 * every card on a long Home page on every frame, even those far off-screen, which costs the onn box
 * about 12 ms per frame. The owning scroll view invalidates this layout as it scrolls, so children
 * are drawn again as soon as they come within [overscan] of the visible area.
 */
internal class CullingLinearLayout(context: Context, private val overscan: Int) : LinearLayout(context) {
    override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
        val scroller = parent
        if (child.animation != null) return super.drawChild(canvas, child, drawingTime)
        when (scroller) {
            is ScrollView -> {
                val top = scroller.scrollY - overscan
                val bottom = scroller.scrollY + scroller.height + overscan
                val childTop = child.top + child.translationY
                if (childTop + child.height < top || childTop > bottom) return false
            }
            is HorizontalScrollView -> {
                val left = scroller.scrollX - overscan
                val right = scroller.scrollX + scroller.width + overscan
                val childLeft = child.left + child.translationX
                if (childLeft + child.width < left || childLeft > right) return false
            }
        }
        return super.drawChild(canvas, child, drawingTime)
    }
}
