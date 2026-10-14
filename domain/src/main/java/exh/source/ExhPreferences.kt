package exh.source

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import exh.log.EHLogLevel
import exh.log.EHLogLevel.Companion.EH_LOG_LEVEL_PREF
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.release.service.AppUpdatePolicy

@Inject
@SingleIn(AppScope::class)
class ExhPreferences(
    private val preferenceStore: PreferenceStore,
) {
    // KMK -->
    val appShouldAutoUpdate = preferenceStore.getStringSet(
        "should_auto_update",
        setOf(
            AppUpdatePolicy.DEVICE_ONLY_ON_WIFI,
        ),
    )
    // KMK <--

    // SY -->
    val isHentaiEnabled = preferenceStore.getBoolean("eh_is_hentai_enabled", false)

    // KMK -->
    val ehIncognitoMode = preferenceStore.getBoolean("eh_incognito_mode", false)
    // KMK <--

    val enableExhentai = preferenceStore.getBoolean(Preference.privateKey("enable_exhentai"), false)

    val imageQuality = preferenceStore.getString("ehentai_quality", "auto")

    val useHentaiAtHome = preferenceStore.getInt("eh_enable_hah", 0)

    val useJapaneseTitle = preferenceStore.getBoolean("use_jp_title", false)

    val exhUseOriginalImages = preferenceStore.getBoolean("eh_useOrigImages", false)

    val ehTagFilterValue = preferenceStore.getInt("eh_tag_filtering_value", 0)

    val ehTagWatchingValue = preferenceStore.getInt("eh_tag_watching_value", 0)

    // EH Cookies
    val memberIdVal = preferenceStore.getString(Preference.privateKey("eh_ipb_member_id"), "")

    val passHashVal = preferenceStore.getString(Preference.privateKey("eh_ipb_pass_hash"), "")
    val igneousVal = preferenceStore.getString(Preference.privateKey("eh_igneous"), "")
    val ehSettingsProfile = preferenceStore.getInt(Preference.privateKey("eh_ehSettingsProfile"), -1)
    val exhSettingsProfile = preferenceStore.getInt(Preference.privateKey("eh_exhSettingsProfile"), -1)
    val exhSettingsKey = preferenceStore.getString(Preference.privateKey("eh_settingsKey"), "")
    val exhSessionCookie = preferenceStore.getString(Preference.privateKey("eh_sessionCookie"), "")
    val exhHathPerksCookies = preferenceStore.getString(Preference.privateKey("eh_hathPerksCookie"), "")

    val exhShowSyncIntro = preferenceStore.getBoolean("eh_show_sync_intro", true)

    val exhReadOnlySync = preferenceStore.getBoolean("eh_sync_read_only", false)

    val exhLenientSync = preferenceStore.getBoolean("eh_lenient_sync", false)

    val exhShowSettingsUploadWarning = preferenceStore.getBoolean("eh_showSettingsUploadWarning2", true)

    // KMK -->
    fun logLevel(isDebugBuildType: Boolean) = preferenceStore.getInt(EH_LOG_LEVEL_PREF, EHLogLevel.defaultLogLevel(isDebugBuildType))
    // KMK <--

    val exhAutoUpdateFrequency = preferenceStore.getInt("eh_auto_update_frequency", 1)

    val exhAutoUpdateRequirements = preferenceStore.getStringSet("eh_auto_update_restrictions", emptySet())

    val exhAutoUpdateStats = preferenceStore.getString(Preference.appStateKey("eh_auto_update_stats"), "")

    val exhWatchedListDefaultState = preferenceStore.getBoolean("eh_watched_list_default_state", false)

    val exhSettingsLanguages = preferenceStore.getString(
        "eh_settings_languages",
        "false*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\n" +
            "false*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\n" +
            "false*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\nfalse*false*false\n" +
            "false*false*false\nfalse*false*false",
    )

    val exhEnabledCategories = preferenceStore.getString(
        "eh_enabled_categories",
        "false,false,false,false,false,false,false,false,false,false",
    )

    val enhancedEHentaiView = preferenceStore.getBoolean("enhanced_e_hentai_view", true)
}
