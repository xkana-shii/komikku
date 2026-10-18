package eu.kanade.tachiyomi.data.track

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class MetadataAutofillTest {

    @Test
    fun `current implementation remains available`() {
        assertDoesNotThrow {
            Class.forName("eu.kanade.tachiyomi.data.track.mangabaka.dto.MangaBakaItem")
        }
    }
}
