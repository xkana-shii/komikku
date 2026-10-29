package eu.kanade.tachiyomi.ui.base.delegate

import androidx.compose.ui.graphics.toArgb
import eu.kanade.domain.ui.model.AppTheme
import eu.kanade.domain.ui.model.ThemeMode
import eu.kanade.presentation.theme.colorSchemes
import eu.kanade.tachiyomi.R
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.File

// KNS -->
class SplashThemesTest {

    private fun colors(dir: String): Map<String, Int> {
        val file = File("src/main/res/$dir/splash_themes.xml")
        return Regex("""<color name="([a-z_0-9]+)">#([0-9A-Fa-f]{6})</color>""")
            .findAll(file.readText())
            .associate { it.groupValues[1] to (0xFF000000.toInt() or it.groupValues[2].toInt(16)) }
    }

    @Test
    fun `fixed splash colours match the Compose palettes`() {
        val base = colors("values")
        val night = colors("values-night")
        val keys = mapOf(
            AppTheme.DEFAULT to "tachiyomi",
            AppTheme.CATPPUCCIN to "catppuccin",
            AppTheme.TOKYONIGHT to "tokyonight",
            AppTheme.GREEN_APPLE to "greenapple",
            AppTheme.LAVENDER to "lavender",
            AppTheme.MIDNIGHT_DUSK to "midnightdusk",
            AppTheme.MONOCHROME to "monochrome",
            AppTheme.NORD to "nord",
            AppTheme.STRAWBERRY_DAIQUIRI to "strawberry",
            AppTheme.TAKO to "tako",
            AppTheme.TEALTURQUOISE to "tealturquoise",
            AppTheme.TIDAL_WAVE to "tidalwave",
            AppTheme.YINYANG to "yinyang",
            AppTheme.YOTSUBA to "yotsuba",
            AppTheme.CLOUDFLARE to "cloudflare",
            AppTheme.COTTONCANDY to "cottoncandy",
            AppTheme.DOOM to "doom",
            AppTheme.MATRIX to "matrix",
            AppTheme.MOCHA to "mocha",
            AppTheme.SAPPHIRE to "sapphire",
        )
        keys.keys shouldBe colorSchemes.keys
        keys.forEach { (theme, key) ->
            val scheme = colorSchemes.getValue(theme)
            base["splash_${key}_light"] shouldBe scheme.lightScheme.background.toArgb()
            base["splash_${key}_auto"] shouldBe scheme.lightScheme.background.toArgb()
            base["splash_${key}_dark"] shouldBe scheme.darkScheme.background.toArgb()
            night["splash_${key}_auto"] shouldBe scheme.darkScheme.background.toArgb()
            base["splash_${key}_auto_amoled"] shouldBe scheme.lightScheme.background.toArgb()
            night["splash_${key}_auto_amoled"] shouldBe 0xFF000000.toInt()
        }
    }

    @Test
    fun `custom keeps the original Komikku splash colours`() {
        val base = colors("values")
        val night = colors("values-night")
        base["splash_custom_light"] shouldBe 0xFFFBEDED.toInt()
        base["splash_custom_dark"] shouldBe 0xFF1A0A0A.toInt()
        night["splash_custom_auto"] shouldBe 0xFF1A0A0A.toInt()
    }

    @Test
    fun `every selectable theme has splash styles`() {
        val selectable = AppTheme.entries.filter { it.titleRes != null }
        selectable.forEach { SplashThemes.styles.containsKey(it) shouldBe true }
    }

    @Test
    fun `resolve follows the selected mode instead of the device mode`() {
        val nord = SplashThemes.styles.getValue(AppTheme.NORD)
        SplashThemes.resolve(AppTheme.NORD, ThemeMode.LIGHT, isAmoled = true) shouldBe nord.light
        SplashThemes.resolve(AppTheme.NORD, ThemeMode.DARK, isAmoled = false) shouldBe nord.dark
        SplashThemes.resolve(AppTheme.NORD, ThemeMode.SYSTEM, isAmoled = false) shouldBe nord.auto
        SplashThemes.resolve(AppTheme.NORD, ThemeMode.SYSTEM, isAmoled = true) shouldBe nord.autoAmoled
        SplashThemes.resolve(AppTheme.NORD, ThemeMode.DARK, isAmoled = true) shouldBe
            R.style.Theme_Tachiyomi_SplashScreen_Black
    }

    @Test
    fun `deprecated themes fall back to the default palette`() {
        val default = SplashThemes.styles.getValue(AppTheme.DEFAULT)
        SplashThemes.resolve(AppTheme.HOT_PINK, ThemeMode.LIGHT, isAmoled = false) shouldBe default.light
    }
}
// KNS <--
