package eu.kanade.tachiyomi.ui.browse.feed

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.FilterList
import exh.source.getMainSource
import kotlinx.serialization.json.JsonElement
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn

// KMK --> KeiSource is bundled with extensions, not linked into the host app. Its synchronous
// getFilterList starts detached background work and may return only a partial filter schema.
// Invoke its existing suspend metadata contract before building a saved-search request.
internal suspend fun Source.readyFeedFilters(): FilterList {
    val actual = getMainSource()
    val hierarchy = generateSequence(actual.javaClass as Class<*>?) { it.superclass }.toList()
    val kei = hierarchy.firstOrNull { it.name == "keiyoushi.source.KeiSource" } ?: return getFilterList()
    val supports = kei.getDeclaredMethod("getSupportsFilterFetching").apply { isAccessible = true }
    if (supports.call(actual) != true) return getFilterList()
    val fetch = kei.getDeclaredMethod("fetchFilterData", Continuation::class.java).apply { isAccessible = true }
    val build = kei.getDeclaredMethod("getFilterList", JsonElement::class.java).apply { isAccessible = true }
    val data = suspendCoroutineUninterceptedOrReturn<Any?> { continuation -> fetch.call(actual, continuation) }
    return build.call(actual, data) as FilterList
}

private fun Method.call(receiver: Any, vararg arguments: Any?): Any? = try {
    invoke(receiver, *arguments)
} catch (e: InvocationTargetException) {
    throw e.targetException
}
// KMK <--
