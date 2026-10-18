package eu.kanade.tachiyomi.data.backup

import eu.kanade.tachiyomi.data.backup.models.BackupManga
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

// KMK -->
class BackupMangaScanlatorTest {

    @Test
    fun `backup manga conversion preserves supported core fields`() {
        val restored = BackupManga(
            source = 1L,
            url = "/manga/1",
            title = "Example",
            favorite = true,
            dateAdded = 50L,
        ).getMangaImpl()

        restored.source shouldBe 1L
        restored.url shouldBe "/manga/1"
        restored.favorite shouldBe true
    }

    @Test
    fun `backup manga conversion handles non-library entries`() {
        val restored = BackupManga(source = 1L, url = "/manga/1", title = "Example").getMangaImpl()

        restored.favorite shouldBe true
        restored.source shouldBe 1L
    }
}
// KMK <--
