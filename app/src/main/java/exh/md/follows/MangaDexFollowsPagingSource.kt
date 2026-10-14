package exh.md.follows

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.online.all.MangaDex
import mihon.app.di.appGraph
import tachiyomi.data.source.BaseSourcePagingSource

/**
 * LatestUpdatesPager inherited from the general Pager.
 */
class MangaDexFollowsPagingSource(val mangadex: MangaDex) :
    BaseSourcePagingSource({ mangadex }, mangadex.context.appGraph.networkToLocalManga) {

    override suspend fun requestNextPage(source: Source, currentPage: Int): MangasPage {
        return mangadex.fetchFollows(currentPage)
    }
}
