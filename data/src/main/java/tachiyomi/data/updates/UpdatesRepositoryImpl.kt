package tachiyomi.data.updates

import app.cash.sqldelight.Query
import app.cash.sqldelight.async.coroutines.awaitAsList
import kotlinx.coroutines.flow.Flow
import tachiyomi.core.common.util.lang.toLong
import tachiyomi.data.Database
import tachiyomi.data.subscribeToList
import tachiyomi.domain.chapter.model.BookmarkColor
import tachiyomi.domain.manga.model.MangaCover
import tachiyomi.domain.updates.model.UpdatesWithRelations
import tachiyomi.domain.updates.repository.UpdatesRepository

// KMK -->

class UpdatesRepositoryImpl(
    private val database: Database,
) : UpdatesRepository {

    override suspend fun awaitWithRead(
        read: Boolean,
        after: Long,
        limit: Long,
    ): List<UpdatesWithRelations> {
        return getUpdatesByReadStatus(read = read, after = after, limit = limit).awaitAsList()
    }

    override fun subscribeAll(
        after: Long,
        limit: Long,
        unread: Boolean?,
        started: Boolean?,
        bookmarked: Boolean?,
        hideExcludedScanlators: Boolean,
        // KMK -->
        includedCategories: List<Long>,
        excludedCategories: List<Long>,
        // KMK <--
    ): Flow<List<UpdatesWithRelations>> {
        return database.updatesViewQueries.getRecentUpdatesWithFilters(
            after = after,
            limit = limit,
            // invert because unread in Kotlin -> read column in SQL
            read = unread?.let { !it },
            started = started?.toLong(),
            bookmarked = bookmarked,
            hideExcludedScanlators = hideExcludedScanlators.toLong(),
            // KMK -->
            includedEmpty = includedCategories.isEmpty(),
            excludedEmpty = excludedCategories.isEmpty(),
            includedCategories = includedCategories,
            excludedCategories = excludedCategories,
            // KMK <--
            mapper = ::mapUpdatesWithRelations,
        ).subscribeToList()
    }

    override fun subscribeWithRead(
        read: Boolean,
        after: Long,
        limit: Long,
    ): Flow<List<UpdatesWithRelations>> {
        return getUpdatesByReadStatus(read = read, after = after, limit = limit).subscribeToList()
    }

    private fun getUpdatesByReadStatus(read: Boolean, after: Long, limit: Long): Query<UpdatesWithRelations> {
        return if (read) {
            database.updatesViewQueries.getReadUpdates(
                after = after,
                limit = limit,
                mapper = ::mapUpdatesWithRelations,
            )
        } else {
            database.updatesViewQueries.getUnreadUpdates(
                after = after,
                limit = limit,
                mapper = ::mapUpdatesWithRelations,
            )
        }
    }

    private fun mapUpdatesWithRelations(
        mangaId: Long,
        mangaTitle: String,
        chapterId: Long,
        chapterName: String,
        scanlator: String?,
        chapterUrl: String,
        read: Boolean,
        bookmark: Boolean,
        bookmarkColor: Long,
        lastPageRead: Long,
        sourceId: Long,
        favorite: Boolean,
        thumbnailUrl: String?,
        coverLastModified: Long,
        @Suppress("UNUSED_PARAMETER") dateUpload: Long,
        dateFetch: Long,
        excludedScanlator: String?,
    ): UpdatesWithRelations = UpdatesWithRelations(
        mangaId = mangaId,
        // SY -->
        ogMangaTitle = mangaTitle,
        // SY <--
        chapterId = chapterId,
        chapterName = chapterName,
        scanlator = scanlator,
        chapterUrl = chapterUrl,
        read = read,
        bookmark = bookmark,
        // KMK -->
        bookmarkColor = BookmarkColor.fromValue(bookmarkColor),
        // KMK <--
        lastPageRead = lastPageRead,
        sourceId = sourceId,
        dateFetch = dateFetch,
        coverData = MangaCover(
            mangaId = mangaId,
            sourceId = sourceId,
            isMangaFavorite = favorite,
            ogUrl = thumbnailUrl,
            lastModified = coverLastModified,
        ),
    )
}
// KMK <--
