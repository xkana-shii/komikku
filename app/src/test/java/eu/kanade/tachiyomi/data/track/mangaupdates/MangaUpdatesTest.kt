package eu.kanade.tachiyomi.data.track.mangaupdates

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class MangaUpdatesTest {

    @Test
    fun `current implementation remains available`() {
        assertDoesNotThrow {
            Class.forName("eu.kanade.tachiyomi.data.track.mangaupdates.MangaUpdatesApi")
        }
    }
}
