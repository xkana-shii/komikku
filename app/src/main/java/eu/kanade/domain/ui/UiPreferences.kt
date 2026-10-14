package eu.kanade.domain.ui

import androidx.compose.material3.FabPosition
import com.materialkolor.PaletteStyle
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.domain.ui.model.AppTheme
import eu.kanade.domain.ui.model.TabletUiMode
import eu.kanade.domain.ui.model.ThemeMode
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Inject
@SingleIn(AppScope::class)
class UiPreferences(
    private val preferenceStore: PreferenceStore,
) {

    val themeMode = preferenceStore.getEnum("pref_theme_mode_key", ThemeMode.SYSTEM)

    val appTheme = preferenceStore.getEnum(
        "pref_app_theme",
        AppTheme.MONET,
    )

    val themeDarkAmoled = preferenceStore.getBoolean("pref_theme_dark_amoled_key", false)

    // KMK -->
    val colorTheme = preferenceStore.getInt("pref_color_theme", 0xFFDF0090.toInt())

    val customThemeStyle = preferenceStore.getEnum("pref_custom_theme_style_key", PaletteStyle.Fidelity)

    val themeCoverBased = preferenceStore.getBoolean("pref_theme_cover_based_key", true)

    val themeCoverBasedStyle = preferenceStore.getEnum("pref_theme_cover_based_style_key", PaletteStyle.Vibrant)

    val preloadLibraryColor = preferenceStore.getBoolean("pref_preload_library_color_key", true)
    // KMK <--

    val relativeTime = preferenceStore.getBoolean("relative_time_v2", true)

    val dateFormat = preferenceStore.getString("app_date_format", "")

    val tabletUiMode = preferenceStore.getEnum("tablet_ui_mode", TabletUiMode.AUTOMATIC)

    val imagesInDescription = preferenceStore.getBoolean("pref_render_images_description", true)

    // SY -->

    val expandFilters = preferenceStore.getBoolean("eh_expand_filters", false)

    val hideFeedTab = preferenceStore.getBoolean("hide_latest_tab", false)

    val feedTabInFront = preferenceStore.getBoolean("latest_tab_position", false)

    // KMK -->
    val expandRelatedMangas = preferenceStore.getBoolean("expand_related_mangas", true)

    val relatedMangasInOverflow = preferenceStore.getBoolean("related_mangas_in_overflow", false)

    val showHomeOnRelatedMangas = preferenceStore.getBoolean("show_home_on_related_mangas", true)

    val readButtonPosition = preferenceStore.getString("reading_button_position", FabPosition.End.toString())

    val usePanoramaCoverFlow = preferenceStore.getBoolean("use_panorama_cover_flow", false)

    val usePanoramaCoverAlways = preferenceStore.getBoolean("use_panorama_cover_grid", true)

    val usePanoramaCoverMangaInfo = preferenceStore.getBoolean("use_panorama_cover_manga_info", false)

    val topAlignCover = preferenceStore.getBoolean("top_align_cover", false)
    // KMK <--

    val recommendsInOverflow = preferenceStore.getBoolean("recommends_in_overflow", false)

    val mergeInOverflow = preferenceStore.getBoolean("merge_in_overflow", true)

    val previewsRowCount = preferenceStore.getInt("pref_previews_row_count", 4)

    val useNewSourceNavigation = preferenceStore.getBoolean("use_new_source_navigation", true)

    val bottomBarLabels = preferenceStore.getBoolean("pref_show_bottom_bar_labels", true)

    val showNavUpdates = preferenceStore.getBoolean("pref_show_updates_button", true)

    val showNavHistory = preferenceStore.getBoolean("pref_show_history_button", true)

    // SY <--

    companion object {
        fun dateFormat(format: String): DateTimeFormatter = when (format) {
            "" -> DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
            else -> DateTimeFormatter.ofPattern(format, Locale.getDefault())
        }
    }
}
