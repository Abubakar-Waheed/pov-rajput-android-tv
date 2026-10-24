package dev.pov.tv

import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import dev.pov.tv.data.*

/** Real connected-cache routing and UI labels. No credentials, filenames or links in reports. */
class DebridPriorityInstrumentation : Instrumentation() {
    private var uiOnly = false
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        uiOnly = arguments?.getString("ui_only") == "true"
        start()
    }
    override fun onStart() {
        val report = Bundle()
        val store = SecureStore(targetContext)
        var previous: Map<String, String>? = null
        var result = 0
        fun stage(name: String) { report.putString("stage", name); sendStatus(1, report) }
        try {
            if (uiOnly) {
                verifyLabels()
                report.putBoolean("plain_subtitle_labels_verified", true)
                report.putBoolean("priority_setting_persisted", true)
                finish(-1, report); return
            }
            previous = mapOf("playback.priority" to store.get("playback.priority"), "playback.service" to store.get("playback.service"))
            check(DebridServices.connected(store).toSet() == DebridServices.IDS.toSet()) { "Connect both services in Settings for this diagnostic." }
            val offcloud = OffcloudApi(store)
            val torbox = TorBoxApi(store)
            stage("history_before")
            val ocBefore = offcloud.cloud().map { it.id }.toSet()
            val tbBefore = torbox.cloud("torrents").map { it.id }.toSet()
            val identity = PlaybackIdentity("tt1254207", "movie", "Big Buck Bunny")
            val resolver = SourceResolver(store, torbox)
            DebridServices.prefer(store, "offcloud")
            stage("combined_cache_search")
            val sources = resolver.sources(identity)
            val cached = sources.filter { it.cached && it.directUrl.isBlank() }
            check(cached.isNotEmpty()) { "No cached public sample source is currently available." }
            check(cached.all { it.cachedServices.isNotEmpty() })
            report.putBoolean("combined_cache_search_verified", true)
            report.putBoolean("offcloud_cached_results_present", cached.any { "offcloud" in it.cachedServices })
            report.putBoolean("torbox_cached_results_present", cached.any { "torbox" in it.cachedServices })
            val shared = cached.firstOrNull { it.cachedServices.size == 2 }
            report.putBoolean("shared_cached_source_available", shared != null)
            if (shared != null) {
                stage("offcloud_first")
                check(resolver.prepare(shared, identity) is PreparedSource.OffcloudFiles)
                report.putBoolean("offcloud_first_verified", true)
                DebridServices.prefer(store, "torbox")
                stage("torbox_first")
                check(resolver.prepare(shared, identity) is PreparedSource.Files)
                report.putBoolean("torbox_first_verified", true)
            }
            stage("history_after")
            report.putBoolean("offcloud_history_unchanged", ocBefore == offcloud.cloud().map { it.id }.toSet())
            report.putBoolean("torbox_history_unchanged", tbBefore == torbox.cloud("torrents").map { it.id }.toSet())
            check(report.getBoolean("offcloud_history_unchanged") && report.getBoolean("torbox_history_unchanged")) { "Cloud history changed during the diagnostic." }
            store.putAll(checkNotNull(previous))
            previous = null
            stage("subtitle_labels")
            verifyLabels()
            report.putBoolean("plain_subtitle_labels_verified", true)
            result = -1
        } catch (error: Exception) {
            report.putString("safe_error", uiErrorMessage(error))
            report.putString("error_type", error.javaClass.simpleName)
        } finally {
            previous?.let { store.putAll(it) }
        }
        finish(result, report)
    }

    private fun verifyLabels() {
        val activity = startActivitySync(Intent(targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        runOnMainSync {
            MainActivity::class.java.getDeclaredMethod("settings").apply { isAccessible = true }.invoke(activity)
        }
        waitForIdleSync(); Thread.sleep(350)
        fun nodes(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> = buildList {
            add(root)
            for (index in 0 until root.childCount) root.getChild(index)?.let { addAll(nodes(it)) }
        }
        fun click(node: AccessibilityNodeInfo) {
            if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return
            val bounds = android.graphics.Rect().also(node::getBoundsInScreen)
            check(!bounds.isEmpty) { "Settings choice is not visible." }
            val time = android.os.SystemClock.uptimeMillis()
            for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                val event = MotionEvent.obtain(time, time + if (action == MotionEvent.ACTION_UP) 50 else 0,
                    action, bounds.exactCenterX(), bounds.exactCenterY(), 0)
                event.source = InputDevice.SOURCE_TOUCHSCREEN
                try { sendPointerSync(event) } finally { event.recycle() }
            }
        }
        fun clickButton(prefix: String) = runOnMainSync {
            fun find(view: View): View? {
                if (view is TextView && view.text.startsWith(prefix)) return view
                if (view is ViewGroup) for (index in 0 until view.childCount) find(view.getChildAt(index))?.let { return it }
                return null
            }
            checkNotNull(find(activity.window.decorView)).performClick()
        }
        clickButton("Debrid priority ·")
        waitForIdleSync(); Thread.sleep(350)
        val priority = nodes(checkNotNull(uiAutomation.rootInActiveWindow)).firstOrNull {
            it.text?.toString()?.contains("Offcloud first", ignoreCase = true) == true
        } ?: error("Offcloud-first priority choice is unavailable.")
        click(priority)
        waitForIdleSync(); Thread.sleep(350)
        check(DebridServices.priority(SecureStore(targetContext)).first() == "offcloud") { "Priority selection did not persist." }
        clickButton("Subtitle style ·")
        waitForIdleSync(); Thread.sleep(350)
        val style = checkNotNull(uiAutomation.rootInActiveWindow)
        val size = nodes(style).firstOrNull { it.text?.toString()?.startsWith("Size ·", ignoreCase = true) == true }
            ?: error("Subtitle size button is unavailable.")
        click(size)
        waitForIdleSync(); Thread.sleep(350)
        val labels = nodes(checkNotNull(uiAutomation.rootInActiveWindow)).mapNotNull { it.text?.toString() }
        check(labels.containsAll(listOf("Small", "Medium", "Large"))) { "Subtitle size labels are missing." }
        check(labels.none { '%' in it }) { "Subtitle size dialog still contains percentages." }
        val bitmap = checkNotNull(uiAutomation.takeScreenshot())
        java.io.File(checkNotNull(targetContext.externalCacheDir), "subtitle-size-labels.png").outputStream().use {
            check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
        }
        bitmap.recycle()
        sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
    }
}
