// KUN -->
package eu.kanade.domain.sync

import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.core.common.preference.InMemoryPreferenceStore.InMemoryPreference
import tachiyomi.domain.manga.model.CustomMangaInfo
import tachiyomi.domain.manga.repository.MangaRepository

/**
 * The bump is what makes a change stored outside the manga row (history reset, custom-info edit)
 * reach the other devices through the version-based delta. It must stay off when the sync service is
 * disabled or the matching "choose what to sync" section is off: bumping then would upload that
 * section's local copy, which a disabled section must leave alone.
 */
class SyncStateBumperTest {

    private val mangaRepository = mockk<MangaRepository>(relaxed = true)

    @Test
    fun `history removal bumps every affected manga once when history sync is on`() {
        runBlocking {
            bumper(syncService = SYNCYOMI).bumpForHistoryRemoval(listOf(1L, 2L, 2L))

            coVerify(exactly = 1) { mangaRepository.bumpSyncVersion(1L) }
            coVerify(exactly = 1) { mangaRepository.bumpSyncVersion(2L) }
            coVerify(exactly = 0) { mangaRepository.bumpSyncVersion(3L) }
        }
    }

    @Test
    fun `history removal does nothing when the history toggle is off`() {
        runBlocking {
            bumper(syncService = SYNCYOMI, history = false).bumpForHistoryRemoval(listOf(1L))

            coVerify(exactly = 0) { mangaRepository.bumpSyncVersion(any()) }
        }
    }

    @Test
    fun `history removal does nothing when sync is disabled`() {
        runBlocking {
            bumper(syncService = 0).bumpForHistoryRemoval(listOf(1L))

            coVerify(exactly = 0) { mangaRepository.bumpSyncVersion(any()) }
        }
    }

    @Test
    fun `history removal of an empty list does nothing`() {
        runBlocking {
            bumper(syncService = SYNCYOMI).bumpForHistoryRemoval(emptyList())

            coVerify(exactly = 0) { mangaRepository.bumpSyncVersion(any()) }
        }
    }

    @Test
    fun `a custom info edit that changes the stored overrides bumps the manga`() {
        runBlocking {
            bumper(syncService = SYNCYOMI).bumpForCustomInfoEdit(
                mangaId = 1L,
                before = null,
                after = CustomMangaInfo(id = 1L, title = "Custom"),
            )

            coVerify(exactly = 1) { mangaRepository.bumpSyncVersion(1L) }
        }
    }

    @Test
    fun `a custom info edit that clears every override bumps the manga`() {
        runBlocking {
            bumper(syncService = SYNCYOMI).bumpForCustomInfoEdit(
                mangaId = 1L,
                before = CustomMangaInfo(id = 1L, title = "Custom"),
                after = CustomMangaInfo(id = 1L, title = null),
            )

            coVerify(exactly = 1) { mangaRepository.bumpSyncVersion(1L) }
        }
    }

    @Test
    fun `an unchanged custom info edit does not bump`() {
        runBlocking {
            bumper(syncService = SYNCYOMI).bumpForCustomInfoEdit(
                mangaId = 1L,
                before = CustomMangaInfo(id = 1L, title = "Custom"),
                after = CustomMangaInfo(id = 1L, title = "Custom"),
            )

            coVerify(exactly = 0) { mangaRepository.bumpSyncVersion(any()) }
        }
    }

    @Test
    fun `a blank or all-null custom info value is the same as no stored override`() {
        runBlocking {
            val bumper = bumper(syncService = SYNCYOMI)

            bumper.bumpForCustomInfoEdit(
                mangaId = 1L,
                before = null,
                after = CustomMangaInfo(id = 1L, title = "  ", status = 0L),
            )

            coVerify(exactly = 0) { mangaRepository.bumpSyncVersion(any()) }
        }
    }

    @Test
    fun `a custom info edit does nothing when the custom info toggle is off`() {
        runBlocking {
            bumper(syncService = SYNCYOMI, customInfo = false).bumpForCustomInfoEdit(
                mangaId = 1L,
                before = null,
                after = CustomMangaInfo(id = 1L, title = "Custom"),
            )

            coVerify(exactly = 0) { mangaRepository.bumpSyncVersion(any()) }
        }
    }

    @Test
    fun `a custom info edit does nothing when sync is disabled`() {
        runBlocking {
            bumper(syncService = 0).bumpForCustomInfoEdit(
                mangaId = 1L,
                before = null,
                after = CustomMangaInfo(id = 1L, title = "Custom"),
            )

            coVerify(exactly = 0) { mangaRepository.bumpSyncVersion(any()) }
        }
    }

    private fun bumper(
        syncService: Int,
        history: Boolean = true,
        customInfo: Boolean = true,
    ) = SyncStateBumper(
        syncPreferences = SyncPreferences(
            InMemoryPreferenceStore(
                sequenceOf(
                    InMemoryPreference("sync_service", syncService, 0),
                    InMemoryPreference("sync_history", history, true),
                    InMemoryPreference("sync_customInfo", customInfo, true),
                ),
            ),
        ),
        mangaRepository = mangaRepository,
    )

    private companion object {
        const val SYNCYOMI = 1
    }
}
// KUN <--
