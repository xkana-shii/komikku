package eu.kanade.tachiyomi.data

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn

open class BannerProgressStatus {
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _isRunning = MutableStateFlow(false)

    val isRunning = _isRunning
        .debounce(1000L) // Don't notify if it finishes quickly enough
        .stateIn(scope, SharingStarted.WhileSubscribed(), false)

    suspend fun start() {
        _isRunning.emit(true)
    }

    suspend fun stop() {
        _isRunning.emit(false)
    }

    val progress = MutableStateFlow(0f)

    suspend fun updateProgress(progress: Float) {
        this.progress.emit(progress)
    }
}

@Inject
@SingleIn(AppScope::class)
class LibraryUpdateStatus : BannerProgressStatus()

@Inject
@SingleIn(AppScope::class)
class SyncStatus : BannerProgressStatus()

@Inject
@SingleIn(AppScope::class)
class BackupRestoreStatus : BannerProgressStatus()
