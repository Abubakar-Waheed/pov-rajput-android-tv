package dev.pov.tv

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.accessibility.CaptioningManager
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView

/** Device-local text subtitle preferences, shared by fullscreen and mini-player views. */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
internal class SubtitlePreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("pov_subtitles", Context.MODE_PRIVATE)
    var transparentBackground: Boolean
        get() = preferences.getBoolean("transparent_background", false)
        set(value) { preferences.edit().putBoolean("transparent_background", value).apply() }
    var boldText: Boolean
        get() = preferences.getBoolean("bold_text", false)
        set(value) { preferences.edit().putBoolean("bold_text", value).apply() }

    internal fun style(base: CaptionStyleCompat): CaptionStyleCompat = CaptionStyleCompat(
        base.foregroundColor,
        if (transparentBackground) Color.TRANSPARENT else base.backgroundColor,
        if (transparentBackground) Color.TRANSPARENT else base.windowColor,
        if (transparentBackground && base.edgeType == CaptionStyleCompat.EDGE_TYPE_NONE)
            CaptionStyleCompat.EDGE_TYPE_OUTLINE else base.edgeType,
        base.edgeColor,
        if (boldText) Typeface.create(base.typeface ?: Typeface.DEFAULT, Typeface.BOLD) else base.typeface,
    )

    fun applyTo(view: PlayerView) {
        val subtitles = view.subtitleView ?: return
        val override = transparentBackground || boldText
        // Embedded cue spans/windows can override background and font weight. Restore them
        // when both options are off, keeping the original rendering as the default.
        subtitles.setApplyEmbeddedStyles(!override)
        if (!override) {
            subtitles.setUserDefaultStyle()
            return
        }
        val captions = view.context.getSystemService(Context.CAPTIONING_SERVICE) as? CaptioningManager
        val base = captions?.userStyle?.let(CaptionStyleCompat::createFromCaptionStyle) ?: CaptionStyleCompat.DEFAULT
        subtitles.setStyle(style(base))
    }
}
