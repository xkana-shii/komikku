package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.ui.reader.automation.AutomationSpeed
import eu.kanade.tachiyomi.ui.reader.setting.ReaderBottomButton
import eu.kanade.tachiyomi.ui.reader.setting.ReaderOrientation
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences.Companion.zoomWideImagesAllowedList
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences.WebtoonScaleType
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerConfig
import eu.kanade.tachiyomi.util.system.hasDisplayCutout
import mihon.app.di.appGraph
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import java.text.NumberFormat

object SettingsReaderScreen : SearchableSettings {
    @Suppress("unused")
    private fun readResolve(): Any = SettingsReaderScreen

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_reader

    @Composable
    override fun getPreferences(): List<Preference> {
        val context = LocalContext.current
        val readerPref = remember { context.appGraph.readerPreferences }

        return listOf(
            Preference.PreferenceItem.ListPreference(
                preference = readerPref.defaultReadingMode,
                entries = ReadingMode.entries.drop(1)
                    .associate { it.flagValue to stringResource(it.stringRes) }
                    .toMap(),
                title = stringResource(MR.strings.pref_viewer_type),
            ),
            Preference.PreferenceItem.ListPreference(
                preference = readerPref.doubleTapAnimSpeed,
                entries = mapOf(
                    1 to stringResource(MR.strings.double_tap_anim_speed_0),
                    500 to stringResource(MR.strings.double_tap_anim_speed_normal),
                    250 to stringResource(MR.strings.double_tap_anim_speed_fast),
                ),
                title = stringResource(MR.strings.pref_double_tap_anim_speed),
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = readerPref.showReadingMode,
                title = stringResource(MR.strings.pref_show_reading_mode),
                subtitle = stringResource(MR.strings.pref_show_reading_mode_summary),
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = readerPref.showNavigationOverlayOnStart,
                title = stringResource(MR.strings.pref_show_navigation_mode),
                subtitle = stringResource(MR.strings.pref_show_navigation_mode_summary),
            ),
// KMK -->
            Preference.PreferenceItem.SwitchPreference(
                preference = readerPref.smallerTapZone,
                title = stringResource(KMR.strings.pref_viewer_nav_smaller_tap_zone),
            ),
            // KMK <--
            /* SY -->
            Preference.PreferenceItem.SwitchPreference(
                preference = readerPref.pageTransitions(),
                title = stringResource(MR.strings.pref_page_transitions),
            ),
            SY <-- */
            Preference.PreferenceItem.SwitchPreference(
                preference = readerPref.loadAheadOnlyOverWifi,
                title = stringResource(MR.strings.pref_load_ahead_only_over_wifi),
                subtitle = stringResource(MR.strings.pref_load_ahead_only_over_wifi_summary),
            ),
            getDisplayGroup(readerPreferences = readerPref),
            getEInkGroup(readerPreferences = readerPref),
            getReadingGroup(readerPreferences = readerPref),
            getPagedGroup(readerPreferences = readerPref),
            getWebtoonGroup(readerPreferences = readerPref),
            // SY -->
            getContinuousVerticalGroup(readerPreferences = readerPref),
            // SY <--
            getNavigationGroup(readerPreferences = readerPref),
            getActionsGroup(readerPreferences = readerPref),
            // SY -->
            getPageDownloadingGroup(readerPreferences = readerPref),
            getForkSettingsGroup(readerPreferences = readerPref),
            // SY <--
            // KNS -->
            getAutomationGroup(readerPreferences = readerPref),
            // KNS <--
        )
    }

