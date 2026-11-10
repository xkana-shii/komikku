package eu.kanade.domain.track.interactor

import eu.kanade.domain.sync.SyncStateBumper
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.model.TrackMangaMetadata
import eu.kanade.tachiyomi.source.model.SManga
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.SetMangaUserInfo
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.model.Track

// KNS -->
class ApplyPriorityTrackerMetadataTest {

    private val manga = Manga.create().copy(
        id = 1,
        favorite = true,
        ogTitle = "Source Title",
        ogAuthor = "Source Author",
        ogDescription = "Source description",
        ogThumbnailUrl = "https://source/cover.png",
        ogGenre = listOf("Action"),
        ogStatus = SManga.ONGOING.toLong(),
    )

    private fun track(trackerId: Long) = Track(
        id = trackerId, mangaId = 1, trackerId = trackerId, remoteId = 100 + trackerId, libraryId = null,
        title = "t", lastChapterRead = 4.0, totalChapters = 10, status = 1, score = 0.0, remoteUrl = "",
        startDate = null, finishDate = null, private = false,
    )

    // ---- merge

    @Test
    fun `only the fields a tracker provides are applied`() {
        val result = ApplyPriorityTrackerMetadata.merge(
            manga,
            TrackMangaMetadata(title = "Tracker Title", description = "  ", authors = null),
        )

        result.title shouldBe "Tracker Title"
        result.description shouldBe null
        result.author shouldBe null
        result.thumbnailUrl shouldBe null
    }

    @Test
    fun `existing edits survive fields the tracker does not provide`() {
        val edited = manga.copy(userDescription = "My notes", userTitle = "Mine")

        val result = ApplyPriorityTrackerMetadata.merge(edited, TrackMangaMetadata(authors = "Someone"))

        result.description shouldBe "My notes"
        result.title shouldBe "Mine"
        result.author shouldBe "Someone"
    }

    @Test
    fun `a value equal to the source clears the edit and tags are merged`() {
        val edited = manga.copy(userTitle = "Old edit")

        val result = ApplyPriorityTrackerMetadata.merge(
            edited,
            TrackMangaMetadata(title = "Source Title", tags = listOf("action", "Drama"), status = SManga.COMPLETED.toLong()),
        )

        result.title shouldBe null
        result.genre shouldBe listOf("Action", "Drama")
        result.status shouldBe SManga.COMPLETED.toLong()
    }

    @Test
    fun `identical metadata gives an identical update`() {
        val metadata = TrackMangaMetadata(title = "Tracker Title", tags = listOf("Drama"))
        val first = ApplyPriorityTrackerMetadata.merge(manga, metadata)
        val applied = manga.copy(userTitle = first.title, userGenre = first.genre)

        ApplyPriorityTrackerMetadata.merge(applied, metadata) shouldBe first
    }

    // ---- interactor

    private class Fixture(manga: Manga, vararg linked: Long) {
        var enabled = true
        var preferred: Long? = null
        var priority: Long = 0
        val setInfo = mockk<SetMangaUserInfo>(relaxed = true)
        val tracker = mockk<BaseTracker>()
        var fetches = 0
        val prefs = mockk<TrackPreferences>()

        init {
            val enabledPref = mockk<Preference<Boolean>>()
            every { enabledPref.get() } answers { enabled }
            every { prefs.autoPriorityTrackerMetadata } returns enabledPref
            every { prefs.resolvePreferredTracker(any(), any()) } answers {
                val applicable = secondArg<Set<Long>>()
                preferred?.takeIf { it in applicable } ?: priority.takeIf { it > 0 && it in applicable }
            }
            every { tracker.supportsMangaMetadata } returns true
            every { tracker.isLoggedIn } returns true
        }

        val getTracks = mockk<GetTracks>()
        val trackerManager = mockk<TrackerManager>()
        val getManga = mockk<GetManga>()
        val bumper = mockk<SyncStateBumper>(relaxed = true)
        val linkedIds = linked.toList()

        init {
            coEvery { getManga.await(any<Long>()) } returns manga
        }

