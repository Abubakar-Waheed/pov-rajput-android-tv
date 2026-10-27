package dev.pov.tv

import android.content.Context
import android.graphics.Rect
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ScrollView

/**
 * Scroll containers whose built-in jump-to-focus can be switched off. The streaming Home places rows
 * itself with one smooth scroll; letting the platform also jump to the focused view makes every
 * D-pad press scroll twice, which reads as a stutter.
 */
internal class TvScrollView(context: Context, private val autoReveal: () -> Boolean) : ScrollView(context) {
    override fun requestChildFocus(child: View?, focused: View?) {
        if (focused == null || autoReveal()) return super.requestChildFocus(child, focused)
        val hint = focused.revealOnFocusHint
        focused.revealOnFocusHint = false
        try { super.requestChildFocus(child, focused) } finally { focused.revealOnFocusHint = hint }
    }
    override fun requestChildRectangleOnScreen(child: View, rectangle: Rect, immediate: Boolean): Boolean =
        if (autoReveal()) super.requestChildRectangleOnScreen(child, rectangle, immediate) else false
}

internal class TvHorizontalScrollView(context: Context, private val autoReveal: () -> Boolean) : HorizontalScrollView(context) {
    override fun requestChildFocus(child: View?, focused: View?) {
        if (focused == null || autoReveal()) return super.requestChildFocus(child, focused)
        val hint = focused.revealOnFocusHint
        focused.revealOnFocusHint = false
        try { super.requestChildFocus(child, focused) } finally { focused.revealOnFocusHint = hint }
    }
    override fun requestChildRectangleOnScreen(child: View, rectangle: Rect, immediate: Boolean): Boolean =
        if (autoReveal()) super.requestChildRectangleOnScreen(child, rectangle, immediate) else false
}

/**
 * A fixed-size frame that re-lays out only itself when its content changes, so updating the
 * feature's title and synopsis does not re-measure every shelf on the page mid-scroll.
 */
internal class LayoutBoundaryFrame(context: Context) : FrameLayout(context) {
    private var pending = false
    override fun requestLayout() {
        if (!isLaidOut || isInLayout || width == 0 || height == 0) { super.requestLayout(); return }
        forceLayout()
        if (pending) return
        pending = true
        post {
            pending = false
            measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
            layout(left, top, right, bottom)
            invalidate()
        }
    }
}
