package mihon.app.di.injekt

import dev.zacsweers.metro.Inject
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.source.interactor.GetExhSavedSearch
import eu.kanade.domain.source.interactor.ToggleIncognito
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.domain.track.interactor.AddTracks
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.data.backup.create.BackupCreator
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import exh.pref.DelegateSourcePreferences
import exh.source.ExhPreferences
import mihon.core.migration.Migration
import mihon.domain.source.interactor.UpdateMangaFromRemote
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.chapter.interactor.SetMangaDefaultChapterFlags
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.GetAllManga
import tachiyomi.domain.manga.interactor.GetDuplicateLibraryManga
import tachiyomi.domain.manga.interactor.GetExhFavoriteMangaWithMetadata
import tachiyomi.domain.manga.interactor.GetFavorites
import tachiyomi.domain.manga.interactor.GetFlatMetadataById
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.GetSearchMetadata
import tachiyomi.domain.manga.interactor.InsertFlatMetadata
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.source.interactor.DeleteSavedSearchById
import tachiyomi.domain.source.interactor.GetRemoteManga
import tachiyomi.domain.source.interactor.InsertSavedSearch
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.track.interactor.GetTracks
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addSingleton

/**
 * Bridges singletons provided by the Metro [mihon.app.di.AppGraph] back into Injekt for the
 * Komikku/SY/KMK-only code that still resolves dependencies through Injekt.
 */
@Inject
class KMKMetroInteropModule(
    private val uiPreferences: eu.kanade.domain.ui.UiPreferences,
    private val networkPreferences: eu.kanade.tachiyomi.network.NetworkPreferences,
    private val sourcePreferences: SourcePreferences,
    private val libraryPreferences: LibraryPreferences,
    private val readerPreferences: ReaderPreferences,
    private val securityPreferences: SecurityPreferences,
    private val syncPreferences: SyncPreferences,
    private val exhPreferences: ExhPreferences,
    private val delegateSourcePreferences: DelegateSourcePreferences,

    private val sourceManager: SourceManager,
    private val downloadManager: DownloadManager,
    private val downloadCache: DownloadCache,
    private val trackerManager: TrackerManager,

    private val backupCreatorFactory: BackupCreator.Factory,

    private val getCategories: GetCategories,
    private val getManga: GetManga,
    private val getFlatMetadataById: GetFlatMetadataById,
    private val insertFlatMetadata: InsertFlatMetadata,
    private val getAllManga: GetAllManga,
    private val networkToLocalManga: NetworkToLocalManga,
    private val updateManga: UpdateManga,
    private val updateMangaFromRemote: UpdateMangaFromRemote,
    private val setMangaCategories: SetMangaCategories,
    private val getSearchMetadata: GetSearchMetadata,
    private val getExhFavoriteMangaWithMetadata: GetExhFavoriteMangaWithMetadata,
    private val getFavorites: GetFavorites,
    private val addTracks: AddTracks,
    private val getTracks: GetTracks,
    private val deleteSavedSearchById: DeleteSavedSearchById,
    private val getDuplicateLibraryManga: GetDuplicateLibraryManga,
    private val getRemoteManga: GetRemoteManga,
    private val insertSavedSearch: InsertSavedSearch,
    private val setMangaDefaultChapterFlags: SetMangaDefaultChapterFlags,
    private val getExhSavedSearch: GetExhSavedSearch,
    private val toggleIncognito: ToggleIncognito,
    private val migrations: Set<Migration>,
) : InjektModule {

    override fun InjektRegistrar.registerInjectables() {
        addSingleton(uiPreferences)
        addSingleton(networkPreferences)
        addSingleton(sourcePreferences)
        addSingleton(libraryPreferences)
        addSingleton(readerPreferences)
        addSingleton(securityPreferences)
        addSingleton(syncPreferences)
        addSingleton(exhPreferences)
        addSingleton(delegateSourcePreferences)

        addSingleton(sourceManager)
        addSingleton(downloadManager)
        addSingleton(downloadCache)
        addSingleton(trackerManager)

        addSingleton(backupCreatorFactory)

        addSingleton(getCategories)
        addSingleton(getManga)
        addSingleton(getFlatMetadataById)
        addSingleton(insertFlatMetadata)
        addSingleton(getAllManga)
        addSingleton(networkToLocalManga)
        addSingleton(updateManga)
        addSingleton(updateMangaFromRemote)
        addSingleton(setMangaCategories)
        addSingleton(getSearchMetadata)
        addSingleton(getExhFavoriteMangaWithMetadata)
        addSingleton(getFavorites)
        addSingleton(addTracks)
        addSingleton(getTracks)
        addSingleton(deleteSavedSearchById)
        addSingleton(getDuplicateLibraryManga)
        addSingleton(getRemoteManga)
        addSingleton(insertSavedSearch)
        addSingleton(setMangaDefaultChapterFlags)
        addSingleton(getExhSavedSearch)
        addSingleton(toggleIncognito)
        addSingleton<Set<Migration>>(migrations)

        // Exh metadata source bridge (source-api resolves these through Injekt).
        addSingleton<eu.kanade.tachiyomi.source.online.MetadataSource.GetMangaId>(getManga)
        addSingleton<eu.kanade.tachiyomi.source.online.MetadataSource.GetFlatMetadataById>(getFlatMetadataById)
        addSingleton<eu.kanade.tachiyomi.source.online.MetadataSource.InsertFlatMetadata>(insertFlatMetadata)
    }
}
