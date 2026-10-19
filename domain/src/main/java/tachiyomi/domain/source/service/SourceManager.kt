package tachiyomi.domain.source.service

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.source.model.StubSource

interface SourceManager {

    /**
     * Snapshot of the loaded sources, empty until the extensions have finished loading and re-emitted
     * whenever the loaded set changes.
     */
    val sources: Flow<List<Source>>

    suspend fun get(sourceKey: Long): Source?

    suspend fun getOrStub(sourceKey: Long): Source

    // KUN -->
    /**
     * Non-suspending variant of [getOrStub]. Returns the stub known so far (if any), or null while the
     * loaded map and the stub table know nothing about [sourceKey]. Callers that must not block on the
     * extension scan should use this and fall back to a placeholder.
     */
    fun getOrStubOrNull(sourceKey: Long): Source?
    // KUN <--

    suspend fun getAll(): List<Source>

    suspend fun getOnlineSources(): List<HttpSource>

    // SY -->
    suspend fun getVisibleOnlineSources(): List<HttpSource>

    suspend fun getVisibleSources(): List<Source>
    // SY <--

    // KMK -->
    suspend fun getMergedSources(mangaId: Long): List<Source>
    // KMK <--

    suspend fun getStubSources(): List<StubSource>
}
