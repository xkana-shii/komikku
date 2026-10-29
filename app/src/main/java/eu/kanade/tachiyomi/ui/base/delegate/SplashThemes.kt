package eu.kanade.tachiyomi.ui.base.delegate

import android.app.Activity
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.annotation.StyleRes
import androidx.lifecycle.lifecycleScope
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.AppTheme
import eu.kanade.domain.ui.model.ThemeMode
import eu.kanade.tachiyomi.R
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import mihon.app.di.appGraph

// KNS -->
/**
 * Startup splash styles that follow the selected app appearance.
 *
 * The system draws the first frame before [Activity.onCreate], so it can only use a style that was chosen on a
 * previous run: on Android 12+ the style is persisted with SplashScreen.setSplashScreenTheme (by resource
 * name, hence the stable Theme.Tachiyomi.SplashScreen.<Theme>.<Variant> names) and applies from the next cold
 * start. Before Android 12 the system's first frame is the manifest theme; the AndroidX compat splash that
 * replaces it is rendered from the theme set before installSplashScreen(), so it can follow the preferences.
 *
 * Variants: Auto follows the device's light/dark setting, Light/Dark are fixed for an app mode that
 * differs from the device, AutoAmoled is black when the device is dark, and Black is AMOLED in forced dark.
 *
 * Only themes with fixed palettes can match their first screen. Custom is computed from a user seed at runtime,
 * which a static style cannot express, so it keeps the original Komikku splash colours (and exact black for
 * AMOLED dark). Cover-based colours only exist inside the app. Monet uses the system dynamic background on
 * Android 12+. SplashThemesTest checks the fixed colours against the Compose colour schemes.
 */
internal object SplashThemes {

    internal class SplashStyles(
        @StyleRes val auto: Int,
        @StyleRes val light: Int,
        @StyleRes val dark: Int,
        @StyleRes val autoAmoled: Int,
    )

    @StyleRes
    fun resolve(appTheme: AppTheme, themeMode: ThemeMode, isAmoled: Boolean): Int {
        val styles = styles[appTheme] ?: styles.getValue(AppTheme.DEFAULT)
        return when (themeMode) {
            ThemeMode.LIGHT -> styles.light
            ThemeMode.DARK -> if (isAmoled) R.style.Theme_Tachiyomi_SplashScreen_Black else styles.dark
            ThemeMode.SYSTEM -> if (isAmoled) styles.autoAmoled else styles.auto
        }
    }

    @StyleRes
    fun resolve(uiPreferences: UiPreferences): Int {
        return resolve(
            uiPreferences.appTheme.get(),
            uiPreferences.themeMode.get(),
            uiPreferences.themeDarkAmoled.get(),
        )
    }

