package eu.kanade.domain.extension.interactor

import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.zacsweers.metro.Inject
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.util.system.LocaleHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import com.murgupluoglu.flagkit.R as FlagkitR

@Inject
class GetExtensionLanguages(
    private val preferences: SourcePreferences,
    private val extensionManager: ExtensionManager,
) {
    fun subscribe(): Flow<List<String>> {
        return combine(
            preferences.enabledLanguages.changes(),
            extensionManager.availableExtensionsFlow,
        ) { enabledLanguage, availableExtensions ->
            availableExtensions
                .flatMap { ext ->
                    ext.sources.map { it.lang }
                }
                .distinct()
                .sortedWith(
                    compareBy<String> { it !in enabledLanguage }.then(LocaleHelper.comparator),
                )
        }
    }

    // KMK -->
    companion object {
        fun getLanguageIconID(lang: String): Int? {
            return when (lang) {
                "all" -> R.drawable.ic_flag_un
                // "other" -> R.drawable.globe
                "af" -> FlagkitR.drawable.za // Afrikaans -> South Africa, ZA
                "am" -> FlagkitR.drawable.et // Amharic -> Ethiopia, ET
                "ar" -> FlagkitR.drawable.eg // Arabic -> Egypt, EG - Saudi Arabia, SA
                "az" -> FlagkitR.drawable.az // Azerbaijani -> Azerbaijan, AZ
                "be" -> FlagkitR.drawable.by // Belarusian -> Belarus, BY
                "bg" -> FlagkitR.drawable.bg // Bulgarian -> Bulgaria, BG
                "bn" -> FlagkitR.drawable.bd // Bengali -> Bangladesh, BD
                "br" -> FlagkitR.drawable.fr // Breton -> France, FR
                "bs" -> FlagkitR.drawable.ba // Bosnia and Herzegovina, BA
                "ca" -> FlagkitR.drawable.es // Catalan -> Spain, ES
                "ceb" -> FlagkitR.drawable.ph // Cebuano -> Philippines, PH
                "cn" -> FlagkitR.drawable.cn // Chinese -> China, CN
                "co" -> FlagkitR.drawable.es // Corsican -> Spain, ES
                "cs" -> FlagkitR.drawable.cz // Czech -> Czech Republic, CZ
                "da" -> FlagkitR.drawable.dk // Danish -> Denmark, DK
                "de" -> FlagkitR.drawable.de // German -> Germany, DE
                "el" -> FlagkitR.drawable.gr // Greek -> Greece, GR
                "en" -> FlagkitR.drawable.us // English -> United States, US
                "eo" -> R.drawable.ic_flag_esperanto // Esperanto -> no country
                "es-419" -> FlagkitR.drawable.mx // Spanish -> Mexico MX, Latin America, Latin America
                "es" -> FlagkitR.drawable.es // Spanish -> Spain, ES
                "et" -> FlagkitR.drawable.ee // Estonian -> Estonia, EE
                "eu" -> FlagkitR.drawable.es // Basque -> Spain, ES
                "fa" -> FlagkitR.drawable.ir // Persian -> Iran, IR
                "fi" -> FlagkitR.drawable.fi // Finnish -> Finland, FI
                "fil" -> FlagkitR.drawable.ph // Filipino -> Philippines, PH
                "fo" -> FlagkitR.drawable.fo // Faroese -> Faroe Island, FO
                "fr" -> FlagkitR.drawable.fr // French -> France, FR
                "ga" -> FlagkitR.drawable.ie // Irish -> Ireland, IE
                "gn" -> FlagkitR.drawable.py // Guarani -> Paraguay, PY
                "gu" -> FlagkitR.drawable.in_ // Gujarati -> India, IN
                "ha" -> FlagkitR.drawable.ng // Hausa -> Nigeria, NG
                "he" -> FlagkitR.drawable.il // Hebrew -> Israel, IL
                "hi" -> FlagkitR.drawable.in_ // Hindi -> India, IN
                "hr" -> FlagkitR.drawable.hr // Croatian -> Croatia, HR
                "ht" -> FlagkitR.drawable.ht // Haitian -> Haiti, HT
                "hu" -> FlagkitR.drawable.hu // Hungarian -> Hungary, HU
                "hy" -> FlagkitR.drawable.am // Armenian -> Armenia, AM
                "id" -> FlagkitR.drawable.id // Indonesian -> Indonesia, ID
                "ig" -> FlagkitR.drawable.ng // Igbo -> Nigeria, NG
                "is" -> FlagkitR.drawable.is_ // Icelandic -> Iceland, IS
                "it" -> FlagkitR.drawable.it // Italian -> Italy, IT
                "ja" -> FlagkitR.drawable.jp // Japanese -> Japan, JP
                "jv" -> FlagkitR.drawable.id // Javanese -> Indonesia, ID
                "ka" -> FlagkitR.drawable.ge // Georgian -> Georgia, GE
                "kk" -> FlagkitR.drawable.kz // Kazakh -> Kazakhstan, KZ
                "km" -> FlagkitR.drawable.kh // Khmer -> Cambodia, KH
                "kn" -> FlagkitR.drawable.in_ // Kannada -> India, IN
                "ko" -> FlagkitR.drawable.kr // Korean -> South Korea, KR
                "kr" -> FlagkitR.drawable.ng // Kanuri -> Nigeria, NG
                "ku" -> FlagkitR.drawable.iq // Kurdish -> Iraq, IQ
                "ky" -> FlagkitR.drawable.kg // Kyrgyz -> Kyrgyzstan, KG
                // "la" -> FlagkitR.drawable.ic_flag_la       // Latin -> Latin America, Latin America
                "lb" -> FlagkitR.drawable.lu // Luxembourgish -> Luxembourg, LU
                "lmo" -> FlagkitR.drawable.it // Lombard, Italy, IT
                "lo" -> FlagkitR.drawable.la // Lao -> Laos, LA
                "lt" -> FlagkitR.drawable.lt // Lithuanian -> Lithuania, LT
                "lv" -> FlagkitR.drawable.lv // Latvian -> Latvia, LV
                "mg" -> FlagkitR.drawable.mg // Malagasy -> Madagascar, MG
                "mi" -> FlagkitR.drawable.nz // Maori -> New Zealand, NZ
                "mk" -> FlagkitR.drawable.mk // Macedonian -> Macedonia, MK
                "ml" -> FlagkitR.drawable.in_ // Malayalam -> India, IN
                "mn" -> FlagkitR.drawable.mn // Mongolian -> Mongolia, MN
                "mo" -> FlagkitR.drawable.md // Moldavian -> Moldova, MD
                "mr" -> FlagkitR.drawable.in_ // Marathi -> India, IN
                "ms" -> FlagkitR.drawable.my // Malay -> Malaysia, MY
                "mt" -> FlagkitR.drawable.mt // Maltese -> Malta, MT
                "my" -> FlagkitR.drawable.mm // Myanmar -> Myanmar, MM
                "ne" -> FlagkitR.drawable.np // Nepali -> Nepal, NP
                "nl" -> FlagkitR.drawable.nl // Dutch -> Netherlands, NL
                "no" -> FlagkitR.drawable.no // Norwegian -> Norway, NO
                "ny" -> FlagkitR.drawable.mw // Nyanja -> Malawi, MW
                "pl" -> FlagkitR.drawable.pl // Polish -> Poland, PL
                "ps" -> FlagkitR.drawable.af // Pashto -> Afghanistan, AF - Pakistan, PK
                "pt-BR" -> FlagkitR.drawable.br // Portuguese, Brazil, BR
                "pt-PT" -> FlagkitR.drawable.pt // Portuguese, Portugal, PT
                "pt" -> FlagkitR.drawable.pt // Portuguese -> Portugal, PT
                "rm" -> FlagkitR.drawable.ch // Romansh -> Switzerland, SW
                "ro" -> FlagkitR.drawable.ro // Romanian -> Romania, RO
                "ru" -> FlagkitR.drawable.ru // Russian -> Russia, RU
                "sd" -> FlagkitR.drawable.pk // Sindhi -> Pakistan, PK
                "sh" -> FlagkitR.drawable.hr // Serbo-Croatian -> Serbia, HR
                "si" -> FlagkitR.drawable.lk // Sinhalese -> Sri Lanka, LK
                "sk" -> FlagkitR.drawable.sk // Slovak -> Slovakia, SK
                "sl" -> FlagkitR.drawable.si // Slovenian -> Slovenia, SI
                "sm" -> FlagkitR.drawable.ws // Samoan -> Samoa, WS
                "sn" -> FlagkitR.drawable.zw // Shona -> Zimbabwe, ZW
                "so" -> FlagkitR.drawable.so // Somali -> Somalia, SO
                "sq" -> FlagkitR.drawable.al // Albanian -> Albania, AL
                "sr" -> FlagkitR.drawable.hr // Serbian -> Serbia, HR
                "st" -> FlagkitR.drawable.ls // Sesotho -> South Africa, ZA - Lesotho, LS
                "sv" -> FlagkitR.drawable.se // Swedish -> Sweden, SE
                "sw" -> FlagkitR.drawable.tz // Swahili -> Tanzania, TZ - Kenya, KE
                "ta" -> FlagkitR.drawable.in_ // Tamil -> India, IN
                "te" -> FlagkitR.drawable.in_ // Telugu -> India, IN
                "tg" -> FlagkitR.drawable.tj // Tajik -> Tajikistan, TJ
                "th" -> FlagkitR.drawable.th // Thai -> Thailand, TH
                "ti" -> FlagkitR.drawable.er // Tigrinya -> Eritrea, ER
                "tk" -> FlagkitR.drawable.tm // Turkmen -> Turkmenistan, TM
                "tl" -> FlagkitR.drawable.ph // Filipino -> Philippines, PH
                "to" -> FlagkitR.drawable.to // Tonga -> Tonga, TO
                "tr" -> FlagkitR.drawable.tr // Turkish -> Turkey, TR
                "uk" -> FlagkitR.drawable.ua // Ukrainian -> Ukraine, UA
                "ur" -> FlagkitR.drawable.pk // Urdu -> Pakistan, PK
                "uz" -> FlagkitR.drawable.uz // Uzbek -> Uzbekistan, UZ
                "vec" -> FlagkitR.drawable.it // Venetian -> Italy, IT
                "vi" -> FlagkitR.drawable.vn // Vietnamese -> Vietnam, VN
                "yo" -> FlagkitR.drawable.ng // Yoruba -> Nigeria, NG
                "zh-Hans" -> FlagkitR.drawable.cn // Chinese, simplified -> China, CN
                "zh-Hant" -> FlagkitR.drawable.tw // Chinese, traditional -> Taiwan, TW
                "zh" -> FlagkitR.drawable.cn // Chinese -> China, CN
                "zu" -> FlagkitR.drawable.za // Zulu -> South Africa, ZA
                // Additional from App language
                "gl" -> FlagkitR.drawable.es // Galician -> Spain, ES
                "in" -> FlagkitR.drawable.id // Indonesian -> Indonesia, ID
                "nb-NO" -> FlagkitR.drawable.no // Norwegian Bokmål -> Norway, NO
                "nn" -> FlagkitR.drawable.no // Norwegian Nynorsk -> Norway, NO
                "sc" -> FlagkitR.drawable.it // Sardinian -> Italy, IT
                "sdh" -> FlagkitR.drawable.ir // Southern Kurdish -> Iran, IR
                "sah" -> FlagkitR.drawable.ru // Sakha -> Russia, RU
                "cv" -> FlagkitR.drawable.ru // Chuvash -> Russia, RU
                "sa" -> FlagkitR.drawable.in_ // Sanskrit -> India, IN
                "ka-GE" -> FlagkitR.drawable.ge // Georgian -> Georgia, GE
                "zh-CN" -> FlagkitR.drawable.cn // Chinese, simplified -> China, CN
                "zh-TW" -> FlagkitR.drawable.tw // Chinese, traditional -> Taiwan, TW"
                else -> null
            }
        }
    }
    // KMK <--
}

