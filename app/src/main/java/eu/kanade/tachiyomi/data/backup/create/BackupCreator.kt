package eu.kanade.tachiyomi.data.backup.create

import android.content.Context
import android.net.Uri
import com.hippo.unifile.UniFile
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import eu.kanade.domain.connections.service.WebhookEvent
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.data.backup.BackupFileValidator
import eu.kanade.tachiyomi.data.backup.BackupWriter
import eu.kanade.tachiyomi.data.backup.create.creators.CategoriesBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.ExtensionStoresBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.FeedBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.MangaBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.PreferenceBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.SavedSearchBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.SourcesBackupCreator
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupExtensionStore
import eu.kanade.tachiyomi.data.backup.models.BackupFeed
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupPreference
import eu.kanade.tachiyomi.data.backup.models.BackupSavedSearch
import eu.kanade.tachiyomi.data.backup.models.BackupSource
import eu.kanade.tachiyomi.data.backup.models.BackupSourcePreferences
import eu.kanade.tachiyomi.data.webhook.WebhookNotifier
import exh.source.MERGED_SOURCE_ID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.serialization.protobuf.ProtoBuf
import logcat.LogPriority
import okio.buffer
import okio.gzip
import okio.sink
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.backup.service.BackupPreferences
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.repository.CategoryRepository
import tachiyomi.domain.manga.interactor.GetFavorites
import tachiyomi.domain.manga.interactor.GetMergedManga
import tachiyomi.domain.manga.interactor.GetMergedMangaById
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.i18n.MR
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Clock

