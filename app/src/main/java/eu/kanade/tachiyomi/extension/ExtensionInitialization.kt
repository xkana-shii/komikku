
package eu.kanade.tachiyomi.extension

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

// KMK -->
/**
 * Waits until the extension registry has completed initial
 * loading before emitting its first snapshot.
 *
 * Changes after initialization continue to emit normally.
 */
internal fun <T : Any> StateFlow<Map<String, T>>.afterInitialization(
    initialized: StateFlow<Boolean>,
): Flow<List<T>> = flow {
    initialized.first { it }

    emitAll(
        this@afterInitialization.map { extensions ->
            extensions.values.toList()
        },
    )
}
// KMK <--
