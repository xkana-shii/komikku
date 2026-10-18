package eu.kanade.tachiyomi.ui.browse.feed

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class GenericSavedSearchTest {

    @Test
    fun `current implementation remains available`() {
        assertDoesNotThrow {
            Class.forName("eu.kanade.tachiyomi.ui.browse.feed.FeedScreenState")
        }
    }
}
