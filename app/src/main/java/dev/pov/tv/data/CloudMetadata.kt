package dev.pov.tv.data

import java.util.Locale

/** Artwork hints only: a guessed filename never becomes a tracking identity. */
internal object CloudMetadata {
    data class Query(val title: String, val year: String, val type: String?)
    fun query(filename: String): Query? {
        val name = filename.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("\\.(mkv|mp4|m4v|avi|mov|webm|ts|m2ts)$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\[[^]]*]"), " ").replace('.', ' ').replace('_', ' ')
        val episode = Regex("(?i)\\b(?:s\\d{1,2}(?:e\\d{1,3})?|\\d{1,2}x\\d{1,3})\\b").find(name)
        val year = Regex("\\b(?:19|20)\\d{2}\\b").findAll(name).firstOrNull { it.range.first > 0 && name.take(it.range.first).isNotBlank() }
        val quality = Regex("(?i)\\b(?:2160p|1080p|720p|480p|4k|web[ -]?dl|webrip|bluray|brrip|hdtv|remux)\\b").find(name)
        val end = listOfNotNull(episode?.range?.first, year?.range?.first, quality?.range?.first).minOrNull() ?: name.length
        val title = name.take(end).trim(' ', '-', '(', ')')
        if (title.length < 2) return null
        return Query(title, year?.value.orEmpty(), if (episode != null) "series" else null)
    }
    fun match(query: Query, candidates: List<MediaItemData>): MediaItemData? {
        fun normalize(value: String) = value.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }
        val exact = candidates.filter { normalize(it.title) == normalize(query.title) && (query.type == null || it.type == query.type) }
            .distinctBy { it.type to it.imdbId }
        val dated = if (query.year.isNotBlank()) exact.filter { it.year.take(4) == query.year } else exact
        return dated.singleOrNull()
    }
}