        fun build(tracks: (Long) -> Track): ApplyPriorityTrackerMetadata {
            coEvery { getTracks.await(1L) } answers { linkedIds.map(tracks) }
            every { trackerManager.get(any()) } returns tracker
            return ApplyPriorityTrackerMetadata(prefs, getTracks, trackerManager, getManga, setInfo, bumper)
        }
    }

    private fun fixture(vararg linked: Long, metadata: () -> TrackMangaMetadata) =
        Fixture(manga, *linked).also { f ->
            coEvery { f.tracker.getMangaMetadata(any()) } answers {
                f.fetches++
                metadata()
            }
        }

    @Test
    fun `disabled does nothing at all`() = runBlocking<Unit> {
        val f = fixture(5) { TrackMangaMetadata(title = "T") }.also {
            it.enabled = false
            it.priority = 5
        }

        f.build(::track)(1) shouldBe false

        f.fetches shouldBe 0
        coVerify(exactly = 0) { f.setInfo.setAll(any()) }
    }

    @Test
    fun `the priority tracker is used and the details are written once`() = runBlocking<Unit> {
        val f = fixture(5, 6) { TrackMangaMetadata(title = "Tracker Title") }.also { it.priority = 6 }
        val apply = f.build(::track)

        apply(1) shouldBe true
        coVerify(exactly = 1) { f.setInfo.setAll(match { it.single().title == "Tracker Title" }) }
        coVerify(exactly = 1) { f.tracker.getMangaMetadata(match { it.trackerId == 6L }) }
    }

    @Test
    fun `a preferred tracker of the manga beats the global priority`() = runBlocking<Unit> {
        val f = fixture(5, 6) { TrackMangaMetadata(title = "T") }.also {
            it.priority = 6
            it.preferred = 5
        }

        f.build(::track)(1)

        coVerify { f.tracker.getMangaMetadata(match { it.trackerId == 5L }) }
    }

    @Test
    fun `a priority tracker that is not linked selects nothing`() = runBlocking<Unit> {
        val f = fixture(5) { TrackMangaMetadata(title = "T") }.also { it.priority = 9 }

        f.build(::track)(1) shouldBe false

        f.fetches shouldBe 0
    }

    @Test
    fun `metadata is reused instead of fetched again`() = runBlocking<Unit> {
        val f = fixture(5) { TrackMangaMetadata(title = "T") }.also { it.priority = 5 }
        val apply = f.build(::track)

        apply(1)
        apply(1)

        f.fetches shouldBe 1
    }

    @Test
    fun `a failing tracker changes nothing and does not fall back`() = runBlocking<Unit> {
        val f = Fixture(manga, 5, 6).also { it.priority = 6 }
        coEvery { f.tracker.getMangaMetadata(any()) } throws java.io.IOException("offline")

        f.build(::track)(1) shouldBe false

        coVerify(exactly = 0) { f.setInfo.setAll(any()) }
        coVerify(exactly = 1) { f.tracker.getMangaMetadata(any()) }
    }

    @Test
    fun `unchanged details are not written`() = runBlocking<Unit> {
        val applied = manga.copy(userTitle = "Tracker Title")
        val f = Fixture(applied, 5).also { it.priority = 5 }
        coEvery { f.tracker.getMangaMetadata(any()) } returns TrackMangaMetadata(title = "Tracker Title")

        f.build(::track)(1) shouldBe false

        coVerify(exactly = 0) { f.setInfo.setAll(any()) }
    }

    @Test
    fun `a selection that changes while fetching discards the answer`() = runBlocking<Unit> {
        val f = Fixture(manga, 5, 6).also { it.priority = 5 }
        coEvery { f.tracker.getMangaMetadata(any()) } answers {
            f.preferred = 6 // the user picks another tracker mid-fetch
            TrackMangaMetadata(title = "Stale")
        }

        f.build(::track)(1) shouldBe false

        coVerify(exactly = 0) { f.setInfo.setAll(any()) }
    }
}
// KNS <--
