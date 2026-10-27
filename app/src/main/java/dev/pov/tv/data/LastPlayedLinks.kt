package dev.pov.tv.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.security.MessageDigest

/** Signed playback URLs are credentials: retain a bounded, encrypted, account-scoped cache. */
class LastPlayedLinks internal constructor(
    private val get: (String) -> String,
    private val put: (String, String) -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
) {
    constructor(store: SecureStore) : this({ store.get(it) }, store::put)
    data class Link(val identity: PlaybackIdentity, val url: String, val key: String, val service: String?, val account: String, val savedAt: Long)
    private fun token(identity: PlaybackIdentity) = "${identity.type}:${identity.imdbId}:${identity.season}:${identity.episode}"
    private fun account(service: String?): String {
        val credential = when (service) { null -> return "direct"; in DebridServices.IDS -> get(DebridServices.credential(service)); else -> return "" }
        if (credential.isBlank()) return ""
        return MessageDigest.getInstance("SHA-256").digest(credential.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }
    }
    fun scope(service: String?): String = account(service)
    private fun entries(): List<Link> = runCatching {
        val array = JSONArray(get(STORAGE).ifBlank { "[]" })
        (0 until array.length()).map { index -> array.getJSONObject(index).let {
            Link(PlaybackIdentity.fromJson(it.getJSONObject("identity")), it.getString("url"), it.getString("key"),
                it.optString("service").ifBlank { null }, it.getString("account"), it.getLong("savedAt"))
        } }.filter { it.savedAt <= now() && now() - it.savedAt < DAY && secure(it.url) }
    }.getOrDefault(emptyList())
    fun find(identity: PlaybackIdentity): Link? = synchronized(lock) {
        entries().firstOrNull { token(it.identity) == token(identity) && it.account.isNotBlank() && it.account == account(it.service) }
    }
    fun save(identity: PlaybackIdentity, url: String, key: String, service: String?, expectedAccount: String? = null) = synchronized(lock) {
        if (!secure(url) || !identity.imdbId.matches(Regex("tt[0-9]{5,12}"))) return@synchronized
        val scope = account(service).takeIf { it.isNotBlank() && (expectedAccount == null || expectedAccount == it) } ?: return@synchronized
        write(listOf(Link(identity, url, key, service, scope, now())) + entries().filterNot { token(it.identity) == token(identity) })
    }
    fun remove(identity: PlaybackIdentity) = synchronized(lock) { write(entries().filterNot { token(it.identity) == token(identity) }) }
    private fun write(items: List<Link>) {
        val array = JSONArray()
        items.take(40).forEach { array.put(JSONObject().put("identity", it.identity.toJson()).put("url", it.url).put("key", it.key)
            .put("service", it.service.orEmpty()).put("account", it.account).put("savedAt", it.savedAt)) }
        put(STORAGE, array.toString())
    }
    private fun secure(value: String) = runCatching { URI(value).let { it.scheme == "https" && !it.host.isNullOrBlank() && it.userInfo == null } }.getOrDefault(false)
    companion object { private const val STORAGE = "playback.last_links"; private const val DAY = 24 * 60 * 60 * 1000L; private val lock = Any() }
}
