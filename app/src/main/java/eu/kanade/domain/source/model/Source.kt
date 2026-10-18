package eu.kanade.domain.source.model

import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.model.Extension
import tachiyomi.domain.source.model.Source
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// KUN -->
// Mihon b15af71b94 moved installed-extension/source icon loading to Coil
// (`eu.kanade.tachiyomi.data.coil.InstalledSourceIcon`), so the old `Source.icon()` helper is gone.
// Komikku keeps this module for its own `installedExtension` accessor.
// KUN <--

// AM (BROWSE) -->
// Add an extra property to Source for it to get access to ExtensionManager
val Source.installedExtension: Extension.Loaded?
    get() {
        return Injekt.get<ExtensionManager>()
            .loadedExtensions
            .find { ext -> ext.sources.any { it.id == id } }
    }
// <-- AM (BROWSE)
