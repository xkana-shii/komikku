package eu.kanade.tachiyomi.extension

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class ExtensionInitializationTest {

    @Test
    fun `current implementation remains available`() {
        assertDoesNotThrow {
            Class.forName("eu.kanade.tachiyomi.extension.ExtensionManager")
        }
    }
}
