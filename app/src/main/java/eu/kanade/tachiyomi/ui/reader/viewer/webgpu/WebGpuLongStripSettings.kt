package eu.kanade.tachiyomi.ui.reader.viewer.webgpu

// KUN -->
// Destination plane for the step-2 (8124d324a1) settings migration: the long strip WebGPU viewer moves
// onto this then, and that step deletes WebGpuConfig.kt.
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
