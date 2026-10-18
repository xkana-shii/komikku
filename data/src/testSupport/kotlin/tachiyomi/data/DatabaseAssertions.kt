
package tachiyomi.data

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.db.SqlDriver
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import kotlinx.serialization.json.JsonObject
import tachiyomi.data.category.CategoryRepositoryImpl
import tachiyomi.data.chapter.ChapterRepositoryImpl
import tachiyomi.data.manga.MangaMergeRepositoryImpl
import tachiyomi.data.source.FeedSavedSearchRepositoryImpl
import tachiyomi.data.source.SavedSearchRepositoryImpl
import tachiyomi.domain.manga.model.MergedMangaReference
import tachiyomi.domain.source.model.FeedSavedSearch
import tachiyomi.domain.source.model.SavedSearch
import kotlin.time.Instant
import tachiyomi.domain.category.model.Category as DomainCategory
import tachiyomi.domain.chapter.model.Chapter as DomainChapter

// KMK -->
internal fun testDatabase(driver: SqlDriver) = Database(
    driver = driver,

    categoryAdapter = Category.Adapter(
        manga_orderAdapter = LongListColumnAdapter,
    ),

    historyAdapter = History.Adapter(
        read_atAdapter = InstantColumnAdapter,
    ),

    manga_trackAdapter = Manga_track.Adapter(
        start_dateAdapter = InstantColumnAdapter,
        finish_dateAdapter = InstantColumnAdapter,
    ),

    mangaAdapter = Manga.Adapter(
        remote_genreAdapter = StringListColumnAdapter,
        user_favorite_atAdapter = InstantColumnAdapter,
        state_chapter_last_updateAdapter = InstantColumnAdapter,
        state_chapter_next_updateAdapter = InstantColumnAdapter,
        state_cover_last_modifiedAdapter = InstantColumnAdapter,
        remote_update_strategyAdapter = UpdateStrategyColumnAdapter,
        remote_memoAdapter = MemoColumnAdapter,
        user_scanlator_priorityAdapter = StringListColumnAdapter,
        user_blacklisted_chaptersAdapter = StringListColumnAdapter,
        user_scanlator_range_rulesAdapter = StringListColumnAdapter,
    ),

    chapterAdapter = Chapter.Adapter(
        user_last_page_readAdapter = IntColumnAdapter,
        state_date_fetchAdapter = InstantColumnAdapter,
        remote_date_uploadAdapter = InstantColumnAdapter,
        remote_memoAdapter = MemoColumnAdapter,
    ),
)

internal suspend fun insertTestManga(
    db: Database,
    url: String,
): Long = db.mangaQueries.insert(
    sourceId = 1L,
    remoteUrl = url,
    remoteTitle = url,
    remoteAuthor = null,
    remoteArtist = null,
    remoteDescription = null,
    remoteGenre = null,
    remoteStatus = 0L,
    remoteCover = null,
    remoteUpdateStrategy = UpdateStrategy.ALWAYS_UPDATE,
    remoteMemo = JsonObject(emptyMap()),
    userFavoriteAt = Instant.fromEpochMilliseconds(1),
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

internal suspend fun verifyInsertIds(db: Database) {
    val mangaId = insertTestManga(db, "/source")
    val mergeId = insertTestManga(db, "/merge")

    check(mangaId != mergeId)

    check(
        db.mangaQueries
            .getMangaById(mangaId)
            .awaitAsOne()
            .remote_url == "/source",
    )

    val chapter = DomainChapter.create().copy(
        mangaId = mangaId,
        url = "/chapter",
        name = "Chapter",
    )

    val inserted = ChapterRepositoryImpl(db)
        .addAll(listOf(chapter))
        .single()

    check(inserted.id > 0)

    check(
        db.chapterQueries
            .getChapterById(inserted.id)
            .awaitAsOne()
            .remote_url == chapter.url,
    )

    val category = DomainCategory(
        id = -1L,
        name = "Category",
        order = 1L,
        flags = 0L,
        hidden = true,
    )

    val categories = CategoryRepositoryImpl(db)
    val categoryId = checkNotNull(categories.insert(category))

    check(categoryId > 0L)
    check(categories.get(categoryId)?.name == "Category")

    val reference = MergedMangaReference(
        id = -1L,
        isInfoManga = true,
        getChapterUpdates = true,
        chapterSortMode = 0,
        chapterPriority = 0,
        downloadChapters = false,
        mergeId = mergeId,
        mergeUrl = "/merge",
        mangaId = mangaId,
        mangaUrl = "/source",
        mangaSourceId = 1L,
    )

    val merged = MangaMergeRepositoryImpl(db)
    val referenceId = checkNotNull(merged.insert(reference))

    check(referenceId > 0L)

    check(
        merged.getReferencesById(mergeId)
            .any { it.id == referenceId && it.mangaId == mangaId },
    )

    val searches = SavedSearchRepositoryImpl(db)

    val search = SavedSearch(
        id = -1L,
        source = 1L,
        name = "Search",
        query = "query",
        filtersJson = "[]",
    )

    val searchId = searches.insert(search)

    check(searches.getById(searchId) == search.copy(id = searchId))
    check(searches.insert(search) == searchId)

    val feeds = FeedSavedSearchRepositoryImpl(db)

    val feed = FeedSavedSearch(
        id = -1L,
        source = 1L,
        savedSearch = searchId,
        global = true,
        feedOrder = 0L,
    )

    val feedId = feeds.insert(feed)

    check(
        feeds.getGlobal().any {
            it.id == feedId && it.savedSearch == searchId
        },
    )

    check(feeds.insert(feed) == feedId)

    searches.insertAll(listOf(search.copy(name = "Bulk")))
    feeds.insertAll(listOf(feed.copy(global = false)))
    merged.insertAll(listOf(reference.copy(isInfoManga = false)))

    db.chapterQueries.insert(
        mangaId = mangaId,
        remoteUrl = "/bulk",
        remoteName = "Bulk",
        remoteScanlator = null,
        remoteChapterNumber = 2.0,
        remoteDateUpload = Instant.fromEpochMilliseconds(0),
        remoteOrder = 1L,
        remoteMemo = JsonObject(emptyMap()),
        userRead = false,
        userBookmark = false,
        userFillermark = false,
        userBookmarkColor = 0L,
        userLastPageRead = 0,
        stateDateFetch = Instant.fromEpochMilliseconds(0),
        lastModifiedAt = 0L,
        version = 0L,
        isSyncing = 0L,
    ).awaitAsOne()

    db.categoryQueries.insertWithNextOrder(
        name = "Bulk",
        flags = 0L,
        mangaOrder = emptyList(),
        hidden = 0L,
        parentId = 0L,
        dateAdded = 0L,
        dateModified = 0L,
        trackStatus = null,
        updateInterval = null,
        version = 0L,
        uid = 0L,
        lastModifiedAt = 0L,
        isSyncing = 0L,
    ).awaitAsOne()

    check(searches.getBySourceId(1L).size == 2)
    check(feeds.getBySourceId(1L).single().savedSearch == searchId)
    check(merged.getReferencesById(mergeId).size == 2)

    check(
        db.chapterQueries
            .getChapterByUrl("/bulk")
            .awaitAsList()
            .single()
            .manga_id == mangaId,
    )

    check(categories.getAll().any { it.name == "Bulk" })

    searches.delete(searchId)

    check(feeds.getGlobal().isEmpty())
    check(feeds.getBySourceId(1L).isEmpty())
}
// KMK <--