@Preview
@Composable
private fun LanguageIconsPreview() {
    val languages = listOf(
        "all",
        "other",
        "af",
        "am",
        "ar",
        "az",
        "be",
        "bg",
        "bn",
        "br",
        "bs",
        "ca",
        "ceb",
        "cn",
        "co",
        "cs",
        "da",
        "de",
        "el",
        "en",
        "eo",
        "es-419",
        "es",
        "et",
        "eu",
        "fa",
        "fi",
        "fil",
        "fo",
        "fr",
        "ga",
        "gn",
        "gu",
        "ha",
        "he",
        "hi",
        "hr",
        "ht",
        "hu",
        "hy",
        "id",
        "ig",
        "is",
        "it",
        "ja",
        "jv",
        "ka",
        "kk",
        "km",
        "kn",
        "ko",
        "kr",
        "ku",
        "ky",
        "la",
        "lb",
        "lmo",
        "lo",
        "lt",
        "lv",
        "mg",
        "mi",
        "mk",
        "ml",
        "mn",
        "mo",
        "mr",
        "ms",
        "mt",
        "my",
        "ne",
        "nl",
        "no",
        "ny",
        "pl",
        "ps",
        "pt-BR",
        "pt-PT",
        "pt",
        "rm",
        "ro",
        "ru",
        "sd",
        "sh",
        "si",
        "sk",
        "sl",
        "sm",
        "sn",
        "so",
        "sq",
        "sr",
        "st",
        "sv",
        "sw",
        "ta",
        "te",
        "tg",
        "th",
        "ti",
        "tk",
        "tl",
        "to",
        "tr",
        "uk",
        "ur",
        "uz",
        "vec",
        "vi",
        "yo",
        "zh-Hans",
        "zh-Hant",
        "zh",
        "zu",
        "gl",
        "in",
        "nb-NO",
        "nn",
        "sc",
        "sdh",
        "sah",
        "cv",
        "sa",
        "ka-GE",
        "zh-CN",
        "zh-TW",
    )
    FlowRow {
        languages.forEach { language ->
            val iconResId = GetExtensionLanguages.getLanguageIconID(language) ?: R.drawable.globe
            Icon(
                painter = painterResource(id = iconResId),
                tint = Color.Unspecified,
                contentDescription = language,
                modifier = Modifier
                    .width(21.dp)
                    .height(15.dp),
            )
        }
    }
}
