package dev.pov.tv.data

import java.net.URI
import java.util.Locale
import org.json.JSONObject

/** Stremio sources with combined debrid caches and preference-ordered cloud resolution. */
class SourceResolver internal constructor(
    private val sourceEndpoint: () -> String,
    private val torbox: SourceTorBox,
    private val publicJson: (String) -> JSONObject = { Net.json(it) },
    private val monotonicNanos: () -> Long = System::nanoTime,
    private val pause: (Long) -> Unit = Thread::sleep,
    private val offcloud: () -> OffcloudApi? = { null },
    private val serviceOrder: (() -> List<String>)? = null
) {
    constructor(store: SecureStore, torbox: TorBoxApi) : this({ store.get("sources.endpoint") }, object : SourceTorBox {
        override fun cached(hashes: List<String>) = torbox.cached(hashes)
        override fun addMagnet(hash: String) = torbox.addMagnet(hash)
        override fun folder(kind: String, id: Long) = torbox.folder(kind, id)
        override fun streamUrl(kind: String, folderId: Long, fileId: Long) = torbox.streamUrl(kind, folderId, fileId)
        override fun prepareCached(source: SourceItem, identity: PlaybackIdentity) = torbox.prepareCached(source, identity)
    }, offcloud = { OffcloudApi(store) }, serviceOrder = { DebridServices.connected(store) })
    /** Fetching choices is read-only: cache checks never create a transfer. */
    fun sources(identity: PlaybackIdentity): List<SourceItem> {
        validateIdentity(identity)
        val endpoint = endpoint(sourceEndpoint())
        val id = if (identity.type == "series") {
            "${identity.imdbId}:${identity.season}:${identity.episode}"
        } else identity.imdbId
        val payload = try {
            publicJson("$endpoint/stream/${identity.type}/$id.json")
        } catch (_: Exception) {
            throw IllegalStateException("The source provider could not be reached. Try again or check the source endpoint in Settings.")
        }
        val streams = payload.optJSONArray("streams") ?: return emptyList()
        val provider = URI(endpoint).host
        val choices = (0 until minOf(streams.length(), 300)).mapNotNull { index ->
            val stream = streams.optJSONObject(index) ?: return@mapNotNull null
            // Header-dependent URLs cannot be represented by SourceItem yet; never silently discard headers.
            if (stream.optJSONObject("behaviorHints")?.optJSONObject("proxyHeaders") != null) return@mapNotNull null
            val hash = stream.optString("infoHash").lowercase(Locale.ROOT).takeIf(HASH::matches).orEmpty()
            val direct = stream.optString("url").takeIf(::isHttps).orEmpty()
            if (hash.isBlank() && direct.isBlank()) return@mapNotNull null
            val description = stream.optString("description").ifBlank { stream.optString("title") }
            val filename = stream.optJSONObject("behaviorHints")?.optString("filename").orEmpty()
            val title = filename.ifBlank { description.lineSequence().firstOrNull().orEmpty() }
                .ifBlank { stream.optString("name").ifBlank { "Video source" } }.take(500)
            if (identity.type == "series" && EpisodeFilename.isExplicitMismatch(title, identity.season, identity.episode)) {
                return@mapNotNull null
            }
            val fileIndex = if (stream.has("fileIdx") && !stream.isNull("fileIdx")) {
                stream.optInt("fileIdx", -1).takeIf { it >= 0 }
            } else null
            SourceItem(
                title = title,
                provider = provider,
                infoHash = hash,
                fileIndex = fileIndex,
                directUrl = direct,
                detail = listOf(stream.optString("name"), description.lineSequence().drop(1).joinToString(" · "))
                    .filter { it.isNotBlank() }.joinToString(" · ").take(800)
            )
        }.distinctBy { Triple(it.infoHash, it.fileIndex, it.directUrl) }
        val availability = checkCaches(activeServices(), choices.map { it.infoHash }.filter { it.isNotBlank() }.distinct())
        return choices.map { source ->
            val cachedOn = availability.filter { source.infoHash in it.hashes }.map { it.service }
            val unavailable = if (source.infoHash.isNotBlank()) availability.filter { it.failed }
                .map { "${DebridServices.name(it.service)} cache unavailable" } else emptyList()
            source.copy(cached = cachedOn.isNotEmpty(), cachedServices = cachedOn,
                detail = (listOf(source.detail) + unavailable).filter { it.isNotBlank() }.joinToString(" · "))
        }.sortedByDescending { it.cached }
    }

    private data class CacheResult(val service: String, val hashes: Set<String>, val failed: Boolean)

    private fun activeServices(): List<String> = (serviceOrder?.invoke()
        ?: if (offcloud() != null) listOf("offcloud") else listOf("torbox"))
        .filter { it in DebridServices.IDS }.distinct()

    /** Independent providers run concurrently; a failed cache API cannot hide another provider's hits. */
    private fun checkCaches(services: List<String>, hashes: List<String>): List<CacheResult> {
        if (hashes.isEmpty()) return services.map { CacheResult(it, emptySet(), false) }
        fun probe(service: String): CacheResult {
            val hits = mutableSetOf<String>()
            return try {
                hashes.chunked(40).forEach { batch ->
                    if (Thread.currentThread().isInterrupted) throw InterruptedException("Source search cancelled.")
                    val found = if (service == "offcloud") checkNotNull(offcloud()) { "Connect Offcloud in Settings." }.cached(batch)
                        else torbox.cached(batch)
                    hits += found.map { it.lowercase(Locale.ROOT) }.filter { it in batch }
                }
                CacheResult(service, hits, false)
            } catch (error: InterruptedException) { throw error }
            catch (_: Exception) { CacheResult(service, hits, true) }
        }
        if (services.size < 2) return services.map(::probe)
        val executor = java.util.concurrent.Executors.newFixedThreadPool(services.size)
        try {
            val tasks = services.map { service -> executor.submit(java.util.concurrent.Callable { probe(service) }) }
            return tasks.map { task ->
                try { task.get() }
                catch (error: java.util.concurrent.ExecutionException) { throw (error.cause as? Exception ?: error) }
            }
        } finally { executor.shutdownNow() }
    }

    /** Cached providers are tried in priority order; only an explicitly chosen uncached source may create a download. */
    fun prepare(source: SourceItem, identity: PlaybackIdentity): PreparedSource {
        validateIdentity(identity)
        require(identity.type != "series" || !EpisodeFilename.isExplicitMismatch(source.title, identity.season, identity.episode)) {
            "This source identifies a different episode. Choose another source."
        }
        if (source.directUrl.isNotBlank()) {
            require(isHttps(source.directUrl)) { "Only HTTPS video sources are supported." }
            return PreparedSource.Direct(source.directUrl, identity)
        }
        val hash = source.infoHash.lowercase(Locale.ROOT)
        require(HASH.matches(hash)) { "The selected source has no valid torrent hash." }
        val services = activeServices()
        check(services.isNotEmpty()) { "Connect Offcloud or TorBox in Settings first." }
        val availability = checkCaches(services, listOf(hash))
        val cachedOn = availability.filter { hash in it.hashes }.map { it.service }
        var failure: Exception? = null
        for (service in cachedOn) {
            if (Thread.currentThread().isInterrupted) throw InterruptedException("Source preparation cancelled.")
            try { return prepareWith(service, source.copy(cached = true), identity) }
            catch (error: InterruptedException) { throw error }
            catch (error: java.util.concurrent.CancellationException) { throw error }
            catch (error: Exception) { if (failure == null) failure = error }
        }
        failure?.let { throw it }
        check(!source.cached) { "This source is no longer cached on your connected services. Refresh sources before starting a cloud download." }
        check(availability.none { it.failed }) { "Cache availability could not be verified. Try again before starting a download." }
        val downloadService = source.downloadService ?: services.first()
        check(downloadService in services) { "The selected download account changed. Refresh sources." }
        return prepareWith(downloadService, source.copy(cached = false), identity)
    }

    private fun prepareWith(service: String, source: SourceItem, identity: PlaybackIdentity): PreparedSource =
        if (service == "offcloud") checkNotNull(offcloud()) { "Connect Offcloud in Settings first." }.prepare(source, identity)
        else prepareTorBox(source, identity)

    private fun prepareTorBox(source: SourceItem, identity: PlaybackIdentity): PreparedSource.Files {
        val hash = source.infoHash.lowercase(Locale.ROOT)
        require(HASH.matches(hash)) { "The selected source has no valid torrent hash." }
        if (hash in torbox.cached(listOf(hash))) return torbox.prepareCached(source, identity)
        check(!source.cached) { "This source is no longer cached. Refresh sources before starting a cloud download." }
        val transfer = torbox.addMagnet(hash)
        // Cached transfers normally expose files immediately. Uncached transfers stay in TorBox;
        // a bounded wait keeps the UI from spinning indefinitely and never deletes user transfers.
        val deadline = monotonicNanos() + 20_000_000_000L
        do {
            if (Thread.currentThread().isInterrupted) throw InterruptedException("Source preparation was cancelled.")
            val folder = try {
                torbox.folder("torrents", transfer)
            } catch (error: ApiException) {
                // A freshly accepted transfer may not have appeared in the library yet.
                if (error.code == 404 || error.errorKey == "ITEM_NOT_FOUND") null else throw error
            }
            if (folder != null && folder.ready && folder.files.isNotEmpty()) {
                check(folder.id == transfer && folder.kind == "torrents") { "TorBox returned a different transfer. Open the selected file in your cloud library." }
                val videos = folder.files.map { VideoCandidate(it.id, it.name, it.size) }
                val eligible = EpisodeFilename.choices(videos, source.title, identity)
                val files = eligible.map { CloudFile(it.id, it.name, it.size) }
                val preferred = EpisodeFilename.preferredId(eligible, source.title) ?: files.singleOrNull()?.id
                return PreparedSource.Files(folder, files, identity, preferred)
            }
            val remaining = deadline - monotonicNanos()
            if (remaining <= 0) break
            pause(minOf(1_500, (remaining + 999_999) / 1_000_000))
        } while (!Thread.currentThread().isInterrupted)
        throw IllegalStateException("This transfer is not ready to play yet. It remains in your TorBox library; try again once TorBox has finished processing it.")
    }

    /** Compatibility for callers that can handle only a single, unambiguous stream. */
    fun resolve(source: SourceItem, identity: PlaybackIdentity): String = when (val prepared = prepare(source, identity)) {
        is PreparedSource.Direct -> prepared.url
        is PreparedSource.OffcloudFiles -> prepared.files.singleOrNull()?.url
            ?: throw IllegalStateException("Choose a video file before playing.")
        is PreparedSource.Files -> {
            val file = prepared.automaticFile ?: throw IllegalStateException("This source contains multiple video versions. Choose a video file before playing.")
            val url = if (prepared.accountTag != null) prepared.resolvedUrls.getValue(file.id)
                else torbox.streamUrl(prepared.folder.kind, prepared.folder.id, file.id)
            require(isHttps(url)) { "TorBox did not return a supported HTTPS playback link." }
            url
        }
    }

    private fun validateIdentity(identity: PlaybackIdentity) {
        require(IMDB_ID.matches(identity.imdbId)) { "This title has no supported IMDb identifier." }
        require(identity.type == "movie" || identity.type == "series") { "This media type is not supported." }
        require(identity.type != "series" || (identity.season >= 0 && identity.episode > 0)) {
            "Choose an episode before searching for sources."
        }
    }

    private fun endpoint(value: String): String {
        val candidate = value.trim().ifEmpty { DEFAULT_ENDPOINT }.removeSuffix("/").removeSuffix("/manifest.json")
        require(candidate.length <= 4_096 && isHttps(candidate)) { "Enter a valid HTTPS Stremio add-on endpoint." }
        val uri = URI(candidate)
        require(uri.query == null && uri.fragment == null) { "Use the add-on base URL or manifest URL, without a query or fragment." }
        return candidate.trimEnd('/')
    }

    private fun isHttps(value: String): Boolean = try {
        val uri = URI(value)
        uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() && uri.userInfo == null
    } catch (_: Exception) { false }

    private companion object {
        const val DEFAULT_ENDPOINT = "https://torrentio.strem.fun"
        val HASH = Regex("[a-f0-9]{40}")
        val IMDB_ID = Regex("tt[0-9]{5,12}")
    }
}

