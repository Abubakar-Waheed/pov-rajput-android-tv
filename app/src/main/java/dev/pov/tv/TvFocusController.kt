package dev.pov.tv

import android.graphics.Rect
import android.view.FocusFinder
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.LinearLayout

internal data class TvFocusSnapshot(val key: String?, val scrollY: Int)

/** Scroll containers never own the invisible focus used to navigate TV controls. */
internal class TvFocusController(
    private val body: ViewGroup,
    private val scroll: ScrollView,
    private val obstruction: () -> Rect? = { null },
) {
    private var lastKey: String? = null

    init {
        scroll.isFocusable = false; scroll.isFocusableInTouchMode = false
        scroll.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        body.isFocusable = false; body.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
    }

    fun capture(): TvFocusSnapshot {
        val focused = body.findFocus()
        return TvFocusSnapshot(focused?.let(::key) ?: lastKey, scroll.scrollY)
    }

    fun move(direction: Int): Boolean {
        val focused = body.findFocus() ?: return first()
        if (focused !in leaves()) return first()
        val target = next(direction)
        if (target != null) select(target) else if (direction == View.FOCUS_UP) reveal(focused)
        // Keep the last visible leaf selected at an edge rather than scrolling empty space.
        return true
    }

    fun next(direction: Int): View? {
        val focused = body.findFocus() ?: return null
        if (direction == View.FOCUS_UP || direction == View.FOCUS_DOWN) {
            val rows = focusRows()
            val index = rows.indexOfFirst { focused in it.targets }
            if (index < 0) return null
            val target = rows.getOrNull(index + if (direction == View.FOCUS_DOWN) 1 else -1) ?: return null
            // Grid columns stay aligned; a shelf/header/action boundary always enters at its first item.
            val column = if (rows[index].owner.tag == "media-grid-row" && target.owner.tag == "media-grid-row")
                rows[index].targets.indexOf(focused).coerceAtMost(target.targets.lastIndex) else 0
            return target.targets[column]
        }
        return FocusFinder.getInstance().findNextFocus(body, focused, direction)?.takeIf { it in leaves() }
    }
    private data class FocusRow(val owner: View, val targets: List<View>)
    private fun focusRows(): List<FocusRow> {
        val targets = leaves()
        fun within(view: View, parent: View): Boolean {
            var node: View? = view
            while (node != null && node !== body) { if (node === parent) return true; node = node.parent as? View }
            return false
        }
        fun owner(view: View): View {
            var branch = view
            var parent = view.parent as? View
            while (parent != null && parent !== body) {
                if (parent is LinearLayout && parent.orientation == LinearLayout.HORIZONTAL) {
                    // Do not collapse several vertically stacked controls in a dashboard column.
                    return if (branch.isFocusable || targets.count { within(it, branch) } == 1) parent else view
                }
                branch = parent; parent = parent.parent as? View
            }
            return view
        }
        return targets.groupBy(::owner).map { (row, items) -> FocusRow(row, items) }
    }

    fun first(): Boolean {
        val target = lastKey?.let(::find) ?: leaves().firstOrNull() ?: return false
        return select(target)
    }

    fun restore(snapshot: TvFocusSnapshot?): Boolean {
        val target = snapshot?.key?.let(::find) ?: if (snapshot?.key == null) leaves().firstOrNull() else null
        if (target == null) return false
        target.requestFocus(); lastKey = key(target)
        if (snapshot != null) scroll.scrollTo(0, snapshot.scrollY)
        reveal(target)
        return true
    }

    fun hasVisibleLeafFocus(): Boolean = body.findFocus()?.let { it in leaves() } == true
    fun revealCurrent() { body.findFocus()?.let(::reveal) }
    fun reset() { lastKey = null }
    private fun find(value: String): View? = leaves().firstOrNull { key(it) == value }
    private fun key(view: View): String? = (view.tag as? String) ?: view.contentDescription?.toString()
    private fun select(view: View): Boolean {
        val result = view.requestFocus()
        if (result) { lastKey = key(view); reveal(view) }
        return result
    }
    private fun reveal(view: View) {
        if (focusRows().firstOrNull()?.targets?.contains(view) == true) { scroll.scrollTo(0, 0); return }
        var ancestor: View? = view
        while (ancestor != null && ancestor !== body) {
            if (ancestor.tag == "featured-hero") { scroll.scrollTo(0, 0); return }
            ancestor = ancestor.parent as? View
        }
        view.requestRectangleOnScreen(Rect(0, 0, view.width, view.height), true)
        val blocked = obstruction() ?: return
        val visible = Rect()
        if (view.getGlobalVisibleRect(visible) && Rect.intersects(visible, blocked)) {
            scroll.scrollBy(0, (visible.bottom - blocked.top + 12).coerceAtLeast(0))
        }
    }
    private fun leaves(): List<View> = descendants(body).filter { it !== body && it.isFocusable && it.isEnabled && it.isShown }
    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
}
