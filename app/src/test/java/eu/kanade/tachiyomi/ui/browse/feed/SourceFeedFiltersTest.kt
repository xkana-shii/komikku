package eu.kanade.tachiyomi.ui.browse.feed

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class SourceFeedFiltersTest {

    @Test
    fun `current implementation remains available`() {
        assertDoesNotThrow {
            Class.forName("eu.kanade.tachiyomi.ui.browse.feed.FeedScreenState")
        }
    }
}
