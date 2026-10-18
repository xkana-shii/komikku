package tachiyomi.data.manga

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.data.Database
import tachiyomi.data.chapter.ChapterMapper
import tachiyomi.data.chapter.ChapterRepositoryImpl
import tachiyomi.data.testDatabase
import tachiyomi.domain.chapter.model.BookmarkColor
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.interactor.GetCustomMangaInfo
import tachiyomi.domain.manga.model.CustomMangaInfo
import tachiyomi.domain.manga.repository.CustomMangaRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingletonFactory
import kotlin.random.Random
import kotlin.time.Instant

// KMK -->
/**
 * Verifies the library aggregates, cached or live, against
 * independent Kotlin recomputation.
 *
 * Library reads are read-only: cache refills are explicit.
 */
class MangaChapterStatsTest {

    private lateinit var driver: SqlDriver
    private lateinit var db: Database

    private var nextChapterUrlId = 0L

    private val scanlators = listOf(
        null,
        "alpha",
        "beta",
        "gamma",
    )

    @BeforeEach
    fun setUp() = runBlocking<Unit> {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)

        Database.Schema.create(driver).await()

        driver.execute(
            null,
            "PRAGMA foreign_keys = ON",
            0,
        ).await()

        db = testDatabase(driver)
        nextChapterUrlId = 0L
    }

    @AfterEach
    fun tearDown() {
        driver.close()
    }

    @Test
    fun `visibility revisions isolate writes and propagate to merged parents`() = runBlocking<Unit> {
        seedLibrary(4)

        insertChapter(
            mangaId = 1L,
            scanlator = "alpha",
            read = false,
            bookmark = false,
        )

        suspend fun revisions(): Map<Long, Long> {
            return db.libraryViewQueries
                .library(MangaMapper::mapLibraryManga)
                .awaitAsList()
                .associate { it.id to it.visibilityRevision }
        }

        suspend fun assertChanged(
            sql: String,
            expected: Set<Long>,
        ) {
            val before = revisions()

            driver.execute(
                null,
                sql,
                0,
            ).await()

            val after = revisions()

            after.keys
                .filter { after[it] != before[it] }
                .toSet() shouldBe expected
        }

        val affected = setOf(1L, 4L)

        listOf(
            "remote_scanlator = 'beta'",
            "remote_chapter_number = 2.0",
            "remote_url = '/changed'",
            "user_read = 1",
            "user_bookmark = 1",
            "user_bookmark_color = 2",
        ).forEach { modification ->
            assertChanged(
                "UPDATE chapter SET $modification WHERE manga_id = 1",
                affected,
            )
        }

        assertChanged(
            "UPDATE chapter SET user_last_page_read = 17 WHERE manga_id = 1",
            emptySet(),
        )

        assertChanged(
            "UPDATE manga SET remote_title = 'new title' WHERE id = 3",
            emptySet(),
        )

        assertChanged(
            "UPDATE manga SET remote_title = 'child renamed' WHERE id = 1",
            setOf(4L),
        )

        assertChanged(
            "UPDATE manga SET source_id = 2 WHERE id = 1",
            affected,
        )

        assertChanged(
            """
            INSERT INTO excluded_scanlator(manga_id, scanlator)
            VALUES(1, 'beta')
            """.trimIndent(),
            affected,
        )

        assertChanged(
            """
            UPDATE excluded_scanlator
            SET scanlator = 'gamma'
            WHERE manga_id = 1
            """.trimIndent(),
            affected,
        )

        assertChanged(
            "DELETE FROM excluded_scanlator WHERE manga_id = 1",
            affected,
        )

        assertChanged(
            "UPDATE merged SET chapter_priority = 42 WHERE manga_id = 1",
            setOf(4L),
        )

        assertChanged(
            """
            UPDATE manga
            SET user_blacklisted_chapters = 'url:/changed'
            WHERE id = 1
            """.trimIndent(),
            affected,
        )

        assertChanged(
            """
            UPDATE manga
            SET user_scanlator_priority = 'beta'
            WHERE id = 1
            """.trimIndent(),
            affected,
        )

        assertChanged(
            """
            UPDATE manga
            SET user_scanlator_range_rules = '1:2:beta'
            WHERE id = 1
            """.trimIndent(),
            affected,
        )

        // Sync writes still invalidate visibility.
        assertChanged(
            """
            UPDATE chapter
            SET is_syncing = 1,
                remote_scanlator = 'restore'
            WHERE manga_id = 1
            """.trimIndent(),
            affected,
        )

        // Moving a chapter invalidates the old manga, the new
        // manga, and the source manga's merged parent.
        assertChanged(
            "UPDATE chapter SET manga_id = 3 WHERE manga_id = 1",
            setOf(1L, 3L, 4L),
        )

        assertChanged(
            "DELETE FROM chapter WHERE manga_id = 3",
            setOf(3L),
        )

        val beforeInsert = revisions()

        insertChapter(
            mangaId = 3L,
            scanlator = "alpha",
            read = false,
            bookmark = false,
        )

        val afterInsert = revisions()

        afterInsert.keys
            .filter { beforeInsert[it] != afterInsert[it] }
            .toSet() shouldBe setOf(3L)

        val deletedRevision = afterInsert.getValue(3L)

        driver.execute(
            null,
            "DELETE FROM manga WHERE id = 3",
            0,
        ).await()

        revisions().containsKey(3L) shouldBe false

        // Recreating a deleted row cannot reuse a stale token.
        driver.execute(
            null,
            """
            INSERT INTO manga(
                id, source_id, remote_url, remote_title,
                remote_status, user_favorite_at,
                state_initialized, user_reader_flags,
                user_chapter_flags, state_cover_last_modified,
                state_date_added
            )
            VALUES(
                3, 1, '/restored', 'Restored',
                0, 1, 1, 0, 0, 0, 0
            )
            """.trimIndent(),
            0,
        ).await()

        (revisions().getValue(3L) > deletedRevision) shouldBe true
    }

    @Test
    fun `fresh database schema provides visibility revision tracking`() = runBlocking<Unit> {
        val mangaId = insertManga(favorite = true)

        val initial = libraryRow(mangaId).visibilityRevision

        insertChapter(
            mangaId = mangaId,
            scanlator = "alpha",
            read = false,
            bookmark = false,
        )

        val updated = libraryRow(mangaId).visibilityRevision

        (updated > initial) shouldBe true
    }

    @Test
    fun `library matches a live recount with and without cached rows`() = runBlocking<Unit> {
        val rng = Random(20260725)

        seedLibrary(count = 12)

        repeat(400) {
            applyRandomWrite(rng)

            // Missing cache rows fall back to live aggregates.
            assertAggregatesMatch()

            refill()

            hasMissing() shouldBe false
            assertAggregatesMatch()
        }
    }

    @Test
    fun `refill caches every library entry except merged ones`() = runBlocking<Unit> {
        seedLibrary(count = 4)

        val ids = db.mangaQueries
            .getAll(MangaMapper::mapManga)
            .awaitAsList()
            .map { it.id }

        insertChapter(
            mangaId = ids[0],
            scanlator = null,
            read = true,
            bookmark = false,
        )

        hasMissing() shouldBe true

        refill()

        // Merged parents are aggregated live.
        statsRowCount() shouldBe 3L
        hasMissing() shouldBe false

        assertAggregatesMatch()
    }

    @Test
    fun `writes the aggregates ignore keep the cached row`() = runBlocking<Unit> {
        seedLibrary(count = 1)

        val mangaId = db.mangaQueries
            .getAll(MangaMapper::mapManga)
            .awaitAsOne()
            .id

        insertChapter(
            mangaId = mangaId,
            scanlator = null,
            read = false,
            bookmark = false,
        )

        refill()

        val chapter = chaptersOf(mangaId).single()

        // Reader progress does not affect aggregate counts.
        db.chapterQueries.update(
            read = null,
            bookmark = null,
            bookmarkColor = null,
            lastPageRead = 7,
            dateFetch = null,
            version = null,
            lastModifiedAt = null,
            isSyncing = 0L,
            chapterId = chapter.id,
        )

        // Rewriting unchanged values does not drop the cache.
        db.chapterQueries.update(
            read = false,
            bookmark = false,
            bookmarkColor = null,
            lastPageRead = null,
            dateFetch = null,
            version = null,
            lastModifiedAt = null,
            isSyncing = 0L,
            chapterId = chapter.id,
        )

        hasMissing() shouldBe false
    }

    @Test
    fun `adding an entry to the library marks it missing`() = runBlocking<Unit> {
        seedLibrary(count = 1)
        refill()

        val mangaId = insertManga(favorite = false)

        insertChapter(
            mangaId = mangaId,
            scanlator = null,
            read = true,
            bookmark = true,
        )

        hasMissing() shouldBe false

        driver.execute(
            null,
            "UPDATE manga SET user_favorite_at = 0 WHERE id = $mangaId",
            0,
        ).await()

        hasMissing() shouldBe true
        libraryRow(mangaId).readCount shouldBe 1L

        refill()

        libraryRow(mangaId).readCount shouldBe 1L
        assertAggregatesMatch()
    }

    @Test
    fun `bookmark color lookup uses OR semantics and only includes bookmarked chapters`() = runBlocking<Unit> {
        val redManga = insertManga(favorite = true)
        val blueManga = insertManga(favorite = true)
        val unbookmarkedManga = insertManga(favorite = true)

        insertChapter(
            mangaId = redManga,
            scanlator = null,
            read = false,
            bookmark = true,
            bookmarkColor = BookmarkColor.RED.value.toLong(),
            rng = Random(1),
        )

        insertChapter(
            mangaId = blueManga,
            scanlator = null,
            read = false,
            bookmark = true,
            bookmarkColor = BookmarkColor.BLUE.value.toLong(),
            rng = Random(2),
        )

        insertChapter(
            mangaId = unbookmarkedManga,
            scanlator = null,
            read = false,
            bookmark = false,
            bookmarkColor = BookmarkColor.NONE.value.toLong(),
            rng = Random(3),
        )

        db.chapterQueries.getMangaIdsWithBookmarkColors(
            listOf(
                BookmarkColor.RED.value.toLong(),
                BookmarkColor.BLUE.value.toLong(),
            ),
        ).awaitAsList().toSet() shouldBe setOf(redManga, blueManga)

        db.chapterQueries.getMangaIdsWithBookmarkColors(
            listOf(BookmarkColor.NONE.value.toLong()),
        ).awaitAsList() shouldBe emptyList<Long>()
    }

    @Test
    fun `deleting an entry leaves no orphaned stats`() = runBlocking<Unit> {
        seedLibrary(count = 4)

        val ids = db.mangaQueries
            .getAll(MangaMapper::mapManga)
            .awaitAsList()
            .map { it.id }

        ids.forEach { mangaId ->
            insertChapter(
                mangaId = mangaId,
                scanlator = "alpha",
                read = true,
                bookmark = true,
            )
        }

        refill()

        ids.forEach { id ->
            db.mangaQueries.deleteById(id)
        }

        statsRowCount() shouldBe 0L
    }

    @Test
    fun `live library invalidations emit changed aggregates without automatic cache refills`() = runBlocking<Unit> {
        seedLibrary(count = 1)

        val id = db.mangaQueries
            .getAll(MangaMapper::mapManga)
            .awaitAsOne()
            .id

        insertChapter(
            mangaId = id,
            scanlator = null,
            read = false,
            bookmark = false,
        )

        val chapter = chaptersOf(id).single()

        val emissions = Channel<List<LibraryManga>>(
            Channel.UNLIMITED,
        )

        val collector = launch {
            MangaRepositoryImpl(db)
                .getLibraryMangaAsFlow()
                .collect { emissions.send(it) }
        }

        try {
            withTimeout(3_000) {
                emissions.receive()
            }.single().readCount shouldBe 0L

            // Reading the library does not write cache rows.
            hasMissing() shouldBe true

            ChapterRepositoryImpl(db).update(
                ChapterUpdate(chapter.id) {
                    read = true
                },
            )

            withTimeout(3_000) {
                emissions.receive()
            }.single().readCount shouldBe 1L

            hasMissing() shouldBe true

            withTimeoutOrNull(600) {
                emissions.receive()
            } shouldBe null

            ChapterRepositoryImpl(db).update(
                ChapterUpdate(chapter.id) {
                    read = false
                },
            )

            withTimeout(3_000) {
                emissions.receive()
            }.single().readCount shouldBe 0L

            hasMissing() shouldBe true

            // Explicit cache maintenance remains functional.
            refill()

            hasMissing() shouldBe false
        } finally {
            collector.cancelAndJoin()
        }
    }

    @Test
    fun `collecting a current library does not write the statistics cache again`() = runBlocking<Unit> {
        seedLibrary(count = 1)
        refill()

        driver.execute(
            null,
            "CREATE TABLE refill_audit(count INTEGER NOT NULL)",
            0,
        ).await()

        driver.execute(
            null,
            "INSERT INTO refill_audit VALUES (0)",
            0,
        ).await()

        driver.execute(
            null,
            """
            CREATE TRIGGER count_extra_refills
            AFTER INSERT ON manga_chapter_stats
            BEGIN
                UPDATE refill_audit SET count = count + 1;
            END
            """.trimIndent(),
            0,
        ).await()

        val repository = MangaRepositoryImpl(db)

        repository.getLibraryMangaAsFlow().first().size shouldBe 1
        repository.getLibraryMangaAsFlow().first().size shouldBe 1

        hasMissing() shouldBe false

        driver.executeQuery(
            identifier = null,
            sql = "SELECT count FROM refill_audit",
            mapper = { cursor ->
                cursor.next()
                QueryResult.Value(cursor.getLong(0)!!)
            },
            parameters = 0,
        ).value shouldBe 0L
    }

    @Test
    fun `library flow returns correct live aggregates without automatically refilling cache`() = runBlocking<Unit> {
        seedLibrary(count = 4)

        val ids = db.mangaQueries
            .getAll(MangaMapper::mapManga)
            .awaitAsList()
            .map { it.id }

        ids.forEach { mangaId ->
            insertChapter(
                mangaId = mangaId,
                scanlator = null,
                read = true,
                bookmark = false,
            )
        }

        hasMissing() shouldBe true

        val library = MangaRepositoryImpl(db)
            .getLibraryMangaAsFlow()
            .first()

        library.size shouldBe 4

        // Live aggregates are accurate without cache writes.
        assertAggregatesMatch()
        hasMissing() shouldBe true

        // Explicit refill makes the cache current.
        refill()

        hasMissing() shouldBe false
        assertAggregatesMatch()
    }

    @Test
    fun `one-shot library read returns live aggregates without automatically refilling cache`() = runBlocking<Unit> {
        seedLibrary(count = 4)

        val ids = db.mangaQueries
            .getAll(MangaMapper::mapManga)
            .awaitAsList()
            .map { it.id }

        ids.forEach { mangaId ->
            insertChapter(
                mangaId = mangaId,
                scanlator = null,
                read = true,
                bookmark = false,
            )
        }

        hasMissing() shouldBe true

        val library = MangaRepositoryImpl(db)
            .getLibraryManga()

        library.size shouldBe 4

        assertAggregatesMatch()

        // A library read must not have a write side effect.
        hasMissing() shouldBe true

        refill()

        hasMissing() shouldBe false
        assertAggregatesMatch()
    }

    @Test
    fun `a failing refill still returns the live aggregates`() = runBlocking<Unit> {
        seedLibrary(count = 4)

        val ids = db.mangaQueries
            .getAll(MangaMapper::mapManga)
            .awaitAsList()
            .map { it.id }

        ids.forEach { mangaId ->
            insertChapter(
                mangaId = mangaId,
                scanlator = null,
                read = true,
                bookmark = true,
            )
        }

        driver.execute(
            null,
            """
            CREATE TRIGGER fail_refill
            BEFORE INSERT ON manga_chapter_stats
            BEGIN
                SELECT RAISE(ABORT, 'full');
            END
            """.trimIndent(),
            0,
        ).await()

        // Actually exercise the broken refill path.
        val refillResult = runCatching {
            refill()
        }

        refillResult.isFailure shouldBe true

        // Library reads remain usable because the SQL view
        // computes live aggregates independently of this cache.
        MangaRepositoryImpl(db)
            .getLibraryMangaAsFlow()
            .first()
            .size shouldBe 4

        MangaRepositoryImpl(db)
            .getLibraryManga()
            .size shouldBe 4

        hasMissing() shouldBe true
        assertAggregatesMatch()
    }

    // -------------------------------------------------------------
    // Independent aggregation oracle
    // -------------------------------------------------------------

    private suspend fun assertAggregatesMatch() {
        db.libraryViewQueries
            .library(MangaMapper::mapLibraryManga)
            .awaitAsList()
            .forEach { row ->
                val expected = recompute(
                    sourceMangaIdsFor(row.id),
                )

                withClue("$row expected=$expected") {
                    row.totalChapters shouldBe expected.total
                    row.readCount shouldBe expected.read
                    row.bookmarkCount shouldBe expected.bookmark
                    row.bookmarkReadCount shouldBe expected.bookmarkRead

                    (
                        row.latestUpload?.toEpochMilliseconds() ?: 0L
                        ) shouldBe expected.latestUpload

                    (
                        row.chapterFetchedAt?.toEpochMilliseconds() ?: 0L
                        ) shouldBe expected.fetchedAt

                    (
                        row.lastRead?.toEpochMilliseconds() ?: 0L
                        ) shouldBe expected.lastRead
                }
            }
    }

    /**
     * A merged entry aggregates its child chapters.
     * Other manga aggregate their own chapters.
     */
    private suspend fun sourceMangaIdsFor(
        mangaId: Long,
    ): List<Long> {
        val children = db.mergedQueries
            .selectByMergeId(mangaId)
            .awaitAsList()
            .mapNotNull { it.manga_id }

        return children.ifEmpty {
            listOf(mangaId)
        }
    }

    private data class Aggregate(
        val total: Long = 0L,
        val read: Long = 0L,
        val bookmark: Long = 0L,
        val bookmarkRead: Long = 0L,
        val latestUpload: Long = 0L,
        val fetchedAt: Long = 0L,
        val lastRead: Long = 0L,
    )

    private suspend fun recompute(
        mangaIds: List<Long>,
    ): Aggregate {
        var aggregate = Aggregate()

        mangaIds.forEach { mangaId ->
            val excluded = db.excluded_scanlatorQueries
                .getExcludedScanlatorsByMangaId(mangaId)
                .awaitAsList()
                .toSet()

            val historyByChapter = db.historyQueries
                .getHistoryByMangaId(mangaId) {
                        _,
                        chapterId,
                        readAt,
                        _,
                    ->
                    chapterId to readAt?.toEpochMilliseconds()
                }
                .awaitAsList()
                .toMap()

            val chapters = db.chapterQueries
                .getChaptersByMangaId(
                    mangaId,
                    0L,
                    0L,
                    0L,
                    ChapterMapper::mapChapter,
                )
                .awaitAsList()
                .filter { it.scanlator !in excluded }

            chapters.forEach { chapter ->
                val lastRead = historyByChapter[chapter.id] ?: 0L

                aggregate = aggregate.copy(
                    total = aggregate.total + 1L,
                    read = aggregate.read +
                        if (chapter.read) 1L else 0L,
                    bookmark = aggregate.bookmark +
                        if (chapter.bookmark) 1L else 0L,
                    bookmarkRead = aggregate.bookmarkRead +
                        if (chapter.bookmark && chapter.read) 1L else 0L,
                    latestUpload = maxOf(
                        aggregate.latestUpload,
                        chapter.dateUpload?.toEpochMilliseconds() ?: 0L,
                    ),
                    fetchedAt = maxOf(
                        aggregate.fetchedAt,
                        chapter.dateFetch.toEpochMilliseconds(),
                    ),
                    lastRead = maxOf(
                        aggregate.lastRead,
                        lastRead,
                    ),
                )
            }
        }

        return aggregate
    }

    // -------------------------------------------------------------
    // Randomized database writes
    // -------------------------------------------------------------

    private suspend fun applyRandomWrite(rng: Random) {
        val mangaIds = db.mangaQueries
            .getAll(MangaMapper::mapManga)
            .awaitAsList()
            .map { it.id }

        val chapters = mangaIds.flatMap {
            chaptersOf(it)
        }

        when (rng.nextInt(10)) {
            0, 1, 2 -> insertChapter(
                mangaId = mangaIds.random(rng),
                scanlator = scanlators.random(rng),
                read = rng.nextBoolean(),
                bookmark = rng.nextBoolean(),
                rng = rng,
            )

            3, 4 -> chapters.randomOrNull(rng)?.let { chapter ->
                db.chapterQueries.update(
                    read = rng.nextBoolean(),
                    bookmark = rng.nextBoolean(),
                    bookmarkColor = 0L,
                    lastPageRead = rng.nextInt(50),
                    dateFetch = null,
                    version = null,
                    lastModifiedAt = null,
                    isSyncing = 0L,
                    chapterId = chapter.id,
                )
            }

            5 -> chapters.randomOrNull(rng)?.let { chapter ->
                // Reader page progress must not invalidate
                // aggregate statistics.
                db.chapterQueries.update(
                    read = null,
                    bookmark = null,
                    bookmarkColor = null,
                    lastPageRead = rng.nextInt(50),
                    dateFetch = null,
                    version = null,
                    lastModifiedAt = null,
                    isSyncing = 0L,
                    chapterId = chapter.id,
                )
            }

            6 -> chapters.randomOrNull(rng)?.let { chapter ->
                // Remote metadata and locally fetched date
                // are updated independently.
                db.chapterQueries.updateRemote(
                    name = chapter.name,
                    scanlator = scanlators.random(rng),
                    chapterNumber = chapter.chapterNumber,
                    sourceOrder = chapter.sourceOrder,
                    dateUpload = Instant.fromEpochMilliseconds(
                        rng.nextLong(1, 5_000),
                    ),
                    memo = chapter.memo,
                    chapterId = chapter.id,
                )

                db.chapterQueries.update(
                    read = null,
                    bookmark = null,
                    bookmarkColor = null,
                    lastPageRead = null,
                    dateFetch = Instant.fromEpochMilliseconds(
                        rng.nextLong(1, 5_000),
                    ),
                    version = null,
                    lastModifiedAt = null,
                    isSyncing = 0L,
                    chapterId = chapter.id,
                )
            }

            7 -> chapters.randomOrNull(rng)?.let { chapter ->
                db.historyQueries.upsert(
                    chapterId = chapter.id,
                    readAt = Instant.fromEpochMilliseconds(
                        rng.nextLong(1, 9_000),
                    ),
                    readDuration = rng.nextLong(1, 60),
                )
            }

            8 -> when (rng.nextInt(5)) {
                0 -> db.historyQueries.resetHistoryByMangaIds(
                    listOf(mangaIds.random(rng)),
                )

                1 -> db.historyQueries.removeAllHistory()

                2 -> chapters.randomOrNull(rng)?.let { chapter ->
                    val target = mangaIds
                        .filter { it != chapter.mangaId }
                        .random(rng)

                    moveChapter(
                        chapterId = chapter.id,
                        mangaId = target,
                    )
                }

                3 -> db.historyQueries.removeResettedHistory()

                else -> chapters.randomOrNull(rng)?.let {
                    deleteChapters(listOf(it.id))
                }
            }

            else -> {
                val mangaId = mangaIds.random(rng)
                val scanlator = scanlators
                    .filterNotNull()
                    .random(rng)

                val current = db.excluded_scanlatorQueries
                    .getExcludedScanlatorsByMangaId(mangaId)
                    .awaitAsList()

                if (scanlator in current) {
                    db.excluded_scanlatorQueries.remove(
                        mangaId,
                        listOf(scanlator),
                    )
                } else {
                    db.excluded_scanlatorQueries.insert(
                        mangaId,
                        scanlator,
                    )
                }
            }
        }
    }

    // -------------------------------------------------------------
    // Test data
    // -------------------------------------------------------------

    private suspend fun seedLibrary(count: Int) {
        repeat(count) { index ->
            val source = if (
                count >= 3 && index == count - 1
            ) {
                MERGED_SOURCE_ID
            } else {
                1L
            }

            insertManga(
                favorite = true,
                source = source,
            )
        }

        if (count >= 3) {
            val ids = db.mangaQueries
                .getAll(MangaMapper::mapManga)
                .awaitAsList()
                .map { it.id }

            val mergeId = ids.last()

            listOf(ids[0], ids[1]).forEach { childId ->
                db.mergedQueries.insert(
                    infoManga = true,
                    getChapterUpdates = true,
                    chapterSortMode = 0L,
                    chapterPriority = 0L,
                    downloadChapters = true,
                    mergeId = mergeId,
                    mergeUrl = "/merge",
                    mangaId = childId,
                    mangaUrl = "/manga/$childId",
                    mangaSource = 1L,
                ).awaitAsOne()
            }
        }
    }

    private suspend fun insertManga(
        favorite: Boolean,
        source: Long = 1L,
    ): Long {
        val index = db.mangaQueries
            .getAll(MangaMapper::mapManga)
            .awaitAsList()
            .size

        return db.mangaQueries.insert(
            sourceId = source,
            remoteUrl = "/manga/$index",
            remoteTitle = "Manga $index",
            remoteAuthor = null,
            remoteArtist = null,
            remoteDescription = null,
            remoteGenre = null,
            remoteStatus = 0L,
            remoteCover = null,
            remoteUpdateStrategy = UpdateStrategy.ALWAYS_UPDATE,
            remoteMemo = JsonObject(emptyMap()),
            userFavoriteAt = if (favorite) {
                Instant.fromEpochMilliseconds(1)
            } else {
                null
            },
            userNotes = "",
            userReaderFlags = 0L,
            userChapterFlags = 0L,
            userFilteredScanlators = null,
            userScanlatorPriority = emptyList(),
            userBlacklistedChapters = emptyList(),
            userScanlatorRangeRules = emptyList(),
            stateChapterLastUpdate = null,
            stateChapterNextUpdate = null,
            stateChapterFetchInterval = 0L,
            stateCoverLastModified = Instant.fromEpochMilliseconds(0),
            stateInitialized = true,
            lastModifiedAt = 0L,
            version = 0L,
            isSyncing = 0L,
            stateDateAdded = 0L,
        ).awaitAsOne()
    }

    private suspend fun insertChapter(
        mangaId: Long,
        scanlator: String?,
        read: Boolean,
        bookmark: Boolean,
        bookmarkColor: Long = BookmarkColor.NONE.value.toLong(),
        rng: Random = Random(0),
        dates: Long? = null,
    ) {
        // Avoid duplicate URLs when randomized tests reuse
        // a Random with the same seed.
        val urlId = nextChapterUrlId++

        val fetchedAt = Instant.fromEpochMilliseconds(
            dates ?: rng.nextLong(1, 5_000),
        )

        val uploadedAt = Instant.fromEpochMilliseconds(
            dates ?: rng.nextLong(1, 5_000),
        )

        db.chapterQueries.insert(
            mangaId = mangaId,
            remoteUrl = "/chapter/$urlId",
            remoteName = "Chapter",
            remoteScanlator = scanlator,
            remoteChapterNumber = 1.0,
            remoteDateUpload = uploadedAt,
            remoteOrder = 0L,
            remoteMemo = JsonObject(emptyMap()),
            userRead = read,
            userBookmark = bookmark,
            userFillermark = false,
            userBookmarkColor = bookmarkColor,
            userLastPageRead = 0,
            stateDateFetch = fetchedAt,
            lastModifiedAt = 0L,
            version = 0L,
            isSyncing = 0L,
        ).awaitAsOne()
    }

    /**
     * ChapterUpdate no longer changes mangaId.
     * Use SQL to test the database's move triggers.
     */
    private suspend fun moveChapter(
        chapterId: Long,
        mangaId: Long,
    ) {
        driver.execute(
            identifier = null,
            sql = """
                UPDATE chapter
                SET manga_id = $mangaId
                WHERE id = $chapterId
            """.trimIndent(),
            parameters = 0,
        ).await()
    }

    private suspend fun deleteChapters(chapterIds: List<Long>) {
        db.chapterQueries.removeChaptersWithIds(chapterIds)
    }

    private suspend fun chaptersOf(mangaId: Long) =
        db.chapterQueries
            .getChaptersByMangaId(
                mangaId,
                0L,
                0L,
                0L,
                ChapterMapper::mapChapter,
            )
            .awaitAsList()

    private suspend fun libraryRow(mangaId: Long) =
        db.libraryViewQueries
            .library(MangaMapper::mapLibraryManga)
            .awaitAsList()
            .single { it.id == mangaId }

    private suspend fun refill() {
        db.manga_chapter_statsQueries.refill()
    }

    private suspend fun hasMissing(): Boolean =
        db.manga_chapter_statsQueries
            .hasMissing()
            .awaitAsOne()

    private fun statsRowCount(): Long = driver.executeQuery(
        identifier = null,
        sql = "SELECT count(*) FROM manga_chapter_stats",
        parameters = 0,
        mapper = { cursor ->
            cursor.next()
            QueryResult.Value(cursor.getLong(0)!!)
        },
    ).value

    private inline fun <T> withClue(
        clue: Any,
        block: () -> T,
    ): T {
        return try {
            block()
        } catch (e: AssertionError) {
            throw AssertionError(
                "manga $clue: ${e.message}",
                e,
            )
        }
    }

    companion object {
        private const val MERGED_SOURCE_ID = 6969L

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
}
// KMK <--
