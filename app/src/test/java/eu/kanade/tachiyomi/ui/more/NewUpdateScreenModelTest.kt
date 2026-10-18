package eu.kanade.tachiyomi.ui.more

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class NewUpdateScreenModelTest {

    @Test
    fun `current implementation remains available`() {
        assertDoesNotThrow {
            Class.forName("eu.kanade.tachiyomi.ui.more.NewUpdateViewModel")
        }
    }
}
