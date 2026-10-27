package dev.pov.tv

import android.content.Context
import android.graphics.drawable.Drawable
import android.widget.ImageView

/**
 * Artwork slots have a fixed size, so a newly decoded image never needs to re-measure the page.
 * Without this, every poster arriving on a long Home page triggers a full layout pass mid-scroll.
 */
internal class FixedSizeImageView(context: Context) : ImageView(context) {
    private var swapping = false

    override fun setImageDrawable(drawable: Drawable?) {
        swapping = true
        try { super.setImageDrawable(drawable) } finally { swapping = false }
    }

    override fun requestLayout() {
        if (!swapping) super.requestLayout()
    }
}
