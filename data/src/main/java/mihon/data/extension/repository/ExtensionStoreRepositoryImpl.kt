package mihon.data.extension.repository

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.supervisorScope
import logcat.LogPriority
import mihon.core.metro.AppCoroutineScope
import mihon.data.extension.service.ExtensionStoreService
import mihon.domain.extension.model.ChangedExtensionStore
import mihon.domain.extension.model.ExtensionStore
import mihon.domain.extension.model.ExtensionStoreChangedException
import mihon.domain.extension.model.ExtensionStoresRefresh
import mihon.domain.extension.model.FetchedExtensions
import mihon.domain.extension.model.NO_SIGNING_KEY
import mihon.domain.extension.model.canBeRefreshedTo
import mihon.domain.extension.repository.ExtensionStoreRepository
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.Database
import tachiyomi.data.subscribeToList
import tachiyomi.data.subscribeToOne
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class ExtensionStoreRepositoryImpl(
    @AppCoroutineScope scope: CoroutineScope,
    private val service: ExtensionStoreService,
    private val database: Database,
) : ExtensionStoreRepository {

    private val stores = database.extension_storeQueries.getAll(::extensionStoreMapper)
        .subscribeToList()
        .catch {
            logcat(LogPriority.ERROR, it) { "Failed to query extension stores" }
            emit(emptyList())
        }
        .shareIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(
                stopTimeout = 5.seconds,
                replayExpiration = Duration.ZERO,
            ),
            replay = 1,
        )

    override suspend fun insert(indexUrl: String): Result<Unit> {
        return service.fetch(indexUrl).mapCatching { upsert(it) }
    }

    override suspend fun insertFromPreference(indexUrl: String, name: String) {
        database.extension_storeQueries.upsert(
            indexUrl = indexUrl,
            name = name,
            badgeLabel = name,
            // KMK -->
            signingKey = NO_SIGNING_KEY,
            // KMK <--
            contactWebsite = indexUrl,
            contactDiscord = null,
            isLegacy = false,
            extensionListUrl = null,
        )
    }

    // KMK -->
    override suspend fun refreshAll(): ExtensionStoresRefresh {
        val renamed = mutableMapOf<String, String>()
        val changed = mutableListOf<ChangedExtensionStore>()
        // KMK <--
        try {
            // KMK -->
            val savedStores = stores.first().associateBy { it.indexUrl }.toMutableMap()
            val refreshed = savedStores.values.toList().mapNotNull { store ->
                // KMK <--
                service.fetch(store.indexUrl)
                    .onFailure {
                        logcat(LogPriority.ERROR, it) {
                            "Failed to refresh extension store '${store.name} (${store.indexUrl})'"
                        }
                    }
                    // KMK -->
                    .getOrNull()
                    ?.let { fetched ->
                        // A refresh must not turn a saved store into another store (e.g. a repo that now
                        // points to another store's index), or that store's extensions would be listed
                        // under this store's signing key and name.
                        if (!store.canBeRefreshedTo(fetched, savedStores.values)) {
                            logcat(LogPriority.WARN) {
                                "Not refreshing extension store '${store.name} (${store.indexUrl})': it now serves " +
                                    "'${fetched.name} (${fetched.indexUrl})' signed by ${fetched.signingKey} " +
                                    "instead of ${store.signingKey}"
                            }
                            changed += ChangedExtensionStore(store, servedName = fetched.name)
                            return@let null
                        }
                        savedStores.remove(store.indexUrl)
                        savedStores[fetched.indexUrl] = fetched
                        store.indexUrl to fetched
                    }
                // KMK <--
            }
            database.transaction {
                refreshed.forEach { (indexUrl, store) ->
                    if (database.extension_storeQueries.contains(indexUrl).awaitAsOne()) {
                        upsert(store)
                        if (indexUrl != store.indexUrl) {
                            // KUN -->
                            // Recorded only here, where the store is actually applied: a store the user
                            // deleted mid-refresh must not leak its new URL into the disabled-store
                            // remap, or re-adding it later would come back silently disabled.
                            renamed[indexUrl] = store.indexUrl
                            // KUN <--
                            database.extension_storeQueries.delete(indexUrl)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
        // KMK -->
        return ExtensionStoresRefresh(renamed = renamed, changed = changed)
        // KMK <--
    }

    override suspend fun upsert(store: ExtensionStore) {
        database.extension_storeQueries.upsert(
            indexUrl = store.indexUrl,
            name = store.name,
            badgeLabel = store.badgeLabel,
            signingKey = store.signingKey,
            contactWebsite = store.contact.website,
            contactDiscord = store.contact.discord,
            isLegacy = store.isLegacy,
            extensionListUrl = store.extensionListUrl,
        )
    }

    override suspend fun fetchExtensions(
        // KMK -->
        disabledRepos: Set<String>,
    ): FetchedExtensions {
        // KMK <--
        return try {
            supervisorScope {
                // KMK -->
                val results = database.extension_storeQueries
                    .getAll(::extensionStoreMapper)
                    .awaitAsList()
                    .filterNot { it.indexUrl in disabledRepos }
                    // KMK <--
                    .map { store ->
                        async {
                            service.getExtensions(store).onFailure {
                                this@ExtensionStoreRepositoryImpl.logcat(LogPriority.ERROR, it) {
                                    "Failed to fetch extensions for store '${store.name} (${store.indexUrl})'"
                                }
                            }
                        }
                    }
                    .awaitAll()
                // KMK -->
                FetchedExtensions(
                    extensions = results.flatMap { it.getOrDefault(emptyList()) },
                    changedStores = results.mapNotNull {
                        (it.exceptionOrNull() as? ExtensionStoreChangedException)?.changed
                    },
                )
                // KMK <--
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            // KMK -->
            FetchedExtensions()
            // KMK <--
        }
    }

    override suspend fun getAll(): List<ExtensionStore> {
        return database.extension_storeQueries
            .getAll(::extensionStoreMapper)
            .awaitAsList()
    }

    override fun getAllAsFlow(): Flow<List<ExtensionStore>> {
        return stores
    }

    override fun getCountAsFlow(): Flow<Long> {
        return database.extension_storeQueries
            .getCount()
            .subscribeToOne()
    }

    override suspend fun remove(indexUrl: String) {
        database.extension_storeQueries.delete(indexUrl)
    }

    private fun extensionStoreMapper(
        indexUrl: String,
        name: String,
        badgeLabel: String,
        signingKey: String,
        contactWebsite: String,
        contactDiscord: String?,
        isLegacy: Boolean,
        extensionListUrl: String?,
    ): ExtensionStore = ExtensionStore(
        indexUrl = indexUrl,
        name = name,
        badgeLabel = badgeLabel,
        signingKey = signingKey,
        contact = ExtensionStore.Contact(
            website = contactWebsite,
            discord = contactDiscord,
        ),
        isLegacy = isLegacy,
        extensionListUrl = extensionListUrl,
    )
}
