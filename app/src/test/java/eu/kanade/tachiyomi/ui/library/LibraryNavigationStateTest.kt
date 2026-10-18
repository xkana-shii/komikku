package eu.kanade.tachiyomi.ui.library

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class LibraryNavigationStateTest {

    @Test
    fun `current implementation remains available`() {
        assertDoesNotThrow {
            Class.forName("eu.kanade.tachiyomi.ui.library.LibraryViewModel")
        }
    }
}
