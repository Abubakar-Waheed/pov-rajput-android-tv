package dev.pov.tv

import android.app.Instrumentation
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.os.SystemClock
import android.view.Display
import android.view.View
import android.widget.TextView
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import java.util.concurrent.atomic.AtomicReference

/** Public video only; service IDs are synthetic UI fixtures, never API keys. */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class DolbyDebridInstrumentation : Instrumentation() {
    private val report = Bundle()
    private var stage = "start"
    private var activity: PlayerActivity? = null
    private fun <T> onMain(task: () -> T): T {
        val result = AtomicReference<Result<T>>()
        runOnMainSync { result.set(runCatching(task)) }
        return result.get().getOrThrow()
    }
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    private fun await(label: String, condition: () -> Boolean) {
        stage = label
        val end = SystemClock.uptimeMillis() + 30000
        while (SystemClock.uptimeMillis() < end) {
            if (onMain(condition)) return
            SystemClock.sleep(100)
        }
        error("Public test timed out")
    }
    override fun onStart() {
        val originalRecent = targetContext.getSharedPreferences("playback", 0).getString("recent", null)
        val preferences = targetContext.getSharedPreferences("pov_video", 0)
        val originalMode = preferences.getString("dolby_mode", null)
        var passed = false
        try {
            onMain { InAppPlaybackSession.initialize(targetContext); InAppPlaybackSession.stop() }
            val hdr = (targetContext.getSystemService(android.content.Context.DISPLAY_SERVICE) as DisplayManager)
                .getDisplay(Display.DEFAULT_DISPLAY)?.hdrCapabilities?.supportedHdrTypes ?: intArrayOf()
            report.putBoolean("display_hdr10", Display.HdrCapabilities.HDR_TYPE_HDR10 in hdr)
            report.putBoolean("display_hlg", Display.HdrCapabilities.HDR_TYPE_HLG in hdr)
            report.putBoolean("display_dolby_vision", Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION in hdr)
            val p7Fixture = java.io.File(checkNotNull(targetContext.externalCacheDir), "pov-p7-base-fixture.mp4")
            if (Display.HdrCapabilities.HDR_TYPE_HDR10 in hdr && p7Fixture.isFile) {
                onMain { VideoCompatibilityPreferences(targetContext).mode = DolbyVisionMode.HDR_BASE }
                activity = startActivitySync(Intent(targetContext, PlayerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra("url", PUBLIC_SAMPLE).putExtra("title", "Synthetic P7 HDR10 base test").putExtra("key", "")
                    .putExtra("playWhenReady", false)) as PlayerActivity
                onMain {
                    val p = checkNotNull(InAppPlaybackSession.playerOrNull)
                    p.setMediaItem(androidx.media3.common.MediaItem.fromUri(android.net.Uri.fromFile(p7Fixture)))
                    p.prepare()
                }
                await("synthetic_p7_base_ready") {
                    val p = checkNotNull(InAppPlaybackSession.playerOrNull)
                    check(p.playerError == null && InAppPlaybackSession.error == null)
                    p.videoDecoderCounters?.ensureUpdated()
                    p.playbackState == Player.STATE_READY && (p.videoDecoderCounters?.renderedOutputBufferCount ?: 0) > 0
                }
                onMain {
                    val p = checkNotNull(InAppPlaybackSession.playerOrNull)
                    val original = p.currentTracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }.flatMap { group ->
                        (0 until group.length).map { group.getTrackFormat(it) }
                    }.single()
                    check(DolbyVisionCompatibility.profile(original) == 7)
                    check(p.videoFormat?.sampleMimeType == MimeTypes.VIDEO_H265)
                    check(p.videoFormat?.colorInfo?.colorTransfer == C.COLOR_TRANSFER_ST2084)
                    InAppPlaybackSession.stop(); activity?.finish()
                }
                report.putBoolean("synthetic_p7_hdr10_base_decoded", true)
                waitForIdleSync()
            }
            if (Display.HdrCapabilities.HDR_TYPE_HLG in hdr) {
                onMain { VideoCompatibilityPreferences(targetContext).mode = DolbyVisionMode.HDR_BASE }
                activity = startActivitySync(Intent(targetContext, PlayerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra("url", PUBLIC_DV8).putExtra("title", "Public Dolby Vision Profile 8 test").putExtra("key", "")
                    .putExtra("playWhenReady", false)) as PlayerActivity
                await("public_dv8_base_ready") {
                    val p = checkNotNull(InAppPlaybackSession.playerOrNull)
                    check(p.playerError == null && InAppPlaybackSession.error == null)
                    p.videoDecoderCounters?.ensureUpdated()
                    p.playbackState == Player.STATE_READY && (p.videoDecoderCounters?.renderedOutputBufferCount ?: 0) > 0
                }
                onMain {
                    val p = checkNotNull(InAppPlaybackSession.playerOrNull)
                    val original = p.currentTracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }.flatMap { group ->
                        (0 until group.length).map { group.getTrackFormat(it) }
                    }.single()
                    check(DolbyVisionCompatibility.profile(original) == 8)
                    check(p.videoFormat?.sampleMimeType == MimeTypes.VIDEO_H265)
                    check(p.videoFormat?.colorInfo?.colorTransfer == C.COLOR_TRANSFER_HLG)
                    InAppPlaybackSession.stop(); activity?.finish()
                }
                report.putBoolean("real_public_p8_hlg_base_decoded", true)
                waitForIdleSync()
            } else report.putBoolean("p8_hlg_test_skipped_display_unsupported", true)
            onMain {
                if (originalMode == null) preferences.edit().remove("dolby_mode").commit()
                else preferences.edit().putString("dolby_mode", originalMode).commit()
            }
            for (service in listOf("offcloud", "torbox", "")) {
                stage = "start_$service"
                activity = startActivitySync(Intent(targetContext, PlayerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra("url", PUBLIC_SAMPLE).putExtra("title", "Public service badge test").putExtra("key", "")
                    .putExtra("debrid_service", service).putExtra("playWhenReady", false)) as PlayerActivity
                val current = checkNotNull(activity)
                await("ready_$service") {
                    val player = checkNotNull(InAppPlaybackSession.playerOrNull)
                    check(player.playerError == null && InAppPlaybackSession.error == null)
                    player.playbackState == Player.STATE_READY
                }
                onMain {
                    val expected = PlaybackDebrid.normalize(service)
                    check(InAppPlaybackSession.debridService == expected)
                    val badge = current.findViewById<View>(R.id.pov_debrid_badge)
                    check(badge.visibility == if (expected == null) View.GONE else View.VISIBLE)
                    if (expected != null) {
                        check(current.findViewById<TextView>(R.id.pov_debrid_name).text.toString() == if (expected == "offcloud") "Offcloud" else "TorBox")
                        val state = Bundle().also(InAppPlaybackSession::saveState)
                        check(state.getString("debrid_service") == expected)
                        check(InAppPlaybackSession.currentIntent(targetContext)?.getStringExtra("debrid_service") == expected)
                        val title = current.findViewById<View>(R.id.pov_playback_title)
                        check(title.width > 100 && badge.right <= (badge.parent as View).width)
                    }
                }
                if (service.isNotEmpty()) {
                    stage = "capture_$service"
                    val file = java.io.File(checkNotNull(targetContext.externalCacheDir), "pov-$service-badge.png")
                    check(file.absolutePath.matches(Regex("[A-Za-z0-9/._-]+")))
                    check(onMain { current.hasWindowFocus() && InAppPlaybackSession.matches(Intent().putExtra("url", PUBLIC_SAMPLE).putExtra("key", "")) })
                    SystemClock.sleep(400)
                    uiAutomation.executeShellCommand("screencap -p ${file.absolutePath}").use { descriptor ->
                        android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
                    }
                    if (!onMain { current.hasWindowFocus() && InAppPlaybackSession.matches(Intent().putExtra("url", PUBLIC_SAMPLE).putExtra("key", "")) }) {
                        file.delete(); error("Public capture lost window ownership")
                    }
                }
                onMain { InAppPlaybackSession.stop(); current.finish() }
                waitForIdleSync()
            }
            report.putBoolean("both_service_badges_and_direct_reset", true)
            report.putBoolean("provenance_saved_for_fullscreen_reuse", true)
            passed = true
        } catch (error: Throwable) {
            report.putString("failed_stage", stage)
            report.putString("error_class", error.javaClass.simpleName)
        } finally {
            onMain {
                InAppPlaybackSession.stop(); activity?.finish()
                if (originalMode == null) preferences.edit().remove("dolby_mode").commit()
                else preferences.edit().putString("dolby_mode", originalMode).commit()
            }
            val unchanged = originalRecent == targetContext.getSharedPreferences("playback", 0).getString("recent", null)
            report.putBoolean("history_unchanged", unchanged)
            report.putBoolean("passed", passed && unchanged)
            finish(if (passed && unchanged) -1 else 0, report)
        }
    }
    companion object {
        private const val PUBLIC_SAMPLE = "https://storage.googleapis.com/shaka-demo-assets/angel-one/dash.mpd"
        private const val PUBLIC_DV8 = "https://raw.githubusercontent.com/androidx/media/1.11.1/libraries/test_data/src/test/assets/media/mp4/dolbyVision-hdr.MOV"
    }
}
