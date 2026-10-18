package eu.kanade.tachiyomi.ui.browse.source.globalsearch

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class SearchViewModelTest {

    @Test
    fun `current implementation remains available`() {
        assertDoesNotThrow {
            Class.forName("eu.kanade.tachiyomi.ui.browse.source.globalsearch.SearchViewModel")
        }
    }
}
