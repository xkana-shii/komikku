package eu.kanade.tachiyomi.ui.browse

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class DebouncedSearchQueryTest {

    @Test
    fun `current implementation remains available`() {
        assertDoesNotThrow {
            Class.forName("tachiyomi.core.common.util.QuerySanitizer")
        }
    }
}
