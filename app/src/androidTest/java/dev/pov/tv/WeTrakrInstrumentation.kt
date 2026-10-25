package dev.pov.tv

import android.app.Instrumentation
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import dev.pov.tv.data.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference

/** Offline protocol fixtures in the real TV UI. No account tokens are read or replaced. */
class WeTrakrInstrumentation : Instrumentation() {
    private fun <T> onMain(task: () -> T): T {
        val result = AtomicReference<Result<T>>()
        runOnMainSync { result.set(runCatching(task)) }
        return result.get().getOrThrow()
    }
    private fun views(root: View): List<View> = buildList { add(root); if (root is ViewGroup) for (i in 0 until root.childCount) addAll(views(root.getChildAt(i))) }
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val report = Bundle(); var passed = false; var stage = "start"; var activity: MainActivity? = null
        val store = SecureStore(targetContext)
        val hadSelection = store.has("tracking.library"); val originalSelection = store.get("tracking.library")
        try {
            val memory = mutableMapOf("client_id" to "fixture-client-1234567890", "access_token" to "fixture-access", "refresh_token" to "fixture-refresh",
                "expires_at" to (System.currentTimeMillis() + 600000).toString(), "account_revision" to "fixture-tv-account")
            fun media(type: String, id: String, title: String) = JSONObject().put("type", type).put("title", title).put("ids", JSONObject().put("imdb", id))
            val movie = media("movie", "tt1234567", "Example Movie")
            val show = media("show", "tt7654321", "Example Show")
            val client = WeTrakrApi(object : WeTrakrCredentials {
                override fun get(key: String) = memory[key].orEmpty()
                override fun put(values: Map<String, String>) { memory.putAll(values) }
            }, { path, _, _, _ -> when {
                path == "/oauth/device/code" -> """{"device_code":"fixture-device-code","user_code":"ABC123","verification_url":"https://wetrakr.com/activate","expires_in":600,"interval":5}"""
                path == "/oauth/device/token" -> throw ApiException(400, "Pending", "authorization_pending")
                path.startsWith("/sync/tracking/playing/movies") -> JSONArray().put(JSONObject(movie.toString()).put("playback", JSONObject().put("progress_percent", 42))).toString()
                path.startsWith("/sync/tracking/playing/episodes") -> "[]"
                path.startsWith("/sync/tracking/planning/movies") -> JSONArray().put(movie).toString()
                path.startsWith("/sync/tracking/planning/shows") -> JSONArray().put(show).toString()
                path.startsWith("/sync/tracking/watching/shows") -> JSONArray().put(JSONObject(show.toString()).put("next_watching_episode", JSONObject().put("season_number", 1).put("number", 3))).toString()
                path.startsWith("/sync/lists/fixture-list/items") -> JSONArray().put(movie).toString()
                path.startsWith("/sync/lists") -> """[{"id":"fixture-list","name":"Example list"}]"""
                else -> error("Unexpected offline fixture request")
            } })
            activity = startActivitySync(Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            val main = activity
            fun field(name: String) = MainActivity::class.java.getDeclaredField(name).apply { isAccessible = true }
            fun method(name: String) = MainActivity::class.java.getDeclaredMethod(name).apply { isAccessible = true }
            val theme = field("theme"); val preview = field("publicPreview"); val weField = field("wetrakr")
            val previousTheme = onMain { theme.get(main) }; val previousPreview = onMain { preview.getBoolean(main) }; val previousClient = onMain { weField.get(main) }
            fun captions() = onMain { views(main.window.decorView).filterIsInstance<TextView>().map { it.text.toString() } }
            fun await(test: () -> Boolean) {
                val deadline = SystemClock.elapsedRealtime() + 10000
                while (SystemClock.elapsedRealtime() < deadline) { if (test()) return; SystemClock.sleep(100) }
                error("Offline TV check timed out")
            }
            try {
                store.put("tracking.library", "wetrakr")
                onMain { weField.set(main, client); preview.setBoolean(main, true) }
                for (spec in ThemeSpec.entries) {
                    stage = "tracking_page_${spec.name}"
                    onMain { theme.set(main, spec); method("shell").invoke(main); method("traktPage").invoke(main) }
                    await { captions().contains("Movie watchlist") && captions().contains("TV watchlist") && captions().contains("Up next") }
                    check(captions().contains("My WeTrakr")); check(captions().contains("Continue watching"))
                    onMain {
                        @Suppress("UNCHECKED_CAST") val navigation = field("navigationViews").get(main) as Map<String, View>
                        val settings = checkNotNull(navigation["settings"]); val bounds = Rect()
                        check(settings.getGlobalVisibleRect(bounds) && bounds.width() > 0 && bounds.height() > 0)
                    }
                    onMain { method("traktLists").invoke(main) }
                    await { captions().contains("Example list") }
                }
                report.putInt("themes_with_tracking_lists_and_visible_settings", ThemeSpec.entries.size)
                stage = "device_login_dialog"
                onMain { method("startWeTrakr").invoke(main) }
                // The activation code is synthetic; inspect only our public test dialog.
                await { onMain {
                    val dialog = field("authorization").get(main) as? DeviceAuthorizationDialog
                    dialog != null && (DeviceAuthorizationDialog::class.java.getDeclaredField("code").apply { isAccessible = true }.get(dialog) as TextView).text.toString() == "ABC123"
                } }
                sendKeyDownUpSync(KeyEvent.KEYCODE_BACK); waitForIdleSync()
                check(memory["access_token"] == "fixture-access")
                report.putBoolean("device_code_dialog_and_cancel_work", true)
                passed = true
            } finally { onMain { weField.set(main, previousClient); theme.set(main, previousTheme); preview.setBoolean(main, previousPreview) } }
        } catch (error: Throwable) { report.putString("error_type", error.javaClass.simpleName); report.putString("stage", stage) }
        finally {
            if (hadSelection) store.put("tracking.library", originalSelection) else store.remove("tracking.library")
            onMain { activity?.finish() }
            report.putBoolean("passed", passed); finish(if (passed) -1 else 0, report)
        }
    }
}
