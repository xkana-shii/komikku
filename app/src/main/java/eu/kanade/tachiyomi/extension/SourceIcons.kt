package eu.kanade.tachiyomi.extension

import eu.kanade.tachiyomi.extension.model.Extension
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

// KMK --> Resolve from the authoritative extension map, independently of Browse's lazy UI list.
internal fun Map<String, Extension.Installed>.sourcePackage(sourceId: Long): String? =
    values.find { extension -> extension.sources.any { it.id == sourceId } }?.pkgName

internal fun <T> observeSourceIcon(extensionChanges: Flow<*>, loadIcon: () -> T?): Flow<T?> =
    extensionChanges.map { loadIcon() }.distinctUntilChanged()
// KMK <--
