package eu.kanade.tachiyomi.ui.reader.viewer

// KUN -->
// Destination plane for the step-2 (8124d324a1) settings migration: the long strip viewers move onto
// this then, and that step deletes WebtoonConfig.kt / WebGpuConfig.kt (with them the fork's fields below).
// KUN <--
/**
 * Settings every long strip viewer uses.
 */
data class LongStripSettings(
    val cropBorders: Boolean,
    val zoomOutDisabled: Boolean,
    // KUN -->
    /** SY: crop borders while reading the continuous-vertical ("webtoon with gaps") mode. */
    val continuousCropBorders: Boolean,
    /** KMK: decode long-strip pages with SubsamplingScaleImageView instead of the mpreg decoder. */
    val alwaysDecodeLongStripWithSSIV: Boolean,
    // KUN <--
)
