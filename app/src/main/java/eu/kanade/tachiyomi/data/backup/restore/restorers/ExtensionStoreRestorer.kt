package eu.kanade.tachiyomi.data.backup.restore.restorers

import dev.zacsweers.metro.Inject
import eu.kanade.tachiyomi.data.backup.models.BackupExtensionStore
import mihon.domain.extension.model.ExtensionStore
import mihon.domain.extension.repository.ExtensionStoreRepository

@Inject
class ExtensionStoreRestorer(
    private val extensionStoreRepository: ExtensionStoreRepository,
) {

    suspend operator fun invoke(
        backupStore: BackupExtensionStore,
    ) {
        // KMK -->
        val indexUrl = if (backupStore.isLegacy == null) {
            backupStore.indexUrl.removeSuffix("/index.min.json").removeSuffix("/index.json")
                .removeSuffix("/repo.json") + "/repo.json"
        } else {
            backupStore.indexUrl
        }
        // KMK <--
        // KUN -->
        val incomingStore = ExtensionStore(
            indexUrl = indexUrl,
            name = backupStore.name,
            badgeLabel = backupStore.badgeLabel ?: backupStore.name,
            signingKey = backupStore.signingKey,
            contact = ExtensionStore.Contact(
                website = backupStore.contactWebsite,
                discord = backupStore.contactDiscord,
            ),
            isLegacy = backupStore.isLegacy ?: true,
            extensionListUrl = backupStore.extensionListUrl,
        )
        val existingStore = extensionStoreRepository.getAll().firstOrNull { it.indexUrl == indexUrl }
        extensionStoreRepository.upsert(
            if (existingStore == null) incomingStore else mergeExistingStore(existingStore, incomingStore),
        )
        // KUN <--
    }

    // KUN -->
    /**
     * Merges a store from a backup into the store already installed locally.
     *
     * Older payloads can carry `signingKey = ""`, `extensionListUrl = null` and the `isLegacy`
     * default as empty placeholders. Those placeholders must not erase newer local metadata, so
     * blank/null incoming values keep the local value. A local non-legacy store is never
     * downgraded to legacy: `isLegacy` only ends up true when both sides say so.
     */
    private fun mergeExistingStore(existing: ExtensionStore, incoming: ExtensionStore): ExtensionStore {
        return incoming.copy(
            signingKey = incoming.signingKey.ifBlank { existing.signingKey },
            extensionListUrl = incoming.extensionListUrl ?: existing.extensionListUrl,
            isLegacy = existing.isLegacy && incoming.isLegacy,
        )
    }
    // KUN <--
}
