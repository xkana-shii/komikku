
package tachiyomi.domain.history.interactor

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.interactor.GetMergedChaptersByMangaId
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.service.chapterUrlBlacklistKey
import tachiyomi.domain.history.model.HistoryWithRelations
import tachiyomi.domain.history.repository.HistoryRepository
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.GetCustomMangaInfo
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.CustomMangaInfo
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaCover
import tachiyomi.domain.manga.repository.CustomMangaRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingletonFactory
import kotlin.time.Instant

// KMK -->
@Execution(ExecutionMode.CONCURRENT)
class GetNextChaptersTest {

    private val getChaptersByMangaId: GetChaptersByMangaId = mockk()
    private val getMergedChaptersByMangaId: GetMergedChaptersByMangaId = mockk()
    private val getManga: GetManga = mockk()
    private val historyRepository: HistoryRepository = mockk()

    private var smartMergeEnabled = false

    private val libraryPreferences: LibraryPreferences = mockk {
        every {
            smartScanlatorMergeEnabled(any())
        } answers {
            smartMergeEnabled
        }
    }

    private val interactor = GetNextChapters(
        getChaptersByMangaId,
        getMergedChaptersByMangaId,
        getManga,
        historyRepository,
        libraryPreferences,
    )

    private fun chapter(
        id: Long,
        mangaId: Long,
        read: Boolean,
        number: Double,
    ) = Chapter.create().copy(
        id = id,
        mangaId = mangaId,
        read = read,
        chapterNumber = number,
        // Source-order sorting with sortDescending=false expects
        // lower sourceOrder values for later chapters.
        sourceOrder = -(number * 100).toLong(),
    )

    private fun history(
        mangaId: Long,
        chapterId: Long,
    ) = HistoryWithRelations(
        id = mangaId,
        chapterId = chapterId,
        mangaId = mangaId,
        ogTitle = "Manga $mangaId",
        chapterNumber = 1.0,
        read = true,
        lastPageRead = 5,
        totalCountCalculated = 2,
        readCountCalculated = 1,
        readAt = Instant.fromEpochMilliseconds(0),
        readDuration = 0,
        coverData = MangaCover(
            mangaId = mangaId,
            sourceId = 1,
            isMangaFavorite = true,
            ogUrl = null,
            lastModified = 0,
        ),
    )

    private fun givenManga(id: Long) {
        coEvery {
            getManga.await(id)
        } returns Manga.create().copy(
            id = id,
            source = 1L,
        )
    }

    /**
     * The current HistoryRepository exposes getHistory() as a Flow.
     * It does not expose getRecentHistory().
     */
    private fun givenHistory(
        vararg entries: HistoryWithRelations,
    ) {
        every {
            historyRepository.getHistory(
                query = "",
                unfinishedManga = null,
                unfinishedChapter = null,
                nonLibraryEntries = null,
            )
        } returns flowOf(entries.toList())
    }

    @Test
    fun `returns the unread chapter from the newest history entry`() = runTest {
        val newest = chapter(
            id = 2L,
            mangaId = 10L,
            read = false,
            number = 2.0,
        )

        givenManga(10L)

        coEvery {
            getChaptersByMangaId.await(10L, applyFilter = true)
        } returns listOf(
            chapter(1L, 10L, read = true, number = 1.0),
            newest,
        )

        givenHistory(history(10L, 1L))

        interactor.awaitFirstReadable() shouldBe newest
    }

    @Test
    fun `skips a completed series and resumes an older one`() = runTest {
        val older = chapter(
            id = 4L,
            mangaId = 20L,
            read = false,
            number = 2.0,
        )

        givenManga(10L)
        givenManga(20L)

        coEvery {
            getChaptersByMangaId.await(10L, applyFilter = true)
        } returns listOf(
            chapter(1L, 10L, read = true, number = 1.0),
        )

        coEvery {
            getChaptersByMangaId.await(20L, applyFilter = true)
        } returns listOf(
            chapter(3L, 20L, read = true, number = 1.0),
            older,
        )

        givenHistory(
            history(10L, 1L),
            history(20L, 3L),
        )

        interactor.awaitFirstReadable() shouldBe older
    }

