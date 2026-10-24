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

    var sizePercent: Int
        get() = preferences.getInt("size_percent", 100).takeIf { it in SIZES } ?: 100
        set(value) { require(value in SIZES); preferences.edit().putInt("size_percent", value).apply() }
    val sizeLabel: String get() = SIZE_LABELS[SIZES.indexOf(sizePercent)]

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
        subtitles.setApplyEmbeddedFontSizes(!override && sizePercent == 100)
        val captions = view.context.getSystemService(Context.CAPTIONING_SERVICE) as? CaptioningManager
        subtitles.setFractionalTextSize(androidx.media3.ui.SubtitleView.DEFAULT_TEXT_SIZE_FRACTION *
            sizePercent / 100f * (captions?.fontScale ?: 1f))
        if (!override) {
            subtitles.setUserDefaultStyle()
            return
        }
        val base = captions?.userStyle?.let(CaptionStyleCompat::createFromCaptionStyle) ?: CaptionStyleCompat.DEFAULT
        subtitles.setStyle(style(base))
    }

    companion object {
        val SIZES = listOf(80, 100, 130)
        val SIZE_LABELS = listOf("Small", "Medium", "Large")
    }
}
