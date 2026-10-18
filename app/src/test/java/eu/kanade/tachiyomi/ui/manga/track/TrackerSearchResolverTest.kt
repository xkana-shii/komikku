package eu.kanade.tachiyomi.ui.manga.track

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class TrackerSearchResolverTest {

    @Test
    fun `current implementation remains available`() {
        assertDoesNotThrow {
            Class.forName("eu.kanade.tachiyomi.data.track.mangabaka.dto.MangaBakaItem")
        }
    }
}
