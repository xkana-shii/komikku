// KUN -->
package eu.kanade.domain.sync

import eu.kanade.domain.sync.models.SyncSettings
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SyncSettingsTest {

    @Test
    fun `force push requires library entries and chapters`() {
        assertTrue(SyncSettings().supportsForcePush)
        assertFalse(SyncSettings(chapters = false).supportsForcePush)
        assertFalse(SyncSettings(libraryEntries = false).supportsForcePush)
    }

    @Test
    fun `disabled manga payload sections are preserved instead of blocking force push`() {
        val settings = SyncSettings(
            categories = false,
            tracking = false,
            history = false,
            customInfo = false,
            appSettings = false,
            sourceSettings = false,
            privateSettings = false,
            extensionStores = false,
            savedSearchesFeeds = false,
        )

        assertTrue(settings.supportsForcePush)
        assertTrue(settings.forcePushNeedsSnapshot)
        assertFalse(SyncSettings().forcePushNeedsSnapshot)
    }
}
// KUN <--
