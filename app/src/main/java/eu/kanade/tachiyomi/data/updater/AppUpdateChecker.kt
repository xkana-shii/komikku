package eu.kanade.tachiyomi.data.updater

import android.content.Context
import android.os.Build
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.util.system.isFossBuildType
import eu.kanade.tachiyomi.util.system.isNightlyBuildType
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

// KUN -->
// The fork has no separate stable/preview repositories: every build (preview
// "r<count>" and tagged "v<semver>") is published as a prerelease under
// komi-kun/komi-kun, so both build types read releases from there.
fun getGithubRepo(peekIntoPreview: Boolean = false): String = "komi-kun/komi-kun"
// KUN <--

val RELEASE_TAG: String by lazy { getReleaseTag() }

fun getReleaseTag(peekIntoPreview: Boolean = false): String =
    if (isNightlyBuildType || peekIntoPreview) {
        "r${BuildConfig.COMMIT_COUNT}"
    } else {
        "v${BuildConfig.VERSION_NAME}"
    }

val RELEASE_URL = "https://github.com/$GITHUB_REPO/releases/tag/$RELEASE_TAG"
