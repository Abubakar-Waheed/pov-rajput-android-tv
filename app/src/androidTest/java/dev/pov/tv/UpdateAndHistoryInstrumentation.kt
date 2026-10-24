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
import dev.pov.tv.data.GitHubUpdates
import dev.pov.tv.data.PlaybackIdentity
import java.io.File
import java.security.MessageDigest

/** Synthetic history only; never uploads or reports account data. */
class UpdateAndHistoryInstrumentation : Instrumentation() {
    private var screenshotsOnly = false
    private var installerOnly = false
    private var published = false
    private val report = Bundle()
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); published = arguments?.getString("published_update") == "true"; installerOnly = arguments?.getString("installer_only") == "true"; screenshotsOnly = arguments?.getString("screenshots_only") == "true"; start() }
    override fun onStart() {
        var code = 0
        try {
            if (screenshotsOnly) verifyScreenshots() else if (installerOnly) verifyInstaller() else if (published) verifyPublished() else verifyLocal()
            code = -1
        } catch (error: Exception) {
            report.putString("error", error.javaClass.simpleName)
            report.putString("safe_message", if (error is IllegalStateException) error.message else "Diagnostic failed")
            report.putString("location", error.stackTrace.filter { it.className.startsWith(javaClass.name) }.joinToString { "${it.methodName}:${it.lineNumber}" })
        }
        finish(code, report)
    }
    private fun nodes(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> = buildList {
        add(root); for (i in 0 until root.childCount) root.getChild(i)?.let { addAll(nodes(it)) }
    }
    private fun find(label: String, timeout: Long = 5000): AccessibilityNodeInfo {
        val end = SystemClock.uptimeMillis() + timeout
        while (SystemClock.uptimeMillis() < end) {
            uiAutomation.rootInActiveWindow?.let { root -> nodes(root).firstOrNull {
                (it.text?.toString()?.equals(label, true) == true || (label.startsWith("No newer") && it.text?.toString()?.endsWith(label) == true))
            }?.let { return it } }
            Thread.sleep(150)
        }
        error("Missing UI control: $label")
    }
    private fun click(label: String) {
        val node = find(label)
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
        waitForIdleSync(); Thread.sleep(250)
    }
    private fun capture(name: String) {
        val bitmap = checkNotNull(uiAutomation.takeScreenshot())
        File(checkNotNull(targetContext.externalCacheDir), name).outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }
    private fun verifyLocal() {
        val main = startActivitySync(Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        val history = PlaybackStore(targetContext)
        val identity = PlaybackIdentity("pov-update-history-audit", "movie", "Continue watching sample")
        val saved = PlaybackStore.Recent("pov-history-audit", identity.title, identity, 60_000, 600_000)
        val themeField = MainActivity::class.java.getDeclaredField("theme").apply { isAccessible = true }
        val currentField = MainActivity::class.java.getDeclaredField("current").apply { isAccessible = true }
        val originalTheme = themeField.get(main)
        val originalCurrent = currentField.get(main)
        val body = MainActivity::class.java.getDeclaredField("body").apply { isAccessible = true }.get(main) as ViewGroup
        var refreshes = 0
        fun page() = MainActivity::class.java.getDeclaredMethod("page", String::class.java, String::class.java).apply { isAccessible = true }.invoke(main, "Continue watching test", "")
        fun tagged(view: View, prefix: String): View? {
            if (view.tag?.toString()?.startsWith(prefix) == true) return view
            if (view is ViewGroup) for (i in 0 until view.childCount) tagged(view.getChildAt(i), prefix)?.let { return it }
            return null
        }
        try {
            for (theme in ThemeSpec.entries) {
                history.show(saved.key, identity)
                var tile: View? = null
                runOnMainSync {
                    themeField.set(main, theme); page()
                    currentField.set(main, { refreshes++; body.removeAllViews() } as () -> Unit)
                    if (theme == ThemeSpec.DASHBOARD) {
                        MainActivity::class.java.getDeclaredMethod("dashboard", List::class.java, List::class.java, List::class.java)
                            .apply { isAccessible = true }.invoke(main, listOf(saved), emptyList<Any>(), emptyList<Any>())
                    } else MainActivity::class.java.getDeclaredMethod("resumeRow", List::class.java)
                        .apply { isAccessible = true }.invoke(main, listOf(saved))
                    tile = checkNotNull(tagged(body, if (theme == ThemeSpec.DASHBOARD) "dashboard:pov-history-audit" else "resume:pov-history-audit"))
                    tile!!.requestFocus()
                }
                waitForIdleSync(); Thread.sleep(250)
                // Exercise the remote's long-press event, rather than invoking its listener directly.
                val time = SystemClock.uptimeMillis()
                sendKeySync(KeyEvent(time, time, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 0, 0,
                    KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_DPAD))
                Thread.sleep(750)
                sendKeySync(KeyEvent(time, time + 750, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 1, 0,
                    KeyCharacterMap.VIRTUAL_KEYBOARD, 0, KeyEvent.FLAG_LONG_PRESS, InputDevice.SOURCE_DPAD))
                sendKeySync(KeyEvent(time, time + 800, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER, 0, 0,
                    KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_DPAD))
                find("Remove from Continue watching")
                if (theme == ThemeSpec.CINEMATIC) capture("continue-watching-remove.png")
                click("Remove from Continue watching")
                check(history.isHidden(saved.key, identity)) { "Removed history item did not stay hidden." }
            }
            check(refreshes == ThemeSpec.entries.size)
            report.putBoolean("long_press_all_five_themes", true)
        } finally {
            history.show(saved.key, identity)
            runOnMainSync { themeField.set(main, originalTheme); currentField.set(main, originalCurrent) }
        }
        val previewField = MainActivity::class.java.getDeclaredField("publicPreview").apply { isAccessible = true }
        fun mainCall(name: String) = MainActivity::class.java.getDeclaredMethod(name).apply { isAccessible = true }.invoke(main)
        try {
            runOnMainSync { previewField.setBoolean(main, true); mainCall("updateBadge"); mainCall("home") }
            val end = SystemClock.uptimeMillis() + 30000
            var rendered = false
            while (!rendered && SystemClock.uptimeMillis() < end) {
                runOnMainSync { rendered = tagged(body, "featured-hero") != null }
                Thread.sleep(250)
            }
            check(rendered) { "Public catalog preview did not load." }
            Thread.sleep(4500); capture("home-public.png")
            runOnMainSync { mainCall("settings") }; waitForIdleSync(); Thread.sleep(350)
            capture("settings-public.png")
            runOnMainSync {
                fun themeButton(view: View): View? {
                    if (view is TextView && view.text.startsWith("Themes ·")) return view
                    if (view is ViewGroup) for (i in 0 until view.childCount) themeButton(view.getChildAt(i))?.let { return it }
                    return null
                }
                checkNotNull(themeButton(body)).performClick()
            }
            waitForIdleSync(); Thread.sleep(350); capture("appearance-public.png")
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        } finally { runOnMainSync { previewField.setBoolean(main, false); mainCall("updateBadge") } }
        val updates = GitHubUpdates(targetContext)
        val fixture = File(checkNotNull(targetContext.externalCacheDir), "release-candidate.apk")
        if (fixture.exists()) {
            fun release(file: File, sha: String = checksum(file)) = GitHubUpdates.Release(
                GitHubUpdates.Candidate("v0.2.6-rajput", "", "", "", file.length()), 12, "0.2.6-rajput", sha)
            updates.validate(fixture, release(fixture))
            fun reject(file: File, release: GitHubUpdates.Release) {
                try { updates.validate(file, release); error("Unsafe APK accepted") }
                catch (error: IllegalStateException) { check(error.message != "Unsafe APK accepted") }
            }
            reject(fixture, release(fixture, "0".repeat(64)))
            reject(fixture, release(fixture).copy(code = 999))
            val otherSigner = File(checkNotNull(targetContext.externalCacheDir), "wrong-signer.apk")
            check(otherSigner.exists()); reject(otherSigner, release(otherSigner))
            report.putBoolean("apk_checksum_version_and_signer_checked", true)
        }
        startActivitySync(Intent(targetContext, UpdateActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        find("No newer compatible release is available.", 60000)
        report.putBoolean("github_check_before_publication", true)
        capture("updates-current.png")
    }
    private fun checksum(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val buffer = ByteArray(65536); while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) } }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    private fun verifyScreenshots() {
        val main = startActivitySync(Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        fun call(name: String) = MainActivity::class.java.getDeclaredMethod(name).apply { isAccessible = true }.invoke(main)
        val preview = MainActivity::class.java.getDeclaredField("publicPreview").apply { isAccessible = true }
        val body = MainActivity::class.java.getDeclaredField("body").apply { isAccessible = true }.get(main) as ViewGroup
        fun findView(view: View, predicate: (View) -> Boolean): View? {
            if (predicate(view)) return view
            if (view is ViewGroup) for (i in 0 until view.childCount) findView(view.getChildAt(i), predicate)?.let { return it }
            return null
        }
        try {
            runOnMainSync { preview.setBoolean(main, true); call("updateBadge"); call("home") }
            var rendered = false
            repeat(120) {
                if (!rendered) { runOnMainSync { rendered = findView(body) { it.tag == "featured-hero" } != null }; Thread.sleep(250) }
            }
            check(rendered); Thread.sleep(5000); capture("home-public.png")
            runOnMainSync { call("settings") }; waitForIdleSync(); Thread.sleep(350); capture("settings-public.png")
            runOnMainSync { call("debridPriority") }; waitForIdleSync(); Thread.sleep(350); capture("debrid-priority.png")
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            runOnMainSync { checkNotNull(findView(body) { it is TextView && it.text.startsWith("Themes") }).performClick() }
            waitForIdleSync(); Thread.sleep(350); capture("appearance-public.png")
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            report.putBoolean("public_screenshots_captured", true)
        } finally { runOnMainSync { preview.setBoolean(main, false); call("updateBadge"); call("home") } }
    }
    private fun verifyInstaller() {
        val activity = startActivitySync(Intent(targetContext, UpdateActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as UpdateActivity
        find("No newer compatible release is available.", 60000)
        val fixture = File(checkNotNull(targetContext.externalCacheDir), "release-candidate.apk")
        val updates = GitHubUpdates(targetContext)
        updates.apkFile.parentFile!!.mkdirs()
        fixture.copyTo(updates.apkFile, overwrite = true)
        val release = GitHubUpdates.Release(GitHubUpdates.Candidate("v0.2.6-rajput", "", "", "", fixture.length()),
            12, "0.2.6-rajput", checksum(fixture))
        runOnMainSync {
            UpdateActivity::class.java.getDeclaredField("release").apply { isAccessible = true }.set(activity, release)
            UpdateActivity::class.java.getDeclaredField("ready").apply { isAccessible = true }.setBoolean(activity, true)
            UpdateActivity::class.java.getDeclaredMethod("install").apply { isAccessible = true }.invoke(activity)
        }
        if (!targetContext.packageManager.canRequestPackageInstalls()) {
            find("Allow app updates"); capture("update-install-permission.png"); click("Later")
            report.putBoolean("install_permission_prompt_checked", true)
        } else {
            val end = SystemClock.uptimeMillis() + 15000
            while (SystemClock.uptimeMillis() < end) {
                val root = uiAutomation.rootInActiveWindow
                if (root != null && root.packageName?.toString() != targetContext.packageName &&
                    nodes(root).any { it.text?.toString()?.equals("Install", true) == true }) {
                    capture("update-installer-local.png")
                    report.putBoolean("android_installer_file_grant_checked", true); return
                }
                Thread.sleep(250)
            }
            error("Android installer did not open.")
        }
    }
    private fun verifyPublished() {
        startActivitySync(Intent(targetContext, UpdateActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        find("Download and install", 60000)
        capture("update-available.png")
        click("Download and install")
        // Wait for the user-visible Android installer; do not confirm from inside the dying test process.
        val end = SystemClock.uptimeMillis() + 180000
        while (SystemClock.uptimeMillis() < end) {
            val root = uiAutomation.rootInActiveWindow
            if (root != null && root.packageName?.toString() != targetContext.packageName &&
                nodes(root).any { it.text?.toString()?.equals("Install", true) == true }) {
                capture("update-install-confirmation.png")
                report.putBoolean("github_download_verified_installer_opened", true); return
            }
            Thread.sleep(500)
        }
        error("Update did not reach Android install confirmation.")
    }
}
