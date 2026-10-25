package dev.pov.tv

import android.app.Instrumentation
import android.os.Bundle
import dev.pov.tv.data.Net
import dev.pov.tv.data.SecureStore
import dev.pov.tv.data.TorBoxApi
import dev.pov.tv.data.SourceItem
import dev.pov.tv.data.PlaybackIdentity
import java.net.HttpURLConnection
import java.net.URL

/** Explicit ADB test of the production cached resolver using public Big Buck Bunny sources. */
class TorBoxCachedLinkInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val report = Bundle()
        fun stage(name: String) { report.putString("stage", name); sendStatus(1, report) }
        try {
            val api = TorBoxApi(SecureStore(targetContext))
            stage("account")
            api.account()
            report.putBoolean("account_verified", true)
            stage("history_before")
            val before = api.cloud("torrents").map { it.id }.toSet()
            stage("catalog")
            val endpoint = dev.pov.tv.data.SourceProviders(SecureStore(targetContext)).load().firstOrNull { it.enabled }?.let { dev.pov.tv.data.SourceProviders.endpoint(it.address) }.orEmpty()
            check(endpoint.startsWith("https://")) { "Configure your own source provider before running this diagnostic." }
            val streams = Net.json("$endpoint/stream/movie/tt1254207.json").getJSONArray("streams")
            val hashes = (0 until streams.length()).mapNotNull {
                streams.optJSONObject(it)?.optString("infoHash")?.lowercase()?.takeIf { hash -> hash.matches(Regex("[a-f0-9]{40}")) }
            }.distinct().take(80)
            stage("cache")
            val hash = api.cached(hashes).firstOrNull() ?: error("No cached public test source is available.")
            stage("production_resolver")
            val source = SourceItem("Big Buck Bunny", "diagnostic", hash, cached = true)
            val identity = PlaybackIdentity("tt1254207", "movie", "Big Buck Bunny")
            val choices = api.prepareCached(source, identity) { stage(it) }
            check(before == api.cloud("torrents").map { it.id }.toSet()) { "History changed while listing cached files." }
            val prepared = if (choices.resolvedUrls.isNotEmpty()) choices else {
                check(choices.files.isNotEmpty())
                report.putBoolean("pack_metadata_without_link_requests", true)
                val selected = choices.files.firstOrNull {
                    it.name.lowercase().replace(Regex("[^a-z]"), "").contains("bigbuckbunny")
                } ?: choices.files.first()
                api.prepareCached(source, identity, selected) { stage(it) }
            }
            check(prepared.files.isNotEmpty() && prepared.resolvedUrls.size == prepared.files.size)
            check(api.ownsTag(checkNotNull(prepared.accountTag)))
            report.putBoolean("production_cached_resolver_verified", true)
            stage("history_after")
            report.putBoolean("cloud_history_unchanged", before == api.cloud("torrents").map { it.id }.toSet())
            check(report.getBoolean("cloud_history_unchanged")) { "TorBox history changed during the test." }
            val url = prepared.resolvedUrls.values.first()
            stage("direct_link")
            report.putBoolean("direct_link_works", probe(url))
            Thread.sleep(2_000)
            report.putBoolean("direct_link_reopens", probe(url))
            check(report.getBoolean("direct_link_works") && report.getBoolean("direct_link_reopens")) { "The resolved link was unavailable." }
            finish(-1, report)
        } catch (error: Exception) {
            report.putString("safe_error", uiErrorMessage(error))
            report.putString("error_type", error.javaClass.simpleName)
            finish(0, report)
        }
    }
    private fun probe(address: String): Boolean {
        val url = URL(address)
        require(url.protocol == "https" && url.userInfo == null)
        val connection = url.openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 10_000; connection.readTimeout = 10_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Range", "bytes=0-0")
            connection.responseCode in setOf(200, 206) && connection.inputStream.use { it.read() >= 0 }
        } finally { connection.disconnect() }
    }
}
