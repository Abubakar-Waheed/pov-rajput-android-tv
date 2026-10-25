package dev.pov.tv

import android.app.Instrumentation
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import dev.pov.tv.data.*
import java.util.concurrent.atomic.AtomicReference

/** Isolated provider fixtures only; restores the original encrypted configuration in finally. */
class SourceProvidersInstrumentation : Instrumentation() {
    private fun <T> main(task: () -> T): T {
        val result = AtomicReference<Result<T>>()
        runOnMainSync { result.set(runCatching(task)) }; return result.get().getOrThrow()
    }
    private fun nodes(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> = listOf(root) +
        (0 until root.childCount).flatMap { root.getChild(it)?.let(::nodes).orEmpty() }
    private fun awaitNode(label: String): AccessibilityNodeInfo {
        val until = SystemClock.elapsedRealtime() + 10000
        while (SystemClock.elapsedRealtime() < until) {
            uiAutomation.rootInActiveWindow?.let { root -> nodes(root).firstOrNull { it.text?.toString()?.equals(label, ignoreCase = true) == true }?.let { return it } }
            SystemClock.sleep(100)
        }
        error("Expected provider dialog control unavailable")
    }
    private fun click(label: String) {
        val bounds = Rect(); awaitNode(label).getBoundsInScreen(bounds)
        val down = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, bounds.centerX().toFloat(), bounds.centerY().toFloat(), 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try { check(uiAutomation.injectInputEvent(event, true)) } finally { event.recycle() }
        }
        waitForIdleSync(); SystemClock.sleep(200)
    }
    private fun fill(name: String, address: String) {
        val edits = nodes(checkNotNull(uiAutomation.rootInActiveWindow)).filter { it.className?.toString() == "android.widget.EditText" }
        check(edits.size == 2)
        edits.zip(listOf(name, address)).forEach { (node, value) ->
            check(node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
            }))
        }
        click("SAVE")
    }
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val report = Bundle(); var passed = false; var stage = "setup"; var app: MainActivity? = null
        val store = SecureStore(targetContext)
        val saved = listOf(SourceProviders.KEY, "sources.endpoint").associateWith { store.has(it) to store.get(it) }
        try {
            val config = SourceProviders(store)
            config.save(listOf(SourceProvider("fixture-a", "Fixture Alpha", "https://alpha.example"),
                SourceProvider("fixture-b", "Fixture Beta", "https://beta.example", false)))
            app = startActivitySync(Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            val activity = app
            main {
                MainActivity::class.java.getDeclaredField("publicPreview").apply { isAccessible = true }.setBoolean(activity, true)
                MainActivity::class.java.getDeclaredMethod("sourceProviders").apply { isAccessible = true }.invoke(activity)
            }
            stage = "toggle"
            click("Fixture Alpha · On  ›"); click("Turn off")
            check(!config.load().first().enabled)
            click("Fixture Beta · Off  ›"); click("Turn on")
            check(config.load()[1].enabled)
            report.putBoolean("independent_toggles_preserve_addresses", true)
            stage = "edit"
            click("Fixture Beta · On  ›"); click("Edit name and address")
            fill("Fixture Renamed", "https://beta.example/private-config/manifest.json")
            check(config.load()[1].name == "Fixture Renamed" && config.load()[1].address == "https://beta.example/private-config")
            stage = "add"
            click("Add source provider…"); fill("Fixture Gamma", "https://gamma.example")
            check(config.load().size == 3)
            report.putBoolean("add_edit_and_manifest_normalization", true)
            stage = "remove"
            click("Fixture Gamma · On  ›"); click("Remove"); click("CONTINUE")
            check(config.load().size == 2)
            click("CLOSE")
            report.putBoolean("confirmed_removal", true)
            passed = true
        } catch (_: Exception) { report.putString("failure_stage", stage) }
        finally {
            saved.forEach { (key, value) -> if (value.first) store.put(key, value.second) else store.remove(key) }
            app?.let { main { it.finish() } }
        }
        report.putBoolean("passed", passed); finish(if (passed) -1 else 0, report)
    }
}
