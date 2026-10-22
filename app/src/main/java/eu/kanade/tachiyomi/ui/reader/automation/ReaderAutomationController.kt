package eu.kanade.tachiyomi.ui.reader.automation

import android.os.Build
import android.os.PowerManager
import android.view.Choreographer
import android.view.WindowManager
import androidx.core.content.getSystemService
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.ReaderViewModel
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.viewer.Viewer
import eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerViewer
import eu.kanade.tachiyomi.ui.reader.viewer.webgpu.WebGpuViewer
import eu.kanade.tachiyomi.ui.reader.viewer.webgpu.WebGpuViewerContinuous
import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonViewer
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import tachiyomi.core.common.i18n.pluralStringResource
import tachiyomi.i18n.kmk.KMR
import kotlin.coroutines.resume

// KNS -->
/** What automation drives: a scrolling (long strip) or a page-flipping viewer. */
sealed interface AutomationTarget {
    /** Scrolls by [pixels]; false when the viewer could not move (it is at the end). */
    fun interface Scroll : AutomationTarget {
        fun scrollBy(pixels: Float): Boolean
    }

    fun interface Flip : AutomationTarget {
        fun next()
    }

    companion object {
        /** The automation the [viewer] supports, if [preferences] enable it for that kind of viewer. */
        fun of(viewer: Viewer?, preferences: ReaderPreferences): AutomationTarget? = when (viewer) {
            is WebGpuViewerContinuous -> Scroll(viewer::automationScrollBy).takeIf { preferences.autoScroll.get() }
            is WebGpuViewer -> Flip(viewer::moveToNext).takeIf { preferences.autoFlip.get() }
            is WebtoonViewer -> {
                val accumulator = PixelAccumulator()
                Scroll { viewer.automationScrollBy(accumulator.take(it)) }.takeIf { preferences.autoScroll.get() }
            }
            is PagerViewer -> Flip(viewer::moveToNext).takeIf { preferences.autoFlip.get() }
            else -> null
        }
    }
}

/**
 * Runs reader automation: scrolls long strips at the chosen seconds-per-screen (adjusted to the
 * display refresh rate every frame) or flips pages at the chosen interval. It keeps the screen on while
 * running and stops on a tap (the menu opening), when the reader leaves the foreground or the screen
 * turns off, at the configured duration or chapter count, and when there is nothing left to read.
 * Nothing runs while it is not started.
 */
class ReaderAutomationController(
    private val activity: ReaderActivity,
    private val preferences: ReaderPreferences,
    private val state: () -> ReaderViewModel.State,
) {
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private var job: Job? = null

    init {
        activity.lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStop(owner: LifecycleOwner) = stop()
            },
        )
    }

    fun toggle(viewer: Viewer?) {
        if (_running.value) stop() else start(viewer)
    }

    fun start(viewer: Viewer?) {
        if (job?.isActive == true) return
        val target = AutomationTarget.of(viewer, preferences) ?: return
        _running.value = true
        job = activity.lifecycleScope.launch {
            activity.hideMenu()
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            try {
                run(target)?.let(::report)
            } finally {
                if (!preferences.keepScreenOn.get()) {
                    activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                _running.value = false
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun run(target: AutomationTarget): AutomationStop? {
        val session = AutomationSession(
            maxMinutes = preferences.automationMaxMinutes.get(),
            maxChapters = preferences.automationMaxChapters.get(),
            startedAtMillis = System.currentTimeMillis(),
            startChapterId = currentChapterId(),
        )
        val powerManager = activity.getSystemService<PowerManager>()
        val stall = StallDetector(maxStalledFrames = if (target is AutomationTarget.Scroll) 30 else 2)
        while (true) {
            val active = activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) &&
                powerManager?.isInteractive != false
            session.check(System.currentTimeMillis(), currentChapterId(), active)?.let { return it }
            when (target) {
                is AutomationTarget.Scroll -> {
                    awaitFrame()
                    val pixels = AutomationSpeed.pixelsPerFrame(
                        screenHeight = activity.resources.displayMetrics.heightPixels,
                        secondsPerScreen = preferences.autoScrollSpeed.get(),
                        refreshRate = refreshRate(),
                    )
                    if (stall.record(target.scrollBy(pixels))) {
                        // A strip stops moving at the end of the loaded pages while the next chapter
                        // loads (the High Quality strip only loads it near the end): wait for it, and end
                        // only after the last chapter, as the previous personal fork kept going too.
                        if (state().viewerChapters?.nextChapter == null) return AutomationStop.END
                        stall.reset()
                    }
                }
                is AutomationTarget.Flip -> {
                    delay(preferences.autoFlipInterval.get() * 1000L)
                    val before = position()
                    if (isAtLastPage()) return AutomationStop.END
                    target.next()
                    delay(FLIP_SETTLE_MILLIS)
                    if (stall.record(position() != before)) return AutomationStop.END
                }
            }
        }
    }

    private fun report(stop: AutomationStop) {
        when (stop) {
            AutomationStop.MAX_MINUTES -> preferences.automationMaxMinutes.get().let {
                activity.toast(activity.pluralStringResource(KMR.plurals.reader_automation_max_minutes_reached, it, it))
            }
            AutomationStop.MAX_CHAPTERS -> preferences.automationMaxChapters.get().let {
                activity.toast(activity.pluralStringResource(KMR.plurals.reader_automation_max_chapters_reached, it, it))
            }
            AutomationStop.END -> activity.showMenu()
            AutomationStop.INACTIVE -> Unit
        }
    }

    private fun currentChapterId(): Long? = state().currentChapter?.chapter?.id

    private fun position() = currentChapterId() to state().currentPage

    private fun isAtLastPage(): Boolean {
        val current = state()
        return current.viewerChapters?.nextChapter == null &&
            current.totalPages > 0 &&
            current.currentPage >= current.totalPages
    }

    private fun refreshRate(): Float = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        activity.display?.refreshRate ?: DEFAULT_REFRESH_RATE
    } else {
        @Suppress("DEPRECATION")
        activity.windowManager.defaultDisplay.refreshRate
    }

    private suspend fun awaitFrame() = suspendCancellableCoroutine { continuation ->
        val callback = Choreographer.FrameCallback { if (continuation.isActive) continuation.resume(Unit) }
        Choreographer.getInstance().postFrameCallback(callback)
        continuation.invokeOnCancellation { Choreographer.getInstance().removeFrameCallback(callback) }
    }

    private companion object {
        const val DEFAULT_REFRESH_RATE = 60f
        const val FLIP_SETTLE_MILLIS = 600L
    }
}
// KNS <--
