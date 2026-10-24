package dev.pov.tv

import android.app.Activity
import android.graphics.Color
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import dev.pov.tv.data.SourceItem
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w960dp-h540dp-land-mdpi")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class SourceBadgesTest {
    @Test fun `explicit release tags become normalized badges without guessing absent capabilities`() {
        val source = SourceItem("Movie.2160p.Dolby.Vision.HDR10Plus.WEB-DL.x265.mkv", "fixture", sizeBytes = 2500000000, qualityHeight = 2160)
        assertEquals(listOf("4K", "2.50 GB", "DV", "HDR10+", "WEB-DL", "HEVC"), SourceBadges.from(source).map { it.label })
        val plain = SourceBadges.from(SourceItem("Movie.mkv", "fixture"))
        assertEquals(listOf("Quality unavailable", "Size unavailable"), plain.map { it.label })
    }
    @Test fun `tag boundaries avoid DVD title fragments and duplicate HDR labels`() {
        assertEquals(listOf("Quality unavailable", "Size unavailable"), SourceBadges.from(SourceItem("DVD.DVD-Rip.Webdling.AV123.HDR100.mkv", "fixture")).map { it.label })
        val labels = SourceBadges.from(SourceItem("Movie.DV.HDR10+.HDR.SDR.BDRemux.H265.mkv", "fixture")).map { it.label }
        assertEquals(listOf("Quality unavailable", "Size unavailable", "DV", "HDR10+", "BLU-RAY", "REMUX", "HEVC"), labels)
    }
    @Test fun `quality colors match requested resolution groups`() {
        for ((height, tone) in listOf(2160 to SourceBadgeTone.UHD, 1080 to SourceBadgeTone.FULL_HD, 720 to SourceBadgeTone.HD)) {
            assertEquals(tone, SourceBadges.from(SourceItem("Movie", "fixture", qualityHeight = height)).first().tone)
        }
    }
    @Test fun `rounded badges wrap without clipping or taking remote focus across themes`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        try {
            for (theme in ThemeSpec.entries) {
                val source = SourceItem("Example.Movie.2160p.DV.HDR10Plus.BluRay.REMUX.HEVC.mkv", "fixture", sizeBytes = 2500000000, qualityHeight = 2160)
                val row = TvChoiceRow(activity, "fixture", source.title, "Full filename remains available", "CACHED · Public fixture", theme.palette, SourceBadges.from(source)) {}
                val root = FrameLayout(activity)
                root.addView(row, FrameLayout.LayoutParams(280, -2)); activity.setContentView(root)
                shadowOf(Looper.getMainLooper()).idle()
                root.measure(View.MeasureSpec.makeMeasureSpec(280, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY))
                root.layout(0, 0, 280, 500)
                val strip = descendants(row).filterIsInstance<SourceBadgeStrip>().single()
                val chips = descendants(strip).filterIsInstance<TextView>()
                assertTrue("Narrow rows must wrap", chips.map { it.top }.distinct().size > 1)
                chips.forEach { chip ->
                    assertFalse(chip.isFocusable); assertFalse(chip.isClickable)
                    assertTrue(chip.typeface.isBold); assertTrue(chip.textSize >= 13)
                    assertTrue(chip.left >= 0 && chip.right <= strip.width)
                    assertTrue(chip.top >= 0 && chip.bottom <= strip.height)
                }
                assertTrue(strip.bottom <= row.height)
                val quality = chips.first()
                assertEquals(Color.parseColor("#FFE34D"), quality.currentTextColor)
                row.requestFocus(); shadowOf(Looper.getMainLooper()).idle()
                assertEquals("Selection must retain resolution color", Color.parseColor("#FFE34D"), quality.currentTextColor)
                assertEquals(ViewGroup.FOCUS_BLOCK_DESCENDANTS, row.descendantFocusability)
            }
        } finally { activity.finish() }
    }
    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
}
