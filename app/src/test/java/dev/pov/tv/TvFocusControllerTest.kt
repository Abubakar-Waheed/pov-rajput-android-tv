package dev.pov.tv

import android.app.Activity
import android.graphics.Color
import android.graphics.Rect
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/** Native attached views: focus must remain on a visible action across several screens. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w960dp-h540dp-land-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
class TvFocusControllerTest {
    private lateinit var activity: Activity
    private lateinit var scroll: ScrollView
    private lateinit var body: LinearLayout
    private lateinit var controller: TvFocusController

    @Before fun setup() {
        activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        scroll = ScrollView(activity).apply {
            isFillViewport = true
            isVerticalFadingEdgeEnabled = false
        }
        body = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(body, android.widget.FrameLayout.LayoutParams(-1, -2))
        activity.setContentView(scroll)
        controller = TvFocusController(body, scroll)
        rebuild()
        shadowOf(Looper.getMainLooper()).idle()
        layout()
    }

    @After fun cleanup() { activity.finish(); shadowOf(Looper.getMainLooper()).idle() }

    @Test fun `scroll containers cannot become invisible remote selections`() {
        assertFalse(scroll.isFocusable)
        assertFalse(scroll.isFocusableInTouchMode)
        assertFalse(body.isFocusable)
        assertEquals(ViewGroup.FOCUS_AFTER_DESCENDANTS, scroll.descendantFocusability)
        assertTrue(controller.first())
        assertSelected(0)
        assertNotSame(scroll, body.findFocus())
        assertNotSame(body, body.findFocus())
    }

    @Test fun `down and up always select an actual visible leaf across thirty episode actions`() {
        controller.reset()
        assertTrue(controller.first())
        assertSelected(0)
        for (index in 1 until 30) {
            assertTrue(controller.move(View.FOCUS_DOWN))
            assertSelected(index)
        }
        assertTrue("Traversal must have moved through several screen heights", scroll.scrollY > scroll.height * 4)
        val bottomScroll = scroll.scrollY
        assertTrue(controller.move(View.FOCUS_DOWN))
        assertSelected(29)
        assertEquals("Down at the last action must not scroll into empty space", bottomScroll, scroll.scrollY)
        for (index in 28 downTo 0) {
            assertTrue(controller.move(View.FOCUS_UP))
            assertSelected(index)
        }
        val topScroll = scroll.scrollY
        assertTrue(controller.move(View.FOCUS_UP))
        assertSelected(0)
        assertEquals(topScroll, scroll.scrollY)
    }

    @Test fun `snapshot restores the selected episode and viewport after rebuilding child views`() {
        controller.reset(); controller.first()
        repeat(18) { controller.move(View.FOCUS_DOWN) }
        val oldSelection = body.findFocus()
        val snapshot = controller.capture()
        assertEquals("episode:18", snapshot.key)
        assertTrue(snapshot.scrollY > 0)
        rebuild(); layout()
        assertTrue(controller.restore(snapshot))
        assertSelected(18)
        assertNotSame("Restoration must identify the rebuilt action by its stable key", oldSelection, body.findFocus())
        assertEquals(snapshot.scrollY, scroll.scrollY)
    }

    @Test fun `a removed action does not masquerade as a successful restored selection`() {
        assertFalse(controller.restore(TvFocusSnapshot("episode:removed", 500)))
        controller.reset()
        assertTrue(controller.first())
        assertSelected(0)
    }

    @Test fun `last episode remains reachable and is revealed above an overlapping mini player`() {
        body.setPadding(0, 0, 0, 96)
        val obstruction = Rect()
        controller = TvFocusController(body, scroll) { obstruction }
        layout()
        val viewport = Rect()
        assertTrue(scroll.getGlobalVisibleRect(viewport))
        obstruction.set(viewport.right - 150, viewport.top + 100, viewport.right, viewport.bottom)
        controller.first()
        repeat(29) { controller.move(View.FOCUS_DOWN) }
        assertSelected(29)
        val selectedBounds = Rect()
        assertTrue(body.getChildAt(29).getGlobalVisibleRect(selectedBounds))
        assertTrue("The selected episode must be visible above the mini player", selectedBounds.bottom <= obstruction.top)
    }

    @Test fun `first poster always reaches adjacent headers and headers enter the first poster`() {
        body.removeAllViews()
        body.addView(TextView(activity).apply { text = "Page title" }, LinearLayout.LayoutParams(-1, 90))
        val hero = focusButton("hero")
        body.addView(hero, LinearLayout.LayoutParams(400, 48))
        val firstHeader = focusButton("shelf:one"); body.addView(firstHeader, LinearLayout.LayoutParams(-1, 48))
        val firstRow = posterRow("one")
        val secondHeader = focusButton("shelf:two"); body.addView(secondHeader, LinearLayout.LayoutParams(-1, 48))
        val secondRow = posterRow("two")
        layout(); controller.reset(); controller.first()
        assertSame(hero, body.findFocus())
        controller.move(View.FOCUS_DOWN); assertSame(firstHeader, body.findFocus())
        controller.move(View.FOCUS_DOWN); assertSame(firstRow.getChildAt(0), body.findFocus())
        controller.move(View.FOCUS_DOWN); assertSame(secondHeader, body.findFocus())
        controller.move(View.FOCUS_DOWN); assertSame(secondRow.getChildAt(0), body.findFocus())
        controller.move(View.FOCUS_UP); assertSame(secondHeader, body.findFocus())
        controller.move(View.FOCUS_UP); assertSame(firstRow.getChildAt(0), body.findFocus())
        controller.move(View.FOCUS_UP); assertSame(firstHeader, body.findFocus())
        controller.move(View.FOCUS_UP); assertSame(hero, body.findFocus())
        assertEquals(0, scroll.scrollY)
    }
    @Test fun `every tab reveals its nonfocusable heading when the first control is reached`() {
        for (tab in listOf("Movies", "Shows", "Anime", "Library", "WeTrakr", "Settings")) {
            body.removeAllViews()
            body.addView(TextView(activity).apply { text = tab }, LinearLayout.LayoutParams(-1, 110))
            repeat(12) { body.addView(focusButton("$tab:$it"), LinearLayout.LayoutParams(-1, 48)) }
            layout(); controller.reset(); controller.first()
            repeat(11) { controller.move(View.FOCUS_DOWN) }
            check(scroll.scrollY > 0)
            repeat(11) { controller.move(View.FOCUS_UP) }
            assertSame(body.getChildAt(1), body.findFocus())
            assertEquals("$tab must reveal its heading", 0, scroll.scrollY)
        }
    }
    @Test fun `grid keeps a column while shelf entry never inherits that column`() {
        body.removeAllViews()
        val first = posterRow("grid-one").apply { tag = "media-grid-row" }
        val second = posterRow("grid-two").apply { tag = "media-grid-row" }
        val header = focusButton("shelf:after-grid"); body.addView(header, LinearLayout.LayoutParams(-1, 48))
        val shelf = posterRow("shelf")
        layout(); first.getChildAt(2).requestFocus()
        controller.move(View.FOCUS_DOWN); assertSame(second.getChildAt(2), body.findFocus())
        controller.move(View.FOCUS_DOWN); assertSame(header, body.findFocus())
        controller.move(View.FOCUS_DOWN); assertSame(shelf.getChildAt(0), body.findFocus())
    }
    private fun focusButton(value: String) = Button(activity).apply {
        text = value; tag = value; isFocusable = true; isFocusableInTouchMode = true
    }
    private fun posterRow(key: String): LinearLayout {
        val row = LinearLayout(activity)
        repeat(4) { row.addView(focusButton("$key:$it"), LinearLayout.LayoutParams(130, 110)) }
        body.addView(android.widget.HorizontalScrollView(activity).apply { isFocusable = false; addView(row) }, LinearLayout.LayoutParams(-1, 110))
        return row
    }

    private fun rebuild() {
        body.removeAllViews()
        repeat(30) { index ->
            body.addView(Button(activity).apply {
                text = "Episode ${index + 1}"
                tag = "episode:$index"
                isFocusable = true; isFocusableInTouchMode = true; isClickable = true
                setOnFocusChangeListener { _, focused ->
                    setBackgroundColor(if (focused) Color.CYAN else Color.DKGRAY)
                    setTextColor(if (focused) Color.BLACK else Color.WHITE)
                }
            }, LinearLayout.LayoutParams(-1, 48))
        }
    }

    private fun layout() {
        scroll.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(180, View.MeasureSpec.EXACTLY))
        scroll.layout(0, 0, 600, 180)
    }

    private fun assertSelected(index: Int) {
        val selected = body.findFocus()
        assertSame("Episode $index should own the visible selection", body.getChildAt(index), selected)
        assertTrue(controller.hasVisibleLeafFocus())
        assertTrue(selected!!.isFocusable)
        val bounds = Rect()
        selected.getDrawingRect(bounds)
        body.offsetDescendantRectToMyCoords(selected, bounds)
        assertTrue("Selected action top must be inside the viewport", bounds.top >= scroll.scrollY)
        assertTrue("Selected action bottom must be inside the viewport", bounds.bottom <= scroll.scrollY + scroll.height)
    }
}
