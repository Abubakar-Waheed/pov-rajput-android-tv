package dev.pov.tv.data

/** Connected accounts all participate in cache searches; order chooses the preferred resolver. */
object DebridServices {
    val IDS = listOf("offcloud", "torbox")
    fun name(id: String): String = when (id) {
        "offcloud" -> "Offcloud"
        "torbox" -> "TorBox"
        else -> error("Unknown debrid service.")
    }
    private fun credential(id: String) = if (id == "offcloud") "offcloud.key" else "torbox.token"

    internal fun parsePriority(value: String, legacy: String): List<String> {
        val saved = value.split(',').map { it.trim() }.filter { it in IDS }.distinct()
        val first = saved.ifEmpty { listOf(if (legacy == "offcloud") "offcloud" else "torbox") }
        return first + IDS.filter { it !in first }
    }
    fun priority(store: SecureStore) = parsePriority(store.get("playback.priority"), store.get("playback.service"))
    fun connected(store: SecureStore) = priority(store).filter { store.has(credential(it)) }
    fun prefer(store: SecureStore, first: String) {
        require(first in IDS)
        store.putAll(mapOf("playback.priority" to (listOf(first) + IDS.filter { it != first }).joinToString(","),
            "playback.service" to first))
    }
}
