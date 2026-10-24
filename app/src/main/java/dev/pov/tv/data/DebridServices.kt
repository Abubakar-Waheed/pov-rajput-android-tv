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

    internal fun parsePriority(value: String, legacy: String, available: List<String> = IDS): List<String> {
        val saved = value.split(',').map { it.trim() }.filter { it in available }.distinct()
        val first = saved.ifEmpty { listOf(if (legacy in available) legacy else if ("torbox" in available) "torbox" else available.first()) }
        return first + available.filter { it !in first }
    }
    fun priority(store: SecureStore) = parsePriority(store.get("playback.priority"), store.get("playback.service"))
    fun connected(store: SecureStore) = priority(store).filter { store.has(credential(it)) }
    /** Saves a complete permutation, retaining ranks when accounts disconnect. */
    fun setPriority(store: SecureStore, order: List<String>) {
        require(order.size == IDS.size && order.toSet() == IDS.toSet())
        store.putAll(mapOf("playback.priority" to order.joinToString(","), "playback.service" to order.first()))
    }
    internal fun moved(order: List<String>, index: Int, offset: Int): List<String> {
        require(index in order.indices && offset in listOf(-1, 1))
        val destination = index + offset
        if (destination !in order.indices) return order.toList()
        return order.toMutableList().apply { add(destination, removeAt(index)) }
    }
    fun move(store: SecureStore, index: Int, offset: Int) = setPriority(store, moved(priority(store), index, offset))
    fun prefer(store: SecureStore, first: String) {
        require(first in IDS)
        setPriority(store, listOf(first) + priority(store).filter { it != first })
    }
}