internal data class VideoCandidate(val id: Long, val name: String, val size: Long)

/** The adapter never treats a provider's zero-based file index as a TorBox file ID. */
internal interface SourceTorBox {
    fun cached(hashes: List<String>): Set<String>
    fun addMagnet(hash: String): Long
    fun folder(kind: String, id: Long): CloudFolder
    fun streamUrl(kind: String, folderId: Long, fileId: Long): String
    fun prepareCached(source: SourceItem, identity: PlaybackIdentity): PreparedSource.Files =
        error("Cached TorBox resolution is unavailable.")
}

/** File selection intentionally does not equate a provider's zero-based fileIdx with a TorBox file id. */
internal object EpisodeFilename {
    private val videoExtension = Regex("(?i)\\.(mkv|mp4|m4v|avi|mov|ts|m2ts|mts|webm|mpg|mpeg|wmv|flv)$")
    private val sample = Regex("(?i)(?:^|[/\\\\ ._-])(?:sample|featurette|proof)(?:[/\\\\ ._-]|$)|(?:^|[/\\\\])(?:extras|trailers)[/\\\\]|[ ._-]trailer\\.[a-z0-9]+$")
    private val seasonEpisode = Regex("(?i)(?:^|[^a-z0-9])s(\\d{1,2})[ ._-]*e(\\d{1,3})((?:(?:[ ._-]*(?:-|to)[ ._-]*e?\\d{1,3})|(?:[ ._-]*e\\d{1,3}))*)")
    private val crossEpisode = Regex("(?i)(?:^|[^a-z0-9])(\\d{1,2})x(\\d{1,3})(?:[ ._-]*-[ ._-]*(\\d{1,3}))?(?=$|[^0-9])")
    private val tailEpisode = Regex("(?i)(?:[ ._-]*(?:-|to)[ ._-]*e?(\\d{1,3}))|(?:[ ._-]*e(\\d{1,3}))")

