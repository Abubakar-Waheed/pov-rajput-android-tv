package dev.pov.tv

import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/** Public test media only; no account values or personal history are reported. */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class SubtitleSeekInstrumentation : Instrumentation() {
    private val report = Bundle()
    private var stage = "start"
    private var playback: PlayerActivity? = null
    private var originalLanguage: String? = null
    private var originalPosition: Int? = null
    private var originalRecent: String? = null
    private var player: androidx.media3.exoplayer.ExoPlayer? = null
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    private fun <T> onMain(task: () -> T): T {
        val result = AtomicReference<Result<T>>()
        runOnMainSync { result.set(runCatching(task)) }
        return result.get().getOrThrow()
    }
    private fun await(name: String, timeout: Long = 15000, condition: () -> Boolean) {
        stage = name; report.putString("stage", name); sendStatus(1, report)
        val until = SystemClock.uptimeMillis() + timeout
        while (SystemClock.uptimeMillis() < until) { if (onMain(condition)) return; Thread.sleep(100) }
        error("Timed out: $name")
    }
    private fun nodes(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> = buildList {
        add(root); for (i in 0 until root.childCount) root.getChild(i)?.let { addAll(nodes(it)) }
    }
    private fun clickLabel(label: String) {
        repeat(12) {
            val root = uiAutomation.rootInActiveWindow
            val node = root?.let(::nodes)?.firstOrNull { it.text?.toString()?.equals(label, ignoreCase = true) == true && it.isVisibleToUser }
            if (node != null) {
                if (!node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    val bounds = android.graphics.Rect().also(node::getBoundsInScreen)
                    check(!bounds.isEmpty)
                    val time = SystemClock.uptimeMillis()
                    for (action in listOf(android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP)) {
                        val event = android.view.MotionEvent.obtain(time, time + if (action == android.view.MotionEvent.ACTION_UP) 50 else 0,
                            action, bounds.exactCenterX(), bounds.exactCenterY(), 0)
                        event.source = InputDevice.SOURCE_TOUCHSCREEN
                        try { sendPointerSync(event) } finally { event.recycle() }
                    }
                }
                waitForIdleSync(); return
            }
            root?.let(::nodes)?.firstOrNull { it.isScrollable }?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            Thread.sleep(200)
        }
        error("Missing choice: $label")
    }
    private fun capture(name: String) {
        val bitmap = checkNotNull(uiAutomation.takeScreenshot())
        File(checkNotNull(targetContext.externalCacheDir), name).outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        bitmap.recycle()
    }
    private fun movePositionSlider(percent: Int) {
        val slider = nodes(checkNotNull(uiAutomation.rootInActiveWindow))
            .first { it.className?.toString() == "android.widget.SeekBar" }
        val current = checkNotNull(slider.rangeInfo).current.toInt()
        val target = percent - 1
        repeat(kotlin.math.abs(current - target)) { nativeTap(if (target > current) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT) }
    }
    private fun event(action: Int, key: Int, repeat: Int = 0, down: Long = SystemClock.uptimeMillis()) =
        KeyEvent(down, SystemClock.uptimeMillis(), action, key, repeat, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_DPAD)
    private fun received(action: Int, key: Int, repeat: Int = 0) = onMain { playback!!.dispatchKeyEvent(event(action, key, repeat)) }
    private fun nativeTap(key: Int) {
        val down = SystemClock.uptimeMillis()
        check(uiAutomation.injectInputEvent(event(KeyEvent.ACTION_DOWN, key, down = down), true))
        check(uiAutomation.injectInputEvent(event(KeyEvent.ACTION_UP, key, down = down), true))
        waitForIdleSync()
    }
    override fun onStart() {
        var success = false
        val prefs = targetContext.getSharedPreferences("pov_subtitles", 0)
        try {
            onMain { InAppPlaybackSession.initialize(targetContext); InAppPlaybackSession.stop() }
            originalRecent = targetContext.getSharedPreferences("playback", 0).getString("recent", null)
            originalLanguage = prefs.getString("language", null)
            originalPosition = if (prefs.contains("position_percent")) prefs.getInt("position_percent", -1) else null
            val main = startActivitySync(Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            onMain {
                MainActivity::class.java.getDeclaredMethod("settings").apply { isAccessible = true }.invoke(main)
                fun find(view: View): View? {
                    if (view is TextView && view.text.startsWith("Subtitle language")) return view
                    if (view is ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i))?.let { return it }
                    return null
                }
                checkNotNull(find(main.window.decorView)).performClick()
            }
            waitForIdleSync(); Thread.sleep(300); stage = "language_picker"
            capture("subtitle-language-picker.png")
            clickLabel("French")
            await("language_choice_saved") { prefs.getString("language", null) == "fr" }
            report.putBoolean("settings_language_persisted", true)
            onMain {
                fun find(view: View): View? {
                    if (view is TextView && view.text.startsWith("Subtitle position")) return view
                    if (view is ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i))?.let { return it }
                    return null
                }
                checkNotNull(find(main.window.decorView)).performClick()
            }
            waitForIdleSync(); Thread.sleep(300)
            capture("subtitle-position-picker.png")
            movePositionSlider(2)
            await("lower_position_saved") { prefs.getInt("position_percent", -1) == 2 }
            clickLabel("Done")
            capture("subtitle-language-settings.png")
            playback = startActivitySync(Intent(targetContext, PlayerActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("url", SAMPLE)
                .putExtra("key", "").putExtra("title", "Public subtitle and seek test").putExtra("playWhenReady", false)) as PlayerActivity
            player = onMain { checkNotNull(InAppPlaybackSession.playerOrNull).also { it.volume = 0f } }
            await("french_selected_automatically", 45000) {
                check(player!!.playerError == null)
                player!!.playbackState == Player.STATE_READY && player!!.currentTracks.groups.any { group ->
                    group.type == C.TRACK_TYPE_TEXT && (0 until group.length).any {
                        group.isTrackSelected(it) && group.getTrackFormat(it).language?.startsWith("fr") == true
                    }
                }
            }
            report.putBoolean("preferred_subtitle_selected_on_fresh_playback", true)
            val view = onMain { playback!!.findViewById<PlayerView>(R.id.pov_player) }
            val model = onMain { PlayerActivity::class.java.getDeclaredField("seekPreview").apply { isAccessible = true }.get(playback) as DpadSeekPreview }
            val label = onMain { view.overlayFrameLayout!!.findViewWithTag<TextView>("pov_seek_preview") }
            var seeks = 0
            onMain {
                player!!.seekTo(10_000)
                player!!.addListener(object : Player.Listener {
                    override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                        if (reason == Player.DISCONTINUITY_REASON_SEEK) seeks++
                    }
                })
            }
            await("initial_position") { player!!.playbackState == Player.STATE_READY && player!!.currentPosition in 9950..10100 }
            fun displayedCues(): List<androidx.media3.common.text.Cue> {
                @Suppress("UNCHECKED_CAST")
                return androidx.media3.ui.SubtitleView::class.java.getDeclaredField("cues").apply { isAccessible = true }
                    .get(view.subtitleView) as List<androidx.media3.common.text.Cue>
            }
            await("lower_cue_rendered") { displayedCues().any { it.text != null && kotlin.math.abs(it.line - 0.98f) < 0.001f } }
            onMain { view.hideController() }
            capture("subtitle-position-lower.png")
            nativeTap(KeyEvent.KEYCODE_DPAD_UP)
            onMain { seeks = 0; view.showController(); check(view.findViewById<View>(androidx.media3.ui.R.id.exo_progress).requestFocus()) }
            received(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)
            received(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, 1)
            received(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, 2)
            Thread.sleep(1400)
            onMain {
                check(model.active && model.position == 13_000L) { "Preview active=${model.active}, position=${model.position}, actual=${player!!.currentPosition}" }
                check(player!!.currentPosition == 10_000L && !player!!.playWhenReady && seeks == 0)
                check(label.visibility == View.VISIBLE && label.text.contains("00:13") && label.text.contains("+3s"))
                check(!label.text.contains("Release"))
            }
            capture("seek-seconds-preview.png")
            received(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT)
            await("release_commits_once_keeps_pause") { seeks == 1 && player!!.currentPosition == 13_000L && !player!!.playWhenReady && !model.active }
            report.putBoolean("one_second_preview_no_seek_until_release", true)
            onMain { player!!.play() }
            await("sample_playing") { player!!.isPlaying }
            onMain { seeks = 0; view.hideController() }
            received(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT)
            repeat(4) { received(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, it + 1) }
            Thread.sleep(1300)
            val target = onMain {
                check(model.active && !player!!.playWhenReady && seeks == 0)
                check(model.offsetSeconds == -5L)
                model.position
            }
            received(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_LEFT)
            await("release_resumes_at_target") { seeks == 1 && player!!.playWhenReady && player!!.currentPosition in target..(target + 1000) }
            report.putBoolean("hidden_controls_seek_and_release_resume", true)
            onMain { seeks = 0; view.hideController() }
            received(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)
            received(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK)
            received(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK)
            received(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT)
            onMain { check(!model.active && seeks == 0 && player!!.playWhenReady); player!!.pause(); view.showController()
                view.findViewById<View>(androidx.media3.ui.R.id.exo_play_pause).requestFocus() }
            nativeTap(KeyEvent.KEYCODE_DPAD_RIGHT)
            onMain { check(playback!!.currentFocus?.id == androidx.media3.ui.R.id.exo_ffwd && !model.active && seeks == 0) }
            report.putBoolean("cancel_and_action_row_navigation_preserved", true)
            onMain { SubtitlePreferences(targetContext).language = "off"; InAppPlaybackSession.refreshSubtitleLanguage() }
            await("subtitle_off") { player!!.currentTracks.groups.none { it.type == C.TRACK_TYPE_TEXT && it.isSelected } }
            onMain { SubtitlePreferences(targetContext).language = "en"; InAppPlaybackSession.refreshSubtitleLanguage() }
            await("english_selection") { player!!.currentTracks.groups.any { g ->
                g.type == C.TRACK_TYPE_TEXT && (0 until g.length).any { g.isTrackSelected(it) && g.getTrackFormat(it).language?.startsWith("en") == true }
            } }
            report.putBoolean("off_and_changed_language_apply", true)
            onMain {
                player!!.seekTo(10_000)
                PlayerActivity::class.java.getDeclaredMethod("showSubtitlePosition").apply { isAccessible = true }.invoke(playback)
            }
            waitForIdleSync(); Thread.sleep(300)
            movePositionSlider(16)
            await("higher_position_in_player") {
                prefs.getInt("position_percent", -1) == 16 && displayedCues().any { it.text != null && kotlin.math.abs(it.line - 0.84f) < 0.001f }
            }
            capture("subtitle-position-live.png")
            clickLabel("Cancel")
            await("cancel_restores_position") { prefs.getInt("position_percent", -1) == 2 && displayedCues().any { it.text != null && kotlin.math.abs(it.line - 0.98f) < 0.001f } }
            report.putBoolean("subtitle_position_settings_and_player_verified", true)
            success = true
        } catch (error: Throwable) {
            report.putString("failed_stage", stage)
            report.putString("error_class", error.javaClass.simpleName)
            report.putString("safe_message", if (error is IllegalStateException) error.message else "Public playback test failed")
            report.putString("location", error.stackTrace.filter { it.className.startsWith(javaClass.name) }.joinToString { "${it.methodName}:${it.lineNumber}" })
        } finally {
            onMain {
                if (InAppPlaybackSession.playerOrNull === player && player != null) InAppPlaybackSession.stop()
                playback?.finish()
                if (originalLanguage == null) prefs.edit().remove("language").commit() else prefs.edit().putString("language", originalLanguage).commit()
                if (originalPosition == null) prefs.edit().remove("position_percent").commit() else prefs.edit().putInt("position_percent", originalPosition!!).commit()
            }
            val unchanged = targetContext.getSharedPreferences("playback", 0).getString("recent", null) == originalRecent
            report.putBoolean("history_unchanged", unchanged)
            report.putBoolean("passed", success && unchanged)
            finish(if (success && unchanged) -1 else 0, report)
        }
    }
    companion object { private const val SAMPLE = "https://storage.googleapis.com/shaka-demo-assets/angel-one/dash.mpd" }
}
