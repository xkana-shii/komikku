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
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.manga.model.MergedMangaReference
import tachiyomi.domain.source.model.FeedSavedSearch
import tachiyomi.domain.source.model.SavedSearch
import tachiyomi.domain.chapter.model.Chapter as DomainChapter

// KMK -->
internal fun testDatabase(driver: SqlDriver) = Database(
    driver,
    historyAdapter = History.Adapter(DateColumnAdapter),
    mangaAdapter = Manga.Adapter(StringListColumnAdapter, UpdateStrategyColumnAdapter, MemoColumnAdapter),
    chapterAdapter = Chapter.Adapter(MemoColumnAdapter),
)

internal suspend fun insertTestManga(db: Database, url: String): Long = db.mangaQueries.insertReturningId(
    source = 1,
    url = url,
    artist = null,
    author = null,
    description = null,
    genre = null,
    title = url,
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
).awaitAsOne()

internal suspend fun verifyInsertIds(db: Database) {
    val mangaId = insertTestManga(db, "/source")
    val mergeId = insertTestManga(db, "/merge")
    check(mangaId != mergeId)
    check(db.mangaQueries.getMangaById(mangaId).awaitAsOne().remote_url == "/source")
    val chapter = DomainChapter.create().copy(mangaId = mangaId, url = "/chapter", name = "Chapter")
    val inserted = ChapterRepositoryImpl(db).addAll(listOf(chapter)).single()
    check(inserted.id > 0)
    check(db.chapterQueries.getChapterById(inserted.id).awaitAsOne().remote_url == chapter.url)
    val category = Category(-1, "Category", 1, 0, hidden = true)
    val categories = CategoryRepositoryImpl(db)
    val categoryId = categories.insert(category)
    check(categories.get(categoryId) == category.copy(id = categoryId))

    val reference = MergedMangaReference(-1, true, true, 0, 0, false, mergeId, "/merge", mangaId, "/source", 1)
    val merged = MangaMergeRepositoryImpl(db)
    val referenceId = checkNotNull(merged.insert(reference))
    check(merged.getReferencesById(mergeId).single() == reference.copy(id = referenceId))

    val searches = SavedSearchRepositoryImpl(db)
    val search = SavedSearch(-1, 1, "Search", "query", "[]")
    val searchId = searches.insert(search)
    check(searches.getById(searchId) == search.copy(id = searchId))
    check(searches.insert(search) == searchId)
    val feeds = FeedSavedSearchRepositoryImpl(db)
    val feed = FeedSavedSearch(-1, 1, searchId, true, 0)
    val feedId = feeds.insert(feed)
    check(feeds.getGlobal().single().let { it.id == feedId && it.savedSearch == searchId })
    check(feeds.insert(feed) == feedId)

    // Write-only bulk APIs must still execute, even though they return no IDs.
    searches.insertAll(listOf(search.copy(name = "Bulk")))
    feeds.insertAll(listOf(feed.copy(global = false)))
    merged.insertAll(listOf(reference.copy(isInfoManga = false)))
    db.chapterQueries.insertReturningId(
        mangaId = mangaId,
        remoteUrl = "/bulk",
        remoteName = "Bulk",
        remoteScanlator = null,
        userRead = false,
        userBookmark = false,
        userFillermark = false,
        userLastPageRead = 0,
        remoteChapterNumber = 2.0,
        remoteOrder = 1,
        stateDateFetch = 0,
        remoteDateUpload = 0,
        stateVersion = 0,
        remoteMemo = JsonObject(emptyMap()),
    ).awaitAsOne()
    db.categoryQueries.insert("Bulk", 2, 0, 0)
    check(searches.getBySourceId(1).size == 2)
    check(feeds.getBySourceId(1).single().savedSearch == searchId)
    check(merged.getReferencesById(mergeId).size == 2)
    check(db.chapterQueries.getChapterByUrl("/bulk").awaitAsList().single().manga_id == mangaId)
    check(categories.getAll().any { it.name == "Bulk" })

    searches.delete(searchId)
    check(feeds.getGlobal().isEmpty())
    check(feeds.getBySourceId(1).isEmpty())
}
// KMK <--