    fun isExplicitMismatch(name: String, season: Int, episode: Int): Boolean {
        val markers = episodes(name)
        return markers.isNotEmpty() && (season to episode) !in markers
    }

    /** Preserve every eligible edition, in service order; choice belongs to the user. */
    fun choices(files: List<VideoCandidate>, sourceTitle: String, identity: PlaybackIdentity): List<VideoCandidate> {
        val videos = files.filter { it.id >= 0 && videoExtension.containsMatchIn(it.name) && !sample.containsMatchIn(it.name) }
        if (videos.isEmpty()) throw IllegalStateException("This transfer does not contain a playable video file yet.")
        if (videos.groupBy { it.id }.any { (_, entries) -> entries.distinct().size > 1 }) {
            throw IllegalStateException("TorBox returned conflicting video file identifiers. Refresh the cloud folder before selecting a file.")
        }
        val unique = videos.distinctBy { it.id }
        if (identity.type == "movie") return unique
        val matches = unique.filter { (identity.season to identity.episode) in episodes(it.name) }
        if (matches.isNotEmpty()) return matches
        val exact = unique.filter { filenameKey(it.name) == filenameKey(sourceTitle) && !isExplicitMismatch(it.name, identity.season, identity.episode) }
        if (exact.size == 1) return exact
        if (unique.size == 1 && !isExplicitMismatch(unique[0].name, identity.season, identity.episode)) return unique
        if (unique.all { isExplicitMismatch(it.name, identity.season, identity.episode) }) {
            throw IllegalStateException("This source contains a different episode. Choose another source.")
        }
        throw IllegalStateException("This pack's episode files could not be matched safely. Select the correct file and identify its episode from TorBox Cloud.")
    }

