package eu.kanade.tachiyomi.ui.reader.viewer.webgpu

// KUN -->
// The step-2 (8124d324a1) settings plane: the long strip WebGPU viewer reads this instead of the
// retired WebGpuConfig.
// KUN <--
/**
 * Settings only the long strip WebGPU viewer uses.
 */
data class WebGpuLongStripSettings(
    val page: Page,
    val common: WebGpuSettings,
) {

    /**
     * What the pages are made with, so a change remakes them.
     */
    data class Page(
        val continuousMinWidth: Int,
        val continuousGap: Int,
    )
}
