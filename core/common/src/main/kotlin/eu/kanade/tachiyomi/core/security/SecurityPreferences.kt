package eu.kanade.tachiyomi.core.security

import dev.icerock.moko.resources.StringResource
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR

@Inject
@SingleIn(AppScope::class)
class SecurityPreferences(
    private val preferenceStore: PreferenceStore,
) {

    val useAuthenticator = preferenceStore.getBoolean("use_biometric_lock", false)

    val lockAppAfter = preferenceStore.getInt("lock_app_after", 0)

    val secureScreen = preferenceStore.getEnum("secure_screen_v2", SecureScreenMode.INCOGNITO)

    val hideNotificationContent = preferenceStore.getBoolean("hide_notification_content", false)

    // SY -->
    val authenticatorTimeRanges = this.preferenceStore.getStringSet("biometric_time_ranges", mutableSetOf())

    val authenticatorDays = this.preferenceStore.getInt("biometric_days", 0x7F)

    val encryptDatabase = this.preferenceStore.getBoolean(Preference.appStateKey("encrypt_database"), false)

    val sqlPassword = this.preferenceStore.getString(Preference.appStateKey("sql_password"), "")

    val passwordProtectDownloads = preferenceStore.getBoolean(
        Preference.privateKey("password_protect_downloads"),
        false,
    )

    val encryptionType = this.preferenceStore.getEnum("encryption_type", EncryptionType.AES_256)

    val cbzPassword = this.preferenceStore.getString(Preference.appStateKey("cbz_password"), "")
    // SY <--

    /**
     * For app lock. Will be set when there is a pending timed lock.
     * Otherwise this pref should be deleted.
     */
    val lastAppClosed = preferenceStore.getLong(
        Preference.appStateKey("last_app_closed"),
        0,
    )

    enum class SecureScreenMode(val titleRes: StringResource) {
        ALWAYS(MR.strings.lock_always),
        INCOGNITO(MR.strings.pref_incognito_mode),
        NEVER(MR.strings.lock_never),
    }

    // SY -->
    enum class EncryptionType(val titleRes: StringResource) {
        AES_256(SYMR.strings.aes_256),
        AES_128(SYMR.strings.aes_128),
        ZIP_STANDARD(SYMR.strings.standard_zip_encryption),
    }
    // SY <--
}
