package tachiyomi.data.manga

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.data.AndroidDatabaseHandler
import tachiyomi.data.Chapters
import tachiyomi.data.Database
import tachiyomi.data.DateColumnAdapter
import tachiyomi.data.Mangas
import tachiyomi.data.MemoColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.UpdateStrategyColumnAdapter
import tachiyomi.data.chapter.ChapterRepositoryImpl
import tachiyomi.data.history.HistoryRepositoryImpl
import tachiyomi.domain.manga.interactor.GetCustomMangaInfo
import tachiyomi.domain.manga.model.CustomMangaInfo
import tachiyomi.domain.manga.repository.CustomMangaRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingletonFactory
import java.util.Date
import kotlin.random.Random

// KMK -->
/** Verifies cached chapter aggregates against a Kotlin recomputation. */
class MangaChapterStatsTest {

    private lateinit var driver: SqlDriver
    private lateinit var db: Database
    private lateinit var chapterRepository: ChapterRepositoryImpl
    private lateinit var historyRepository: HistoryRepositoryImpl

    private val scanlators = listOf(null, "alpha", "beta", "gamma")

    @BeforeEach
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        Database.Schema.create(driver).value
        driver.execute(null, "PRAGMA foreign_keys = ON", 0)
        db = Database(
            driver = driver,
            historyAdapter = tachiyomi.data.History.Adapter(last_readAdapter = DateColumnAdapter),
            mangasAdapter = Mangas.Adapter(
                genreAdapter = StringListColumnAdapter,
                update_strategyAdapter = UpdateStrategyColumnAdapter,
                memoAdapter = MemoColumnAdapter,
            ),
            chaptersAdapter = Chapters.Adapter(memoAdapter = MemoColumnAdapter),
        )
        val handler = AndroidDatabaseHandler(db = db, driver = driver)
        chapterRepository = ChapterRepositoryImpl(handler)
        historyRepository = HistoryRepositoryImpl(handler)
    }

    @AfterEach
    fun tearDown() {
        driver.close()
    }

    @Test
    fun `aggregates survive a random sequence of writes`() {
        val seed = 20260725
        val rng = Random(seed)
        // KNS
        val opLog = mutableListOf<String>()
        // KNS
        seedLibrary(count = 12)

        repeat(400) { step ->
            // KNS
            val op = applyRandomWrite(rng)
            opLog += "step=$step op=$op"
            // KNS
            try {
                assertRandomAggregatesMatch()
            } catch (e: AssertionError) {
                // KNS
                throw AssertionError(
                    buildString {
                        appendLine("Random aggregate mismatch (seed=$seed, step=$step)")
                        appendLine("Last operations:")
                        opLog.takeLast(80).forEach { appendLine(it) }
                    },
                    e,
                )
                // KNS
            }
        }
    }

    @Test
    fun `deleting an entry leaves no orphaned stats`() {
        seedLibrary(count = 4)
        val ids = db.mangasQueries.getAll(MangaMapper::mapManga).executeAsList().map { it.id }
        ids.forEach { insertChapter(it, scanlator = "alpha", read = true, bookmark = true, fillermark = true) }

        ids.forEach { db.mangasQueries.deleteById(it) }

        statsRowCount() shouldBe 0
    }

    @Test
    fun `direct low-level deletion still recovers the maxima`() {
        seedLibrary(count = 1)
        val mangaId = db.mangasQueries.getAll(MangaMapper::mapManga).executeAsOne().id
        repeat(20) { i ->
            insertChapter(mangaId, scanlator = null, read = true, bookmark = false, fillermark = false, dates = (i + 1).toLong())
        }
        val chapters = chaptersOf(mangaId)
        chapters.forEach { db.historyQueries.upsert(chapterIdOf(it), Date(it.date_upload * 10), 1) }

        val holdingEveryMaximum = chapters.maxBy { it.date_upload }
        db.chaptersQueries.removeChaptersWithIds(listOf(chapterIdOf(holdingEveryMaximum)))

        libraryRow(mangaId).let {
            it.totalChapters shouldBe 19
            it.latestUpload shouldBe 19
            it.chapterFetchedAt shouldBe 19
            it.lastRead shouldBe 190
        }
        assertAggregatesMatch()
    }

    @Test
    fun `adversarial deletion through the repository path stays correct`() {
        seedLibrary(count = 1)
        val mangaId = db.mangasQueries.getAll(MangaMapper::mapManga).executeAsOne().id
        repeat(40) { i ->
            insertChapter(mangaId, scanlator = null, read = true, bookmark = true, fillermark = true, dates = (40 - i).toLong())
        }
        val chapters = chaptersOf(mangaId)
        chapters.forEach { db.historyQueries.upsert(chapterIdOf(it), Date(it.date_upload * 10), 1) }

        deleteChapters(chapters.sortedBy { it.date_upload }.drop(5).map { chapterIdOf(it) })

        libraryRow(mangaId).let {
            it.totalChapters shouldBe 5
            it.latestUpload shouldBe 5
            it.chapterFetchedAt shouldBe 5
            it.lastRead shouldBe 50
        }
        assertAggregatesMatch()
    }

    @Test
    fun `bulk deletion leaves the other entries untouched`() {
        seedLibrary(count = 4)
        val ids = db.mangasQueries.getAll(MangaMapper::mapManga).executeAsList().map { it.id }
        ids.forEach { id ->
            repeat(5) { i ->
                insertChapter(id, scanlator = null, read = true, bookmark = false, fillermark = false, dates = (i + 1).toLong())
            }
        }
        val deleteFrom = listOf(ids[0], ids[2])
        val untouched = ids[1]
        val before = libraryRow(untouched)

        deleteChapters(deleteFrom.flatMap { chaptersOf(it) }.map { chapterIdOf(it) })

        libraryRow(untouched).let {
            it.totalChapters shouldBe before.totalChapters
            it.readCount shouldBe before.readCount
            it.latestUpload shouldBe before.latestUpload
        }
        deleteFrom.forEach { libraryRow(it).totalChapters shouldBe 0 }
        assertAggregatesMatch()
    }

    @Test
    fun `bulk history reset through the repository path stays correct`() {
        seedLibrary(count = 1)
        val mangaId = db.mangasQueries.getAll(MangaMapper::mapManga).executeAsOne().id
        repeat(50) { i ->
            insertChapter(mangaId, scanlator = null, read = true, bookmark = false, fillermark = false, dates = (i + 1).toLong())
        }
        chaptersOf(mangaId).forEach { db.historyQueries.upsert(chapterIdOf(it), Date((60 - it.date_upload) * 10), 1) }
        libraryRow(mangaId).lastRead shouldBe 590

        resetHistoryByMangaIds(listOf(mangaId))

        libraryRow(mangaId).lastRead shouldBe 0
        assertAggregatesMatch()
    }

    @Test
    fun `removing resetted history through the repository path stays correct`() {
        seedLibrary(count = 1)
        val mangaId = db.mangasQueries.getAll(MangaMapper::mapManga).executeAsOne().id
        repeat(20) { i ->
            insertChapter(mangaId, scanlator = null, read = true, bookmark = false, fillermark = false, dates = (i + 1).toLong())
        }
        val chapters = chaptersOf(mangaId).sortedBy { it.date_upload }
        chapters.forEach { db.historyQueries.upsert(chapterIdOf(it), Date(it.date_upload * 10), 1) }

        val newest = chapters.takeLast(10).map { chapterIdOf(it) }.toSet()
        resetHistory(historyIdsFor(mangaId).filter { it.second in newest }.map { it.first })
        removeResettedHistory()

        libraryRow(mangaId).lastRead shouldBe 100
        assertAggregatesMatch()
    }

    @Test
    fun `lowering the newest date recomputes it without a membership change`() {
        seedLibrary(count = 1)
        val mangaId = db.mangasQueries.getAll(MangaMapper::mapManga).executeAsOne().id
        insertChapter(mangaId, scanlator = null, read = false, bookmark = false, fillermark = false, dates = 100)
        insertChapter(mangaId, scanlator = null, read = false, bookmark = false, fillermark = false, dates = 900)
        val newest = chaptersOf(mangaId).maxBy { it.date_upload }

        libraryRow(mangaId).latestUpload shouldBe 900

        db.chaptersQueries.update(
            mangaId = null, url = null, name = null, scanlator = null,
            read = null, bookmark = null, fillermark = null, lastPageRead = null, chapterNumber = null,
            sourceOrder = null, dateFetch = 50, dateUpload = 50,
            chapterId = chapterIdOf(newest), version = null, isSyncing = 0, memo = null,
        )

        libraryRow(mangaId).let {
            it.latestUpload shouldBe 100
            it.chapterFetchedAt shouldBe 100
        }
        assertAggregatesMatch()
    }

    @Test
    fun `raising a date and flipping read in one update keeps both correct`() {
        seedLibrary(count = 1)
        val mangaId = db.mangasQueries.getAll(MangaMapper::mapManga).executeAsOne().id
        insertChapter(mangaId, scanlator = null, read = false, bookmark = false, fillermark = false, dates = 100)
        val chapter = chaptersOf(mangaId).single()

        db.chaptersQueries.update(
            mangaId = null, url = null, name = null, scanlator = null,
            read = true, bookmark = true, fillermark = true, lastPageRead = null, chapterNumber = null,
            sourceOrder = null, dateFetch = 700, dateUpload = 700,
            chapterId = chapterIdOf(chapter), version = null, isSyncing = 0, memo = null,
        )

        libraryRow(mangaId).let {
            it.readCount shouldBe 1
            it.bookmarkCount shouldBe 1
            it.bookmarkReadCount shouldBe 1
            it.fillermarkCount shouldBe 1
            it.fillermarkReadCount shouldBe 1
            it.latestUpload shouldBe 700
        }
        assertAggregatesMatch()
    }

    @Test
    fun `excluding a scanlator removes its chapters from the counts`() {
        seedLibrary(count = 1)
        val mangaId = db.mangasQueries.getAll(MangaMapper::mapManga).executeAsOne().id
        insertChapter(mangaId, scanlator = "alpha", read = true, bookmark = false, fillermark = false)
        insertChapter(mangaId, scanlator = "beta", read = true, bookmark = false, fillermark = false)

        libraryRow(mangaId).totalChapters shouldBe 2

        db.excluded_scanlatorsQueries.insert(mangaId, "beta")
        libraryRow(mangaId).totalChapters shouldBe 1
        assertAggregatesMatch()

        db.excluded_scanlatorsQueries.remove(mangaId, listOf("beta"))
        libraryRow(mangaId).totalChapters shouldBe 2
        assertAggregatesMatch()
    }

    private fun assertRandomAggregatesMatch() {
        db.libraryViewQueries.library(MangaMapper::mapLibraryManga).executeAsList().forEach { row ->
            val expected = recompute(sourceMangaIdsFor(row.id))
            withClue(
                "id=${row.id}, expected=$expected, actual=Aggregate(" +
                    "total=${row.totalChapters}, read=${row.readCount}, bookmark=${row.bookmarkCount}, " +
                    "fillermark=${row.fillermarkCount}, bookmarkRead=${row.bookmarkReadCount}, " +
                    "fillermarkRead=${row.fillermarkReadCount}, latestUpload=${row.latestUpload}, " +
                    "fetchedAt=${row.chapterFetchedAt}, lastRead=${row.lastRead})",
            ) {
                row.totalChapters shouldBe expected.total
                row.readCount shouldBe expected.read
                row.latestUpload shouldBe expected.latestUpload
                row.chapterFetchedAt shouldBe expected.fetchedAt
                row.lastRead shouldBe expected.lastRead
            }
        }
    }

    private fun assertAggregatesMatch() {
        db.libraryViewQueries.library(MangaMapper::mapLibraryManga).executeAsList().forEach { row ->
            val expected = recompute(sourceMangaIdsFor(row.id))
            withClue(
                "id=${row.id}, expected=$expected, actual=Aggregate(" +
                    "total=${row.totalChapters}, read=${row.readCount}, bookmark=${row.bookmarkCount}, " +
                    "fillermark=${row.fillermarkCount}, bookmarkRead=${row.bookmarkReadCount}, " +
                    "fillermarkRead=${row.fillermarkReadCount}, latestUpload=${row.latestUpload}, " +
                    "fetchedAt=${row.chapterFetchedAt}, lastRead=${row.lastRead})",
            ) {
                row.totalChapters shouldBe expected.total
                row.readCount shouldBe expected.read
                row.bookmarkCount shouldBe expected.bookmark
                row.bookmarkReadCount shouldBe expected.bookmarkRead
                row.fillermarkCount shouldBe expected.fillermark
                row.fillermarkReadCount shouldBe expected.fillermarkRead
                row.latestUpload shouldBe expected.latestUpload
                row.chapterFetchedAt shouldBe expected.fetchedAt
                row.lastRead shouldBe expected.lastRead
            }
        }
    }

    private fun sourceMangaIdsFor(mangaId: Long): List<Long> {
        val children = db.mergedQueries.selectByMergeId(mangaId).executeAsList().mapNotNull { it.manga_id }
        return children.ifEmpty { listOf(mangaId) }
    }

    private data class Aggregate(
        val total: Long = 0,
        val read: Long = 0,
        val bookmark: Long = 0,
        val fillermark: Long = 0,
        val bookmarkRead: Long = 0,
        val fillermarkRead: Long = 0,
        val latestUpload: Long = 0,
        val fetchedAt: Long = 0,
        val lastRead: Long = 0,
    )

    private fun recompute(mangaIds: List<Long>): Aggregate {
        var agg = Aggregate()
        mangaIds.forEach { mangaId ->
            val excluded = db.excluded_scanlatorsQueries.getExcludedScanlatorsByMangaId(mangaId)
                .executeAsList()
                .filterNotNull()
                .toSet()
            val history = db.historyQueries.getHistoryByMangaId(mangaId) { _, chapterId, lastRead, _ ->
                chapterId to (lastRead?.time ?: 0L)
            }.executeAsList().toMap()

            chaptersOf(mangaId)
                .filter { it.scanlator !in excluded }
                .forEach { chapter ->
                    agg = agg.copy(
                        total = agg.total + 1,
                        read = agg.read + if (chapter.read) 1 else 0,
                        bookmark = agg.bookmark + if (chapter.bookmark) 1 else 0,
                        fillermark = agg.fillermark + if (chapter.fillermark) 1 else 0,
                        bookmarkRead = agg.bookmarkRead + if (chapter.bookmark && chapter.read) 1 else 0,
                        fillermarkRead = agg.fillermarkRead + if (chapter.fillermark && chapter.read) 1 else 0,
                        latestUpload = maxOf(agg.latestUpload, chapter.date_upload),
                        fetchedAt = maxOf(agg.fetchedAt, chapter.date_fetch),
                        lastRead = maxOf(agg.lastRead, history[chapterIdOf(chapter)] ?: 0L),
                    )
                }
        }
        return agg
    }

    private fun applyRandomWrite(rng: Random): String {
        val mangaIds = db.mangasQueries.getAll(MangaMapper::mapManga).executeAsList().map { it.id }
        val chapters = mangaIds.flatMap { chaptersOf(it) }

        return when (rng.nextInt(10)) {
            0, 1, 2 -> {
                val targetMangaId = mangaIds[rng.nextInt(mangaIds.size)]
                val scanlator = scanlators[rng.nextInt(scanlators.size)]
                val read = rng.nextBoolean()
                val bookmark = rng.nextBoolean()
                val fillermark = rng.nextBoolean()
                insertChapter(targetMangaId, scanlator, read, bookmark, fillermark, rng)
                "insertChapter(mangaId=$targetMangaId, scanlator=$scanlator, read=$read, bookmark=$bookmark, fillermark=$fillermark)"
            }
            3, 4 -> if (chapters.isNotEmpty()) {
                val chapter = chapters[rng.nextInt(chapters.size)]
                db.chaptersQueries.update(
                    mangaId = null,
                    url = null,
                    name = null,
                    scanlator = null,
                    read = rng.nextBoolean(),
                    bookmark = rng.nextBoolean(),
                    fillermark = rng.nextBoolean(),
                    lastPageRead = rng.nextLong(50),
                    chapterNumber = null,
                    sourceOrder = null,
                    dateFetch = null,
                    dateUpload = null,
                    chapterId = chapterIdOf(chapter),
                    version = null,
                    isSyncing = 0,
                    memo = null,
                )
                "updateFlags(chapterId=${chapterIdOf(chapter)})"
            } else {
                "noop(updateFlags no chapters)"
            }
            5 -> if (chapters.isNotEmpty()) {
                val chapter = chapters[rng.nextInt(chapters.size)]
                db.chaptersQueries.update(
                    mangaId = null, url = null, name = null, scanlator = null,
                    read = null, bookmark = null, fillermark = null, lastPageRead = rng.nextLong(50),
                    chapterNumber = null, sourceOrder = null, dateFetch = null, dateUpload = null,
                    chapterId = chapterIdOf(chapter), version = null, isSyncing = 0, memo = null,
                )
                "updateLastPageRead(chapterId=${chapterIdOf(chapter)})"
            } else {
                "noop(updateLastPageRead no chapters)"
            }
            6 -> if (chapters.isNotEmpty()) {
                val chapter = chapters[rng.nextInt(chapters.size)]
                val scanlator = scanlators[rng.nextInt(scanlators.size)]
                // KNS
                val dateFetch = rng.nextLong(1, 5_000)
                val dateUpload = rng.nextLong(1, 5_000)
                // KNS
                db.chaptersQueries.update(
                    mangaId = null, url = null, name = null, scanlator = scanlator,
                    read = null, bookmark = null, fillermark = null, lastPageRead = null, chapterNumber = null,
                    sourceOrder = null, dateFetch = dateFetch,
                    dateUpload = dateUpload,
                    chapterId = chapterIdOf(chapter), version = null, isSyncing = 0, memo = null,
                )
                "updateMeta(chapterId=${chapterIdOf(chapter)}, scanlator=$scanlator, dateFetch=$dateFetch, dateUpload=$dateUpload)"
            } else {
                "noop(updateMeta no chapters)"
            }
            7 -> if (chapters.isNotEmpty()) {
                val chapter = chapters[rng.nextInt(chapters.size)]
                db.historyQueries.upsert(chapterIdOf(chapter), Date(rng.nextLong(1, 9_000)), rng.nextLong(1, 60))
                "upsertHistory(chapterId=${chapterIdOf(chapter)})"
            } else {
                "noop(upsertHistory no chapters)"
            }
            8 -> when (rng.nextInt(3)) {
                0 -> {
                    val mangaId = mangaIds[rng.nextInt(mangaIds.size)]
                    db.historyQueries.resetHistoryByMangaIds(listOf(mangaId))
                    "resetHistoryByMangaId(mangaId=$mangaId)"
                }
                1 -> {
                    db.historyQueries.removeAllHistory()
                    "removeAllHistory()"
                }
                else -> if (chapters.isNotEmpty()) {
                    val chapter = chapters[rng.nextInt(chapters.size)]
                    deleteChapters(listOf(chapterIdOf(chapter)))
                    "deleteChapter(chapterId=${chapterIdOf(chapter)})"
                } else {
                    "noop(deleteChapter no chapters)"
                }
            }
            else -> {
                val mangaId = mangaIds[rng.nextInt(mangaIds.size)]
                val scanlatorPool = scanlators.filterNotNull()
                val scanlator = scanlatorPool[rng.nextInt(scanlatorPool.size)]
                val current = db.excluded_scanlatorsQueries.getExcludedScanlatorsByMangaId(mangaId)
                    .executeAsList()
                if (scanlator in current) {
                    db.excluded_scanlatorsQueries.remove(mangaId, listOf(scanlator))
                    "removeExcludedScanlator(mangaId=$mangaId, scanlator=$scanlator)"
                } else {
                    db.excluded_scanlatorsQueries.insert(mangaId, scanlator)
                    "insertExcludedScanlator(mangaId=$mangaId, scanlator=$scanlator)"
                }
            }
        }
    }

    private fun seedLibrary(count: Int) {
        repeat(count) { i ->
            db.mangasQueries.insert(
                source = if (count >= 3 && i == count - 1) MERGED_SOURCE_ID else 1L,
                url = "/manga/$i",
                artist = null,
                author = null,
                description = null,
                genre = null,
                title = "Manga $i",
                status = 0,
                thumbnailUrl = null,
                favorite = true,
                lastUpdate = 0,
                nextUpdate = 0,
                initialized = true,
                viewerFlags = 0,
                chapterFlags = 0,
                coverLastModified = 0,
                dateAdded = 0,
                updateStrategy = UpdateStrategy.ALWAYS_UPDATE,
                calculateInterval = 0,
                version = 0,
                notes = "",
                memo = JsonObject(emptyMap()),
            )
        }
        if (count >= 3) {
            val ids = db.mangasQueries.getAll(MangaMapper::mapManga).executeAsList().map { it.id }
            val mergeId = ids.last()
            listOf(ids[0], ids[1]).forEach { childId ->
                db.mergedQueries.insert(
                    infoManga = true,
                    getChapterUpdates = true,
                    chapterSortMode = 0,
                    chapterPriority = 0,
                    downloadChapters = true,
                    mergeId = mergeId,
                    mergeUrl = "/merge",
                    mangaId = childId,
                    mangaUrl = "/manga/$childId",
                    mangaSource = 1,
                )
            }
        }
    }

    private fun insertChapter(
        mangaId: Long,
        scanlator: String?,
        read: Boolean,
        bookmark: Boolean,
        fillermark: Boolean,
        rng: Random = Random(0),
        dates: Long? = null,
    ) {
        db.chaptersQueries.insert(
            mangaId = mangaId,
            url = "/chapter/${rng.nextLong()}",
            name = "Chapter",
            scanlator = scanlator,
            read = read,
            bookmark = bookmark,
            fillermark = fillermark,
            lastPageRead = 0,
            chapterNumber = 1.0,
            sourceOrder = 0,
            dateFetch = dates ?: rng.nextLong(1, 5_000),
            dateUpload = dates ?: rng.nextLong(1, 5_000),
            version = 0,
            memo = JsonObject(emptyMap()),
        )
    }

    private fun deleteChapters(chapterIds: List<Long>) = runBlocking {
        chapterRepository.removeChaptersWithIds(chapterIds)
    }

    private fun historyIdsFor(mangaId: Long): List<Pair<Long, Long>> = db.historyQueries
        .getHistoryByMangaId(mangaId) { id, chapterId, _, _ -> id to chapterId }
        .executeAsList()

    private fun resetHistory(historyIds: List<Long>) = runBlocking {
        historyRepository.resetHistory(historyIds)
    }

    private fun resetHistoryByMangaIds(mangaIds: List<Long>) = runBlocking {
        historyRepository.resetHistoryByMangaIds(mangaIds)
    }

    private fun removeResettedHistory() = runBlocking {
        historyRepository.removeResettedHistory()
    }

    private fun chaptersOf(mangaId: Long): List<Chapters> = db.chaptersQueries
        .getChaptersByMangaId(
            mangaId = mangaId,
            applyFilter = 0L,
            bookmarkMask = 0L,
            bookmarkUnmask = 0L,
            fillermarkMask = 0L,
            fillermarkUnmask = 0L,
        )
        .executeAsList()

    private fun chapterIdOf(chapter: Chapters): Long = chapter._id

    private fun libraryRow(mangaId: Long) = db.libraryViewQueries
        .library(MangaMapper::mapLibraryManga)
        .executeAsList()
        .single { it.id == mangaId }

    private fun statsRowCount(): Long = driver.executeQuery(
        identifier = null,
        sql = "SELECT count(*) FROM manga_chapter_stats",
        parameters = 0,
        mapper = { cursor ->
            cursor.next()
            QueryResult.Value(cursor.getLong(0)!!)
        },
    ).value

    private fun <T> withClue(clue: Any, block: () -> T): T = try {
        block()
    } catch (e: AssertionError) {
        throw AssertionError("manga $clue: ${e.message}", e)
    }

    companion object {
        private const val MERGED_SOURCE_ID = 6969L

        @JvmStatic
        @BeforeAll
        fun registerCustomMangaInfo() {
            Injekt.addSingletonFactory {
                GetCustomMangaInfo(
                    object : CustomMangaRepository {
                        override fun get(mangaId: Long) = null
                        override fun set(mangaInfo: CustomMangaInfo) = Unit
                    },
                )
            }
        }
    }
}
// KMK <--
