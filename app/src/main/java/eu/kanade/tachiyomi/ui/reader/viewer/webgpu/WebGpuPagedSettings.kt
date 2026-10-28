package eu.kanade.tachiyomi.ui.reader.viewer.webgpu

import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences

// KUN -->
// The step-2 (8124d324a1) settings plane: the paged WebGPU viewers read this instead of the retired
// WebGpuConfig.
// KUN <--
/**
 * Settings only the paged WebGPU viewers use.
 */
data class WebGpuPagedSettings(
    val page: Page,
    val common: WebGpuSettings,
) {

    /**
     * What the pages are made with, so a change remakes them.
     */
    data class Page(
        val dualPageView: ReaderPreferences.DualPageView,
        val transitionAnimation: ReaderPreferences.TransitionAnimation,
        val transitionAnimationDual: ReaderPreferences.TransitionAnimation,
        val cutoutMode: ReaderPreferences.CutoutMode,
        val cutoutModeDual: ReaderPreferences.CutoutMode,
    )
}
