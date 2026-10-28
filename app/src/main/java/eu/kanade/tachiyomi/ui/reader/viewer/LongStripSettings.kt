package eu.kanade.tachiyomi.ui.reader.viewer

// KUN -->
// The step-2 (8124d324a1) settings plane: the long strip viewers read this instead of the retired
// WebtoonConfig / WebGpuConfig, and it carries the fork's fields below.
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
    // KUN: populated but read by the preference owner (`ReaderPageImageView`) until step 5.
    val alwaysDecodeLongStripWithSSIV: Boolean,
    // KUN <--
)