    @Test
    fun `skips read chapters after an older history entry`() = runTest {
        val unread = chapter(
            id = 4L,
            mangaId = 20L,
            read = false,
            number = 3.0,
        )

        givenManga(10L)
        givenManga(20L)

        coEvery {
            getChaptersByMangaId.await(10L, applyFilter = true)
        } returns listOf(
            chapter(1L, 10L, read = true, number = 1.0),
        )

        coEvery {
            getChaptersByMangaId.await(20L, applyFilter = true)
        } returns listOf(
            chapter(2L, 20L, read = true, number = 1.0),
            chapter(3L, 20L, read = true, number = 2.0),
            unread,
        )

        givenHistory(
            history(10L, 1L),
            history(20L, 2L),
        )

        interactor.awaitFirstReadable() shouldBe unread
    }

    @Test
    fun `checks each manga only once when History has duplicates`() = runTest {
        val older = chapter(
            id = 5L,
            mangaId = 20L,
            read = false,
            number = 2.0,
        )

        givenManga(10L)
        givenManga(20L)

        coEvery {
            getChaptersByMangaId.await(10L, applyFilter = true)
        } returns listOf(
            chapter(1L, 10L, read = true, number = 1.0),
        )

        coEvery {
            getChaptersByMangaId.await(20L, applyFilter = true)
        } returns listOf(
            chapter(4L, 20L, read = true, number = 1.0),
            older,
        )

        givenHistory(
            history(10L, 1L),
            history(10L, 1L),
            history(20L, 4L),
        )

        interactor.awaitFirstReadable() shouldBe older

        coVerify(exactly = 1) {
            getChaptersByMangaId.await(10L, applyFilter = true)
        }
    }

    @Test
    fun `returns null when every recent series is complete`() = runTest {
        givenManga(10L)
        givenManga(20L)

        coEvery {
            getChaptersByMangaId.await(any(), applyFilter = true)
        } returns listOf(
            chapter(1L, 10L, read = true, number = 1.0),
        )

        givenHistory(
            history(10L, 1L),
            history(20L, 1L),
        )

        interactor.awaitFirstReadable() shouldBe null
    }

    @Test
    fun `uses the requested bounded scan limit`() = runTest {
        val unread = chapter(
            id = 4L,
            mangaId = 20L,
            read = false,
            number = 2.0,
        )

        givenManga(10L)
        givenManga(20L)

        coEvery {
            getChaptersByMangaId.await(10L, applyFilter = true)
        } returns listOf(
            chapter(1L, 10L, read = true, number = 1.0),
        )

        coEvery {
            getChaptersByMangaId.await(20L, applyFilter = true)
        } returns listOf(
            chapter(3L, 20L, read = true, number = 1.0),
            unread,
        )

        givenHistory(
            history(10L, 1L),
            history(20L, 3L),
        )

        // Only manga 10 is examined because the limit is one.
        interactor.awaitFirstReadable(scanLimit = 1L) shouldBe null

        coVerify(exactly = 0) {
            getChaptersByMangaId.await(20L, applyFilter = true)
        }
    }

    @Test
    fun `returns null immediately when scan limit is zero`() = runTest {
        interactor.awaitFirstReadable(scanLimit = 0L) shouldBe null
    }

    @Test
    fun `returns null when recent History is empty`() = runTest {
        givenHistory()

        interactor.awaitFirstReadable() shouldBe null
    }

    @Test
    fun `uses existing chapter filters while finding a continuation`() = runTest {
        val unread = chapter(
            id = 2L,
            mangaId = 10L,
            read = false,
            number = 2.0,
        )

        givenManga(10L)

        coEvery {
            getChaptersByMangaId.await(10L, applyFilter = true)
        } returns listOf(
            chapter(1L, 10L, read = true, number = 1.0),
            unread,
        )

        givenHistory(history(10L, 1L))

        interactor.awaitFirstReadable() shouldBe unread

        coVerify {
            getChaptersByMangaId.await(10L, applyFilter = true)
        }
    }

    @Test
    fun `resolves a hidden history chapter to the visible scanlator duplicate`() = runTest {
        val hidden = chapter(
            1L,
            10L,
            read = false,
            number = 1.0,
        ).copy(scanlator = "Group B")

        val preferred = chapter(
            2L,
            10L,
            read = false,
            number = 1.0,
        ).copy(scanlator = "Group A")

        smartMergeEnabled = true

        coEvery {
            getManga.await(10L)
        } returns Manga.create().copy(
            id = 10L,
            source = 1L,
            scanlatorPriority = listOf("Group A", "Group B"),
        )

        coEvery {
            getChaptersByMangaId.await(10L, applyFilter = true)
        } returns listOf(hidden, preferred)

        interactor.await(
            mangaId = 10L,
            fromChapterId = hidden.id,
            onlyUnread = false,
        ).first() shouldBe preferred
    }

