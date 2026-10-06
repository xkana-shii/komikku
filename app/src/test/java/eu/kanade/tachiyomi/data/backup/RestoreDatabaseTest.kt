package eu.kanade.tachiyomi.data.backup

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.BackupFeed
import eu.kanade.tachiyomi.data.backup.models.BackupHistory
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupSavedSearch
import eu.kanade.tachiyomi.data.backup.models.BackupTracking
import eu.kanade.tachiyomi.data.backup.restore.restoreBatch
import eu.kanade.tachiyomi.data.backup.restore.restorers.FeedRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.MangaRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.SavedSearchRestorer
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.data.Chapters
import tachiyomi.data.Database
import tachiyomi.data.DateColumnAdapter
import tachiyomi.data.History
import tachiyomi.data.Mangas
import tachiyomi.data.MemoColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.UpdateStrategyColumnAdapter
import tachiyomi.data.category.CategoryRepositoryImpl
import tachiyomi.data.chapter.ChapterRepositoryImpl
import tachiyomi.data.manga.MangaRepositoryImpl
import tachiyomi.data.track.TrackRepositoryImpl
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.manga.interactor.FetchInterval
import tachiyomi.domain.manga.interactor.GetCustomMangaInfo
import tachiyomi.domain.manga.interactor.GetMangaByUrlAndSourceId
import tachiyomi.domain.manga.interactor.SetCustomMangaInfo
import tachiyomi.domain.manga.model.CustomMangaInfo
import tachiyomi.domain.manga.repository.CustomMangaRepository
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.interactor.InsertTrack
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingletonFactory
import java.util.Properties

