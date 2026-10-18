package eu.kanade.presentation.category

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class CategoryManagerRowsTest {

    @Test
    fun `current implementation remains available`() {
        assertDoesNotThrow {
            Class.forName("tachiyomi.domain.category.model.Category")
        }
    }
}
