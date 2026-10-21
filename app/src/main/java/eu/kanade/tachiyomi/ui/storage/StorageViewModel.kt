package eu.kanade.tachiyomi.ui.storage

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import exh.source.MERGED_SOURCE_ID
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.service.CategoryTree
import tachiyomi.domain.manga.interactor.GetLibraryManga
import tachiyomi.domain.manga.interactor.GetMergedMangaById
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.source.local.io.Archive
import tachiyomi.source.local.io.LocalSourceFileSystem
import tachiyomi.source.local.isLocal
import kotlin.random.Random

// KNS -->
/**
 * Storage overview: how much space each library entry's downloads (or local folder) use, by category,
 * with deletion. Sizes are recomputed when the library, the categories or the download cache change.
 */
@OptIn(FlowPreview::class)
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class StorageViewModel(
    downloadCache: DownloadCache,
    private val downloadManager: DownloadManager,
    getLibraryManga: GetLibraryManga,
    getCategories: GetCategories,
    private val getMergedMangaById: GetMergedMangaById,
    private val updateManga: UpdateManga,
    private val sourceManager: SourceManager,
    private val localFileSystem: LocalSourceFileSystem,
) : ViewModel() {

    @Immutable
    data class Item(val entry: StorageEntry, val color: Color)

    sealed interface State {
        data class Loading(val progress: Int) : State

        @Immutable
        data class Success(
            val items: List<Item>,
            val categories: List<Category>,
            val selectedCategoryId: Long,
        ) : State
    }

    private val entries = MutableStateFlow<List<StorageEntry>?>(null)
    private val progress = MutableStateFlow(0)
    private val selectedCategoryId = MutableStateFlow(StorageOverview.ALL_CATEGORY_ID)
    private val categories = getCategories.subscribe()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val state: StateFlow<State> = combine(entries, categories, selectedCategoryId, progress) { entries, categories, selected, progress ->
        if (entries == null) {
            State.Loading(progress)
        } else {
            State.Success(
                items = StorageOverview.filter(entries, selected).map { Item(it, colorOf(it.manga)) },
                categories = CategoryTree.flattened(categories.filterNot { it.isSystemCategory }),
                selectedCategoryId = selected,
            )
        }
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State.Loading(0))

    init {
        viewModelScope.launch {
            combine(
                downloadCache.changes.debounce(500L),
                getLibraryManga.subscribe().distinctUntilChanged { old, new ->
                    old.map { it.manga.id to it.categories } == new.map { it.manga.id to it.categories }
                },
                categories,
            ) { _, library, categories -> library to categories }
                .collect { (library, categories) ->
                    progress.value = 0
                    entries.value = withIOContext { compute(library.map { it.manga to it.categories }, categories) }
                }
        }
    }

    fun selectCategory(categoryId: Long) {
        selectedCategoryId.value = categoryId
    }

    /** Deletes [item]'s downloads (or its local folder) and optionally removes it from the library. */
    fun delete(item: Item, removeFromLibrary: Boolean) {
        val manga = item.entry.manga
        entries.update { current -> current?.filterNot { it.manga.id == manga.id } }
        viewModelScope.launchNonCancellable {
            when {
                manga.isLocal() -> localFileSystem.getMangaDirectory(manga.url)?.delete()
                manga.source == MERGED_SOURCE_ID -> downloadedMembers(manga).forEach { member ->
                    sourceManager.get(member.source)?.let { downloadManager.deleteManga(member, it) }
                }
                else -> sourceManager.get(manga.source)?.let { downloadManager.deleteManga(manga, it) }
            }
            if (removeFromLibrary) updateManga.awaitUpdateFavorite(manga.id, false)
        }
    }

    private suspend fun compute(library: List<Pair<Manga, List<Long>>>, categories: List<Category>): List<StorageEntry> {
        val members = library.map { it.first }
            .filter { it.source == MERGED_SOURCE_ID }
            .associate { it.id to downloadedMembers(it) }
        // One size lookup per entry, or per member for a merged entry
        val total = library.distinctBy { it.first.id }
            .sumOf { (manga) -> members[manga.id]?.size ?: 1 }
            .coerceAtLeast(1)
        var done = 0
        return StorageOverview.entries(
            library = library,
            categories = categories,
            members = { members[it.id] },
            sizeOf = { manga -> storedSize(manga).also { progress.value = (++done * 100 / total).coerceAtMost(100) } },
        )
    }

    /** A merged entry's members whose downloads it shows; local members are never touched from here. */
    private suspend fun downloadedMembers(merged: Manga): List<Manga> =
        getMergedMangaById.await(merged.id).filterNot { it.isLocal() || it.source == MERGED_SOURCE_ID }

    private fun storedSize(manga: Manga): StoredSize = if (manga.isLocal()) {
        StoredSize(
            size = StorageOverview.sizeOf(localFileSystem.getMangaDirectory(manga.url)),
            chapterCount = localFileSystem.getFilesInMangaDirectory(manga.url)
                .count { it.isDirectory || Archive.isSupported(it) },
        )
    } else {
        StoredSize(downloadManager.getDownloadSize(manga), downloadManager.getDownloadCount(manga))
    }

    private fun colorOf(manga: Manga): Color {
        val random = Random(manga.id)
        return Color(random.nextInt(256), random.nextInt(256), random.nextInt(256))
    }
}
// KNS <--
