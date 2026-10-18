package exh.md.similar

import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.manga.model.toSManga
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.MetadataMangasPage
import exh.md.handlers.SimilarHandler
import exh.md.service.MangaDexService
import exh.md.service.SimilarService
import exh.md.utils.MdLang
import exh.md.utils.MdUtil
import exh.md.utils.MdUtil.Companion.baseUrl
import exh.recs.sources.RecommendationPagingSource
import exh.recs.sources.RecommendationSource
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.Headers
import tachiyomi.data.source.NoResultsException
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.sy.SYMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * MangaDexSimilarPagingSource inherited from the general Pager.
 */
internal class MangaDexSimilarPagingSource(
    manga: Manga,
    // KMK -->
    private val recommendationSource: RecommendationSource,
    // KMK <--
) : RecommendationPagingSource(
    manga,
    // KMK -->
    recommendationSource,
    // KMK <--
) {

    override val name: String
        get() = "MangaDex"

    override val category: StringResource
        get() = SYMR.strings.similar_titles

    override val associatedSourceId: Long
        // KMK -->
        get() = recommendationSource.id

    private val client by lazy { Injekt.get<NetworkHelper>().client }

    private val mdLang by lazy {
        recommendationSource.lang.let { lang ->
            MdLang.fromExt(lang)
        } ?: MdLang.ENGLISH
    }

    // KUN -->
    // MdUtil.getEnabledMangaDex is a suspend function in this fork, so the delegated headers
    // cannot be resolved from a lazy initializer. Resolve the handler on first use instead.
    private val fallbackHeaders by lazy {
        Headers.Builder().apply {
            set("Referer", "$baseUrl/")
            set("Origin", baseUrl)
            set("sec-fetch-dest", "document")
            set("sec-fetch-mode", "navigate")
        }.build()
    }

    private var similarHandler: SimilarHandler? = null

    private suspend fun getSimilarHandler(): SimilarHandler {
        similarHandler?.let { return it }
        val headers = MdUtil.getEnabledMangaDex(Injekt.get(), Injekt.get())?.headers ?: fallbackHeaders
        return SimilarHandler(
            mdLang.lang,
            MangaDexService(client, headers),
            SimilarService(client),
        ).also { similarHandler = it }
    }
    // KUN <--
    // KMK <--

    override suspend fun requestNextPage(source: Source, currentPage: Int): MangasPage {
        // KUN -->
        val handler = getSimilarHandler()
        // KUN <--
        val mangasPage = coroutineScope {
            try {
                val similarPageDef = async {
                    // KMK -->
                    handler.getSimilar(manga.toSManga())
                    // KMK <--
                }
                val relatedPageDef = async {
                    // KMK -->
                    handler.getRelated(manga.toSManga())
                    // KMK <--
                }
                val similarPage = similarPageDef.await()
                val relatedPage = relatedPageDef.await()

                MetadataMangasPage(
                    relatedPage.mangas + similarPage.mangas,
                    false,
                    relatedPage.mangasMetadata + similarPage.mangasMetadata,
                )
            } catch (e: HttpException) {
                when (e.code) {
                    404 -> throw NoResultsException()
                    else -> throw e
                }
            }
        }

        return mangasPage.takeIf { it.mangas.isNotEmpty() } ?: throw NoResultsException()
    }
}
