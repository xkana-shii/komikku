package eu.kanade.tachiyomi.ui.more

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.data.updater.AppUpdateDownloadJob
import eu.kanade.tachiyomi.extension.util.ExtensionInstaller
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.ProgressListener
import eu.kanade.tachiyomi.util.storage.getUriCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import java.io.File

@AssistedInject
class NewUpdateViewModel(
    @Assisted changelogInfo: String,
    @Assisted private val downloadLink: String,
    private val context: Context,
    private val preferences: BasePreferences,
    private val network: NetworkHelper,
) : ViewModel() {

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(changelogInfo: String, downloadLink: String): NewUpdateViewModel
    }

    private val apkFile: File
        get() = AppUpdateDownloadJob.updateApk(context)

    // KUN -->
    // The notification path and the scheduled auto-update still fetch the APK through
    // [AppUpdateDownloadJob]. When that already landed, the screen must offer Install instead of a
    // redundant download; an empty file is an interrupted download, not an installable APK.
    private val apkAlreadyDownloaded: Boolean
        get() = apkFile.isFile && apkFile.length() > 0
    // KUN <--

    val state: StateFlow<NewUpdateViewModel.State>
        field = MutableStateFlow<NewUpdateViewModel.State>(
            State(
                changelogInfo = changelogInfo,
                // KUN -->
                downloadProgress = if (apkAlreadyDownloaded) 100 else 0,
                stage = if (apkAlreadyDownloaded) Stage.Downloaded else Stage.Available,
                // KUN <--
            ),
        )

    private var downloadJob: Job? = null

    fun startDownload() {
        if (downloadJob?.isActive == true) return

        downloadJob = viewModelScope.launch {
            state.update { it.copy(downloadProgress = 0, stage = Stage.Downloading) }
            try {
                withIOContext { downloadApk() }
                state.update { it.copy(downloadProgress = 100, stage = Stage.Downloaded) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
                state.update { it.copy(stage = Stage.Failed) }
            }
        }
    }

    private fun downloadApk() {
        val progressListener = object : ProgressListener {
            // Progress of the download
            var savedProgress = 0

            // Keep track of the last update sent to avoid updating the state too often.
            var lastTick = 0L

            override fun update(bytesRead: Long, contentLength: Long, done: Boolean) {
                // A response without a length (e.g. a resumed one that lost its total) has no
                // meaningful percentage to report.
                if (contentLength <= 0L) return

                val progress = (100 * (bytesRead.toFloat() / contentLength)).toInt()
                val currentTime = System.currentTimeMillis()
                if (progress > savedProgress && currentTime - 200 > lastTick) {
                    savedProgress = progress
                    lastTick = currentTime
                    state.update { it.copy(downloadProgress = progress) }
                }
            }
        }

        // KUN -->
        // Komikku's resumable downloader (retry + `Range` request) instead of Mihon's
        // newCachelessCallWithProgress + saveTo pair; [AppUpdateDownloadJob] downloads through it too.
        network.downloadFileWithResume(downloadLink, apkFile, progressListener)
        // KUN <--
    }

    fun skipUpdate(versionName: String) {
        // KUN -->
        // Notification-initiated and scheduled updates still download through
        // [AppUpdateDownloadJob]; cancel that as well as this screen's own download.
        downloadJob?.cancel()
        AppUpdateDownloadJob.stop(context)
        // KUN <--
        preferences.skippedAppUpdate.set(versionName)
    }

    fun installUpdate() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkFile.getUriCompat(context), ExtensionInstaller.APK_MIME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    }

    @Immutable
    data class State(
        val changelogInfo: String,
        val downloadProgress: Int = 0,
        val stage: Stage = Stage.Available,
    )

    enum class Stage {
        Available,
        Downloading,
        Downloaded,
        Failed,
    }
}
