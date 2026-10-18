package tachiyomi.domain.manga.interactor

import dev.zacsweers.metro.Inject
import tachiyomi.domain.manga.repository.CustomMangaRepository

@Inject
class GetCustomMangaInfo(
    private val customMangaRepository: CustomMangaRepository,
) {

    fun get(mangaId: Long) = customMangaRepository.get(mangaId)

    // KUN -->
    companion object {
        /**
         * Bridge for domain models ([tachiyomi.domain.manga.model.Manga],
         * [tachiyomi.domain.manga.model.MangaCover], …) whose static `customMangaInfo` cannot take
         * constructor dependencies. Set once by `App` from the Metro graph.
         */
        @Volatile
        private var instance: GetCustomMangaInfo? = null

        fun setInstance(value: GetCustomMangaInfo) {
            instance = value
        }

        fun getInstanceOrNull(): GetCustomMangaInfo? = instance
    }
    // KUN <--
}
