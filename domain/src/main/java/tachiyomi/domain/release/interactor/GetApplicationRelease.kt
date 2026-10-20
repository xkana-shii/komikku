package tachiyomi.domain.release.interactor

import dev.zacsweers.metro.Inject
import tachiyomi.domain.release.model.Release
import tachiyomi.domain.release.service.ReleaseService

@Inject
class GetApplicationRelease(
    private val service: ReleaseService,
) {
    suspend fun await(arguments: Arguments): Result {
        // KMK -->
        val releases = service.releaseNotes(arguments)
            .filter {
                // KUN -->
                // Bleeding-edge fork: prereleases are the releases users get, so do
                // not filter them out; only keep the tag family of this build.
                isSameReleaseFamily(arguments.isNightly, it.version) &&
                    // KUN <--
                    isNewVersion(
                        arguments.isNightly,
                        arguments.commitCount,
                        arguments.versionName,
                        it.version,
                    )
            }

        val latest = releases.getLatest() ?: return Result.NoNewUpdate
        // KMK <--

        // Check if latest version is different from current version
        val isNewVersion = isNewVersion(
            isNightly = arguments.isNightly,
            commitCount = arguments.commitCount,
            versionName = arguments.versionName,
            versionTag = latest.version,
        )
        return when {
            isNewVersion -> Result.NewUpdate(latest)
            else -> Result.NoNewUpdate
        }
    }

    // KMK -->
    suspend fun awaitReleaseNotes(arguments: Arguments): Result {
        val releases = service.releaseNotes(arguments)
            // KUN -->
            .filter { isSameReleaseFamily(arguments.isNightly, it.version) }
        // KUN <--

        val latest = releases.getLatest() ?: return Result.NoNewUpdate
        return Result.NewUpdate(latest)
    }

    // KUN -->
    /**
     * Bleeding-edge fork: there is a single repository, and nightly (`r<count>`) and
     * tagged (`v<semver>`) builds publish prereleases side by side, so only tags that
     * belong to the current build's family are eligible. This keeps a release build
     * from offering a nightly APK (a different application id) and vice versa.
     */
    private fun isSameReleaseFamily(isNightly: Boolean, versionTag: String): Boolean =
        if (isNightly) versionTag.startsWith("r") else versionTag.startsWith("v")
    // KUN <--
    // KMK <--

    /**
     * [isNightly] is if current version is Nightly build
     *
     * [versionTag] is the version of new release
     *
     * Release (stable) version will compare with current's [versionName] ("v0.1.2")
     *
     * Nightly version will compare with current's [commitCount] ("r1234")
     */
    private fun isNewVersion(
        isNightly: Boolean,
        commitCount: Int,
        versionName: String,
        versionTag: String,
    ): Boolean {
        // Removes prefixes like "r" or "v"
        val newVersion = versionTag.replace("[^\\d.]".toRegex(), "")
        return if (isNightly) {
            // KNS -->
            // Nightly builds: based on releases in "xkana-shii/komikku-preview" repo
            // KNS <--
            // tagged as something like "r1234"
            newVersion.toInt() > commitCount
        } else {
            // KNS -->
            // Release builds: based on releases in "xkana-shii/komikku" repo
            // KNS <--
            // tagged as something like "v0.1.2"
            val oldVersion = versionName.replace("[^\\d.]".toRegex(), "")

            val newSemVer = newVersion.split(".").map { it.toInt() }
            val oldSemVer = oldVersion.split(".").map { it.toInt() }

            oldSemVer.mapIndexed { index, i ->
                if (newSemVer[index] > i) {
                    return true
                }
                if (newSemVer[index] < i) return false
            }

            false
        }
    }

    data class Arguments(
        val isFoss: Boolean,
        /** If current version is Nightly build */
        val isNightly: Boolean,
        /** Commit count of current version */
        val commitCount: Int,
        /** Current version name, could be version tag (v0.1.2) or commit count (r1234) */
        val versionName: String,
        /** Repository name */
        val repository: String,
        /** Force check for new update */
        val forceCheck: Boolean = false,
    )

    sealed interface Result {
        data class NewUpdate(val release: Release) : Result
        data object NoNewUpdate : Result
        data object OsTooOld : Result
    }
}

// KMK -->
internal fun List<Release>.getLatest(): Release? {
    return firstOrNull()
        ?.copy(
            info = joinToString("\r-----\r") {
                "## ${it.version}\r\r" +
                    it.info
            },
        )
}
// KMK <--
