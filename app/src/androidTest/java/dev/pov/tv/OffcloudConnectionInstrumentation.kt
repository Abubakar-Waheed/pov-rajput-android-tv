package dev.pov.tv

import android.app.Instrumentation
import android.os.Bundle
import dev.pov.tv.data.OffcloudApi
import dev.pov.tv.data.SecureStore
import dev.pov.tv.data.Net
import dev.pov.tv.data.SourceItem
import dev.pov.tv.data.PlaybackIdentity
import org.json.JSONObject

/** Explicit ADB diagnostic. Reports booleans/counts only; never credentials or library titles. */
class OffcloudConnectionInstrumentation : Instrumentation() {
    private var screenshots = false
    private var verifyCached = false
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        screenshots = arguments?.getString("capture_screenshots") == "true"
        verifyCached = arguments?.getString("verify_cached_resolution") == "true"
        start()
    }

    override fun onStart() {
        val report = Bundle()
        try {
            if (screenshots) { captureScreenshots(report); report.putBoolean("screenshots_saved", true); finish(-1, report); return }
            val store = SecureStore(targetContext)
            val api = OffcloudApi(store)
            report.putBoolean("has_saved_key", api.connected())
            check(api.connected()) { "Enter your Offcloud API key in Settings first." }
            report.putString("stage", "account")
            api.setApiKey(store.get("offcloud.key"))
            report.putBoolean("account_verified", true)
            report.putString("stage", "history")
            report.putInt("cloud_download_count", api.cloud().size)
            report.putBoolean("cloud_history_verified", true)
            report.putString("stage", "cache")
            api.cached(listOf("0".repeat(40)))
            report.putBoolean("cache_endpoint_verified", true)
            if (verifyCached) verifyCachedResolution(store, api, report)
            report.putString("stage", "complete")
            finish(-1, report)
        } catch (error: Exception) {
            report.putString("error_type", error.javaClass.simpleName)
            report.putString("cause_type", error.cause?.javaClass?.simpleName.orEmpty())
            report.putString("safe_error", uiErrorMessage(error))
            finish(0, report)
        }
    }

    /** Resolves links only; never creates/deletes a cloud transfer or starts playback. */
    private fun verifyCachedResolution(store: SecureStore, api: OffcloudApi, report: Bundle) {
        report.putString("stage", "cached_resolution")
        val before = api.cloud().map { it.id }.toSet()
        val saved = JSONObject(store.get("offcloud.transfers", "{}"))
        val hashes = saved.keys().asSequence().filter { it.matches(Regex("[a-f0-9]{40}")) }.take(40).toMutableList()
        if (hashes.isEmpty()) {
            // Public Big Buck Bunny catalog sources; inspect cache availability, not media bytes.
            val endpoint = store.get("sources.endpoint").trim().trimEnd('/').removeSuffix("/manifest.json")
            check(endpoint.startsWith("https://")) { "Configure your own source provider before running this diagnostic." }
            val streams = Net.json("$endpoint/stream/movie/tt1254207.json").getJSONArray("streams")
            for (index in 0 until streams.length()) {
                val hash = streams.optJSONObject(index)?.optString("infoHash").orEmpty().lowercase()
                if (hash.matches(Regex("[a-f0-9]{40}"))) hashes += hash
            }
        }
        val hash = hashes.distinct().take(80).chunked(40).asSequence()
            .flatMap { api.cached(it).asSequence() }.firstOrNull()
            ?: error("No cached diagnostic source is available. No cloud transfer was created.")
        val prepared = api.prepare(SourceItem("Cached video", "diagnostic", hash, cached = true),
            PlaybackIdentity("tt1254207", "movie", "Cached resolution diagnostic"))
        check(prepared.requestId == null && prepared.files.isNotEmpty()) { "Cached resolution did not return direct files." }
        report.putBoolean("cached_direct_files_verified", true)
        report.putInt("cached_file_count", prepared.files.size)
        report.putBoolean("cloud_history_unchanged", before == api.cloud().map { it.id }.toSet())
        check(report.getBoolean("cloud_history_unchanged")) { "Cloud history changed during the diagnostic." }
    }

    /** Public catalog/appearance only. Temporarily hide local history without changing saved data. */
    private fun captureScreenshots(report: Bundle) {
        report.putString("capture_stage", "launch")
        val activity = startActivitySync(android.content.Intent(targetContext, MainActivity::class.java)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        fun show(method: String) = runOnMainSync {
            report.putString("capture_stage", "show_$method")
            MainActivity::class.java.getDeclaredField("publicPreview").apply { isAccessible = true }.setBoolean(activity, true)
            MainActivity::class.java.getDeclaredMethod(method).apply { isAccessible = true }.invoke(activity)
        }
        fun capture(name: String) {
            report.putString("capture_stage", "capture_$name")
            waitForIdleSync()
            Thread.sleep(1_200)
            val bitmap = checkNotNull(uiAutomation.takeScreenshot()) { "TV screenshot capture failed." }
            val directory = checkNotNull(targetContext.externalCacheDir) { "Screenshot storage is unavailable." }
            directory.mkdirs()
            java.io.File(directory, "$name.png").outputStream().use {
                check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
            }
            bitmap.recycle()
        }
        show("home")
        val deadline = System.nanoTime() + 30_000_000_000L
        var ready = false
        while (!ready && System.nanoTime() < deadline) {
            runOnMainSync {
                val body = MainActivity::class.java.getDeclaredField("body").apply { isAccessible = true }.get(activity) as android.view.ViewGroup
                ready = body.childCount > 2
            }
            if (!ready) Thread.sleep(500)
        }
        check(ready) { "Public catalog did not finish loading for capture." }
        Thread.sleep(12_000)
        capture("public-home")
        show("settings"); capture("public-settings")
        show("appearance"); capture("public-appearance")
    }
}
