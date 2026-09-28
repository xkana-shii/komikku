package eu.kanade.tachiyomi.ui.library

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import tachiyomi.domain.source.service.SourceManager

// KMK --> Rebuild library source badges/languages when startup stubs become installed sources.
internal fun <T> Flow<T>.withSourceUpdates(sourceManager: SourceManager): Flow<T> =
    combine(sourceManager.sources) { library, _ -> library }
// KMK <--
