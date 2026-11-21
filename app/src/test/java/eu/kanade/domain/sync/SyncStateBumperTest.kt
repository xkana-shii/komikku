// KUN -->
package eu.kanade.domain.sync

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.core.common.preference.InMemoryPreferenceStore.InMemoryPreference
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.history.model.History
import tachiyomi.domain.history.repository.HistoryRepository
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUserInfoUpdate
import tachiyomi.domain.manga.repository.MangaRepository

/**
 * The bump is what makes a change the version trigger cannot see (a history reset, a custom-info
 * edit of the user_* columns) reach the other devices through the version-based delta. It must stay off when the sync service is
 * disabled or the matching "choose what to sync" section is off: bumping then would upload that
 * section's local copy, which a disabled section must leave alone.
 */
class SyncStateBumperTest {

    private val mangaRepository = mockk<MangaRepository>(relaxed = true)
    private val chapterRepository = mockk<ChapterRepository>(relaxed = true)
    private val historyRepository = mockk<HistoryRepository>(relaxed = true)

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

    // KUN -->
    @Test
    fun `a removed history selection queues the chapter key of each entry`() {
        runBlocking {
            coEvery { mangaRepository.getMangaByIds(listOf(1L)) } returns listOf(
                Manga.create().copy(id = 1L, source = 7L, url = "/m"),
            )
            // only the selected entries are read; a long series is never loaded whole
            coEvery { chapterRepository.getChapterById(11L) } returns
                Chapter.create().copy(id = 11L, mangaId = 1L, url = "/m/ch1")
            coEvery { chapterRepository.getChapterById(12L) } returns
                Chapter.create().copy(id = 12L, mangaId = 1L, url = "/m/ch2")
            coEvery { chapterRepository.getChapterById(13L) } returns
                Chapter.create().copy(id = 13L, mangaId = 1L, url = "/m/ch3")
            val prefs = syncPreferences(syncService = SYNCYOMI)

            bumper(syncService = SYNCYOMI, prefs = prefs).rememberHistoryRemoval(
                mangaIds = listOf(1L),
                chapterIds = listOf(11L, 12L),
            )

            // the key is the server's history key, which is the chapter key byte for byte
            assertEquals(
                setOf("7|/m\u001f/m/ch1", "7|/m\u001f/m/ch2"),
                prefs.pendingDeletedHistoryKeys.get(),
            )
        }
    }

    @Test
    fun `removing every history entry of a manga queues the rows a SQL reset would lose`() {
        runBlocking {
            coEvery { mangaRepository.getMangaByIds(listOf(1L)) } returns listOf(
                Manga.create().copy(id = 1L, source = 7L, url = "/m"),
            )
            // the list the History tab showed may only carry the latest entry per manga; the reset
            // runs over every row, so the queued tombstones have to cover all of them
            coEvery { historyRepository.getHistoryByMangaIds(listOf(1L)) } returns mapOf(
                1L to listOf(
                    History.create().copy(id = 101L, chapterId = 11L),
                    History.create().copy(id = 102L, chapterId = 12L),
                ),
            )
            coEvery { chapterRepository.getChapterById(11L) } returns
                Chapter.create().copy(id = 11L, mangaId = 1L, url = "/m/ch1")
            coEvery { chapterRepository.getChapterById(12L) } returns
                Chapter.create().copy(id = 12L, mangaId = 1L, url = "/m/ch2")
            val prefs = syncPreferences(syncService = SYNCYOMI)

            bumper(syncService = SYNCYOMI, prefs = prefs).rememberHistoryRemoval(mangaIds = listOf(1L))

            assertEquals(
                setOf("7|/m\u001f/m/ch1", "7|/m\u001f/m/ch2"),
                prefs.pendingDeletedHistoryKeys.get(),
            )
        }
    }

    @Test
    fun `a history removal queues nothing while history sync is off`() {
        runBlocking {
            coEvery { mangaRepository.getMangaByIds(listOf(1L)) } returns listOf(
                Manga.create().copy(id = 1L, source = 7L, url = "/m"),
            )
            coEvery { chapterRepository.getChapterById(11L) } returns
                Chapter.create().copy(id = 11L, mangaId = 1L, url = "/m/ch1")
            val prefs = syncPreferences(syncService = SYNCYOMI, history = false)

            bumper(syncService = SYNCYOMI, history = false, prefs = prefs).rememberHistoryRemoval(
                mangaIds = listOf(1L),
                chapterIds = listOf(11L),
            )

            assertEquals(emptySet<String>(), prefs.pendingDeletedHistoryKeys.get())
        }
    }
    // KUN <--

    @Test
    fun `a custom info edit that changes the stored overrides bumps the manga`() {
        runBlocking {
            bumper(syncService = SYNCYOMI).bumpForCustomInfoEdit(
                mangaId = 1L,
                before = null,
                after = userInfo(title = "Custom"),
            )

            coVerify(exactly = 1) { mangaRepository.bumpSyncVersion(1L) }
        }
    }

    @Test
    fun `a custom info edit that clears every override bumps the manga`() {
        runBlocking {
            bumper(syncService = SYNCYOMI).bumpForCustomInfoEdit(
                mangaId = 1L,
                before = userInfo(title = "Custom"),
                after = userInfo(title = null),
            )

            coVerify(exactly = 1) { mangaRepository.bumpSyncVersion(1L) }
        }
    }

    @Test
    fun `an unchanged custom info edit does not bump`() {
        runBlocking {
            bumper(syncService = SYNCYOMI).bumpForCustomInfoEdit(
                mangaId = 1L,
                before = userInfo(title = "Custom"),
                after = userInfo(title = "Custom"),
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
                after = userInfo(title = "  ", status = 0L),
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
                after = userInfo(title = "Custom"),
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
                after = userInfo(title = "Custom"),
            )

            coVerify(exactly = 0) { mangaRepository.bumpSyncVersion(any()) }
        }
    }

    private fun userInfo(title: String? = null, status: Long? = null) = MangaUserInfoUpdate(
        id = 1L,
        title = title,
        author = null,
        artist = null,
        thumbnailUrl = null,
        description = null,
        genre = null,
        status = status,
    )

    private fun bumper(
        syncService: Int,
        history: Boolean = true,
        customInfo: Boolean = true,
        prefs: SyncPreferences = syncPreferences(syncService, history, customInfo),
    ) = SyncStateBumper(
        syncPreferences = prefs,
        mangaRepository = mangaRepository,
        chapterRepository = chapterRepository,
        historyRepository = historyRepository,
    )

    private fun syncPreferences(
        syncService: Int,
        history: Boolean = true,
        customInfo: Boolean = true,
    ) = SyncPreferences(
        InMemoryPreferenceStore(
            sequenceOf(
                InMemoryPreference("sync_service", syncService, 0),
                InMemoryPreference("sync_history", history, true),
                InMemoryPreference("sync_customInfo", customInfo, true),
                // a confirmed v2 host: the history tombstones are only recorded for one
                InMemoryPreference("__APP_STATE_sync_server_supports_v2", true, false),
                InMemoryPreference("__APP_STATE_sync_v2_probed_host", "https://sync.tachiyomi.org", ""),
            ),
        ),
    )

    private companion object {
        const val SYNCYOMI = 1
    }
}
// KUN <--