    /** Deprecated or unknown themes fall back to the default palette, as the Compose theme does. */
    internal val styles: Map<AppTheme, SplashStyles> = mapOf(
        AppTheme.DEFAULT to SplashStyles(
            auto = R.style.Theme_Tachiyomi_SplashScreen_Default_Auto,
            light = R.style.Theme_Tachiyomi_SplashScreen_Default_Light,
            dark = R.style.Theme_Tachiyomi_SplashScreen_Default_Dark,
            autoAmoled = R.style.Theme_Tachiyomi_SplashScreen_Default_AutoAmoled,
        ),
        AppTheme.CATPPUCCIN to SplashStyles(
            auto = R.style.Theme_Tachiyomi_SplashScreen_Catppuccin_Auto,
            light = R.style.Theme_Tachiyomi_SplashScreen_Catppuccin_Light,
            dark = R.style.Theme_Tachiyomi_SplashScreen_Catppuccin_Dark,
            autoAmoled = R.style.Theme_Tachiyomi_SplashScreen_Catppuccin_AutoAmoled,
        ),
        AppTheme.TOKYONIGHT to SplashStyles(
            auto = R.style.Theme_Tachiyomi_SplashScreen_TokyoNight_Auto,
            light = R.style.Theme_Tachiyomi_SplashScreen_TokyoNight_Light,
            dark = R.style.Theme_Tachiyomi_SplashScreen_TokyoNight_Dark,
            autoAmoled = R.style.Theme_Tachiyomi_SplashScreen_TokyoNight_AutoAmoled,
        ),
        AppTheme.GREEN_APPLE to SplashStyles(
            auto = R.style.Theme_Tachiyomi_SplashScreen_GreenApple_Auto,
            light = R.style.Theme_Tachiyomi_SplashScreen_GreenApple_Light,
            dark = R.style.Theme_Tachiyomi_SplashScreen_GreenApple_Dark,
            autoAmoled = R.style.Theme_Tachiyomi_SplashScreen_GreenApple_AutoAmoled,
        ),
        AppTheme.LAVENDER to SplashStyles(
            auto = R.style.Theme_Tachiyomi_SplashScreen_Lavender_Auto,
            light = R.style.Theme_Tachiyomi_SplashScreen_Lavender_Light,
            dark = R.style.Theme_Tachiyomi_SplashScreen_Lavender_Dark,
            autoAmoled = R.style.Theme_Tachiyomi_SplashScreen_Lavender_AutoAmoled,
        ),
        AppTheme.MIDNIGHT_DUSK to SplashStyles(
            auto = R.style.Theme_Tachiyomi_SplashScreen_MidnightDusk_Auto,
            light = R.style.Theme_Tachiyomi_SplashScreen_MidnightDusk_Light,
            dark = R.style.Theme_Tachiyomi_SplashScreen_MidnightDusk_Dark,
            autoAmoled = R.style.Theme_Tachiyomi_SplashScreen_MidnightDusk_AutoAmoled,
        ),
        AppTheme.MONOCHROME to SplashStyles(
            auto = R.style.Theme_Tachiyomi_SplashScreen_Monochrome_Auto,
            light = R.style.Theme_Tachiyomi_SplashScreen_Monochrome_Light,
            dark = R.style.Theme_Tachiyomi_SplashScreen_Monochrome_Dark,
            autoAmoled = R.style.Theme_Tachiyomi_SplashScreen_Monochrome_AutoAmoled,
        ),
        AppTheme.NORD to SplashStyles(
            auto = R.style.Theme_Tachiyomi_SplashScreen_Nord_Auto,
            light = R.style.Theme_Tachiyomi_SplashScreen_Nord_Light,
            dark = R.style.Theme_Tachiyomi_SplashScreen_Nord_Dark,
            autoAmoled = R.style.Theme_Tachiyomi_SplashScreen_Nord_AutoAmoled,
        ),
        AppTheme.STRAWBERRY_DAIQUIRI to SplashStyles(
            auto = R.style.Theme_Tachiyomi_SplashScreen_StrawberryDaiquiri_Auto,
            light = R.style.Theme_Tachiyomi_SplashScreen_StrawberryDaiquiri_Light,
            dark = R.style.Theme_Tachiyomi_SplashScreen_StrawberryDaiquiri_Dark,
            autoAmoled = R.style.Theme_Tachiyomi_SplashScreen_StrawberryDaiquiri_AutoAmoled,
        ),
        AppTheme.TAKO to SplashStyles(
            auto = R.style.Theme_Tachiyomi_SplashScreen_Tako_Auto,
            light = R.style.Theme_Tachiyomi_SplashScreen_Tako_Light,
            dark = R.style.Theme_Tachiyomi_SplashScreen_Tako_Dark,
            autoAmoled = R.style.Theme_Tachiyomi_SplashScreen_Tako_AutoAmoled,
        ),
        AppTheme.TEALTURQUOISE to SplashStyles(
            auto = R.style.Theme_Tachiyomi_SplashScreen_TealTurquoise_Auto,
            light = R.style.Theme_Tachiyomi_SplashScreen_TealTurquoise_Light,
            dark = R.style.Theme_Tachiyomi_SplashScreen_TealTurquoise_Dark,
            autoAmoled = R.style.Theme_Tachiyomi_SplashScreen_TealTurquoise_AutoAmoled,
        ),
        AppTheme.TIDAL_WAVE to SplashStyles(
            auto = R.style.Theme_Tachiyomi_SplashScreen_TidalWave_Auto,
            light = R.style.Theme_Tachiyomi_SplashScreen_TidalWave_Light,
            dark = R.style.Theme_Tachiyomi_SplashScreen_TidalWave_Dark,
            autoAmoled = R.style.Theme_Tachiyomi_SplashScreen_TidalWave_AutoAmoled,
        ),
        AppTheme.YINYANG to SplashStyles(
            auto = R.style.Theme_Tachiyomi_SplashScreen_YinYang_Auto,
            light = R.style.Theme_Tachiyomi_SplashScreen_YinYang_Light,
            dark = R.style.Theme_Tachiyomi_SplashScreen_YinYang_Dark,
            autoAmoled = R.style.Theme_Tachiyomi_SplashScreen_YinYang_AutoAmoled,
        ),
        AppTheme.YOTSUBA to SplashStyles(
            auto = R.style.Theme_Tachiyomi_SplashScreen_Yotsuba_Auto,
            light = R.style.Theme_Tachiyomi_SplashScreen_Yotsuba_Light,
            dark = R.style.Theme_Tachiyomi_SplashScreen_Yotsuba_Dark,
            autoAmoled = R.style.Theme_Tachiyomi_SplashScreen_Yotsuba_AutoAmoled,
        ),
        AppTheme.CLOUDFLARE to SplashStyles(
            auto = R.style.Theme_Tachiyomi_SplashScreen_Cloudflare_Auto,
            light = R.style.Theme_Tachiyomi_SplashScreen_Cloudflare_Light,
            dark = R.style.Theme_Tachiyomi_SplashScreen_Cloudflare_Dark,
            autoAmoled = R.style.Theme_Tachiyomi_SplashScreen_Cloudflare_AutoAmoled,
        ),
        AppTheme.COTTONCANDY to SplashStyles(
            auto = R.style.Theme_Tachiyomi_SplashScreen_CottonCandy_Auto,
            light = R.style.Theme_Tachiyomi_SplashScreen_CottonCandy_Light,
            dark = R.style.Theme_Tachiyomi_SplashScreen_CottonCandy_Dark,
            autoAmoled = R.style.Theme_Tachiyomi_SplashScreen_CottonCandy_AutoAmoled,
        ),
        AppTheme.DOOM to SplashStyles(
            auto = R.style.Theme_Tachiyomi_SplashScreen_Doom_Auto,
            light = R.style.Theme_Tachiyomi_SplashScreen_Doom_Light,
            dark = R.style.Theme_Tachiyomi_SplashScreen_Doom_Dark,
            autoAmoled = R.style.Theme_Tachiyomi_SplashScreen_Doom_AutoAmoled,
        ),
        AppTheme.MATRIX to SplashStyles(
            auto = R.style.Theme_Tachiyomi_SplashScreen_Matrix_Auto,
            light = R.style.Theme_Tachiyomi_SplashScreen_Matrix_Light,
            dark = R.style.Theme_Tachiyomi_SplashScreen_Matrix_Dark,
            autoAmoled = R.style.Theme_Tachiyomi_SplashScreen_Matrix_AutoAmoled,
        ),
        AppTheme.MOCHA to SplashStyles(
            auto = R.style.Theme_Tachiyomi_SplashScreen_Mocha_Auto,
            light = R.style.Theme_Tachiyomi_SplashScreen_Mocha_Light,
            dark = R.style.Theme_Tachiyomi_SplashScreen_Mocha_Dark,
            autoAmoled = R.style.Theme_Tachiyomi_SplashScreen_Mocha_AutoAmoled,
        ),
        AppTheme.SAPPHIRE to SplashStyles(
            auto = R.style.Theme_Tachiyomi_SplashScreen_Sapphire_Auto,
            light = R.style.Theme_Tachiyomi_SplashScreen_Sapphire_Light,
            dark = R.style.Theme_Tachiyomi_SplashScreen_Sapphire_Dark,
            autoAmoled = R.style.Theme_Tachiyomi_SplashScreen_Sapphire_AutoAmoled,
        ),
        AppTheme.MONET to SplashStyles(
            auto = R.style.Theme_Tachiyomi_SplashScreen_Monet_Auto,
            light = R.style.Theme_Tachiyomi_SplashScreen_Monet_Light,
            dark = R.style.Theme_Tachiyomi_SplashScreen_Monet_Dark,
            autoAmoled = R.style.Theme_Tachiyomi_SplashScreen_Monet_AutoAmoled,
        ),
        AppTheme.CUSTOM to SplashStyles(
            auto = R.style.Theme_Tachiyomi_SplashScreen_Custom_Auto,
            light = R.style.Theme_Tachiyomi_SplashScreen_Custom_Light,
            dark = R.style.Theme_Tachiyomi_SplashScreen_Custom_Dark,
            autoAmoled = R.style.Theme_Tachiyomi_SplashScreen_Custom_AutoAmoled,
        ),
    )
}

/**
 * Applies the splash style before installSplashScreen() so the AndroidX compat splash (before Android 12) is
 * drawn with it. From Android 12 the system splash is already chosen by the persisted style.
 */
internal fun Activity.applyCompatSplashTheme() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        setTheme(SplashThemes.resolve(appGraph.uiPreferences))
    }
}

/**
 * Keeps the system's persisted splash style in step with the selected appearance, so the next cold start shows it.
 * The persisted style survives the process, which is why it is updated whenever one of the inputs changes.
 */
internal fun ComponentActivity.keepSplashThemePersisted() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val uiPreferences = appGraph.uiPreferences
    lifecycleScope.launch {
        combine(
            uiPreferences.appTheme.changes(),
            uiPreferences.themeMode.changes(),
            uiPreferences.themeDarkAmoled.changes(),
        ) { appTheme, themeMode, isAmoled -> SplashThemes.resolve(appTheme, themeMode, isAmoled) }
            .distinctUntilChanged()
            .collect { splashScreen.setSplashScreenTheme(it) }
    }
}
// KNS <--
