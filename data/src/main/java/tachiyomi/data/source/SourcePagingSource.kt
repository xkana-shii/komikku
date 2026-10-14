package tachiyomi.data.source

import androidx.paging.PagingState
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.MetadataMangasPage
import exh.log.xLogE
import exh.metadata.metadata.RaisedSearchMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mihon.domain.manga.model.toDomainManga
import tachiyomi.core.common.util.QuerySanitizer.sanitize
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.repository.SourcePagingSource

class SourceSearchPagingSource(
    source: suspend () -> Source,
    private val query: String,
    private val filters: FilterList,
    networkToLocalManga: NetworkToLocalManga,
) : BaseSourcePagingSource(source, networkToLocalManga) {
    override suspend fun requestNextPage(source: Source, currentPage: Int): MangasPage {
        return source.getSearchManga(currentPage, query.sanitize(), filters)
    }
}

class SourcePopularPagingSource(
    source: suspend () -> Source,
    networkToLocalManga: NetworkToLocalManga,
) : BaseSourcePagingSource(source, networkToLocalManga) {
    override suspend fun requestNextPage(source: Source, currentPage: Int): MangasPage {
        return source.getPopularManga(currentPage)
    }
}

class SourceLatestPagingSource(
    source: suspend () -> Source,
    networkToLocalManga: NetworkToLocalManga,
) : BaseSourcePagingSource(source, networkToLocalManga) {
    override suspend fun requestNextPage(source: Source, currentPage: Int): MangasPage {
        return source.getLatestUpdates(currentPage)
    }
}

abstract class BaseSourcePagingSource(
    private val source: suspend () -> Source,
    protected val networkToLocalManga: NetworkToLocalManga,
) : SourcePagingSource() {

    protected val seenManga = hashSetOf<String>()

    abstract suspend fun requestNextPage(source: Source, currentPage: Int): MangasPage

    override suspend fun load(
        params: LoadParams<Long>,
    ): LoadResult<Long, /*SY --> */ Pair<Manga, RaisedSearchMetadata?>/*SY <-- */> {
        val page = params.key ?: 1

        return try {
            val source = source()
            val mangasPage = withContext(Dispatchers.IO) {
                requestNextPage(source, page.toInt())
                    .takeIf { it.mangas.isNotEmpty() }
                    ?: throw NoResultsException()
            }

            // SY -->
            getPageLoadResult(source, params, mangasPage)
            // SY <--
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            xLogE("${this::class.simpleName}: Failed to load paging source", e)
            LoadResult.Error(e)
        }
    }

    // SY -->
    open suspend fun getPageLoadResult(
        source: Source,
        params: LoadParams<Long>,
        mangasPage: MangasPage,
    ): LoadResult.Page<Long, /*SY --> */ Pair<Manga, RaisedSearchMetadata?>/*SY <-- */> {
        val page = params.key ?: 1

        // SY -->
        val metadata = if (mangasPage is MetadataMangasPage) {
            mangasPage.mangasMetadata
        } else {
            emptyList()
        }
        // SY <--

        val manga = mangasPage.mangas
            // SY -->
            .mapIndexed { index, sManga -> sManga.toDomainManga(source.id) to metadata.getOrNull(index) }
            .filter { seenManga.add(it.first.url) }
            // KMK -->
            .let { pairs -> networkToLocalManga(pairs.map { it.first }).zip(pairs.map { it.second }) }
        // KMK <--
        // SY <--

        return LoadResult.Page(
            data = manga,
            prevKey = null,
            // Metadata pages (e.g. EHentai) may provide their own key; otherwise derive it.
            nextKey = (mangasPage as? MetadataMangasPage)?.nextKey
                ?: if (mangasPage.hasNextPage) page + 1 else null,
        )
    }
    // SY <--

    override fun getRefreshKey(
        state: PagingState<Long, /*SY --> */ Pair<Manga, RaisedSearchMetadata?>/*SY <-- */>,
    ): Long? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey ?: anchorPage?.nextKey
        }
    }
}

class NoResultsException : Exception()
