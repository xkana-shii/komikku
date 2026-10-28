package eu.kanade.tachiyomi.ui.reader.viewer.webgpu

// KUN -->
// Destination plane for the step-2 (8124d324a1) settings migration: the WebGPU viewers move onto this
// then, and that step deletes WebGpuConfig.kt. It needs no fork field: the fork's only addition to
// WebGpuConfig was the one-shot `pageLayout` -> `dualPageView` seed, which lives in
// ReaderSettingsViewModel now.
// KUN <--
/**
 * Settings both WebGPU viewers use, kept in each of their own settings.
 */
data class WebGpuSettings(
    val doubleTapGesturesDisabled: Boolean,
    val imageFilters: List<WebGpuShader>,
    val tileFilters: List<WebGpuShader>,
)