    @Test
    fun `continues after the visible preferred chapter when History references its hidden duplicate`() = runTest {
        val preferredOne = chapter(
            1L,
            10L,
            read = true,
            number = 1.0,
        ).copy(scanlator = "Group A")

        val hiddenOne = chapter(
            2L,
            10L,
            read = false,
            number = 1.0,
        ).copy(scanlator = "Group B")

        val preferredTwo = chapter(
            3L,
            10L,
            read = true,
            number = 2.0,
        ).copy(scanlator = "Group A")

        val hiddenTwo = chapter(
            4L,
            10L,
            read = false,
            number = 2.0,
        ).copy(scanlator = "Group B")

        val nextPreferred = chapter(
            5L,
            10L,
            read = false,
            number = 3.0,
        ).copy(scanlator = "Group A")

        smartMergeEnabled = true

        coEvery {
            getManga.await(10L)
        } returns Manga.create().copy(
            id = 10L,
            source = 1L,
            scanlatorPriority = listOf("Group A", "Group B"),
        )

        coEvery {
            getChaptersByMangaId.await(10L, applyFilter = true)
        } returns listOf(
            preferredOne,
            hiddenOne,
            preferredTwo,
            hiddenTwo,
            nextPreferred,
        )

        interactor.await(
            mangaId = 10L,
            fromChapterId = hiddenTwo.id,
            onlyUnread = false,
        ).first() shouldBe nextPreferred
    }

    @Test
    fun `continues after a blacklisted History chapter without reopening earlier chapters`() = runTest {
        val first = chapter(
            1L,
            10L,
            read = true,
            number = 1.0,
        )

        val blacklisted = chapter(
            2L,
            10L,
            read = true,
            number = 2.0,
        ).copy(url = "/blacklisted")

        val next = chapter(
            3L,
            10L,
            read = false,
            number = 3.0,
        )

        coEvery {
            getManga.await(10L)
        } returns Manga.create().copy(
            id = 10L,
            source = 1L,
            blacklistedChapters = listOf(
                chapterUrlBlacklistKey(blacklisted.url),
            ),
        )

        coEvery {
            getChaptersByMangaId.await(10L, applyFilter = true)
        } returns listOf(
            first,
            blacklisted,
            next,
        )

        interactor.await(
            mangaId = 10L,
            fromChapterId = blacklisted.id,
            onlyUnread = false,
        ).first() shouldBe next
    }

    @Test
    fun `does not restart from beginning when History chapter is missing`() = runTest {
        givenManga(10L)

        coEvery {
            getChaptersByMangaId.await(10L, applyFilter = true)
        } returns listOf(
            chapter(1L, 10L, read = true, number = 1.0),
            chapter(2L, 10L, read = false, number = 2.0),
        )

        interactor.await(
            mangaId = 10L,
            fromChapterId = 999L,
            onlyUnread = false,
        ) shouldBe emptyList<Chapter>()
    }

    @Test
    fun `returns an empty list for a manga with no chapters`() = runTest {
        givenManga(10L)

        coEvery {
            getChaptersByMangaId.await(10L, applyFilter = true)
        } returns emptyList()

        interactor.await(
            mangaId = 10L,
            fromChapterId = 1L,
            onlyUnread = false,
        ) shouldBe emptyList<Chapter>()
    }

    @Test
    fun `await without chapter ID uses the most recent history entry`() = runTest {
        val unread = chapter(
            2L,
            10L,
            read = false,
            number = 2.0,
        )

        coEvery {
            historyRepository.getLastHistory()
        } returns history(10L, 1L)

        givenManga(10L)

        coEvery {
            getChaptersByMangaId.await(10L, applyFilter = true)
        } returns listOf(
            chapter(1L, 10L, read = true, number = 1.0),
            unread,
        )

        interactor.await(onlyUnread = true) shouldBe listOf(unread)
    }

    @Test
    fun `await returns empty when no reading history exists`() = runTest {
        coEvery {
            historyRepository.getLastHistory()
        } returns null

        interactor.await(onlyUnread = true) shouldBe emptyList<Chapter>()
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun registerCustomMangaInfo() {
            Injekt.addSingletonFactory {
                GetCustomMangaInfo(
                    object : CustomMangaRepository {
                        override fun get(
                            mangaId: Long,
                        ): CustomMangaInfo? = null

                        override fun set(
                            mangaInfo: CustomMangaInfo,
                        ) = Unit
                    },
                )
            }
        }
    }
}
// KMK <--
