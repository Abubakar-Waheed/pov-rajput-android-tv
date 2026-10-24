package dev.pov.tv

import android.content.Context
import android.util.AttributeSet
import androidx.media3.ui.DefaultTimeBar

/** Keep a staged D-pad position visible while Media3 refreshes actual playback progress. */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class PreviewTimeBar @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : DefaultTimeBar(context, attrs) {
    private var actualPosition = 0L
    var previewPosition: Long? = null
        set(value) { field = value; super.setPosition(value ?: actualPosition) }
    override fun setPosition(position: Long) {
        actualPosition = position
        super.setPosition(previewPosition ?: position)
    }
}
