package eu.kanade.presentation.library.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import eu.kanade.tachiyomi.ui.library.LibraryCollectionItem
import eu.kanade.tachiyomi.ui.library.LibraryGridItem
import eu.kanade.tachiyomi.ui.library.LibraryItem
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.model.MangaCover

@Composable
internal fun LibraryComfortableGrid(
    items: List<LibraryGridItem>,
    columns: Int,
    contentPadding: PaddingValues,
    selection: Set<Long>,
    onClick: (LibraryManga) -> Unit,
    onLongClick: (LibraryManga) -> Unit,
    onClickContinueReading: ((LibraryManga) -> Unit)?,
    onClickCollection: (Long) -> Unit,
    searchQuery: String?,
    onGlobalSearchClicked: () -> Unit,
    // KMK -->
    usePanoramaCover: Boolean = false,
    // KMK <--
) {
    LazyLibraryGrid(
        modifier = Modifier.fillMaxSize(),
        columns = columns,
        contentPadding = contentPadding,
    ) {
        globalSearchItem(searchQuery, onGlobalSearchClicked)

        items(
            items = items,
            contentType = { item ->
                when (item) {
                    is LibraryItem -> "library_comfortable_grid_item"
                    is LibraryCollectionItem -> "library_collection_grid_item"
                }
            },
        ) { gridItem ->
            when (gridItem) {
                is LibraryItem -> {
                    val manga = gridItem.libraryManga.manga
                    MangaComfortableGridItem(
                        isSelected = manga.id in selection,
                        title = manga.title,
                        coverData = MangaCover(
                            mangaId = manga.id,
                            sourceId = manga.source,
                            isMangaFavorite = manga.favorite,
                            ogUrl = manga.thumbnailUrl,
                            lastModified = manga.coverLastModified,
                        ),
                        coverBadgeStart = {
                            DownloadsBadge(count = libraryItem.downloadCount)
                            UnreadBadge(count = libraryItem.unreadCount)
                        },
                        coverBadgeEnd = {
                            LanguageBadge(
                                isLocal = libraryItem.isLocal,
                                sourceLanguage = libraryItem.sourceLanguage,
                                // KMK -->
                                useLangIcon = libraryItem.useLangIcon,
                                // KMK <--
                            )
                            // KMK -->
                            SourceIconBadge(source = libraryItem.source)
                            // KMK <--
                        },
                        onLongClick = { onLongClick(libraryItem.libraryManga) },
                        onClick = { onClick(libraryItem.libraryManga) },
                        onClickContinueReading = if (onClickContinueReading != null && libraryItem.unreadCount > 0) {
                            { onClickContinueReading(libraryItem.libraryManga) }
                        } else {
                            null
                        },
                        // KMK -->
                        usePanoramaCover = usePanoramaCover,
                        // KMK <--
                    )
                }
                is LibraryCollectionItem -> {
                    CollectionComfortableGridItem(
                        title = gridItem.collection.name,
                        coverData = gridItem.coverData,
                        onClick = { onClickCollection(gridItem.collection.id) },
                        onLongClick = {},
                    )
                }
            }
        }
    }
}
