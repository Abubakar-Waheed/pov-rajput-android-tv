package dev.pov.tv

import android.app.Instrumentation
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.StyleSpan
import android.graphics.Typeface
import android.widget.FrameLayout
import androidx.media3.common.text.Cue
import androidx.media3.ui.PlayerView

/** Synthetic text-cue rendering check; never loads an account or playback URL. */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class SubtitleStyleInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val report = Bundle()
        val preferences = SubtitlePreferences(targetContext)
        val previousTransparent = preferences.transparentBackground
        val previousBold = preferences.boldText
        var resultCode = -1
        try {
            val activity = startActivitySync(Intent(targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            lateinit var playerView: PlayerView
            runOnMainSync {
                playerView = PlayerView(activity).apply {
                    useController = false
                    setShutterBackgroundColor(Color.TRANSPARENT)
                    videoSurfaceView?.visibility = android.view.View.GONE
                }
                activity.setContentView(FrameLayout(activity).apply {
                    setBackgroundColor(0xFF476786.toInt())
                    addView(playerView, FrameLayout.LayoutParams(-1, -1))
                })
                playerView.subtitleView!!.setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 30f)
            }
            fun render(name: String, transparent: Boolean, bold: Boolean) {
                runOnMainSync {
                    preferences.transparentBackground = transparent; preferences.boldText = bold
                    preferences.applyTo(playerView)
                    val text = SpannableString("Subtitle style preview\nThe quick brown fox jumps over the lazy dog.").apply {
                        setSpan(BackgroundColorSpan(Color.BLACK), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        setSpan(StyleSpan(Typeface.NORMAL), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    playerView.subtitleView!!.setCues(listOf(Cue.Builder().setText(text)
                        .setWindowColor(Color.BLACK).build()))
                }
                waitForIdleSync(); Thread.sleep(700)
                val bitmap = checkNotNull(uiAutomation.takeScreenshot())
                java.io.File(checkNotNull(targetContext.externalCacheDir), "$name.png").outputStream().use {
                    check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
                }
                bitmap.recycle()
            }
            render("subtitles-original", false, false)
            render("subtitles-transparent-bold", true, true)
            val restored = SubtitlePreferences(targetContext)
            check(restored.transparentBackground && restored.boldText)
            report.putBoolean("render_and_persistence_verified", true)

        } catch (error: Exception) {
            report.putString("error_type", error.javaClass.simpleName)
            resultCode = 0
        } finally {
            preferences.transparentBackground = previousTransparent; preferences.boldText = previousBold
        }
        finish(resultCode, report)
    }
}
