package dev.pov.tv

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import androidx.media3.ui.CaptionStyleCompat
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class SubtitlePreferencesTest {
    private val context get() = RuntimeEnvironment.getApplication()
    @Before fun reset() { context.getSharedPreferences("pov_subtitles", Context.MODE_PRIVATE).edit().clear().commit() }

    @Test fun `options default off and persist independently between player instances`() {
        val first = SubtitlePreferences(context)
        assertFalse(first.transparentBackground); assertFalse(first.boldText)
        first.boldText = true
        val second = SubtitlePreferences(context)
        assertTrue(second.boldText); assertFalse(second.transparentBackground)
        second.transparentBackground = true
        second.boldText = false
        val third = SubtitlePreferences(context)
        assertTrue(third.transparentBackground); assertFalse(third.boldText)
    }

    @Test fun `transparent bold captions remove both boxes while retaining foreground and readable edge`() {
        val preferences = SubtitlePreferences(context).apply { transparentBackground = true; boldText = true }
        val base = CaptionStyleCompat(Color.YELLOW, Color.BLACK, Color.BLUE,
            CaptionStyleCompat.EDGE_TYPE_NONE, Color.BLACK, Typeface.SERIF)
        val style = preferences.style(base)
        assertEquals(Color.TRANSPARENT, style.backgroundColor)
        assertEquals(Color.TRANSPARENT, style.windowColor)
        assertEquals(Color.YELLOW, style.foregroundColor)
        assertEquals(CaptionStyleCompat.EDGE_TYPE_OUTLINE, style.edgeType)
        assertTrue(style.typeface!!.isBold)
    }

    @Test fun `bold only preserves user caption colors and edge preferences`() {
        val preferences = SubtitlePreferences(context).apply { boldText = true }
        val base = CaptionStyleCompat(Color.YELLOW, Color.BLUE, Color.RED,
            CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW, Color.GREEN, Typeface.SERIF)
        val style = preferences.style(base)
        assertEquals(base.backgroundColor, style.backgroundColor)
        assertEquals(base.windowColor, style.windowColor)
        assertEquals(base.edgeType, style.edgeType)
        assertEquals(base.edgeColor, style.edgeColor)
        assertTrue(style.typeface!!.isBold)
    }
}
