package eu.kanade.tachiyomi.ui.more

import android.app.Application
import android.content.Intent
import androidx.core.net.toUri
import androidx.work.WorkInfo
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.presentation.util.formattedMessage
import eu.kanade.tachiyomi.data.updater.AppUpdateDownloadJob
import eu.kanade.tachiyomi.extension.util.ExtensionInstaller
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KMK --> Observe the existing worker; downloads survive closing the prompt.
class NewUpdateScreenModel(
    private val downloadLink: String,
    private val versionName: String,
    private val context: Application = Injekt.get(),
    private val work: Flow<List<WorkInfo>> = context.workManager.getWorkInfosForUniqueWorkFlow(AppUpdateDownloadJob.TAG),
    private val startDownload: () -> Unit = { AppUpdateDownloadJob.start(context, downloadLink, versionName, inlineInstall = true) },
    private val install: (String) -> Unit = { installDownloadedUpdate(context, it) },
) : StateScreenModel<NewUpdateScreenModel.State>(State(stage = Stage.Downloading)) {
    init {
        screenModelScope.launch {
            work.collect { work ->
                val matching = work.filter { AppUpdateDownloadJob.urlTag(downloadLink) in it.tags }
                val info = matching.firstOrNull { !it.state.isFinished } ?: matching.firstOrNull { it.state == WorkInfo.State.SUCCEEDED } ?: matching.firstOrNull()
                if (info == null) {
                    mutableState.value = State()
                    return@collect
                }
                mutableState.update {
                    when (info.state) {
                        WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED -> State(
                            stage = Stage.Downloading,
                            progress = info.progress.getInt(AppUpdateDownloadJob.PROGRESS, 0),
                        )
                        WorkInfo.State.SUCCEEDED -> State(
                            stage = Stage.Downloaded,
                            progress = 100,
                            uri = info.outputData.getString(AppUpdateDownloadJob.EXTRA_FILE_URI),
                        )
                        else -> State(stage = Stage.Failed, error = info.outputData.getString(AppUpdateDownloadJob.ERROR))
                    }
                }
            }
        }
    }

    fun accept() {
        when (state.value.stage) {
            Stage.Downloading -> Unit
            Stage.Downloaded -> try {
                install(checkNotNull(state.value.uri))
            } catch (e: Exception) {
                mutableState.update { it.copy(stage = Stage.Failed, error = with(context) { e.formattedMessage }) }
            }
            else -> try {
                mutableState.value = State(stage = Stage.Downloading)
                startDownload()
            } catch (e: Exception) {
                mutableState.update { it.copy(stage = Stage.Failed, error = with(context) { e.formattedMessage }) }
            }
        }
    }

    data class State(
        val stage: Stage = Stage.Available,
        val progress: Int = 0,
        val uri: String? = null,
        val error: String? = null,
    )
    enum class Stage { Available, Downloading, Downloaded, Failed }
}
private fun installDownloadedUpdate(context: Application, uriString: String) {
    val uri = uriString.toUri()
    context.contentResolver.openInputStream(uri)?.close() ?: error("APK is no longer available")
    context.startActivity(
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, ExtensionInstaller.APK_MIME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        },
    )
}
// KMK <--
