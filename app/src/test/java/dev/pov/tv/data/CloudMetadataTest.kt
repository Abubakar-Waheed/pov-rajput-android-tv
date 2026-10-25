package dev.pov.tv.data

import org.junit.Assert.*
import org.junit.Test

class CloudMetadataTest {
    @Test fun `movie filename yields title and release year without codec or release group`() {
        assertEquals(CloudMetadata.Query("The Example Movie", "2025", null), CloudMetadata.query("[Group] The.Example.Movie.2025.2160p.WEB-DL.DV.HEVC.mkv"))
    }
    @Test fun `numeric movie names are not confused with their release year`() {
        assertEquals(CloudMetadata.Query("1917", "2019", null), CloudMetadata.query("1917.2019.1080p.mkv"))
    }
    @Test fun `season packs and episodes use the show title`() {
        assertEquals(CloudMetadata.Query("Example Show", "", "series"), CloudMetadata.query("Example.Show.S02E03.1080p.mkv"))
        assertEquals(CloudMetadata.Query("Example Show", "", "series"), CloudMetadata.query("Example.Show.S02.1080p"))
    }
    @Test fun `ambiguous titles are not assigned posters from an arbitrary remake`() {
        val one = MediaItemData("tt1234567", "movie", "Example", "1990")
        val two = one.copy(imdbId = "tt7654321", year = "2025")
        assertNull(CloudMetadata.match(CloudMetadata.Query("Example", "", null), listOf(one, two)))
        assertEquals(two, CloudMetadata.match(CloudMetadata.Query("Example", "2025", null), listOf(one, two)))
        assertNull(CloudMetadata.match(CloudMetadata.Query("Example", "2024", null), listOf(one, two)))
    }
    @Test fun `partial search matches and different media types are rejected`() {
        val movie = MediaItemData("tt1234567", "movie", "Example Show")
        assertNull(CloudMetadata.match(CloudMetadata.Query("Example", "", null), listOf(movie)))
        assertNull(CloudMetadata.match(CloudMetadata.Query("Example Show", "", "series"), listOf(movie)))
        assertNull(CloudMetadata.query("1080p.WEB-DL.mkv"))
    }
}
