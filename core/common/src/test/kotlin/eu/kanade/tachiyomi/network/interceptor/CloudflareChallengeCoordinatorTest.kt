package eu.kanade.tachiyomi.network.interceptor

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

// KNS -->
class CloudflareChallengeCoordinatorTest {

    @Test
    fun `prompts only while a host is resumed and never goes negative`() {
        CloudflareChallengeCoordinator.onHostPaused()
        CloudflareChallengeCoordinator.canPrompt shouldBe false
        CloudflareChallengeCoordinator.onHostResumed()
        CloudflareChallengeCoordinator.canPrompt shouldBe true
        CloudflareChallengeCoordinator.onHostPaused()
        CloudflareChallengeCoordinator.canPrompt shouldBe false
    }
}
// KNS <--
