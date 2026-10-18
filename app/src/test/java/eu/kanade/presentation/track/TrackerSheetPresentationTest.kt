package eu.kanade.presentation.track

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class TrackerSheetPresentationTest {

    @Test
    fun `current implementation remains available`() {
        assertDoesNotThrow {
            Class.forName("tachiyomi.domain.track.model.Track")
        }
    }
}
