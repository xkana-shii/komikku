package mihon.domain.migration.usecases

import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.source.Source
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import mihon.domain.migration.models.MigrationFlag
import mihon.domain.source.interactor.UpdateMangaFromRemote
import org.junit.jupiter.api.Test
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.interactor.UpdateChapter
import tachiyomi.domain.chapter.model.BookmarkColor
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.history.interactor.GetHistory
import tachiyomi.domain.history.interactor.UpsertHistory
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.interactor.UpsertTrack

// KNS -->
class MigrateMangaBookmarkColorTest {

    private val current = Manga.create().copy(id = 1, source = 10)
    private val target = Manga.create().copy(id = 2, source = 20)

    private val getChaptersByMangaId = mockk<GetChaptersByMangaId>()
    private val updateChapter = mockk<UpdateChapter>(relaxed = true)
    private val updates = slot<List<ChapterUpdate>>()

    private fun chapter(
        id: Long,
        mangaId: Long,
        number: Double,
        bookmark: Boolean = false,
        color: Int = BookmarkColor.DEFAULT_ID,
    ) = Chapter.create().copy(
        id = id,
        mangaId = mangaId,
        chapterNumber = number,
        bookmark = bookmark,
        bookmarkColor = color,
    )

    private suspend fun migrate(
        previous: List<Chapter>,
        destination: List<Chapter>,
        flags: Set<MigrationFlag> = setOf(MigrationFlag.CHAPTER),
    ): Map<Long, ChapterUpdate> {
        coEvery { getChaptersByMangaId.await(current.id) } returns previous
        coEvery { getChaptersByMangaId.await(target.id) } returns destination
        coEvery { updateChapter.awaitAll(capture(updates)) } returns Unit

        val sourceManager = mockk<SourceManager>()
        coEvery { sourceManager.get(any<Long>()) } returns mockk<Source>(relaxed = true)
        val updateMangaFromRemote = mockk<UpdateMangaFromRemote>()
        coEvery {
            updateMangaFromRemote(
                any<Manga>(),
                any<Boolean>(),
                any<Boolean>(),
                any<Boolean>(),
                null,
                any<suspend () -> Unit>(),
            )
        } returns Result.success(mockk())
        val getHistory = mockk<GetHistory>()
        coEvery { getHistory.await(any<Long>()) } returns emptyList()

        MigrateMangaUseCase(
            scope = mockk<CoroutineScope>(relaxed = true),
            sourcePreferences = mockk<SourcePreferences>(relaxed = true),
            trackerManager = mockk<TrackerManager>(relaxed = true),
            sourceManager = sourceManager,
            downloadManager = mockk<DownloadManager>(relaxed = true),
            updateManga = mockk<UpdateManga>(relaxed = true),
            getChaptersByMangaId = getChaptersByMangaId,
            updateChapter = updateChapter,
            getCategories = mockk<GetCategories>(relaxed = true),
            setMangaCategories = mockk<SetMangaCategories>(relaxed = true),
            getTracks = mockk<GetTracks>(relaxed = true),
            upsertTrack = mockk<UpsertTrack>(relaxed = true),
            coverCache = mockk<CoverCache>(relaxed = true),
            updateMangaFromRemote = updateMangaFromRemote,
            getHistory = getHistory,
            upsertHistory = mockk<UpsertHistory>(relaxed = true),
        )(current, target, replace = true, presetFlags = flags)

        return if (updates.isCaptured) updates.captured.associateBy { it.id } else emptyMap()
    }

    @Test
    fun `every bookmark color moves with its bookmark to the matched chapter`() = runTest {
        val colors = BookmarkColor.entries
        val previous = colors.mapIndexed { i, color -> chapter(100L + i, 1, i + 1.0, bookmark = true, color = color.value) }
        val destination = colors.mapIndexed { i, _ -> chapter(200L + i, 2, i + 1.0) }

        val result = migrate(previous, destination)

        colors.forEachIndexed { i, color ->
            val update = result.getValue(200L + i)
            update.bookmark shouldBe true
            update.bookmarkColor shouldBe color.value
        }
    }

    @Test
    fun `an ordinary bookmark keeps the default color and clears a color on the destination`() = runTest {
        val previous = listOf(chapter(100, 1, 1.0, bookmark = true, color = BookmarkColor.DEFAULT_ID))
        val destination = listOf(chapter(200, 2, 1.0, bookmark = true, color = BookmarkColor.RED.value))

        val update = migrate(previous, destination).getValue(200)

        update.bookmark shouldBe true
        update.bookmarkColor shouldBe BookmarkColor.DEFAULT_ID
    }

    @Test
    fun `chapters without a match are left alone`() = runTest {
        val previous = listOf(chapter(100, 1, 1.0, bookmark = true, color = BookmarkColor.GREEN.value))
        val destination = listOf(
            chapter(200, 2, 1.0),
            chapter(201, 2, 2.0, bookmark = true, color = BookmarkColor.BLUE.value),
            chapter(202, 2, -1.0),
        )

        val result = migrate(previous, destination)

        result.getValue(200).bookmarkColor shouldBe BookmarkColor.GREEN.value
        // number 2 is not on the previous manga: nothing about its bookmark is written
        result.getValue(201).bookmark shouldBe null
        result.getValue(201).bookmarkColor shouldBe null
        // an unrecognised number is not part of the update at all
        result.containsKey(202) shouldBe false
    }

    @Test
    fun `duplicate chapter numbers take the color of the chapter the bookmark came from`() = runTest {
        val previous = listOf(
            chapter(100, 1, 5.0, bookmark = true, color = BookmarkColor.RED.value),
            chapter(101, 1, 5.0, bookmark = true, color = BookmarkColor.BLUE.value),
        )
        val destination = listOf(chapter(200, 2, 5.0))

        val update = migrate(previous, destination).getValue(200)

        // the first previous chapter with that number is the match, for the bookmark and its color alike
        update.bookmark shouldBe true
        update.bookmarkColor shouldBe BookmarkColor.RED.value
    }

    @Test
    fun `a destination bookmark is replaced as before when the previous chapter is not bookmarked`() = runTest {
        val previous = listOf(chapter(100, 1, 1.0, bookmark = false))
        val destination = listOf(chapter(200, 2, 1.0, bookmark = true, color = BookmarkColor.PURPLE.value))

        val update = migrate(previous, destination).getValue(200)

        update.bookmark shouldBe false
        update.bookmarkColor shouldBe BookmarkColor.DEFAULT_ID
    }

    @Test
    fun `nothing is written to chapters when chapter migration is off`() = runTest {
        val previous = listOf(chapter(100, 1, 1.0, bookmark = true, color = BookmarkColor.ORANGE.value))
        val destination = listOf(chapter(200, 2, 1.0))

        val result = migrate(previous, destination, flags = setOf(MigrationFlag.CATEGORY))

        result.isEmpty() shouldBe true
        coVerify(exactly = 0) { updateChapter.awaitAll(any()) }
    }
}
// KNS <--
