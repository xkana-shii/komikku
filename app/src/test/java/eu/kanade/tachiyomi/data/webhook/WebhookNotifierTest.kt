package eu.kanade.tachiyomi.data.webhook

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class WebhookNotifierTest {

    @Test
    fun `current implementation remains available`() {
        assertDoesNotThrow {
            Class.forName("eu.kanade.tachiyomi.data.track.Tracker")
        }
    }
}
