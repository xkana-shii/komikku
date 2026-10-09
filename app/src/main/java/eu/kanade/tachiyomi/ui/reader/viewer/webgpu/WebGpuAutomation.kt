package eu.kanade.tachiyomi.ui.reader.viewer.webgpu

// KMK -->
import android.content.Context
import android.os.PowerManager
import android.view.Choreographer
import android.view.WindowManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import tachiyomi.core.common.i18n.pluralStringResource
import tachiyomi.i18n.MR
import kotlin.coroutines.resume

internal fun automateWebGpu(
    activity: ReaderActivity,
    viewer: WebGpuViewer,
    automationInProgress: MutableStateFlow<Boolean>,
    config: WebGpuConfig,
    scope: CoroutineScope,
) {
    scope.launch {
        automationInProgress.collect { isAutomating ->
            if (!isAutomating) {
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                return@collect
            }

            activity.hideMenu()
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val powerManager = activity.getSystemService(Context.POWER_SERVICE) as PowerManager
            val lifecycle = ProcessLifecycleOwner.get().lifecycle
            val startTime = System.currentTimeMillis()
            var lastChapter = viewer.currentChapterId()
            var chaptersAutomated = 0
            val maxMilliseconds = 1000L * 60 * config.automationMaxMinutes

            while (automationInProgress.value) {
                if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) || !powerManager.isInteractive) {
                    automationInProgress.value = false
                    break
                }
                if (maxMilliseconds > 0 && System.currentTimeMillis() - startTime > maxMilliseconds) {
                    activity.toast(
                        activity.pluralStringResource(
                            MR.plurals.reader_automation_max_minutes_reached,
                            config.automationMaxMinutes,
                            config.automationMaxMinutes,
                        ),
                    )
                    automationInProgress.value = false
                    break
                }

                val currentChapter = viewer.currentChapterId()
                if (config.automationMaxChapters > 0 && currentChapter != null && currentChapter != lastChapter) {
                    lastChapter = currentChapter
                    chaptersAutomated++
                    if (chaptersAutomated >= config.automationMaxChapters) {
                        activity.toast(
                            activity.pluralStringResource(
                                MR.plurals.reader_automation_max_chapters_reached,
                                config.automationMaxChapters,
                                config.automationMaxChapters,
                            ),
                        )
                        automationInProgress.value = false
                        break
                    }
                }

                if (viewer is WebGpuViewerContinuous) {
                    awaitFrame()
                    val refreshRate = activity.display?.refreshRate ?: 60f
                    val speed = viewer.readerPreferences.autoScrollSpeed().get()
                    val distance = (activity.resources.displayMetrics.heightPixels / (speed * refreshRate)).toInt()
                    viewer.scrollForAutomation(distance)
                } else {
                    delay(viewer.readerPreferences.autoFlipInterval().get() * 1000L)
                    if (automationInProgress.value) viewer.moveToNext()
                }
            }

            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

private suspend fun awaitFrame() {
    suspendCancellableCoroutine { continuation ->
        val callback = Choreographer.FrameCallback {
            if (continuation.isActive) continuation.resume(Unit)
        }
        Choreographer.getInstance().postFrameCallback(callback)
        continuation.invokeOnCancellation { Choreographer.getInstance().removeFrameCallback(callback) }
    }
}
// KMK <--
