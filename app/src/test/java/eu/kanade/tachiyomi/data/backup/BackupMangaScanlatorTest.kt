package eu.kanade.tachiyomi.data.backup

import eu.kanade.tachiyomi.data.backup.models.BackupManga
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.interactor.GetCustomMangaInfo
import tachiyomi.domain.manga.model.CustomMangaInfo
import tachiyomi.domain.manga.repository.CustomMangaRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingletonFactory

// KMK -->
class BackupMangaScanlatorTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun registerCustomMangaInfo() {
            Injekt.addSingletonFactory {
                GetCustomMangaInfo(
                    object : CustomMangaRepository {
                        override fun get(mangaId: Long): CustomMangaInfo? = null

                        override fun set(mangaInfo: CustomMangaInfo) = Unit
                    },
                )
            }
        }
    }

    @Test
    fun `scanlator priority ranges and blacklist survive a backup manga conversion`() {
        val restored = BackupManga(
            source = 1L,
            url = "/manga/1",
            title = "Example",
            scanlatorPriority = listOf("Preferred", "Fallback"),
            blacklistedChapters = listOf("1.0@Blocked", "url:/chapter%2C2"),
            scanlatorRangeRules = listOf("1.0:5.0:Preferred"),
        ).getMangaImpl()

        restored.scanlatorPriority shouldBe listOf("Preferred", "Fallback")
        restored.blacklistedChapters shouldBe listOf("1.0@Blocked", "url:/chapter%2C2")
        restored.scanlatorRangeRules shouldBe listOf("1.0:5.0:Preferred")
    }

    @Test
    fun `older backups without scanlator fields leave them empty`() {
        val restored = BackupManga(source = 1L, url = "/manga/1", title = "Example").getMangaImpl()

        restored.scanlatorPriority shouldBe emptyList()
        restored.scanlatorRangeRules shouldBe emptyList()
    }
}
// KMK <--
