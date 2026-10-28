package eu.kanade.tachiyomi.ui.reader.viewer.webgpu

// KUN -->
// The step-2 (8124d324a1) settings plane: the WebGPU viewers read this instead of the retired
// WebGpuConfig. It needs no fork field: the fork's only addition to WebGpuConfig was the one-shot
// `pageLayout` -> `dualPageView` seed, which `ReadingMode.toViewer()` applies before it builds a
// WebGPU viewer.
// KUN <--
/**
 * Settings both WebGPU viewers use, kept in each of their own settings.
 */
data class WebGpuSettings(
    val doubleTapGesturesDisabled: Boolean,
    // KNS --> the zoom settings the legacy viewers honour, for the High Quality viewers too
    val zoomGestures: WebGpuZoomGestures,
    // KNS <--
    val imageFilters: List<WebGpuShader>,
    val tileFilters: List<WebGpuShader>,
)
