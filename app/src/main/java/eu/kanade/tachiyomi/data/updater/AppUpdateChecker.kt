package eu.kanade.tachiyomi.data.updater

import android.content.Context
import android.os.Build
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.util.system.isFossBuildType
import eu.kanade.tachiyomi.util.system.isNightlyBuildType
import eu.kanade.tachiyomi.util.system.updaterEnabled
import exh.source.ExhPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tachiyomi.domain.release.interactor.GetApplicationRelease
import tachiyomi.domain.release.service.AppUpdatePolicy

@Inject
@SingleIn(AppScope::class)
class AppUpdateChecker(
    private val getApplicationRelease: GetApplicationRelease,
    // KMK -->
    private val exhPreferences: ExhPreferences,
    // KMK <--
) {

    suspend fun checkForUpdate(
        context: Context,
        forceCheck: Boolean = false,
        // KMK -->
        peekIntoPreview: Boolean = false,
        autoUpdate: Boolean = AppUpdatePolicy.DISABLE_AUTO_DOWNLOAD !in exhPreferences.appShouldAutoUpdate.get(),
        // KMK <--
    ): GetApplicationRelease.Result {
        // KUN -->
        if (!updaterEnabled) return GetApplicationRelease.Result.NoNewUpdate
        // KUN <--
        return withContext(Dispatchers.IO) {
            val result = getApplicationRelease.await(
                GetApplicationRelease.Arguments(
                    isFoss = isFossBuildType,
                    isNightly = isNightlyBuildType || peekIntoPreview,
                    commitCount = BuildConfig.COMMIT_COUNT.toInt(),
                    versionName = BuildConfig.VERSION_NAME,
                    repository = getGithubRepo(peekIntoPreview),
                    forceCheck = forceCheck,
                ),
            )

            // KMK -->
            if (!peekIntoPreview && result is GetApplicationRelease.Result.NewUpdate) {
                AppUpdateNotifier.releasePageUrl = result.release.releaseLink
                AppUpdateNotifier(context).promptUpdate(result.release)

                if (autoUpdate) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        AppUpdateDownloadJob.start(
                            context = context,
                            url = result.release.downloadLink,
                            title = result.release.version,
                            scheduled = true,
                        )
                    }
                }
            }
            // KMK <--

            result
        }
    }

    // KMK -->
    suspend fun getReleaseNotes(peekIntoPreview: Boolean = false): GetApplicationRelease.Result {
        return withContext(Dispatchers.IO) {
            getApplicationRelease.awaitReleaseNotes(
                GetApplicationRelease.Arguments(
                    isFoss = isFossBuildType,
                    isNightly = isNightlyBuildType || peekIntoPreview,
                    commitCount = BuildConfig.COMMIT_COUNT.toInt(),
                    versionName = BuildConfig.VERSION_NAME,
                    repository = getGithubRepo(peekIntoPreview),
                ),
            )
        }
    }
    // KMK <--
}

val GITHUB_REPO: String by lazy { getGithubRepo() }

// KNS -->
// xkana-shii/komikku publishes stable "v<semver>" releases in its own repository and nightly
// ("preview") "r<count>" builds in xkana-shii/komikku-preview.
fun getGithubRepo(peekIntoPreview: Boolean = false): String =
    if (isNightlyBuildType || peekIntoPreview) {
        "xkana-shii/komikku-preview"
    } else {
        "xkana-shii/komikku"
    }
// KNS <--

val RELEASE_TAG: String by lazy { getReleaseTag() }

fun getReleaseTag(peekIntoPreview: Boolean = false): String =
    if (isNightlyBuildType || peekIntoPreview) {
        "r${BuildConfig.COMMIT_COUNT}"
    } else {
        "v${BuildConfig.VERSION_NAME}"
    }

val RELEASE_URL = "https://github.com/$GITHUB_REPO/releases/tag/$RELEASE_TAG"
