package keiyoushi.source

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.FilterList
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

// Test ABI of the extension-owned class; no host dependency on Keiyoushi implementation.
abstract class KeiSource(delegate: Source) : Source by delegate {
    protected open val supportsFilterFetching: Boolean = false
    protected open suspend fun fetchFilterData(): JsonElement = JsonNull
    protected open fun getFilterList(data: JsonElement?): FilterList = FilterList()
    final override fun getFilterList(): FilterList = getFilterList(null)
}
