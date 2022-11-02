package eu.kanade.tachiyomi.ui.source.browse

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers

open class ExhPager(val source: CatalogueSource, val query: String, val filters: FilterList) : Pager() {

    override fun requestNext(): Observable<MangasPage> {
        val page = currentPage

        val observable = if (query.isBlank() && filters.isEmpty()) {
            source.fetchPopularManga(page)
        } else {
            source.fetchSearchManga(page, query, filters)
        }

        return observable
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext {
                if (it.mangas.isNotEmpty()) {
                    onPageReceived(it)
                } else {
                    throw NoResultsException()
                }
            }
    }

    override fun onPageReceived(mangasPage: MangasPage) {
        val page = currentPage
        currentPage = urlToId(mangasPage.mangas.lastOrNull()?.url)
        hasNextPage = mangasPage.hasNextPage && mangasPage.mangas.isNotEmpty()
        results.call(Pair(page, mangasPage.mangas))
    }

    private fun urlToId(url: String?): Int {
        val urlstring = url ?: return 0
        val match = Regex("\\/g\\/([0-9]*)\\/").find(urlstring)!!.destructured.toList().first()
        return match.toInt()
    }
}