    // KNS -->
    @Composable
    private fun getAutomationGroup(readerPreferences: ReaderPreferences): Preference.PreferenceGroup {
        val autoScroll by readerPreferences.autoScroll.collectAsState()
        val autoFlip by readerPreferences.autoFlip.collectAsState()
        return Preference.PreferenceGroup(
            title = stringResource(KMR.strings.pref_reader_automation),
            preferenceItems = listOf(
                Preference.PreferenceItem.InfoPreference(stringResource(KMR.strings.pref_reader_automation_summary)),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.autoScroll,
                    title = stringResource(KMR.strings.pref_auto_scroll),
                ),
                Preference.PreferenceItem.SliderPreference(
                    preference = readerPreferences.autoScrollSpeed,
                    valueRange = AutomationSpeed.MIN_SECONDS_PER_SCREEN..AutomationSpeed.MAX_SECONDS_PER_SCREEN,
                    title = stringResource(KMR.strings.pref_auto_scroll_speed),
                    valueText = { pluralStringResource(KMR.plurals.pref_auto_scroll_speed_summary, it, it) },
                    visible = autoScroll,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.autoFlip,
                    title = stringResource(KMR.strings.pref_auto_flip),
                ),
                Preference.PreferenceItem.SliderPreference(
                    preference = readerPreferences.autoFlipInterval,
                    valueRange = AutomationSpeed.MIN_FLIP_SECONDS..AutomationSpeed.MAX_FLIP_SECONDS,
                    title = stringResource(KMR.strings.pref_auto_flip_interval),
                    valueText = { pluralStringResource(KMR.plurals.pref_auto_flip_interval_summary, it, it) },
                    visible = autoFlip,
                ),
                Preference.PreferenceItem.SliderPreference(
                    preference = readerPreferences.automationMaxMinutes,
                    valueRange = 0..60,
                    title = stringResource(KMR.strings.pref_reader_automation_max_minutes),
                    valueText = {
                        if (it == 0) {
                            stringResource(MR.strings.disabled)
                        } else {
                            pluralStringResource(KMR.plurals.pref_reader_automation_max_minutes_summary, it, it)
                        }
                    },
                    visible = autoScroll || autoFlip,
                ),
                Preference.PreferenceItem.SliderPreference(
                    preference = readerPreferences.automationMaxChapters,
                    valueRange = 0..10,
                    title = stringResource(KMR.strings.pref_reader_automation_max_chapters),
                    valueText = {
                        if (it == 0) {
                            stringResource(MR.strings.disabled)
                        } else {
                            pluralStringResource(KMR.plurals.pref_reader_automation_max_chapters_summary, it, it)
                        }
                    },
                    visible = autoScroll || autoFlip,
                ),
            ),
        )
    }
    // KNS <--

    @Composable
    private fun getDisplayGroup(readerPreferences: ReaderPreferences): Preference.PreferenceGroup {
        val fullscreenPref = readerPreferences.fullscreen
        val fullscreen by fullscreenPref.collectAsState()
        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_display),
            preferenceItems = listOf(
                Preference.PreferenceItem.ListPreference(
                    preference = readerPreferences.defaultOrientationType,
                    entries = ReaderOrientation.entries.drop(1)
                        .associate { it.flagValue to stringResource(it.stringRes) }
                        .toMap(),
                    title = stringResource(MR.strings.pref_rotation_type),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = readerPreferences.readerTheme,
                    entries = mapOf(
                        1 to stringResource(MR.strings.black_background),
                        2 to stringResource(MR.strings.gray_background),
                        0 to stringResource(MR.strings.white_background),
                        3 to stringResource(MR.strings.automatic_background),
                    ),
                    title = stringResource(MR.strings.pref_reader_theme),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = fullscreenPref,
                    title = stringResource(MR.strings.pref_fullscreen),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.drawUnderCutout,
                    title = stringResource(MR.strings.pref_cutout_short),
                    visible = LocalView.current.hasDisplayCutout() && fullscreen,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.keepScreenOn,
                    title = stringResource(MR.strings.pref_keep_screen_on),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.showPageNumber,
                    title = stringResource(MR.strings.pref_show_page_number),
                ),
            ),
        )
    }

    @Composable
    private fun getEInkGroup(readerPreferences: ReaderPreferences): Preference.PreferenceGroup {
        val flashPageState by readerPreferences.flashOnPageChange.collectAsState()

        val flashColorPref = readerPreferences.flashColor

        return Preference.PreferenceGroup(
            title = "E-Ink",
            preferenceItems = listOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.flashOnPageChange,
                    title = stringResource(MR.strings.pref_flash_page),
                    subtitle = stringResource(MR.strings.pref_flash_page_summ),
                ),
                Preference.PreferenceItem.SliderPreference(
                    preference = readerPreferences.flashDurationMillis,
                    valueRange = ReaderPreferences.MILLI_CONVERSION.let { it..it * 15 step it },
                    title = stringResource(MR.strings.pref_flash_duration),
                    valueText = { stringResource(MR.strings.pref_flash_duration_summary, it) },
                    visible = flashPageState,
                ),
                Preference.PreferenceItem.SliderPreference(
                    preference = readerPreferences.flashPageInterval,
                    valueRange = 1..10,
                    title = stringResource(MR.strings.pref_flash_page_interval),
                    valueText = { pluralStringResource(MR.plurals.pref_pages, it, it) },
                    visible = flashPageState,
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = flashColorPref,
                    entries = mapOf(
                        ReaderPreferences.FlashColor.BLACK to stringResource(MR.strings.pref_flash_style_black),
                        ReaderPreferences.FlashColor.WHITE to stringResource(MR.strings.pref_flash_style_white),
                        ReaderPreferences.FlashColor.WHITE_BLACK
                            to stringResource(MR.strings.pref_flash_style_white_black),
                    ),
                    title = stringResource(MR.strings.pref_flash_with),
                    visible = flashPageState,
                ),
            ),
        )
    }

    @Composable
    private fun getReadingGroup(readerPreferences: ReaderPreferences): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_reading),
            preferenceItems = listOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.skipRead,
                    title = stringResource(MR.strings.pref_skip_read_chapters),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.skipFiltered,
                    title = stringResource(MR.strings.pref_skip_filtered_chapters),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.skipDupe,
                    title = stringResource(MR.strings.pref_skip_dupe_chapters),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.alwaysShowChapterTransition,
                    title = stringResource(MR.strings.pref_always_show_chapter_transition),
                ),
            ),
        )
    }

    @Composable
    private fun getPagedGroup(readerPreferences: ReaderPreferences): Preference.PreferenceGroup {
        val navModePref = readerPreferences.navigationModePager
        val imageScaleTypePref = readerPreferences.imageScaleType
        val dualPageSplitPref = readerPreferences.dualPageSplitPaged
        val rotateToFitPref = readerPreferences.dualPageRotateToFit

        val navMode by navModePref.collectAsState()
        val imageScaleType by imageScaleTypePref.collectAsState()
        val dualPageSplit by dualPageSplitPref.collectAsState()
        val rotateToFit by rotateToFitPref.collectAsState()

        // KMK -->
        val pagedDisableZoomInPref = readerPreferences.pagedDisableZoomIn
        val landscapeZoomPref = readerPreferences.landscapeZoom
        val pagedDisableZoomIn by pagedDisableZoomInPref.collectAsState()
        val landscapeZoom by landscapeZoomPref.collectAsState()
        // KMK <--

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pager_viewer),
            preferenceItems = listOf(
                Preference.PreferenceItem.ListPreference(
                    preference = navModePref,
                    entries = ReaderPreferences.TapZones
                        .mapIndexed { index, it -> index to stringResource(it) }
                        .toMap()
                        .toMap(),
                    title = stringResource(MR.strings.pref_viewer_nav),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = readerPreferences.pagerNavInverted,
                    entries = listOf(
                        ReaderPreferences.TappingInvertMode.NONE,
                        ReaderPreferences.TappingInvertMode.HORIZONTAL,
                        ReaderPreferences.TappingInvertMode.VERTICAL,
                        ReaderPreferences.TappingInvertMode.BOTH,
                    )
                        .associateWith { stringResource(it.titleRes) }
                        .toMap(),
                    title = stringResource(MR.strings.pref_read_with_tapping_inverted),
                    visible = navMode != 5,
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = imageScaleTypePref,
                    entries = ReaderPreferences.ImageScaleType
                        .mapIndexed { index, it -> index + 1 to stringResource(it) }
                        .toMap()
                        .toMap(),
                    title = stringResource(MR.strings.pref_image_scale_type),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = readerPreferences.zoomStart,
                    entries = ReaderPreferences.ZoomStart
                        .mapIndexed { index, it -> index + 1 to stringResource(it) }
                        .toMap()
                        .toMap(),
                    title = stringResource(MR.strings.pref_zoom_start),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.cropBorders,
                    title = stringResource(MR.strings.pref_crop_borders),
                ),
                // SY -->
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.pageTransitionsPager,
                    title = stringResource(MR.strings.pref_page_transitions),
                ),
                // SY <--
                Preference.PreferenceItem.SwitchPreference(
                    preference = landscapeZoomPref,
                    title = stringResource(MR.strings.pref_landscape_zoom),
                    // KMK -->
                    visible = imageScaleType in zoomWideImagesAllowedList,
                    // KMK <--
                ),
                // KMK -->
                Preference.PreferenceItem.ListPreference(
                    preference = readerPreferences.landscapeZoomType,
                    entries = ReaderPreferences.LandscapeZoomScaleType.entries
                        .associateWith { stringResource(it.titleRes) }
                        .toMap(),
                    title = stringResource(KMR.strings.pref_landscape_zoom_type),
                    visible = landscapeZoom && imageScaleType in zoomWideImagesAllowedList,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = pagedDisableZoomInPref,
                    title = stringResource(KMR.strings.pref_paged_disable_zoom_in),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.pagedDoubleTapZoomEnabled,
                    title = stringResource(MR.strings.pref_double_tap_zoom),
                    visible = !pagedDisableZoomIn,
                ),
                // KMK <--
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.navigateToPan,
                    title = stringResource(MR.strings.pref_navigate_pan),
                    visible = navMode != 5,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = dualPageSplitPref,
                    title = stringResource(MR.strings.pref_dual_page_split),
                    onValueChanged = {
                        rotateToFitPref.set(false)
                        true
                    },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.dualPageInvertPaged,
                    title = stringResource(MR.strings.pref_dual_page_invert),
                    subtitle = stringResource(MR.strings.pref_dual_page_invert_summary),
                    visible = dualPageSplit,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = rotateToFitPref,
                    title = stringResource(MR.strings.pref_page_rotate),
                    onValueChanged = {
                        dualPageSplitPref.set(false)
                        true
                    },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.dualPageRotateToFitInvert,
                    title = stringResource(MR.strings.pref_page_rotate_invert),
                    visible = rotateToFit,
                ),
            ),
        )
    }

    @Composable
    private fun getWebtoonGroup(readerPreferences: ReaderPreferences): Preference.PreferenceGroup {
        val numberFormat = remember { NumberFormat.getPercentInstance() }

        val navModePref = readerPreferences.navigationModeWebtoon
        val dualPageSplitPref = readerPreferences.dualPageSplitWebtoon
        val rotateToFitPref = readerPreferences.dualPageRotateToFitWebtoon

        val navMode by navModePref.collectAsState()
        val dualPageSplit by dualPageSplitPref.collectAsState()
        val rotateToFit by rotateToFitPref.collectAsState()

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.webtoon_viewer),
            preferenceItems = listOf(
                Preference.PreferenceItem.ListPreference(
                    preference = navModePref,
                    entries = ReaderPreferences.TapZones
                        .mapIndexed { index, it -> index to stringResource(it) }
                        .toMap()
                        .toMap(),
                    title = stringResource(MR.strings.pref_viewer_nav),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = readerPreferences.webtoonNavInverted,
                    entries = listOf(
                        ReaderPreferences.TappingInvertMode.NONE,
                        ReaderPreferences.TappingInvertMode.HORIZONTAL,
                        ReaderPreferences.TappingInvertMode.VERTICAL,
                        ReaderPreferences.TappingInvertMode.BOTH,
                    )
                        .associateWith { stringResource(it.titleRes) }
                        .toMap(),
                    title = stringResource(MR.strings.pref_read_with_tapping_inverted),
                    visible = navMode != 5,
                ),
                // KMK -->
                Preference.PreferenceItem.ListPreference(
                    preference = readerPreferences.webtoonScaleType,
                    entries = WebtoonScaleType.entries
                        .associateWith { stringResource(it.titleRes) }
                        .toMap(),
                    title = stringResource(KMR.strings.pref_webtoon_scale_type),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.longStripGapSmartScale,
                    title = stringResource(KMR.strings.pref_smart_scale_long_strip_gap),
                ),
                // KUN -->
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.alwaysDecodeLongStripWithSSIV,
                    title = stringResource(KMR.strings.pref_always_decode_long_strip_with_ssiv),
                    subtitle = stringResource(KMR.strings.pref_always_decode_long_strip_with_ssiv_summary),
                ),
                // KUN <--
                // KMK <--
                Preference.PreferenceItem.SliderPreference(
                    preference = readerPreferences.webtoonSidePadding,
                    valueRange = ReaderPreferences.let {
                        it.WEBTOON_PADDING_MIN..it.WEBTOON_PADDING_MAX
                    },
                    title = stringResource(MR.strings.pref_webtoon_side_padding),
                    valueText = { numberFormat.format(it / 100f) },
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = readerPreferences.readerHideThreshold,
                    entries = mapOf(
                        ReaderPreferences.ReaderHideThreshold.HIGHEST to stringResource(MR.strings.pref_highest),
                        ReaderPreferences.ReaderHideThreshold.HIGH to stringResource(MR.strings.pref_high),
                        ReaderPreferences.ReaderHideThreshold.LOW to stringResource(MR.strings.pref_low),
                        ReaderPreferences.ReaderHideThreshold.LOWEST to stringResource(MR.strings.pref_lowest),
                    ),
                    title = stringResource(MR.strings.pref_hide_threshold),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.cropBordersWebtoon,
                    title = stringResource(MR.strings.pref_crop_borders),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = dualPageSplitPref,
                    title = stringResource(MR.strings.pref_dual_page_split),
                    onValueChanged = {
                        rotateToFitPref.set(false)
                        true
                    },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.dualPageInvertWebtoon,
                    title = stringResource(MR.strings.pref_dual_page_invert),
                    subtitle = stringResource(MR.strings.pref_dual_page_invert_summary),
                    visible = dualPageSplit,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = rotateToFitPref,
                    title = stringResource(MR.strings.pref_page_rotate),
                    onValueChanged = {
                        dualPageSplitPref.set(false)
                        true
                    },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.dualPageRotateToFitInvertWebtoon,
                    title = stringResource(MR.strings.pref_page_rotate_invert),
                    visible = rotateToFit,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.webtoonDoubleTapZoomEnabled,
                    title = stringResource(MR.strings.pref_double_tap_zoom),
                ),
                // KMK -->
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.webtoonPinchToZoomEnabled,
                    title = stringResource(KMR.strings.pref_pinch_to_zoom),
                ),
                // KMK <--
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.webtoonDisableZoomOut,
                    title = stringResource(MR.strings.pref_webtoon_disable_zoom_out),
                ),
                // SY -->
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.pageTransitionsWebtoon,
                    title = stringResource(MR.strings.pref_page_transitions),
                ),
                // SY <--
            ),
        )
    }

    // SY -->
    @Composable
    private fun getContinuousVerticalGroup(readerPreferences: ReaderPreferences): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.vertical_plus_viewer),
            preferenceItems = listOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.continuousVerticalTappingByPage,
                    title = stringResource(SYMR.strings.tap_scroll_page),
                    subtitle = stringResource(SYMR.strings.tap_scroll_page_summary),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.cropBordersContinuousVertical,
                    title = stringResource(MR.strings.pref_crop_borders),
                ),
            ),
        )
    }
    // SY <--

    @Composable
    private fun getNavigationGroup(readerPreferences: ReaderPreferences): Preference.PreferenceGroup {
        val readWithVolumeKeysPref = readerPreferences.readWithVolumeKeys
        val readWithVolumeKeys by readWithVolumeKeysPref.collectAsState()

        val verticalNavigator by readerPreferences.verticalNavigator.collectAsState()

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_reader_navigation),
            preferenceItems = listOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = readWithVolumeKeysPref,
                    title = stringResource(MR.strings.pref_read_with_volume_keys),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.readWithVolumeKeysInverted,
                    title = stringResource(MR.strings.pref_read_with_volume_keys_inverted),
                    visible = readWithVolumeKeys,
                ),
                Preference.PreferenceItem.MultiSelectListPreference(
                    preference = readerPreferences.verticalNavigator,
                    entries = ReadingMode.entries.filter { it != ReadingMode.DEFAULT }
                        .associate { it to stringResource(it.stringRes) }
                        .toMap(),
                    title = stringResource(MR.strings.pref_vertical_navigator),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.verticalNavigatorOnLeft,
                    title = stringResource(MR.strings.pref_webtoon_vertical_navigator_on_left),
                    visible = verticalNavigator.isNotEmpty(),
                ),
                Preference.PreferenceItem.SliderPreference(
                    preference = readerPreferences.verticalNavigatorHeight,
                    valueRange = 65..100,
                    steps = 6,
                    title = stringResource(MR.strings.pref_vertical_navigator_height),
                    visible = verticalNavigator.isNotEmpty(),
                ),
            ),
        )
    }

    @Composable
    private fun getActionsGroup(readerPreferences: ReaderPreferences): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_reader_actions),
            preferenceItems = listOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.readWithLongTap,
                    title = stringResource(MR.strings.pref_read_with_long_tap),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.folderPerManga,
                    title = stringResource(MR.strings.pref_create_folder_per_manga),
                    subtitle = stringResource(MR.strings.pref_create_folder_per_manga_summary),
                ),
            ),
        )
    }

    // SY -->
    @Composable
    private fun getPageDownloadingGroup(readerPreferences: ReaderPreferences): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = stringResource(SYMR.strings.page_downloading),
            preferenceItems = listOf(
                Preference.PreferenceItem.ListPreference(
                    preference = readerPreferences.preloadSize,
                    entries = mapOf(
                        4 to stringResource(SYMR.strings.reader_preload_amount_4_pages),
                        6 to stringResource(SYMR.strings.reader_preload_amount_6_pages),
                        8 to stringResource(SYMR.strings.reader_preload_amount_8_pages),
                        10 to stringResource(SYMR.strings.reader_preload_amount_10_pages),
                        12 to stringResource(SYMR.strings.reader_preload_amount_12_pages),
                        14 to stringResource(SYMR.strings.reader_preload_amount_14_pages),
                        16 to stringResource(SYMR.strings.reader_preload_amount_16_pages),
                        20 to stringResource(SYMR.strings.reader_preload_amount_20_pages),
                        // KNS --> from the previous personal fork; downloads stay bounded by the reader threads
                        100 to stringResource(KMR.strings.reader_preload_amount_100_pages),
                        // KNS <--
                    ),
                    title = stringResource(SYMR.strings.reader_preload_amount),
                    subtitle = stringResource(SYMR.strings.reader_preload_amount_summary),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = readerPreferences.readerThreads,
                    title = stringResource(SYMR.strings.download_threads),
                    subtitle = stringResource(SYMR.strings.download_threads_summary),
                    entries = List(5) { it }.associateWith { it.toString() }.toMap(),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = readerPreferences.cacheSize,
                    title = stringResource(SYMR.strings.reader_cache_size),
                    subtitle = stringResource(SYMR.strings.reader_cache_size_summary),
                    entries = mapOf(
                        "50" to "50 MB",
                        "75" to "75 MB",
                        "100" to "100 MB",
                        "150" to "150 MB",
                        "250" to "250 MB",
                        "500" to "500 MB",
                        "750" to "750 MB",
                        "1000" to "1 GB",
                        "1500" to "1.5 GB",
                        "2000" to "2 GB",
                        "2500" to "2.5 GB",
                        "3000" to "3 GB",
                        "3500" to "3.5 GB",
                        "4000" to "4 GB",
                        "4500" to "4.5 GB",
                        "5000" to "5 GB",
                    ),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.aggressivePageLoading,
                    title = stringResource(SYMR.strings.aggressively_load_pages),
                    subtitle = stringResource(SYMR.strings.aggressively_load_pages_summary),
                ),
            ),
        )
    }

    @Composable
    private fun getForkSettingsGroup(readerPreferences: ReaderPreferences): Preference.PreferenceGroup {
        val pageLayout by readerPreferences.pageLayout.collectAsState()
        return Preference.PreferenceGroup(
            title = stringResource(SYMR.strings.pref_category_fork),
            preferenceItems = listOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.readerInstantRetry,
                    title = stringResource(SYMR.strings.skip_queue_on_retry),
                    subtitle = stringResource(SYMR.strings.skip_queue_on_retry_summary),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.preserveReadingPosition,
                    title = stringResource(SYMR.strings.preserve_reading_position),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.useAutoWebtoon,
                    title = stringResource(SYMR.strings.auto_webtoon_mode),
                    subtitle = stringResource(SYMR.strings.auto_webtoon_mode_summary),
                ),
                Preference.PreferenceItem.MultiSelectListPreference(
                    preference = readerPreferences.readerBottomButtons,
                    entries = ReaderBottomButton.entries
                        .associate { it.value to stringResource(it.stringRes) }
                        .toMap(),
                    title = stringResource(SYMR.strings.reader_bottom_buttons),
                    subtitle = stringResource(SYMR.strings.reader_bottom_buttons_summary),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = readerPreferences.pageLayout,
                    entries = ReaderPreferences.PageLayouts
                        .mapIndexed { index, it -> index to stringResource(it) }
                        .toMap()
                        .toMap(),
                    title = stringResource(SYMR.strings.page_layout),
                    subtitle = stringResource(SYMR.strings.automatic_can_still_switch),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = readerPreferences.invertDoublePages,
                    title = stringResource(SYMR.strings.invert_double_pages),
                    visible = pageLayout != PagerConfig.PageLayout.SINGLE_PAGE,
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = readerPreferences.centerMarginType,
                    entries = ReaderPreferences.CenterMarginTypes
                        .mapIndexed { index, it -> index + 1 to stringResource(it) }
                        .toMap()
                        .toMap(),
                    title = stringResource(SYMR.strings.center_margin),
                    subtitle = stringResource(SYMR.strings.pref_center_margin_summary),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = readerPreferences.archiveReaderMode,
                    entries = ReaderPreferences.archiveModeTypes
                        .mapIndexed { index, it -> index to stringResource(it) }
                        .toMap()
                        .toMap(),
                    title = stringResource(SYMR.strings.pref_archive_reader_mode),
                    subtitle = stringResource(SYMR.strings.pref_archive_reader_mode_summary),
                ),
            ),
        )
    }
    // SY <--
}
