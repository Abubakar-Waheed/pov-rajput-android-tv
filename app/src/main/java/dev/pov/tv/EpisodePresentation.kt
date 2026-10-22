package dev.pov.tv

import dev.pov.tv.data.EpisodeData
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

internal object EpisodePresentation {
    fun seasons(episodes: List<EpisodeData>): List<Int> = episodes.map { it.season }.distinct()
        .sortedWith(compareBy<Int> { it == 0 }.thenBy { it })
    fun selectedSeason(episodes: List<EpisodeData>, preferred: Int?): Int? =
        preferred?.takeIf { it in seasons(episodes) } ?: seasons(episodes).firstOrNull()
    fun future(released: String, now: Instant = Instant.now()): Boolean = try {
        val moment = try { Instant.parse(released) } catch (_: Exception) {
            LocalDate.parse(released.take(10)).atStartOfDay().toInstant(ZoneOffset.UTC)
        }
        moment.isAfter(now)
    } catch (_: Exception) { false }
}
