package eu.kanade.tachiyomi.data.backup.restore

import android.content.Context
import android.net.Uri
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import eu.kanade.tachiyomi.data.backup.BackupDecoder
import eu.kanade.tachiyomi.data.backup.BackupNotifier
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupExtensionStore
import eu.kanade.tachiyomi.data.backup.models.BackupFeed
import eu.kanade.tachiyomi.data.backup.models.BackupPreference
import eu.kanade.tachiyomi.data.backup.models.BackupSavedSearch
import eu.kanade.tachiyomi.data.backup.models.BackupSource
import eu.kanade.tachiyomi.data.backup.models.BackupSourcePreferences
import eu.kanade.tachiyomi.data.backup.restore.restorers.CategoriesRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.ExtensionStoreRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.FeedRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.MangaRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.PreferenceRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.SavedSearchRestorer
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.createFileInCacheDir
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.chunked
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.withIndex
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.source.repository.StubSourceRepository
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import java.io.File
import java.text.SimpleDateFormat
import java.util.BitSet
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch

@OptIn(ExperimentalAtomicApi::class)
@AssistedInject
class BackupRestorer(
    @Assisted private val notifier: BackupNotifier,
    @Assisted private val isSync: Boolean,
    // KUN -->
    @Assisted private val removeMissingChaptersBefore: Long?,
    // KUN <--
    private val context: Context,
    private val downloadCache: DownloadCache,
    private val categoriesRestorer: CategoriesRestorer,
    private val preferenceRestorer: PreferenceRestorer,
    private val extensionStoreRestorer: ExtensionStoreRestorer,
    private val mangaRestorer: MangaRestorer,
    private val backupDecoder: BackupDecoder,
    // KMK -->
    private val sourceManager: SourceManager,
    private val stubSourceRepository: StubSourceRepository,
    // KMK <--
    // SY -->
    private val savedSearchRestorer: SavedSearchRestorer,
    private val feedRestorer: FeedRestorer,
    // SY <--
) {

    @AssistedFactory
    fun interface Factory {
        fun create(
            notifier: BackupNotifier,
            isSync: Boolean,
            // KUN -->
            removeMissingChaptersBefore: Long?,
            // KUN <--
        ): BackupRestorer
    }

    private var restoreAmount = 0
    private val restoreProgress = AtomicInt(0)
    private val errors = CopyOnWriteArrayList<Pair<Date, String>>()

    init {
        // SY -->
        mangaRestorer.isSync = isSync
        categoriesRestorer.isSync = isSync
        // SY <--
        // KUN -->
        mangaRestorer.removeMissingChaptersBefore = removeMissingChaptersBefore
        // KUN <--
    }

    /**
     * Mapping of source ID to source name from backup data
     */
    private var sourceMapping: Map<Long, String> = emptyMap()

    suspend fun restore(uri: Uri, options: RestoreOptions) {
        val startTime = System.currentTimeMillis()

        restoreFromFile(uri, options)

        // Invalidate download cache to ensure UI reflects any restored downloads
        if (options.libraryEntries) {
            try {
                downloadCache.invalidateCache()
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to invalidate download cache after restore" }
            }
        }

        val time = System.currentTimeMillis() - startTime

        val logFile = writeErrorLog()

        notifier.showRestoreComplete(
            time,
            errors.size,
            logFile.parent,
            logFile.name,
            isSync,
        )
    }

    private suspend fun restoreFromFile(uri: Uri, options: RestoreOptions) {
        val (entryCount, backup) = backupDecoder.decodeMetadata(uri)

        // Store source mapping for error messages
        val backupMaps = backup.backupSources
        sourceMapping = backupMaps.associate { it.sourceId to it.name }

        if (options.libraryEntries) {
            restoreSourceNames(backupMaps)
            restoreAmount += entryCount
        }
        if (options.categories) {
            restoreAmount += 1
        }
        // SY -->
        if (options.savedSearchesFeeds) {
            restoreAmount += 1
        }
        // SY <--
        if (options.appSettings) {
            restoreAmount += 1
        }
        if (options.extensionStores) {
            restoreAmount += backup.backupExtensionStores.size
        }
        if (options.sourceSettings) {
            restoreAmount += 1
        }

        coroutineScope {
            // SY -->
            // Manga restore resolves categories by name, so it must not race the
            // category restore — a delta carrying a new category and a manga in it
            // would silently skip the assignment.
            val categoriesRestoreJob = if (options.categories) {
                launch { restoreCategories(backup.backupCategories) }
            } else {
                null
            }
            // SY <--
            // SY -->
            if (options.savedSearchesFeeds) {
                restoreSavedSearches(
                    backup.backupSavedSearches,
                    // KMK -->
                    backup.backupFeeds,
                    // KMK <--
                )
            }
            // SY <--
            if (options.appSettings) {
                restoreAppPreferences(
                    backup.backupPreferences,
                    backup.backupCategories.takeIf { options.categories },
                    categoriesRestoreJob,
                )
            }
            if (options.sourceSettings) {
                restoreSourcePreferences(backup.backupSourcePreferences)
            }
            if (options.libraryEntries) {
                // SY -->
                // KUN -->
                restoreManga(
                    uri = uri,
                    backupCategories = if (options.categories) backup.backupCategories else emptyList(),
                    restoreCategories = options.categories,
                    restoreTracking = options.tracking,
                    restoreHistory = options.history,
                    restoreCustomInfo = options.customInfo,
                    categoriesRestoreJob = categoriesRestoreJob,
                )
                // KUN <--
                // SY <--
            }
            if (options.extensionStores) {
                restoreExtensionStores(backup.backupExtensionStores)
            }

            // TODO: optionally trigger online library + tracker update
        }
    }

    // Without a stub, a source that isn't installed has no name for the next backup to write
    private suspend fun restoreSourceNames(backupSources: List<BackupSource>) {
        backupSources
            .filter { it.name.isNotBlank() }
            .filter {
                sourceManager.get(it.sourceId) == null && stubSourceRepository.getStubSource(it.sourceId) == null
            }
            .forEach { stubSourceRepository.upsertStubSource(it.sourceId, lang = "", name = it.name) }
    }

    context(scope: CoroutineScope)
    private /* KMK --> */suspend /* KMK <-- */ fun restoreCategories(backupCategories: List<BackupCategory>) {
        scope.ensureActive()
        categoriesRestorer(backupCategories)

        val progress = restoreProgress.incrementAndFetch()
        with(notifier) {
            showRestoreProgress(
                context.stringResource(MR.strings.categories),
                progress,
                restoreAmount,
                isSync,
            )
                // KMK -->
                .show(Notifications.ID_RESTORE_PROGRESS)
            // KMK <--
        }
    }

    // SY -->
    private fun CoroutineScope.restoreSavedSearches(
        backupSavedSearches: List<BackupSavedSearch>,
        // KMK -->
        backupFeeds: List<BackupFeed>,
        // KMK <--
    ) = launch {
        ensureActive()
        savedSearchRestorer.restoreSavedSearches(backupSavedSearches)
        // KMK -->
        feedRestorer.restoreFeeds(backupFeeds)
        // KMK <--

        val progress = restoreProgress.incrementAndFetch()
        with(notifier) {
            showRestoreProgress(
                context.stringResource(KMR.strings.saved_searches_feeds),
                progress,
                restoreAmount,
                isSync,
            )
                // KMK -->
                .show(Notifications.ID_RESTORE_PROGRESS)
            // KMK <--
        }
    }
    // SY <--

    private fun CoroutineScope.restoreManga(
        uri: Uri,
        backupCategories: List<BackupCategory>,
        // KUN -->
        restoreCategories: Boolean,
        restoreTracking: Boolean,
        restoreHistory: Boolean,
        restoreCustomInfo: Boolean,
        // KUN <--
        // SY -->
        categoriesRestoreJob: Job? = null,
        // SY <--
    ) = launch {
        // SY -->
        categoriesRestoreJob?.join()
        // SY <--

        // New entries go first, and a merged entry goes last because it resolves its references
        // against entries that must already be in the database. Rather than hold the entries back in
        // memory, each group is read again in its own pass.
        val isExisting = mangaRestorer.getExistingMatcher()
        val existingIndices = BitSet()
        val deferredIndices = BitSet()
        val newMangas = backupDecoder.decodeEntries(uri)
            .withIndex()
            .filter { (index, manga) ->
                val existing = isExisting(manga)
                val deferred = mangaRestorer.isDeferred(manga)
                if (deferred) {
                    deferredIndices.set(index)
                } else if (existing) {
                    existingIndices.set(index)
                }
                !existing && !deferred
            }
            .map { it.value }
        val backupMangas = flow {
            emitAll(newMangas)
            emitAll(backupDecoder.decodeEntries(uri, existingIndices::get))
            emitAll(backupDecoder.decodeEntries(uri, deferredIndices::get))
        }

        backupMangas
            .chunked(100)
            .collect { chunk ->
                val restoredAsBatch = try {
                    ensureActive()
                    // KUN -->
                    mangaRestorer.restore(
                        chunk,
                        backupCategories,
                        restoreCategories,
                        restoreTracking,
                        restoreHistory,
                        restoreCustomInfo,
                    )
                    // KUN <--
                    true
                } catch (e: Exception) {
                    ensureActive()
                    logcat(LogPriority.WARN, e) { "Batch restore failed, retrying entry by entry" }
                    false
                }

                if (restoredAsBatch) {
                    restoreProgress.addAndFetch(chunk.size)
                } else {
                    chunk.forEach {
                        ensureActive()

                        try {
                            // KUN -->
                            mangaRestorer.restore(
                                listOf(it),
                                backupCategories,
                                restoreCategories,
                                restoreTracking,
                                restoreHistory,
                                restoreCustomInfo,
                            )
                            // KUN <--
                        } catch (e: Exception) {
                            ensureActive()
                            val sourceName = sourceMapping[it.source] ?: it.source.toString()
                            errors.add(Date() to "${it.title} [$sourceName]: ${e.message}")
                        }

                        restoreProgress.incrementAndFetch()
                    }
                }
                with(notifier) {
                    showRestoreProgress(chunk.last().title, restoreProgress.load(), restoreAmount, isSync)
                        // KMK -->
                        .show(Notifications.ID_RESTORE_PROGRESS)
                    // KMK <--
                }
            }
    }

    private fun CoroutineScope.restoreAppPreferences(
        preferences: List<BackupPreference>,
        categories: List<BackupCategory>?,
        categoriesRestoreJob: Job?,
    ) = launch {
        ensureActive()
        categoriesRestoreJob?.join()
        preferenceRestorer.restoreApp(
            preferences,
            categories,
        )

        val progress = restoreProgress.incrementAndFetch()
        with(notifier) {
            showRestoreProgress(
                context.stringResource(MR.strings.app_settings),
                progress,
                restoreAmount,
                isSync,
            )
                // KMK -->
                .show(Notifications.ID_RESTORE_PROGRESS)
            // KMK <--
        }
    }

    private fun CoroutineScope.restoreSourcePreferences(preferences: List<BackupSourcePreferences>) = launch {
        ensureActive()
        preferenceRestorer.restoreSource(preferences)

        val progress = restoreProgress.incrementAndFetch()
        with(notifier) {
            showRestoreProgress(
                context.stringResource(MR.strings.source_settings),
                progress,
                restoreAmount,
                isSync,
            )
                // KMK -->
                .show(Notifications.ID_RESTORE_PROGRESS)
            // KMK <--
        }
    }

    private fun CoroutineScope.restoreExtensionStores(
        backupExtensionStores: List<BackupExtensionStore>,
    ) = launch {
        backupExtensionStores
            .chunked(100)
            .forEach { chunk ->
                chunk.forEach {
                    ensureActive()

                    try {
                        extensionStoreRestorer(it)
                    } catch (e: Exception) {
                        errors.add(Date() to "Error adding extension store: ${it.name} : ${e.message}")
                    }

                    restoreProgress.incrementAndFetch()
                }
                with(notifier) {
                    showRestoreProgress(
                        context.stringResource(MR.strings.extensionStores),
                        restoreProgress.load(),
                        restoreAmount,
                        isSync,
                    )
                        // KMK -->
                        .show(Notifications.ID_RESTORE_PROGRESS)
                    // KMK <--
                }
            }
    }

    private fun writeErrorLog(): File {
        try {
            if (errors.isNotEmpty()) {
                val file = context.createFileInCacheDir("komikku_restore_error.txt")
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

                file.bufferedWriter().use { out ->
                    errors.forEach { (date, message) ->
                        out.write("[${sdf.format(date)}] $message\n")
                    }
                }
                return file
            }
        } catch (_: Exception) {
            // Empty
        }
        return File("")
    }
}
