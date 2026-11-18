package mihon.domain.extension.interactor

import dev.zacsweers.metro.Inject
import mihon.domain.extension.model.ExtensionStoresRefresh
import mihon.domain.extension.repository.ExtensionStoreRepository

@Inject
class UpdateExtensionStores(
    private val repository: ExtensionStoreRepository,
) {
    // KMK -->
    suspend operator fun invoke(): ExtensionStoresRefresh {
        return repository.refreshAll()
    }
    // KMK <--
}
