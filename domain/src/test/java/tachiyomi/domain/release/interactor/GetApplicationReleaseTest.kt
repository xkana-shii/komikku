package tachiyomi.domain.release.interactor

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.release.model.Release
import tachiyomi.domain.release.service.ReleaseService

class GetApplicationReleaseTest {

    private lateinit var getApplicationRelease: GetApplicationRelease
    private lateinit var releaseService: ReleaseService

    @BeforeEach
    fun beforeEach() {
        releaseService = mockk()

        getApplicationRelease = GetApplicationRelease(releaseService)
    }

    @Test
    fun `When has update but is nightly expect new update`() = runTest {
        val releases = listOf(
            Release(
                "r2000",
                "info",
                "http://example.com/release_link",
                "http://example.com/release_link.apk",
            ),
        )

        coEvery { releaseService.releaseNotes(any()) } returns releases

        val result = getApplicationRelease.await(
            GetApplicationRelease.Arguments(
                isFoss = false,
                isNightly = true,
                commitCount = 1000,
                versionName = "",
                repository = "test",
            ),
        )

        // KMK: Don't cast, will throw exception if the result is different from expected
        result shouldBe GetApplicationRelease.Result.NewUpdate(releases.getLatest()!!)
    }

    @Test
    fun `When has update expect new update`() = runTest {
        val releases =
            listOf(
                Release(
                    "v2.0.0",
                    "info",
                    "http://example.com/release_link",
                    "http://example.com/release_link.apk",
                ),
            )

        coEvery { releaseService.releaseNotes(any()) } returns releases

        val result = getApplicationRelease.await(
            GetApplicationRelease.Arguments(
                isFoss = false,
                isNightly = false,
                commitCount = 0,
                versionName = "v1.0.0",
                repository = "test",
            ),
        )

        // KMK: Don't cast, will throw exception if the result is different from expected
        result shouldBe GetApplicationRelease.Result.NewUpdate(releases.getLatest()!!)
    }

    @Test
    fun `When update is a prerelease expect new update`() = runTest {
        val releases = listOf(
            Release(
                "v2.0.0",
                "info",
                "http://example.com/release_link",
                "http://example.com/release_link.apk",
                preRelease = true,
            ),
        )

        coEvery { releaseService.releaseNotes(any()) } returns releases

        val result = getApplicationRelease.await(
            GetApplicationRelease.Arguments(
                isFoss = false,
                isNightly = false,
                commitCount = 0,
                versionName = "v1.0.0",
                repository = "test",
            ),
        )

        // KMK: bleeding-edge fork offers prereleases, since there is no stable channel
        result shouldBe GetApplicationRelease.Result.NewUpdate(releases.getLatest()!!)
    }

    @Test
    fun `When other tag family has update expect no new update`() = runTest {
        val releases = listOf(
            Release(
                "v2.0.0",
                "info",
                "http://example.com/release_link",
                "http://example.com/release_link.apk",
            ),
        )

        coEvery { releaseService.releaseNotes(any()) } returns releases

        // A nightly build must ignore "v*" releases (they ship a different apk)
        val result = getApplicationRelease.await(
            GetApplicationRelease.Arguments(
                isFoss = false,
                isNightly = true,
                commitCount = 1000,
                versionName = "",
                repository = "test",
            ),
        )

        result shouldBe GetApplicationRelease.Result.NoNewUpdate
    }

    @Test
    fun `When has no update expect no new update`() = runTest {
        val releases = listOf(
            Release(
                "v1.0.0",
                "info",
                "http://example.com/release_link",
                "http://example.com/release_link.apk",
            ),
        )

        coEvery { releaseService.releaseNotes(any()) } returns releases

        val result = getApplicationRelease.await(
            GetApplicationRelease.Arguments(
                isFoss = false,
                isNightly = false,
                commitCount = 0,
                versionName = "v2.0.0",
                repository = "test",
            ),
        )

        result shouldBe GetApplicationRelease.Result.NoNewUpdate
    }
}
