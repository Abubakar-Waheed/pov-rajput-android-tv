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
            report.putString("diagnostic_location", error.stackTrace.filter { it.className.startsWith("dev.pov.tv.DebridPriorityInstrumentation") }.joinToString { "${it.methodName}:${it.lineNumber}" })
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
        fun activeRoot(): AccessibilityNodeInfo {
            repeat(30) {
                uiAutomation.rootInActiveWindow?.let { root ->
                    if (root.packageName?.toString() == targetContext.packageName) return root
                }
                Thread.sleep(100)
            }
            error("POV settings window did not become accessible.")
        }
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
                if (view.isClickable && ((view as? TextView)?.text ?: view.contentDescription ?: "").startsWith(prefix)) return view
                if (view is ViewGroup) for (index in 0 until view.childCount) find(view.getChildAt(index))?.let { return it }
                return null
            }
            checkNotNull(find(activity.window.decorView)).performClick()
        }
        val store = SecureStore(targetContext)
        DebridServices.prefer(store, "offcloud")
        clickButton("Debrid priority")
        fun select(label: String) {
            waitForIdleSync(); Thread.sleep(350)
            click(nodes(activeRoot()).firstOrNull {
                it.text?.toString()?.equals(label, ignoreCase = true) == true
            } ?: error("Missing priority control: $label"))
        }
        select("2. TorBox")
        select("Move up")
        check(DebridServices.priority(store) == listOf("torbox", "offcloud"))
        select("2. Offcloud")
        select("Move up")
        check(DebridServices.priority(store) == listOf("offcloud", "torbox"))
        select("1. Offcloud")
        select("Move down")
        check(DebridServices.priority(store) == listOf("torbox", "offcloud"))
        select("1. TorBox")
        select("Move down")
        check(DebridServices.priority(store) == listOf("offcloud", "torbox"))
        waitForIdleSync(); Thread.sleep(350)
        val priorityLabels = nodes(activeRoot()).mapNotNull { it.text?.toString() }
        check(priorityLabels.containsAll(listOf("1. Offcloud", "2. TorBox")))
        val priorityBitmap = checkNotNull(uiAutomation.takeScreenshot())
        java.io.File(checkNotNull(targetContext.externalCacheDir), "debrid-priority.png").outputStream().use {
            check(priorityBitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
        }
        priorityBitmap.recycle()
        select("Done")
        clickButton("Subtitle style ·")
        waitForIdleSync(); Thread.sleep(350)
        val style = activeRoot()
        val size = nodes(style).firstOrNull { it.text?.toString()?.startsWith("Size ·", ignoreCase = true) == true }
            ?: error("Subtitle size button is unavailable.")
        click(size)
        waitForIdleSync(); Thread.sleep(350)
        val labels = nodes(activeRoot()).mapNotNull { it.text?.toString() }
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
