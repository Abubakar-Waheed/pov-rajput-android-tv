package dev.pov.tv.data

/** A selected library provider is captured when a page/action is created. */
interface TrackingClient {
    val name: String
    fun connected(): Boolean
    fun accountRevision(): String
    fun watchlist(type: String): List<MediaItemData>
    fun continueWatching(): List<Pair<PlaybackIdentity, Double>>
    fun nextEpisodes(): List<PlaybackIdentity>
    fun lists(): List<Pair<String, String>>
    fun listItems(id: String): List<MediaItemData>
    fun setWatchlist(identity: PlaybackIdentity, add: Boolean)
    fun markWatched(identity: PlaybackIdentity)
    fun progressForAccount(identity: PlaybackIdentity, percent: Double, action: String, revision: String): Boolean
}
