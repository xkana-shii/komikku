package eu.kanade.domain.track.interactor

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class TrackerBatchTest {

    @Test
    fun `current implementation remains available`() {
        assertDoesNotThrow {
            Class.forName("eu.kanade.domain.track.interactor.RefreshTracks")
        }
    }
}
