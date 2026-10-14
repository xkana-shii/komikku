package eu.kanade.tachiyomi.util

import android.content.Context
import android.os.Build
import dev.zacsweers.metro.Inject
import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.util.storage.getUriCompat
import eu.kanade.tachiyomi.util.system.WebViewUtil
import eu.kanade.tachiyomi.util.system.createFileInCacheDir
import eu.kanade.tachiyomi.util.system.toShareIntent
import eu.kanade.tachiyomi.util.system.toast
import exh.log.EHLogLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

@Inject
class CrashLogUtil(
    private val context: Context,
    private val extensionManager: ExtensionManager,
    private val preferences: BasePreferences,
) {

    suspend fun dumpLogs(exception: Throwable? = null) = withContext(Dispatchers.IO) {
        try {
            val file = context.createFileInCacheDir("komikku_crash_logs.txt")

            file.appendText(getDebugInfo() + "\n\n")
            getExtensionsInfo()?.let { file.appendText("$it\n\n") }
            exception?.let { file.appendText("$it\n\n") }

            val logPriority = if (EHLogLevel.isExtraLogging()) "V" else "E"
            Runtime.getRuntime().exec("logcat *:$logPriority -d -v year -v zone -f ${file.absolutePath}").waitFor()

            val uri = file.getUriCompat(context)
            withContext(Dispatchers.Main) { context.startActivity(uri.toShareIntent(context, "text/plain")) }
        } catch (_: Throwable) {
            withContext(Dispatchers.Main) { context.toast("Failed to get logs") }
        }
    }

    fun getDebugInfo(): String {
        val now = Clock.System.now()
        val tz = TimeZone.currentSystemDefault()
        return """
            App ID: ${BuildConfig.APPLICATION_ID}
            App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.COMMIT_SHA}, ${BuildConfig.VERSION_CODE}, ${BuildConfig.BUILD_TIME})
            Build version: ${BuildConfig.COMMIT_COUNT}
            Installation ID: ${preferences.installationId.get()}
            Skipped app update: ${preferences.skippedAppUpdate.get().ifEmpty { "None" }}
            Android version: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}; build ${Build.DISPLAY})
            Device brand: ${Build.BRAND}
            Device manufacturer: ${Build.MANUFACTURER}
            Device name: ${Build.DEVICE} (${Build.PRODUCT})
            Device model: ${Build.MODEL}
            WebView: ${WebViewUtil.getVersion(context)}
            Current time: ${now.toLocalDateTime(tz)}${tz.offsetAt(now)}
        """.trimIndent()
    }

    private suspend fun getExtensionsInfo(): String? {
        val availableExtensions = extensionManager.availableExtensionsFlow.value.associateBy { it.pkgName }

        // KUN -->
        // Extensions may never finish loading (a plausible crash antecedent, e.g. a locked DB), and the
        // dump must not suspend forever waiting for that: bound the waits and fall back to the snapshot.
        val loadedExtensions = withTimeoutOrNull(EXTENSIONS_INFO_TIMEOUT_MS) {
            extensionManager.getLoadedExtensions()
        } ?: extensionManager.loadedExtensions
        val notLoadedExtensions = withTimeoutOrNull(EXTENSIONS_INFO_TIMEOUT_MS) {
            extensionManager.getNotLoadedExtensions()
        }.orEmpty()
        // KUN <--

        val outdatedInfoList = loadedExtensions
            .sortedBy { it.name }
            .mapNotNull {
                val availableExtension = availableExtensions[it.pkgName]
                val hasUpdate = (availableExtension?.versionCode ?: 0) > it.versionCode

                if (!hasUpdate && !it.isObsolete) return@mapNotNull null

                """
                    - ${it.name}
                      Installed: ${it.versionName} / Available: ${availableExtension?.versionName ?: "?"}
                      Orphaned: ${it.isObsolete}
                """.trimIndent()
            }

        val notLoadedInfoList = notLoadedExtensions
            .sortedBy { it.name }
            .map { extension ->
                buildString {
                    appendLine("- ${extension.name}")
                    appendLine("  Installed: ${extension.versionName} (lib ${extension.libVersion ?: "?"})")
                    append("  Not loaded: ${extension.reason.description}")

                    val reason = extension.reason
                    if (reason is Extension.NotLoaded.Reason.Failed) {
                        appendLine()
                        append(reason.stackTrace.trimEnd().prependIndent("  "))
                    }
                }
            }

        val extensionInfoList = outdatedInfoList + notLoadedInfoList

        return if (extensionInfoList.isNotEmpty()) {
            (listOf("Problematic extensions:") + extensionInfoList)
                .joinToString("\n")
        } else {
            null
        }
    }
}

// KUN -->
/** Cap on how long the crash-log dump waits for the extension manager to finish loading. */
private const val EXTENSIONS_INFO_TIMEOUT_MS = 5_000L
// KUN <--

private val Extension.NotLoaded.Reason.description: String
    get() = when (this) {
        is Extension.NotLoaded.Reason.Untrusted -> "Untrusted"
        Extension.NotLoaded.Reason.Filtered -> "Filtered by content warning"
        Extension.NotLoaded.Reason.Unsigned -> "Unsigned"
        Extension.NotLoaded.Reason.UnsupportedLibVersion -> "Unsupported lib version"
        Extension.NotLoaded.Reason.Malformed -> "Malformed"
        is Extension.NotLoaded.Reason.Failed -> "Failed ($message)"
    }
