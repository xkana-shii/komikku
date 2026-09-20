package eu.kanade.tachiyomi.ui.browse.feed

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import exh.log.xLogE
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import tachiyomi.core.common.util.QuerySanitizer.sanitize
import tachiyomi.domain.source.model.SavedSearch
import xyz.nulldev.ts.api.http.serializer.FilterSerializer
import java.util.WeakHashMap

// KMK --> Extensions may return cached filter subclasses and cast them in their search implementation.
// Await dynamic filter metadata, then deserialize into a separate tree for every request.
internal object FeedSearch {
    class InvalidSavedSearch(cause: Exception? = null) : IllegalArgumentException("Invalid saved search", cause)

    private val locks = WeakHashMap<Source, Mutex>()

    suspend fun <T> withSource(source: Source, block: suspend () -> T): T {
        val mutex = synchronized(locks) { locks.getOrPut(source) { Mutex() } }
        return mutex.withLock { block() }
    }

    suspend fun fetch(source: Source, search: SavedSearch): MangasPage {
        val values = try {
            search.filtersJson?.takeIf { it.isNotBlank() }?.let { Json.decodeFromString<JsonArray>(it) }
        } catch (e: Exception) {
            throw InvalidSavedSearch(e)
        }
        val filters = if (values.isNullOrEmpty()) {
            FilterList()
        } else {
            val independent = withSource(source) { FilterList(source.readyFeedFilters().map { it.copyForRequest() }) }
            try {
                restoreList(FilterSerializer(), independent, values)
            } catch (e: Exception) {
                runCatching { xLogE("Could not restore feed saved search ${search.id} for source ${search.source}", e) }
                throw InvalidSavedSearch(e)
            }
            independent
        }
        return source.getSearchManga(1, search.query?.sanitize().orEmpty(), filters)
    }

    fun uiFilters(source: Source): FilterList = FilterList(source.getFilterList().map { it.copyForRequest() })

    private fun type(filter: Filter<*>): String = when (filter) {
        is Filter.Header -> "HEADER"
        is Filter.Separator -> "SEPARATOR"
        is Filter.Select<*> -> "SELECT"
        is Filter.Sort -> "SORT"
        is Filter.Group<*> -> "GROUP"
        is Filter.TriState -> "TRISTATE"
        is Filter.CheckBox -> "CHECKBOX"
        is Filter.Text -> "TEXT"
        is Filter.AutoComplete -> "AUTOCOMPLETE"
    }

    private fun restoreList(serializer: FilterSerializer, filters: List<Filter<*>>, values: JsonArray) {
        val remaining = filters.toMutableList()
        values.forEach { value ->
            if (value is JsonNull) return@forEach
            val entry = value.jsonObject
            val kind = entry.getValue(FilterSerializer.TYPE).jsonPrimitive.content
            // Headers/hints/separators are presentation, not search state. Their presence may change.
            if (kind == "HEADER" || kind == "SEPARATOR") return@forEach
            val name = entry.getValue("name").jsonPrimitive.content
            val index = remaining.indexOfFirst { it.name == name && type(it) == kind }
            // Serialized groups include every option, even ignored entries removed by a source update.
            if (index < 0 && inactive(entry)) return@forEach
            require(index >= 0) { "Saved filter is unavailable: $kind $name" }
            val target = remaining.removeAt(index)
            restore(serializer, target, entry)
        }
    }

    private fun inactive(value: JsonObject): Boolean = when (value[FilterSerializer.TYPE]?.jsonPrimitive?.content) {
        "HEADER", "SEPARATOR" -> true
        "TRISTATE" -> value.getValue("state").jsonPrimitive.int == Filter.TriState.STATE_IGNORE
        "CHECKBOX" -> !value.getValue("state").jsonPrimitive.boolean
        "TEXT" -> value.getValue("state").jsonPrimitive.content.isEmpty()
        "AUTOCOMPLETE" -> value.getValue("state").jsonArray.isEmpty()
        "GROUP" -> value.getValue("state").jsonArray.all { it is JsonNull || inactive(it.jsonObject) }
        else -> false
    }

    private fun restore(serializer: FilterSerializer, filter: Filter<*>, value: JsonObject) {
        if (filter is Filter.Group<*>) {
            restoreList(serializer, filter.state.filterIsInstance<Filter<*>>(), value.getValue("state").jsonArray)
            return
        }
        val restored = when (filter) {
            is Filter.Select<*> -> {
                val index = value.getValue("state").jsonPrimitive.int
                val mapped = optionIndex(value, index, filter.values.map { it.toString() })
                JsonObject(value + ("state" to JsonPrimitive(mapped.toString())))
            }
            is Filter.Sort -> {
                val selected = value.getValue("state")
                if (selected is JsonNull) {
                    value
                } else {
                    val state = selected.jsonObject
                    val mapped = optionIndex(value, state.getValue("index").jsonPrimitive.int, filter.values.toList())
                    JsonObject(value + ("state" to JsonObject(state + ("index" to JsonPrimitive(mapped)))))
                }
            }
            else -> value
        }
        @Suppress("UNCHECKED_CAST")
        serializer.deserialize(filter as Filter<Any?>, restored)
    }

    private fun optionIndex(saved: JsonObject, index: Int, available: List<String>): Int {
        val labels = saved.getValue("values").jsonArray.map { it.jsonPrimitive.content }
        val selected = requireNotNull(labels.getOrNull(index)) { "Saved option index is invalid" }
        // Stable arrays keep their exact index (including duplicate labels); reordered arrays map by label.
        if (labels == available) return index
        val matches = available.withIndex().filter { it.value == selected }
        require(matches.size == 1) { "Saved option is unavailable or ambiguous: $selected" }
        return matches.single().index
    }
}
// KMK <--
