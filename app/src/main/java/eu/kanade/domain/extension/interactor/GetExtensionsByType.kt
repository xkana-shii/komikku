package eu.kanade.domain.extension.interactor

import dev.zacsweers.metro.Inject
import eu.kanade.domain.extension.model.Extensions
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.model.Extension
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

@Inject
class GetExtensionsByType(
    private val preferences: SourcePreferences,
    private val extensionManager: ExtensionManager,
) {

    fun subscribe(): Flow<Extensions> {
        // KUN --> Upstream reads the content-warning set once via .get() here, so the available list
        // is not re-filtered when the preference changes while subscribed. Observe it so every
        // emission re-filters with the current set.
        val enabledContentWarningsFlow = preferences.enabledContentWarnings.changes()
        // KUN <--

        return combine(
            preferences.enabledLanguages.changes(),
            extensionManager.loadedExtensionsFlow,
            extensionManager.notLoadedExtensionsFlow,
            extensionManager.availableExtensionsFlow,
            // KUN -->
            enabledContentWarningsFlow,
            // KUN <--
        ) { enabledLanguages, _loaded, _notLoaded, _available, enabledContentWarnings ->
            val byName = compareBy<Extension, String>(String.CASE_INSENSITIVE_ORDER) { it.name }

            val (loadedUpdates, loaded) = _loaded.partition { it.hasUpdate }
            val (notLoadedUpdates, notLoaded) = _notLoaded.partition { it.hasUpdate }

            val updates = (loadedUpdates + notLoadedUpdates).sortedWith(byName)

            val installedSignatures = (_loaded + _notLoaded).groupBy({ it.pkgName }, { it.signatures })
            val available = _available
                .filter { extension ->
                    installedSignatures[extension.pkgName].orEmpty().none { extension.store.signingKey in it } &&
                        extension.contentWarning in enabledContentWarnings
                }
                .flatMap { ext ->
                    ext.sources.filter { it.lang in enabledLanguages }
                        .map {
                            ext.copy(
                                name = it.name,
                                lang = it.lang,
                                pkgName = "${ext.pkgName}-${it.id}",
                                sources = listOf(it),
                            )
                        }
                }
                .sortedWith(
                    compareBy<Extension.Available, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
                        .thenBy { it.store.signingKey },
                )

            Extensions(
                updates = updates,
                loaded = loaded.sortedWith(
                    compareBy<Extension.Loaded> {
                        !it.isObsolete /* SY --> */ && !it.isRedundant /* SY <-- */
                    }.then(byName),
                ),
                available = available,
                notLoaded = notLoaded.sortedWith(byName),
            )
        }
    }
}