@AssistedInject
class BackupCreator(
    @Assisted private val isAutoBackup: Boolean,
    private val context: Context,
    private val parser: ProtoBuf,
    private val getFavorites: GetFavorites,
    private val backupPreferences: BackupPreferences,
    private val mangaRepository: MangaRepository,
    private val categoriesBackupCreator: CategoriesBackupCreator,
    private val mangaBackupCreator: MangaBackupCreator,
    private val preferenceBackupCreator: PreferenceBackupCreator,
    private val extensionStoresBackupCreator: ExtensionStoresBackupCreator,
    private val sourcesBackupCreator: SourcesBackupCreator,
    private val backupFileValidator: BackupFileValidator,
    // KMK -->
    private val feedBackupCreator: FeedBackupCreator,
    // KMK <--
    // SY -->
    private val savedSearchBackupCreator: SavedSearchBackupCreator,
    private val getMergedManga: GetMergedManga,
    // SY <--
    // KNS -->
    private val webhookNotifier: WebhookNotifier,
    private val categoryRepository: CategoryRepository,
    private val getMergedMangaById: GetMergedMangaById,
    // KNS <--
) {
    @AssistedFactory
    fun interface Factory {
        fun create(isAutoBackup: Boolean): BackupCreator
    }

    suspend fun backup(uri: Uri, options: BackupOptions): String {
        var file: UniFile? = null
        try {
            file = if (isAutoBackup) {
                // Get dir of file and create
                val dir = UniFile.fromUri(context, uri)

                // Delete older backups
                dir?.listFiles { _, filename -> FILENAME_REGEX.matches(filename) }
                    .orEmpty()
                    .sortedByDescending { it.name }
                    .drop(MAX_AUTO_BACKUPS - 1)
                    .forEach { it.delete() }

                // Create new file to place backup
                dir?.createFile(getFilename())
            } else {
                UniFile.fromUri(context, uri)
            }

            if (file == null || !file.isFile) {
                throw IllegalStateException(context.stringResource(MR.strings.create_backup_file_error))
            }

            // KNS --> the selected categories' entries only, for a selective backup
            val backupManga = backupMangas(selectManga(options), options)
            // KNS <--

            file.openOutputStream()
                .also {
                    // Force overwrite old file
                    (it as? FileOutputStream)?.channel?.truncate(0)
                }
                .sink().gzip().buffer().use {
                    val writer = BackupWriter(it, parser)
                    val sourceIds = mutableSetOf<Long>()
                    backupManga.collect { manga ->
                        writer.writeEntry(manga)
                        sourceIds += manga.source
                    }
                    writer.writeMetadata(
                        Backup(
                            backupManga = emptyList(),
                            backupCategories = backupCategories(options),
                            backupSources = backupSources(sourceIds),
                            backupPreferences = backupAppPreferences(options),
                            backupExtensionStores = backupExtensionStores(options),
                            backupSourcePreferences = backupSourcePreferences(options),

                            // SY -->
                            backupSavedSearches = backupSavedSearches(options),
                            // SY <--

                            // KMK -->
                            backupFeeds = backupFeeds(options),
                            // KMK <--
                        ),
                    )
                    if (writer.isEmpty) {
                        throw IllegalStateException(context.stringResource(MR.strings.empty_backup_error))
                    }
                }
            val fileUri = file.uri

            // Make sure it's a valid backup file
            backupFileValidator.validate(fileUri)

            if (isAutoBackup) {
                backupPreferences.lastAutoBackupTimestamp.set(Clock.System.now().toEpochMilliseconds())
            }

            // KNS -->
            webhookNotifier.notify(WebhookEvent.BACKUP_CREATED)
            // KNS <--
            return fileUri.toString()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            file?.delete()
            throw e
        }
    }

    // KNS -->
    /**
     * The entries to back up. A full backup takes the library, read entries outside it and merged
     * entries' members, as before. A selective one takes the library entries filed in a selected
     * category (see [BackupCategorySelection]) plus the members of selected merged entries, which a
     * merged entry needs; members outside the selection are written as non-library entries, and
     * read entries outside the library are left out.
     */
    internal suspend fun selectManga(options: BackupOptions): List<Manga> {
        val selection = options.includedCategoryIds
        if (selection == null) {
            val nonFavoriteManga = if (options.readEntries) mangaRepository.getReadMangaNotInLibrary() else emptyList()
            // SY -->
            val mergedManga = getMergedManga.await()
            // SY <--
            return getFavorites.await() + nonFavoriteManga /* SY --> */ + mergedManga /* SY <-- */
        }
        val favorites = getFavorites.await()
        val categoriesByManga = categoryRepository.getCategoriesByMangaIds(favorites.map(Manga::id))
        val selected = favorites.filter { manga ->
            BackupCategorySelection.includesManga(selection, categoriesByManga[manga.id].orEmpty().map(Category::id))
        }
        val selectedIds = selected.mapTo(HashSet(), Manga::id)
        val members = selected.filter { it.source == MERGED_SOURCE_ID }
            .flatMap { getMergedMangaById.await(it.id) }
            .filter { selectedIds.add(it.id) }
            .map { it.copy(favorite = false) }
        return selected + members
    }
    // KNS <--

    suspend fun backupCategories(options: BackupOptions): List<BackupCategory> {
        if (!options.categories) return emptyList()

        // KNS -->
        val categories = categoriesBackupCreator()
        val selection = options.includedCategoryIds ?: return categories
        val kept = BackupCategorySelection.categoriesToBackUp(selection, categories.associate { it.id to it.parentId })
        return categories.filter { it.id in kept }
        // KNS <--
    }

    fun backupMangas(mangas: List<Manga>, options: BackupOptions): Flow<BackupManga> {
        if (!options.libraryEntries) return emptyFlow()

        return mangaBackupCreator(mangas, options)
    }

    suspend fun backupSources(sourceIds: Set<Long>): List<BackupSource> {
        return sourcesBackupCreator(sourceIds)
    }

    internal fun backupAppPreferences(options: BackupOptions): List<BackupPreference> {
        if (!options.appSettings) return emptyList()

        return preferenceBackupCreator.createApp(includePrivatePreferences = options.privateSettings)
    }

    internal suspend fun backupExtensionStores(options: BackupOptions): List<BackupExtensionStore> {
        if (!options.extensionStores) return emptyList()

        return extensionStoresBackupCreator()
    }

    suspend fun backupSourcePreferences(options: BackupOptions): List<BackupSourcePreferences> {
        if (!options.sourceSettings) return emptyList()

        return preferenceBackupCreator.createSource(includePrivatePreferences = options.privateSettings)
    }

    // SY -->
    suspend fun backupSavedSearches(options: BackupOptions): List<BackupSavedSearch> {
        if (!options.savedSearchesFeeds) return emptyList()

        return savedSearchBackupCreator()
    }
    // SY <--

    // KMK -->
    /**
     * Backup global Popular/Latest feeds
     */
    suspend fun backupFeeds(options: BackupOptions): List<BackupFeed> {
        if (!options.savedSearchesFeeds) return emptyList()

        return feedBackupCreator()
    }
    // KMK <--

    companion object {
        private const val MAX_AUTO_BACKUPS: Int = 4
        private val FILENAME_REGEX = """${BuildConfig.APPLICATION_ID}_\d{4}-\d{2}-\d{2}_\d{2}-\d{2}.tachibk""".toRegex()

        fun getFilename(): String {
            val date = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.ENGLISH).format(Date())
            return "${BuildConfig.APPLICATION_ID}_$date.tachibk"
        }
    }
}