// KMK -->
class RestoreDatabaseTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var db: Database
    private val customInfo = mutableMapOf<Long, CustomMangaInfo>()
    private val customRepository = object : CustomMangaRepository {
        override fun get(mangaId: Long) = customInfo[mangaId]
        override fun set(mangaInfo: CustomMangaInfo) { customInfo[mangaInfo.id] = mangaInfo }
    }

    @BeforeEach
    fun setUp() = runBlocking<Unit> {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties().apply { setProperty("foreign_keys", "true") })
        Database.Schema.create(driver).await()
        db = Database(
            driver,
            historyAdapter = History.Adapter(DateColumnAdapter),
            mangasAdapter = Mangas.Adapter(StringListColumnAdapter, UpdateStrategyColumnAdapter, MemoColumnAdapter),
            chaptersAdapter = Chapters.Adapter(MemoColumnAdapter),
        )
        Injekt.addSingletonFactory { GetCustomMangaInfo(customRepository) }
    }

    @AfterEach
    fun tearDown() = driver.close()

    @Test
    fun `failed nested restore does not discard its successful neighbours`() = runBlocking<Unit> {
        val errors = mutableListOf<String>()
        db.restoreBatch(
            listOf("before", "broken", "after"),
            restore = { name ->
                db.transaction {
                    db.categoriesQueries.insert(name, 1, 0, 0)
                    check(name != "broken") { "damaged entry" }
                }
            },
            onError = { name, _ -> errors.add(name) },
        )
        db.categoriesQueries.getCategories().awaitAsList().filter { it.id > 0 }.map { it.name }.toSet() shouldBe setOf("before", "after")
        errors shouldBe listOf("broken")
    }

    @Test
    fun `cancellation rolls back a batch without retrying or reporting an entry error`() = runBlocking<Unit> {
        var attempts = 0
        var cancelled = false
        try {
            db.restoreBatch(
                listOf("one", "two"),
                restore = {
                    attempts++
                    db.categoriesQueries.insert(it, 1, 0, 0)
                    throw CancellationException("cancel restore")
                },
                onError = { _, _ -> error("Cancellation must propagate") },
            )
        } catch (_: CancellationException) {
            cancelled = true
        }
        cancelled shouldBe true
        attempts shouldBe 1
        db.categoriesQueries.getCategories().awaitAsList().filter { it.id > 0 } shouldBe emptyList()
    }

    @Test
    fun `sync restore keeps stable IDs metadata and version convergence`() = runBlocking<Unit> {
        val chapters = GetChaptersByMangaId(ChapterRepositoryImpl(db))
        val tracks = TrackRepositoryImpl(db)
        val restorer = MangaRestorer(
            isSync = true, database = db,
            getCategories = GetCategories(CategoryRepositoryImpl(db)),
            getMangaByUrlAndSourceId = GetMangaByUrlAndSourceId(MangaRepositoryImpl(db)),
            getChaptersByMangaId = chapters, updateManga = mockk(relaxed = true),
            getTracks = GetTracks(tracks), insertTrack = InsertTrack(tracks),
            fetchInterval = FetchInterval(chapters), setCustomMangaInfo = SetCustomMangaInfo(customRepository),
            insertFlatMetadata = mockk(relaxed = true), getFlatMetadataById = mockk(relaxed = true),
        )
        db.categoriesQueries.insert("Reading", 1, 0, 1)
        val category = BackupCategory("Reading", 1)
        val backup = BackupManga(
            source = 1, url = "/manga", title = "Original", author = "Original author",
            nextUpdate = 123456, fetchInterval = 7, version = 1,
            categories = listOf(1), chapters = listOf(BackupChapter("/chapter", "Original chapter", version = 1)),
            history = listOf(BackupHistory("/chapter", 1000, 42)),
            tracking = listOf(BackupTracking(syncId = 1, libraryId = 2, mediaId = 3, title = "Tracked")),
        )
        restorer.restore(backup, listOf(category))
        val mangaId = db.mangasQueries.getAll().awaitAsList().single()._id
        val chapterId = chapters.await(mangaId).single().id
        backup.title = "Renamed"
        backup.author = "Changed author"
        backup.version = 5
        backup.chapterFlags = 123
        backup.viewer_flags = 456
        backup.customTitle = "My title"
        backup.chapters = listOf(BackupChapter("/chapter", "Renamed chapter", chapterNumber = 4F, read = true, bookmark = true, fillermark = true, version = 8))
        restorer.restore(backup, listOf(category))
        val manga = db.mangasQueries.getAll().awaitAsList().single()
        manga._id shouldBe mangaId
        manga.title shouldBe "Renamed"
        manga.author shouldBe "Changed author"
        manga.chapter_flags shouldBe 123
        manga.viewer shouldBe 456
        manga.version shouldBe 5
        customInfo[mangaId]?.title shouldBe "My title"
        val restoredChapter = chapters.await(mangaId).single()
        restoredChapter.id shouldBe chapterId
        restoredChapter.read shouldBe true
        restoredChapter.bookmark shouldBe true
        restoredChapter.fillermark shouldBe true
        restoredChapter.version shouldBe 8
        tracks.getTracksByMangaId(mangaId).single().remoteId shouldBe 3
        db.historyQueries.getHistoryByChapterUrl(mangaId, "/chapter").awaitAsOne().time_read shouldBe 42
        GetCategories(CategoryRepositoryImpl(db)).await(mangaId).single().name shouldBe "Reading"
        // Only the version changes; identical visible state must still catch up.
        backup.chapters = listOf(BackupChapter(restoredChapter.url, restoredChapter.name, chapterNumber = restoredChapter.chapterNumber.toFloat(), read = true, bookmark = true, fillermark = true, version = 9))
        restorer.restore(backup, listOf(category))
        chapters.await(mangaId).single().version shouldBe 9
        restorer.restore(backup, listOf(category))
        chapters.await(mangaId).single().version shouldBe 9
        db.mangasQueries.getAll().awaitAsList().single().version shouldBe 5
    }

    @Test
    fun `saved search and feed restoration retains filters and avoids duplicates`() = runBlocking<Unit> {
        val search = BackupSavedSearch("Yaoi latest", "", "[saved-filters]", 1)
        repeat(2) {
            SavedSearchRestorer(db).restoreSavedSearches(listOf(search))
            FeedRestorer(db).restoreFeeds(listOf(BackupFeed(1, true, search), BackupFeed(2, true)))
        }
        db.saved_searchQueries.selectAll().awaitAsList().single().filters_json shouldBe search.filterList
        val feeds = db.feed_saved_searchQueries.selectAllFeedWithSavedSearch().awaitAsList()
        feeds.size shouldBe 2
        feeds.single { it.source == 1L }.filters_json shouldBe search.filterList
    }
}
// KMK <--
