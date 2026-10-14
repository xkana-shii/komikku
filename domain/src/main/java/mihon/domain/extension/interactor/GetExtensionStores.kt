package mihon.domain.extension.interactor

import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import mihon.domain.extension.model.ExtensionStore
import mihon.domain.extension.repository.ExtensionStoreRepository

@Inject
class GetExtensionStores(
    private val repository: ExtensionStoreRepository,
) {
    // KUN -->
    // Read through the repository's shared flow so stores are queried once at startup (Mihon 8066b8dfc9)
    suspend fun get(): List<ExtensionStore> = repository.getAllAsFlow().first()
    // KUN <--

    fun subscribe(): Flow<List<ExtensionStore>> = repository.getAllAsFlow()
}
