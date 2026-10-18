package mihon.telemetry

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics

object TelemetryConfig {
    private var analytics: FirebaseAnalytics? = null
    private var crashlytics: FirebaseCrashlytics? = null

    fun init(context: Context, isPreviewBuildType: Boolean, commitCount: String) {
        // KMK --> Never initialize telemetry for an app identity outside the explicit production allow-list.
        if (!context.isKomikkuProductionApp()) return
        // KMK <--
        analytics = FirebaseAnalytics.getInstance(context)
        FirebaseApp.initializeApp(context)
        crashlytics = FirebaseCrashlytics.getInstance()
        if (isPreviewBuildType) analytics?.setUserProperty("preview_version", commitCount)
    }

    fun setAnalyticsEnabled(enabled: Boolean) {
        analytics?.setAnalyticsCollectionEnabled(enabled)
    }

    fun setCrashlyticsEnabled(enabled: Boolean) {
        crashlytics?.isCrashlyticsCollectionEnabled = enabled
    }

    fun recordException(throwable: Throwable) {
        crashlytics?.recordException(throwable)
    }

    private fun Context.isKomikkuProductionApp(): Boolean = packageName == "app.komikku.kns"
}
