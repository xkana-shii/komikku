// KUN -->
package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupExtensionStore
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import mihon.domain.extension.model.ExtensionStore
import mihon.domain.extension.repository.ExtensionStoreRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ExtensionStoreRestorerTest {

    private val extensionStoreRepository: ExtensionStoreRepository = mockk(relaxed = true)
    private val restorer = ExtensionStoreRestorer(extensionStoreRepository)

    private fun localStore(
        signingKey: String = "local-key",
        extensionListUrl: String? = "https://example.com/index.json",
        isLegacy: Boolean = false,
    ) = ExtensionStore(
        indexUrl = "https://example.com/repo.json",
        name = "Example",
        badgeLabel = "Example",
        signingKey = signingKey,
        contact = ExtensionStore.Contact(website = "https://example.com", discord = null),
        isLegacy = isLegacy,
        extensionListUrl = extensionListUrl,
    )

    @Test
    fun `older payload does not erase newer local metadata`() = runTest {
        coEvery { extensionStoreRepository.getAll() } returns listOf(localStore())
        val upserted = slot<ExtensionStore>()
        coEvery { extensionStoreRepository.upsert(capture(upserted)) } returns Unit

        restorer(
            BackupExtensionStore(
                indexUrl = "https://example.com/repo.json",
                name = "Example",
                signingKey = "",
                isLegacy = null,
                extensionListUrl = null,
            ),
        )

        assertEquals("local-key", upserted.captured.signingKey)
        assertEquals("https://example.com/index.json", upserted.captured.extensionListUrl)
        assertEquals(false, upserted.captured.isLegacy)
    }

    @Test
    fun `newer payload replaces local metadata`() = runTest {
        coEvery { extensionStoreRepository.getAll() } returns listOf(
            localStore(signingKey = "old-key", extensionListUrl = null, isLegacy = true),
        )
        val upserted = slot<ExtensionStore>()
        coEvery { extensionStoreRepository.upsert(capture(upserted)) } returns Unit

        restorer(
            BackupExtensionStore(
                indexUrl = "https://example.com/repo.json",
                name = "Example",
                signingKey = "new-key",
                isLegacy = false,
                extensionListUrl = "https://example.com/new-index.json",
            ),
        )

        assertEquals("new-key", upserted.captured.signingKey)
        assertEquals("https://example.com/new-index.json", upserted.captured.extensionListUrl)
        assertEquals(false, upserted.captured.isLegacy)
    }

    @Test
    fun `non-legacy local store is not downgraded by a legacy payload`() = runTest {
        coEvery { extensionStoreRepository.getAll() } returns listOf(localStore(isLegacy = false))
        val upserted = slot<ExtensionStore>()
        coEvery { extensionStoreRepository.upsert(capture(upserted)) } returns Unit

        restorer(
            BackupExtensionStore(
                indexUrl = "https://example.com/repo.json",
                name = "Example",
                signingKey = "local-key",
                isLegacy = true,
                extensionListUrl = "https://example.com/index.json",
            ),
        )

        assertEquals(false, upserted.captured.isLegacy)
    }

    @Test
    fun `new store is inserted with the payload values`() = runTest {
        coEvery { extensionStoreRepository.getAll() } returns emptyList()
        val upserted = slot<ExtensionStore>()
        coEvery { extensionStoreRepository.upsert(capture(upserted)) } returns Unit

        restorer(
            BackupExtensionStore(
                indexUrl = "https://example.com/repo.json",
                name = "Example",
                signingKey = "backup-key",
                isLegacy = false,
                extensionListUrl = "https://example.com/index.json",
            ),
        )

        assertEquals("backup-key", upserted.captured.signingKey)
        assertEquals("https://example.com/index.json", upserted.captured.extensionListUrl)
        assertEquals(false, upserted.captured.isLegacy)
    }
}
// KUN <--
