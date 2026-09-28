package eu.kanade.domain.source.model

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.model.Extension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import tachiyomi.domain.source.model.Source
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

val Source.icon: ImageBitmap?
    @Composable get() {
        val manager = remember { Injekt.get<ExtensionManager>() }
        val icons = remember(id, manager) {
            manager.getAppIconForSourceAsFlow(id)
                .map { it?.toBitmap()?.asImageBitmap() }
                .flowOn(Dispatchers.IO)
        }
        return icons.collectAsState(initial = null).value
    }

// AM (BROWSE) -->
// Add an extra property to Source for it to get access to ExtensionManager
val Source.installedExtension: Extension.Installed?
    get() {
        return Injekt.get<ExtensionManager>()
            .installedExtensionsFlow
            .value
            .find { ext -> ext.sources.any { it.id == id } }
    }
// <-- AM (BROWSE)
