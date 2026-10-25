package tachiyomi.domain.source.model

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate

data class StubSource(
    override val id: Long,
    override val lang: String,
    override val name: String,
) : Source {

    override val supportsLatest: Boolean = false

    override suspend fun getPopularManga(page: Int): MangasPage = throw SourceNotInstalledException()

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw SourceNotInstalledException()

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage =
        throw SourceNotInstalledException()

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = throw SourceNotInstalledException()

    override suspend fun getPageList(chapter: SChapter): List<Page> =
        throw SourceNotInstalledException()

    // KMK -->
    override suspend fun getRelatedMangaList(
        manga: SManga,
        exceptionHandler: (Throwable) -> Unit,
        pushResults: suspend (relatedManga: Pair<String, List<SManga>>, completed: Boolean) -> Unit,
    ) = throw SourceNotInstalledException()
    // KMK <--

    // KUN -->
    // The backup only carries a source name, so a restored stub has no language. Fall back on the
    // name alone rather than the id; the language comes back once the extension is installed and
    // AndroidSourceManager.registerStubSource() overwrites the stub.
    override fun toString(): String = when {
        name.isBlank() -> id.toString()
        lang.isBlank() -> name
        else -> "$name (${lang.uppercase()})"
    }
    // KUN <--

    companion object {
        fun from(source: Source): StubSource {
            return StubSource(id = source.id, lang = source.lang, name = source.name)
        }
    }
}

class SourceNotInstalledException : Exception()