    fun preferredId(files: List<VideoCandidate>, sourceTitle: String): Long? =
        files.filter { filenameKey(it.name) == filenameKey(sourceTitle) }.singleOrNull()?.id

    fun select(files: List<VideoCandidate>, sourceTitle: String, identity: PlaybackIdentity): VideoCandidate {
        val videos = files.filter { videoExtension.containsMatchIn(it.name) && !sample.containsMatchIn(it.name) }
        if (videos.isEmpty()) throw IllegalStateException("This transfer does not contain a playable video file yet.")
        val exact = videos.filter { filenameKey(it.name) == filenameKey(sourceTitle) }
        if (exact.size == 1 && (identity.type != "series" || !isExplicitMismatch(exact[0].name, identity.season, identity.episode))) {
            return exact[0]
        }
        if (identity.type == "series") {
            val matched = videos.filter { (identity.season to identity.episode) in episodes(it.name) }
            if (matched.size == 1) return matched[0]
            if (matched.size > 1) throw IllegalStateException("This pack has multiple files for the selected episode. Choose another source or select the file from TorBox Cloud.")
        }
        if (videos.size == 1) {
            val only = videos[0]
            if (identity.type == "series" && isExplicitMismatch(only.name, identity.season, identity.episode)) {
                throw IllegalStateException("This source contains a different episode. Choose another source.")
            }
            return only
        }
        throw IllegalStateException("This pack contains multiple videos and the correct file could not be identified safely. Choose another source or select a file from TorBox Cloud.")
    }

    private fun filenameKey(value: String): String = value.substringAfterLast('/').substringAfterLast('\\')
        .replace(videoExtension, "").lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    private fun episodes(name: String): Set<Pair<Int, Int>> {
        val found = mutableSetOf<Pair<Int, Int>>()
        seasonEpisode.findAll(name).forEach { match ->
            val season = match.groupValues[1].toInt()
            var previous = match.groupValues[2].toInt()
            found += season to previous
            tailEpisode.findAll(match.groupValues[3]).forEach { tail ->
                val rangeEnd = tail.groupValues[1].toIntOrNull()
                if (rangeEnd != null) {
                    // Bound ranges to avoid accepting malformed huge packs as every possible episode.
                    if (rangeEnd >= previous && rangeEnd - previous <= 100) {
                        for (episode in previous..rangeEnd) found += season to episode
                    }
                    previous = rangeEnd
                } else {
                    previous = tail.groupValues[2].toInt()
                    found += season to previous
                }
            }
        }
        crossEpisode.findAll(name).forEach { match ->
            val season = match.groupValues[1].toInt()
            val start = match.groupValues[2].toInt()
            val end = match.groupValues[3].toIntOrNull() ?: start
            if (end >= start && end - start <= 100) for (episode in start..end) found += season to episode
        }
        return found
    }
}
