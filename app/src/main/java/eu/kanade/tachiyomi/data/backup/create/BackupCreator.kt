
package eu.kanade.tachiyomi.data.backup.create

import android.content.Context
import android.net.Uri
import com.hippo.unifile.UniFile
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
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
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.manga.interactor.GetFavorites
import tachiyomi.domain.manga.interactor.GetMergedManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaMergeRepository
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
    private val getCategories: GetCategories,
    private val mangaMergeRepository: MangaMergeRepository,
    // KMK <--

    // SY -->
    private val savedSearchBackupCreator: SavedSearchBackupCreator,
    private val getMergedManga: GetMergedManga,
    // SY <--
) {

    @AssistedFactory
    fun interface Factory {
        fun create(isAutoBackup: Boolean): BackupCreator
    }

    suspend fun backup(
        uri: Uri,
        options: BackupOptions,
    ): String {
        var file: UniFile? = null

        try {
            file = if (isAutoBackup) {
                val dir = UniFile.fromUri(context, uri)

                dir?.listFiles { _, filename ->
                    FILENAME_REGEX.matches(filename)
                }
                    .orEmpty()
                    .sortedByDescending { it.name }
                    .drop(MAX_AUTO_BACKUPS - 1)
                    .forEach { it.delete() }

                dir?.createFile(getFilename())
            } else {
                UniFile.fromUri(context, uri)
            }

            if (file == null || !file.isFile) {
                throw IllegalStateException(
                    context.stringResource(
                        MR.strings.create_backup_file_error,
                    ),
                )
            }

            // KMK -->
            val selectedManga = selectManga(options)
            val backupManga = backupMangas(
                selectedManga,
                options,
            )
            // KMK <--

            file.openOutputStream()
                .also {
                    (it as? FileOutputStream)
                        ?.channel
                        ?.truncate(0)
                }
                .sink()
                .gzip()
                .buffer()
                .use { sink ->
                    val writer = BackupWriter(
                        sink,
                        parser,
                    )

                    val sourceIds = mutableSetOf<Long>()

                    backupManga.collect { manga ->
                        writer.writeEntry(manga)
                        sourceIds += manga.source
                    }

                    writer.writeMetadata(
                        Backup(
                            backupManga = emptyList(),
                            backupCategories = backupCategories(
                                options,
                            ),
                            backupSources = backupSources(
                                sourceIds,
                            ),
                            backupPreferences = backupAppPreferences(
                                options,
                            ),
                            backupExtensionStores = backupExtensionStores(
                                options,
                            ),
                            backupSourcePreferences = backupSourcePreferences(
                                options,
                            ),
                            backupSavedSearches = backupSavedSearches(
                                options,
                            ),
                            backupFeeds = backupFeeds(
                                options,
                            ),
                        ),
                    )

                    if (writer.isEmpty) {
                        throw IllegalStateException(
                            context.stringResource(
                                MR.strings.empty_backup_error,
                            ),
                        )
                    }
                }

            val fileUri = file.uri

            backupFileValidator.validate(fileUri)

            if (isAutoBackup) {
                backupPreferences.lastAutoBackupTimestamp.set(
                    Clock.System.now().toEpochMilliseconds(),
                )
            }

            return fileUri.toString()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            file?.delete()
            throw e
        }
    }

    // KMK -->
    /**
     * Selects manga without changing the original full-backup
     * behavior when no category restriction is provided.
     */
    internal suspend fun selectManga(
        options: BackupOptions,
    ): List<Manga> {
        if (!options.libraryEntries) {
            return emptyList()
        }

        val favorites = getFavorites.await()

        val selectedFavorites = if (
            options.includedCategoryIds == null
        ) {
            favorites
        } else {
            favorites.filter { manga ->
                val categoryIds = getCategories.await(manga.id)
                    .map { it.id }

                options.includesManga(categoryIds)
            }
        }

        // A category-limited backup must not silently include
        // unrelated read entries outside the library.
        val nonFavoriteManga = if (
            options.includedCategoryIds == null &&
            options.readEntries
        ) {
            mangaRepository.getReadMangaNotInLibrary()
        } else {
            emptyList()
        }

        // Full backups preserve the existing global merged list.
        // Restricted backups include only dependencies of selected
        // merged entries.
        val mergedManga = if (
            options.includedCategoryIds == null
        ) {
            getMergedManga.await()
        } else {
            selectedFavorites.flatMap { manga ->
                mangaMergeRepository.getMergedMangaById(
                    manga.id,
                )
            }
        }

        return (
            selectedFavorites +
                nonFavoriteManga +
                mergedManga
            )
            .distinctBy { it.id }
    }
    // KMK <--

    suspend fun backupCategories(
        options: BackupOptions,
    ): List<BackupCategory> {
        if (!options.categories) {
            return emptyList()
        }

        return categoriesBackupCreator()
    }

    fun backupMangas(
        mangas: List<Manga>,
        options: BackupOptions,
    ): Flow<BackupManga> {
        if (!options.libraryEntries) {
            return emptyFlow()
        }

        return mangaBackupCreator(
            mangas,
            options,
        )
    }

    suspend fun backupSources(
        sourceIds: Set<Long>,
    ): List<BackupSource> {
        return sourcesBackupCreator(sourceIds)
    }

    internal fun backupAppPreferences(
        options: BackupOptions,
    ): List<BackupPreference> {
        if (!options.appSettings) {
            return emptyList()
        }

        return preferenceBackupCreator.createApp(
            includePrivatePreferences = options.privateSettings,
        )
    }

    internal suspend fun backupExtensionStores(
        options: BackupOptions,
    ): List<BackupExtensionStore> {
        if (!options.extensionStores) {
            return emptyList()
        }

        return extensionStoresBackupCreator()
    }

    suspend fun backupSourcePreferences(
        options: BackupOptions,
    ): List<BackupSourcePreferences> {
        if (!options.sourceSettings) {
            return emptyList()
        }

        return preferenceBackupCreator.createSource(
            includePrivatePreferences = options.privateSettings,
        )
    }

    suspend fun backupSavedSearches(
        options: BackupOptions,
    ): List<BackupSavedSearch> {
        if (!options.savedSearchesFeeds) {
            return emptyList()
        }

        return savedSearchBackupCreator()
    }

    suspend fun backupFeeds(
        options: BackupOptions,
    ): List<BackupFeed> {
        if (!options.savedSearchesFeeds) {
            return emptyList()
        }

        return feedBackupCreator()
    }

    companion object {
        private const val MAX_AUTO_BACKUPS = 4

        private val FILENAME_REGEX =
            """${BuildConfig.APPLICATION_ID}_\d{4}-\d{2}-\d{2}_\d{2}-\d{2}.tachibk"""
                .toRegex()

        fun getFilename(): String {
            val date = SimpleDateFormat(
                "yyyy-MM-dd_HH-mm",
                Locale.ENGLISH,
            ).format(Date())

            return "${BuildConfig.APPLICATION_ID}_$date.tachibk"
        }
    }
}
