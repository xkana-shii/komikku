package eu.kanade.tachiyomi.ui.source.latest

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.ui.source.browse.BrowseSourcePresenter
import eu.kanade.tachiyomi.ui.source.browse.Pager
import exh.isEhBasedSource

/**
 * Presenter of [LatestUpdatesController]. Inherit BrowseCataloguePresenter.
 */
class LatestUpdatesPresenter(sourceId: Long) : BrowseSourcePresenter(sourceId) {

    override fun createPager(query: String, filters: FilterList): Pager {
        return if (source.isEhBasedSource()) { ExhLatestUpdatesPager(source) } else { LatestUpdatesPager(source) }
    }
}
