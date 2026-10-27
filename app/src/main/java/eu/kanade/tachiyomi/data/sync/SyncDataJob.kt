package eu.kanade.tachiyomi.data.sync

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dev.zacsweers.metro.Inject
import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.tachiyomi.data.SyncStatus
import eu.kanade.tachiyomi.data.backup.create.BackupCreator
import eu.kanade.tachiyomi.data.backup.restore.restorers.MangaRestorer
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.isOnline
import eu.kanade.tachiyomi.util.system.isRunning
import eu.kanade.tachiyomi.util.system.setForegroundSafely
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.runBlocking
import logcat.LogPriority
import mihon.app.di.AppGraph
import mihon.app.di.appGraph
import mihon.core.metro.metroGraph
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.repository.CategoryRepository
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.history.repository.HistoryRepository
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.sync.repository.SyncRepository
import java.util.concurrent.TimeUnit

// KUN -->
/**
 * One-shot "upload the whole library" request, carried in this run's input data. It is not a
 * persisted preference: a force request applies to a single sync and is dropped afterwards.
 */
internal const val FORCE_FULL_SYNC_KEY = "force_full"

internal fun Data.forceFullRequested(): Boolean = getBoolean(FORCE_FULL_SYNC_KEY, false)

/**
 * One-shot SyncYomi repair request: make this device's versions authoritative before uploading.
 * Like [FORCE_FULL_SYNC_KEY], it only applies to the run that carries it.
 */
internal const val FORCE_PUSH_SYNC_KEY = "force_push"

internal fun Data.forcePushRequested(): Boolean = getBoolean(FORCE_PUSH_SYNC_KEY, false)
// KUN <--

class SyncDataJob(private val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    private val graph: AppGraph = context.metroGraph()

    @Inject private lateinit var syncRepository: SyncRepository

    @Inject private lateinit var chapterRepository: ChapterRepository

    @Inject private lateinit var historyRepository: HistoryRepository

    @Inject private lateinit var categoryRepository: CategoryRepository

    @Inject private lateinit var syncPreferences: SyncPreferences

    @Inject private lateinit var getCategories: GetCategories

    @Inject private lateinit var backupCreatorFactory: BackupCreator.Factory

    @Inject private lateinit var mangaRestorer: MangaRestorer

    private val notifier by lazy { SyncNotifier(context, graph.securityPreferences, graph.syncStatus) }

    override suspend fun doWork(): Result {
        graph.inject(this)
        if (tags.contains(TAG_AUTO)) {
            if (!context.isOnline()) {
                return Result.retry()
            }
            // Find a running manual worker. If exists, try again later
            if (context.workManager.isRunning(TAG_MANUAL)) {
                return Result.retry()
            }
        }

        // KMK -->
        graph.syncStatus.start()
        // KMK <--

        setForegroundSafely()

        return try {
            SyncManager(
                context = context,
                syncRepository = syncRepository,
                chapterRepository = chapterRepository,
                historyRepository = historyRepository,
                categoryRepository = categoryRepository,
                syncPreferences = syncPreferences,
                getCategories = getCategories,
                backupCreatorFactory = backupCreatorFactory,
                notifier = notifier,
                restorer = mangaRestorer,
                json = graph.json,
            ).syncData(
                // KUN -->
                forceFull = inputData.forceFullRequested(),
                forcePush = inputData.forcePushRequested(),
                // KUN <--
            )
            Result.success()
            // SY -->
        } catch (e: OutOfMemoryError) {
            logcat(LogPriority.ERROR, e)
            notifier.showSyncError("Not enough memory to sync this library")
            Result.success()
            // SY <--
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            notifier.showSyncError(e.message)
            Result.success() // try again next time
        } finally {
            // KMK -->
            graph.syncStatus.stop()
            // KMK <--
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return ForegroundInfo(
            Notifications.ID_SYNC_PROGRESS,
            notifier.showSyncProgress().build(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    companion object {
        private const val TAG_JOB = "SyncDataJob"
        private const val TAG_AUTO = "$TAG_JOB:auto"
        const val TAG_MANUAL = "$TAG_JOB:manual"

        suspend fun isRunning(context: Context): Boolean {
            return context.workManager.isRunning(TAG_JOB)
        }

        // SY -->
        suspend fun isRunning(workManager: WorkManager): Boolean {
            return workManager.isRunning(TAG_JOB)
        }
        // SY <--

        fun setupTask(context: Context, prefInterval: Int? = null) {
            val syncPreferences = context.appGraph.syncPreferences
            val interval = prefInterval ?: syncPreferences.syncInterval.get()

            if (interval > 0) {
                val request = PeriodicWorkRequestBuilder<SyncDataJob>(
                    interval.toLong(),
                    TimeUnit.MINUTES,
                    10,
                    TimeUnit.MINUTES,
                )
                    .addTag(TAG_JOB)
                    .addTag(TAG_AUTO)
                    .build()

                context.workManager.enqueueUniquePeriodicWork(TAG_AUTO, ExistingPeriodicWorkPolicy.UPDATE, request)
            } else {
                context.workManager.cancelUniqueWork(TAG_AUTO)
            }
        }

        suspend fun startNow(
            context: Context,
            manual: Boolean = false,
            // KUN -->
            // One-shot: only affect this run, never persisted in a preference.
            forceFull: Boolean = false,
            forcePush: Boolean = false,
            // KUN <--
        ) {
            val wm = context.workManager
            if (wm.isRunning(TAG_JOB)) {
                // Already running either as a scheduled or manual job
                return
            }
            val tag = if (manual) TAG_MANUAL else TAG_AUTO
            val request = OneTimeWorkRequestBuilder<SyncDataJob>()
                .addTag(TAG_JOB)
                .addTag(tag)
                // KUN -->
                .setInputData(
                    workDataOf(
                        FORCE_FULL_SYNC_KEY to forceFull,
                        FORCE_PUSH_SYNC_KEY to forcePush,
                    ),
                )
                // KUN <--
                .build()
            // KUN -->
            // A force request must not be dropped because a normal manual sync is still queued
            // under the same unique name; KEEP would silently keep the older request.
            val policy = if (forceFull || forcePush) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
            context.workManager.enqueueUniqueWork(tag, policy, request)
            // KUN <--
        }

        fun stop(context: Context) {
            // KMK -->
            val syncPreferences = context.appGraph.syncPreferences
            val syncEnabled = syncPreferences.isSyncEnabled()
            // KMK <--
            val wm = context.workManager

            // KMK -->
            // KUN -->
            // Cancelling by tag is non-blocking; the old implementation blocked the calling (main)
            // thread on `getWorkInfos(...).get()`. Both the auto and manual syncs carry TAG_JOB, so
            // this cancels the running sync without waiting on WorkManager's executor.
            wm.cancelAllWorkByTag(TAG_JOB)
            // KUN <--

            val syncStatus: SyncStatus = context.appGraph.syncStatus
            runBlocking { syncStatus.stop() }

            if (syncEnabled) {
                // Re-enqueue the cancelled scheduled work
                setupTask(context)
            }
            // KMK <--
        }
    }
}
