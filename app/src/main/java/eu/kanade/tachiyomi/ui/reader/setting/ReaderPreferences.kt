package eu.kanade.tachiyomi.ui.reader.setting

import android.os.Build
import androidx.compose.ui.graphics.BlendMode
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import dev.icerock.moko.resources.StringResource
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerConfig
import eu.kanade.tachiyomi.ui.reader.viewer.webgpu.WebGpuShader
import eu.kanade.tachiyomi.ui.reader.viewer.webgpu.WebGpuShaders
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum
import tachiyomi.core.common.preference.getEnumSet
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.i18n.sy.SYMR

@Inject
@SingleIn(AppScope::class)
class ReaderPreferences(
    private val preferenceStore: PreferenceStore,
) {

    // region General

    // SY -->
    val pageTransitionsPager = preferenceStore.getBoolean("pref_enable_transitions_pager_key", true)

    val pageTransitionsWebtoon = preferenceStore.getBoolean("pref_enable_transitions_webtoon_key", true)
    // SY <--

    val loadAheadOnlyOverWifi: Preference<Boolean> = preferenceStore.getBoolean(
        "pref_reader_load_ahead_only_over_wifi",
        true,
    )

    val flashOnPageChange = preferenceStore.getBoolean("pref_reader_flash", false)

    val flashDurationMillis = preferenceStore.getInt("pref_reader_flash_duration", MILLI_CONVERSION)

    val flashPageInterval = preferenceStore.getInt("pref_reader_flash_interval", 1)

    val flashColor = preferenceStore.getEnum("pref_reader_flash_mode", FlashColor.BLACK)

    val doubleTapAnimSpeed = preferenceStore.getInt("pref_double_tap_anim_speed", 500)

    val showPageNumber = preferenceStore.getBoolean("pref_show_page_number_key", true)

    val showReadingMode = preferenceStore.getBoolean("pref_show_reading_mode", true)

    val verticalNavigator: Preference<Set<ReadingMode>> = preferenceStore.getEnumSet(
        "pref_vertical_navigator",
        emptySet(),
    )

    val verticalNavigatorOnLeft: Preference<Boolean> = preferenceStore.getBoolean(
        "pref_vertical_navigator_on_left",
        false,
    )

    val fullscreen = preferenceStore.getBoolean("fullscreen", true)

    val verticalNavigatorHeight: Preference<Int> = preferenceStore.getInt(
        "pref_vertical_navigator_height",
        65,
    )

    val drawUnderCutout = preferenceStore.getBoolean("cutout_short", true)

    val keepScreenOn = preferenceStore.getBoolean("pref_keep_screen_on_key", false)

    // KNS --> reader automation (same keys as the previous personal fork)
    val autoScroll = preferenceStore.getBoolean("pref_auto_scroll", false)

    /** Seconds one screen height takes to scroll by. */
    val autoScrollSpeed = preferenceStore.getInt("pref_auto_scroll_speed", 5)

    val autoFlip = preferenceStore.getBoolean("pref_auto_flip", false)

    /** Seconds between automatic page flips. */
    val autoFlipInterval = preferenceStore.getInt("pref_auto_flip_interval", 5)

    /** 0 = no limit. */
    val automationMaxChapters = preferenceStore.getInt("pref_automation_max_chapters", 5)

    /** 0 = no limit. */
    val automationMaxMinutes = preferenceStore.getInt("pref_automation_max_minutes", 15)
    // KNS <--

    val defaultReadingMode = preferenceStore.getInt(
        "pref_default_reading_mode_key",
        ReadingMode.RIGHT_TO_LEFT.flagValue,
    )

    val defaultOrientationType = preferenceStore.getInt(
        "pref_default_orientation_type_key",
        ReaderOrientation.FREE.flagValue,
    )

    val webtoonDoubleTapZoomEnabled = preferenceStore.getBoolean("pref_enable_double_tap_zoom_webtoon", true)

    // KMK -->
    val pagedDoubleTapZoomEnabled = preferenceStore.getBoolean("pref_enable_double_tap_zoom_paged", true)

    val webtoonPinchToZoomEnabled = preferenceStore.getBoolean("pref_enable_pinch_to_zoom_webtoon", true)
    // KMK <--

    val imageScaleType = preferenceStore.getInt("pref_image_scale_type_key", 1)

    // KMK -->
    val webtoonScaleType = preferenceStore.getEnum("pref_webtoon_scale_type_key", WebtoonScaleType.FIT)
    val longStripGapSmartScale = preferenceStore.getBoolean("pref_webtoon_smart_scale_long_strip_gap", false)

    /**
     * Komikku: escape hatch for long-strip/webtoon pages. When enabled they are decoded with
     * [SubsamplingScaleImageView] instead of the mpreg Coil decoder, which materialises the image at
     * full resolution and can produce blank/OOM pages on very tall strips.
     */
    val alwaysDecodeLongStripWithSSIV: Preference<Boolean> = preferenceStore.getBoolean(
        "pref_always_decode_long_strip_with_ssiv",
        false,
    )
    // KMK <--

    val zoomStart = preferenceStore.getInt("pref_zoom_start_key", 1)

    val readerTheme = preferenceStore.getInt("pref_reader_theme_key", 1)

    val alwaysShowChapterTransition = preferenceStore.getBoolean("always_show_chapter_transition", true)

    val cropBorders = preferenceStore.getBoolean("crop_borders", false)

    val navigateToPan = preferenceStore.getBoolean("navigate_pan", true)

    val landscapeZoom = preferenceStore.getBoolean("landscape_zoom", true)

    // KMK -->
    val landscapeZoomType = preferenceStore.getEnum("landscape_zoom_type", LandscapeZoomScaleType.FIT)
    // KMK <--

    val cropBordersWebtoon = preferenceStore.getBoolean("crop_borders_webtoon", false)

    val webtoonSidePadding = preferenceStore.getInt("webtoon_side_padding", WEBTOON_PADDING_MIN)

    val readerHideThreshold = preferenceStore.getEnum("reader_hide_threshold", ReaderHideThreshold.LOW)

    val folderPerManga = preferenceStore.getBoolean("create_folder_per_manga", false)

    val skipRead = preferenceStore.getBoolean("skip_read", false)

    val skipFiltered = preferenceStore.getBoolean("skip_filtered", true)

    val skipDupe = preferenceStore.getBoolean("skip_dupe", false)

    val webtoonDisableZoomOut = preferenceStore.getBoolean("webtoon_disable_zoom_out", false)

    // KMK -->
    val pagedDisableZoomIn = preferenceStore.getBoolean("paged_disable_zoom_in", false)
    // KMK <--

    // endregion

    // region Split two page spread

    val dualPageSplitPaged = preferenceStore.getBoolean("pref_dual_page_split", false)

    val dualPageInvertPaged = preferenceStore.getBoolean("pref_dual_page_invert", false)

    val dualPageSplitWebtoon = preferenceStore.getBoolean("pref_dual_page_split_webtoon", false)

    val dualPageInvertWebtoon = preferenceStore.getBoolean("pref_dual_page_invert_webtoon", false)

    val dualPageRotateToFit = preferenceStore.getBoolean("pref_dual_page_rotate", false)

    val dualPageRotateToFitInvert = preferenceStore.getBoolean("pref_dual_page_rotate_invert", false)

    val dualPageRotateToFitWebtoon = preferenceStore.getBoolean("pref_dual_page_rotate_webtoon", false)

    val dualPageRotateToFitInvertWebtoon = preferenceStore.getBoolean("pref_dual_page_rotate_invert_webtoon", false)

    val dualPageView: Preference<DualPageView> = preferenceStore.getEnum(
        "pref_dual_page_view",
        DualPageView.NEVER,
    )

    // endregion

    // region Color filter

    val customBrightness = preferenceStore.getBoolean("pref_custom_brightness_key", false)

    val customBrightnessValue = preferenceStore.getInt("custom_brightness_value", 0)

    val colorFilter = preferenceStore.getBoolean("pref_color_filter_key", false)

    val colorFilterValue = preferenceStore.getInt("color_filter_value", 0)

    val colorFilterMode = preferenceStore.getInt("color_filter_mode", 0)

    val grayscale = preferenceStore.getBoolean("pref_grayscale", false)

    val invertedColors = preferenceStore.getBoolean("pref_inverted_colors", false)

    // endregion

    // region Controls

    val readWithLongTap = preferenceStore.getBoolean("reader_long_tap", true)

    val readWithVolumeKeys = preferenceStore.getBoolean("reader_volume_keys", false)

    val readWithVolumeKeysInverted = preferenceStore.getBoolean("reader_volume_keys_inverted", false)

    val navigationModePager = preferenceStore.getInt("reader_navigation_mode_pager", 0)

    val navigationModeWebtoon = preferenceStore.getInt("reader_navigation_mode_webtoon", 0)

    val pagerNavInverted = preferenceStore.getEnum("reader_tapping_inverted", TappingInvertMode.NONE)

    val webtoonNavInverted = preferenceStore.getEnum("reader_tapping_inverted_webtoon", TappingInvertMode.NONE)

    val showNavigationOverlayNewUser = preferenceStore.getBoolean("reader_navigation_overlay_new_user", true)

    val showNavigationOverlayOnStart = preferenceStore.getBoolean("reader_navigation_overlay_on_start", false)

    // KMK -->
    val smallerTapZone = preferenceStore.getBoolean("reader_navigation_smaller_tap_zone", false)
    // KMK <--

    // endregion

    // SY -->

    val readerThreads = preferenceStore.getInt("eh_reader_threads", 2)

    val readerInstantRetry = preferenceStore.getBoolean("eh_reader_instant_retry", true)

    val aggressivePageLoading = preferenceStore.getBoolean("eh_aggressive_page_loading", false)

    val cacheSize = preferenceStore.getString("eh_cache_size", "75")

    val autoscrollInterval = preferenceStore.getFloat("eh_util_autoscroll_interval", 3f)

    val smoothAutoScroll = preferenceStore.getBoolean("smooth_auto_scroll", true)

    val preserveReadingPosition = preferenceStore.getBoolean("eh_preserve_reading_position", false)

    val preloadSize = preferenceStore.getInt("eh_preload_size", 10)

    val useAutoWebtoon = preferenceStore.getBoolean("eh_use_auto_webtoon", true)

    val continuousVerticalTappingByPage = preferenceStore.getBoolean("continuous_vertical_tapping_by_page", false)

    val cropBordersContinuousVertical = preferenceStore.getBoolean("crop_borders_continues_vertical", false)

    val readerBottomButtons: Preference<Set<String>> = preferenceStore.getStringSet("reader_bottom_buttons", ReaderBottomButton.BUTTONS_DEFAULTS)

    val pageLayout = preferenceStore.getInt("page_layout", PagerConfig.PageLayout.AUTOMATIC)

    val invertDoublePages = preferenceStore.getBoolean("invert_double_pages", false)

    val centerMarginType = preferenceStore.getInt("center_margin_type", PagerConfig.CenterMarginType.NONE)

    val archiveReaderMode = preferenceStore.getInt("archive_reader_mode", ArchiveReaderMode.LOAD_FROM_FILE)
    // SY <--

    // region WebGpu

    val transitionAnimation: Preference<TransitionAnimation> =
        preferenceStore.getEnum("webgpu_transition_animation", TransitionAnimation.BASIC)

    val transitionAnimationDual: Preference<TransitionAnimation> =
        preferenceStore.getEnum("webgpu_dual_transition_animation", TransitionAnimation.BASIC)

    val cutoutMode: Preference<CutoutMode> = preferenceStore.getEnum("webgpu_cutout_mode", CutoutMode.AVOID)

    val cutoutModeDual: Preference<CutoutMode> = preferenceStore.getEnum("webgpu_dual_cutout_mode", CutoutMode.IGNORE)

    val continuousMinWidth: Preference<Int> = preferenceStore.getInt("webgpu_continuous_minwidth", 100)

    val continuousGap: Preference<Int> = preferenceStore.getInt("webgpu_continuous_gap", 10)

    val disableDoubleTapGestures: Preference<Boolean> =
        preferenceStore.getBoolean("webgpu_disable_double_tap_gestures", false)

    /** In run order. */
    val imageFilters: Preference<List<WebGpuShader>> = preferenceStore.getObjectFromString(
        "webgpu_image_filters",
        emptyList(),
        WebGpuShaders::serialize,
        WebGpuShaders::deserialize,
    )

    /** In run order. */
    val tileFilters: Preference<List<WebGpuShader>> = preferenceStore.getObjectFromString(
        "webgpu_tile_filters",
        emptyList(),
        WebGpuShaders::serialize,
        WebGpuShaders::deserialize,
    )

    // endregion

    enum class FlashColor {
        BLACK,
        WHITE,
        WHITE_BLACK,
    }

    enum class TappingInvertMode(
        val titleRes: StringResource,
        val shouldInvertHorizontal: Boolean = false,
        val shouldInvertVertical: Boolean = false,
    ) {
        NONE(MR.strings.tapping_inverted_none),
        HORIZONTAL(
            MR.strings.tapping_inverted_horizontal,
            shouldInvertHorizontal = true,
        ),
        VERTICAL(
            MR.strings.tapping_inverted_vertical,
            shouldInvertVertical = true,
        ),
        BOTH(MR.strings.tapping_inverted_both, shouldInvertHorizontal = true, shouldInvertVertical = true),
    }

    enum class ReaderHideThreshold(val threshold: Int) {
        HIGHEST(5),
        HIGH(13),
        LOW(31),
        LOWEST(47),
    }

    // KMK -->
    enum class LandscapeZoomScaleType(
        val titleRes: StringResource,
    ) {
        FIT(MR.strings.scale_type_fit_screen),
        DOUBLE(KMR.strings.scale_type_double),
    }

    enum class WebtoonScaleType(
        val titleRes: StringResource,
        val ratio: Float,
    ) {
        FIT(MR.strings.scale_type_fit_screen, 0f),
        R4_3(KMR.strings.scale_type_4_3, 3f / 4f),
        R3_2(KMR.strings.scale_type_3_2, 2f / 3f),
        R16_9(KMR.strings.scale_type_16_9, 9f / 16f),
        R20_9(KMR.strings.scale_type_20_9, 9f / 20f),
    }
    // KMK <--

    object ArchiveReaderMode {
        const val LOAD_FROM_FILE = 0
        const val LOAD_INTO_MEMORY = 1
        const val CACHE_TO_DISK = 2
    }

    enum class TransitionAnimation(val titleRes: StringResource) {
        BASIC(MR.strings.transition_animation_basic),
        FLIP(MR.strings.transition_animation_flip),
        FLIP_LEFT(MR.strings.transition_animation_flip_left),
        FLIP_RIGHT(
            MR.strings.transition_animation_flip_right,
        ),
        STACK_LEFT(MR.strings.transition_animation_stack_left),
        STACK_RIGHT(MR.strings.transition_animation_stack_right),
        STACK_UP(
            MR.strings.transition_animation_stack_up,
        ),
        STACK_DOWN(MR.strings.transition_animation_stack_down),
        SPHERE(MR.strings.transition_animation_sphere),
        CUBE_INSIDE(
            MR.strings.transition_animation_cube_inside,
        ),
        CUBE_OUTSIDE(MR.strings.transition_animation_cube_outside),
        FADE(MR.strings.transition_animation_fade),
        FADE_WHITE(
            MR.strings.transition_animation_fade_white,
        ),
        NONE(MR.strings.transition_animation_none),
    }

    enum class CutoutMode(val titleRes: StringResource) {
        IGNORE(MR.strings.cutout_mode_ignore),
        AVOID(MR.strings.cutout_mode_avoid),
        SHIFT(MR.strings.cutout_mode_shift),
    }

    enum class DualPageView(val titleRes: StringResource) {
        NEVER(MR.strings.dual_page_view_never),
        ALWAYS(MR.strings.dual_page_view_always),
        WIDE(MR.strings.dual_page_view_wide),
    }

    companion object {
        const val WEBTOON_PADDING_MIN = 0
        const val WEBTOON_PADDING_MAX = 25

        const val MILLI_CONVERSION = 100

        val TapZones = listOf(
            MR.strings.label_default,
            MR.strings.l_nav,
            MR.strings.kindlish_nav,
            MR.strings.edge_nav,
            MR.strings.right_and_left_nav,
            MR.strings.disabled_nav,
        )

        val ImageScaleType = listOf(
            MR.strings.scale_type_fit_screen,
            MR.strings.scale_type_stretch,
            MR.strings.scale_type_fit_width,
            MR.strings.scale_type_fit_height,
            MR.strings.scale_type_original_size,
            MR.strings.scale_type_smart_fit,
        )

        val ImageScaleTypeWebGpuViewer = listOf(
            MR.strings.scale_type_fit_screen,
            MR.strings.scale_type_fit_width,
            MR.strings.scale_type_fit_height,
            MR.strings.scale_type_original_size,
        )

        val ZoomStart = listOf(
            MR.strings.zoom_start_automatic,
            MR.strings.zoom_start_left,
            MR.strings.zoom_start_right,
            MR.strings.zoom_start_center,
        )

        // KMK -->
        val zoomWideImagesAllowedList = listOf(
            SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE,
            SubsamplingScaleImageView.SCALE_TYPE_ORIGINAL_SIZE,
        )
        // KMK <--

        val ColorFilterMode = buildList {
            addAll(
                listOf(
                    MR.strings.label_default to BlendMode.SrcOver,
                    MR.strings.filter_mode_multiply to BlendMode.Modulate,
                    MR.strings.filter_mode_screen to BlendMode.Screen,
                ),
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                addAll(
                    listOf(
                        MR.strings.filter_mode_overlay to BlendMode.Overlay,
                        MR.strings.filter_mode_lighten to BlendMode.Lighten,
                        MR.strings.filter_mode_darken to BlendMode.Darken,
                    ),
                )
            }
        }

        // SY -->
        val PageLayouts = listOf(
            SYMR.strings.single_page,
            SYMR.strings.double_pages,
            SYMR.strings.automatic_orientation,
        )

        val CenterMarginTypes = listOf(
            SYMR.strings.center_margin_none,
            SYMR.strings.center_margin_double_page,
            SYMR.strings.center_margin_wide_page,
            SYMR.strings.center_margin_double_and_wide_page,
        )

        val archiveModeTypes = listOf(
            SYMR.strings.archive_mode_load_from_file,
            SYMR.strings.archive_mode_load_into_memory,
            SYMR.strings.archive_mode_cache_to_disk,
        )
        // SY <--
    }
}
