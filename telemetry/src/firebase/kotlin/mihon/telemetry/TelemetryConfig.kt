package mihon.telemetry

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import exh.log.xLogE
import exh.log.xLogW

object TelemetryConfig {
    private var analytics: FirebaseAnalytics? = null
    private var crashlytics: FirebaseCrashlytics? = null

    fun init(context: Context, isNightlyBuildType: Boolean, commitCount: String) {
        // To stop forks/test builds from polluting our data
        if (!context.isMihonProductionApp()) return

        // Check if Google Play Services is available before initializing Firebase
        if (!isGooglePlayServicesAvailable(context)) {
            xLogW("Google Play Services not available, skipping Firebase initialization")
            return
        }

        try {
            analytics = FirebaseAnalytics.getInstance(context)
            analytics?.setUserProperty("preferred_abi", Build.SUPPORTED_ABIS[0])
            FirebaseApp.initializeApp(context)
            crashlytics = FirebaseCrashlytics.getInstance()
        } catch (e: Exception) {
            xLogE("Failed to initialize Firebase", e)
        }
        // KMK -->
        if (isNightlyBuildType) {
            analytics?.setUserProperty("nightly_version", commitCount)
        }
        // KMK <--
    }

    // GMS availability is detected through the package manager rather than a GMS API call,
    // which would itself try to prompt the user to install/enable Play Services.
    @Suppress("DEPRECATION")
    private fun isGooglePlayServicesAvailable(context: Context): Boolean {
        return try {
            context.packageManager
                .getPackageInfo("com.google.android.gms", PackageManager.GET_META_DATA)
                .applicationInfo
                ?.enabled == true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun setAnalyticsEnabled(enabled: Boolean) {
        analytics?.setAnalyticsCollectionEnabled(enabled)
    }

    fun setCrashlyticsEnabled(enabled: Boolean) {
        crashlytics?.isCrashlyticsCollectionEnabled = enabled
    }

    private fun Context.isMihonProductionApp(): Boolean {
        if (packageName !in MIHON_PACKAGES) return false

        return packageManager.getPackageInfo(packageName, SignatureFlags)
            .getCertificateFingerprints()
            .any { it == MIHON_CERTIFICATE_FINGERPRINT }
    }

    fun recordException(throwable: Throwable) {
        crashlytics?.recordException(throwable)
    }
}

private val MIHON_PACKAGES = hashSetOf("app.komikku", "app.komikku.beta")
private const val MIHON_CERTIFICATE_FINGERPRINT =
    "CB:EC:12:1A:A8:2E:BB:02:AA:A7:38:06:99:2E:03:68:A9:7D:47:B5:45:1E:D6:52:48:16:D0:30:84:C4:59:05"
