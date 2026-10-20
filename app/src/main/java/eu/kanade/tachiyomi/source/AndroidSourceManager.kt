@file:Suppress("PropertyName")

package eu.kanade.tachiyomi.source

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.all.EHentai
import eu.kanade.tachiyomi.source.online.all.Lanraragi
import eu.kanade.tachiyomi.source.online.all.MangaDex
import eu.kanade.tachiyomi.source.online.all.MergedSource
import eu.kanade.tachiyomi.source.online.all.NHentai
import eu.kanade.tachiyomi.source.online.all.Pururin
import eu.kanade.tachiyomi.source.online.english.EightMuses
import exh.log.xLogD
import exh.log.xLogE
import exh.source.BlacklistedSources
import exh.source.DelegatedHttpSource
import exh.source.EHENTAI_EXT_SOURCES
import exh.source.EIGHTMUSES_SOURCE_ID
import exh.source.EXHENTAI_EXT_SOURCES
import exh.source.EnhancedHttpSource
import exh.source.ExhPreferences
import exh.source.MERGED_SOURCE_ID
import exh.source.handleSourceLibrary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import mihon.core.metro.AppCoroutineScope
import tachiyomi.domain.manga.interactor.GetMergedReferencesById
import tachiyomi.domain.source.model.StubSource
import tachiyomi.domain.source.repository.StubSourceRepository
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.source.local.LocalSource
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class AndroidSourceManager(
    @AppCoroutineScope private val scope: CoroutineScope,
    private val context: Context,
    private val extensionManager: ExtensionManager,
    private val sourceRepository: StubSourceRepository,
    private val localSource: LocalSource,
    private val downloadManager: Lazy<DownloadManager>,
    // SY -->
    private val exhPreferences: ExhPreferences,
    private val sourcePreferences: SourcePreferences,
    // SY <--
    // KMK -->
    private val getMergedReferencesById: GetMergedReferencesById,
    // KMK <--
) : SourceManager {

    /**
     * Null until the extensions have loaded, so that nothing observes the empty seed value.
     */
    private val sourcesMapFlow = MutableStateFlow<Map<Long, Source>?>(null)

    private val stubSourcesMap = ConcurrentHashMap<Long, StubSource>()

    // KUN -->
    // Emit the current snapshot straight away (empty while the extensions load) instead of waiting for
    // the first package scan. Callers must tolerate sources that are not loaded yet, but a screen that
    // observes this flow then must never be left waiting forever on a slow or failing extension scan.
    override val sources: Flow<List<Source>> = sourcesMapFlow
        .map { it?.values?.toList().orEmpty() }
    // KUN <--

    init {
        scope.launch {
            extensionManager.loadedExtensionsFlow
                // SY -->
                .combine(exhPreferences.enableExhentai.changes()) { extensions, enableExhentai ->
                    extensions to enableExhentai
                }
                // KMK -->
                .combine(
                    exhPreferences.isHentaiEnabled.changes(),
                ) { (a, b), c -> Triple(a, b, c) }
                // KMK <--
                // SY <--
                .collectLatest { (extensions, enableExhentai/* KMK --> */, isHentaiEnabled/* KMK <-- */) ->
                    val mutableMap = ConcurrentHashMap<Long, Source>(
                        mapOf(LocalSource.ID to localSource),
                    ).apply {
                        // KMK -->
                        if (isHentaiEnabled) {
                            EHENTAI_EXT_SOURCES.forEach { (id, lang) ->
                                put(id, EHentai(id, false, context, lang))
                            }
                            if (enableExhentai) {
                                EXHENTAI_EXT_SOURCES.forEach { (id, lang) ->
                                    put(id, EHentai(id, true, context, lang))
                                }
                            }
                        }
                        // KMK <--
                        // SY -->
                        put(MERGED_SOURCE_ID, MergedSource(context))
                        // SY <--
                    }
                    extensions.forEach { extension ->
                        // KUN -->
                        extension.sources.filterNotNull().mapNotNull { it.toInternalSource(/* KMK --> */isHentaiEnabled/* KMK <-- */) }.forEach {
                            // KUN <--
                            mutableMap[it.id] = it
                            registerStubSource(StubSource.from(it))
                        }
                    }
                    sourcesMapFlow.value = mutableMap
                }
        }

        scope.launch {
            sourceRepository.subscribeAll()
                .collectLatest { sources ->
                    sources.forEach {
                        stubSourcesMap[it.id] = it
                    }
                }
        }
    }

    private fun Source.toInternalSource(
        // KMK -->
        isHentaiEnabled: Boolean,
        // KMK <--
    ): Source? {
        // KUN -->
        try {
            // KUN <--
            // EXH -->
            val sourceQName = this::class.qualifiedName
            val delegate = if (sourceQName != null) {
                // KMK -->
                DELEGATED_SOURCES.firstOrNull { delegated ->
                    sourceQName == delegated.originalSourceQualifiedClassName ||
                        (delegated.factory && sourceQName.startsWith(delegated.originalSourceQualifiedClassName))
                }
                // KMK <--
            } else {
                null
            }
            val newSource = if (this is HttpSource && delegate != null) {
                xLogD("Delegating source: %s -> %s!", sourceQName, delegate.newSourceClass.qualifiedName)
                // KUN -->
                val enhancedInstance = try {
                    delegate.createSource?.invoke(this, context)
                        ?: delegate.newSourceClass.constructors.find { it.parameters.size == 2 }?.call(this, context)
                } catch (e: Throwable) {
                    xLogE("Failed to create delegated source for $sourceQName", e)
                    null
                }
                if (enhancedInstance != null) {
                    val enhancedSource = EnhancedHttpSource(
                        this,
                        enhancedInstance,
                    )

                    currentDelegatedSources[enhancedSource.originalSource.id] = DelegatedSource(
                        enhancedSource.originalSource.name,
                        enhancedSource.originalSource.id,
                        enhancedSource.originalSource::class.qualifiedName ?: delegate.originalSourceQualifiedClassName,
                        (enhancedSource.enhancedSource as DelegatedHttpSource)::class,
                        delegate.factory,
                        delegate.createSource,
                    )
                    enhancedSource
                } else {
                    this
                }
                // KUN <--
            } else {
                this
            }

            return if (
                // KMK -->
                isHentaiEnabled &&
                // KMK <--
                id in BlacklistedSources.BLACKLISTED_EXT_SOURCES
            ) {
                xLogD(
                    "Removing blacklisted source: (id: %s, name: %s, lang: %s)!",
                    id,
                    name,
                    lang,
                )
                null
            } else {
                newSource
            }
            // EXH <--
            // KUN -->
        } catch (e: Throwable) {
            xLogE("Failed to process source $name ($id) into internal source", e)
            return this
        }
        // KUN <--
    }

    /**
     * Awaits the extensions to have loaded before returning the sources.
     */
    private suspend fun sourcesMap(): Map<Long, Source> = sourcesMapFlow.filterNotNull().first()

    override suspend fun get(sourceKey: Long): Source? {
        return sourcesMap()[sourceKey]
    }

    override suspend fun getOrStub(sourceKey: Long): Source {
        return sourcesMap()[sourceKey] ?: stubSourcesMap.getOrPut(sourceKey) {
            createStubSource(sourceKey)
        }
    }

    override suspend fun getAll(): List<Source> {
        return sourcesMap().values.toList()
    }

    override suspend fun getOnlineSources(): List<HttpSource> {
        return sourcesMap().values.filterIsInstance<HttpSource>()
    }

    override suspend fun getStubSources(): List<StubSource> {
        val onlineSourceIds = getOnlineSources().map { it.id }
        return stubSourcesMap.values.filterNot { it.id in onlineSourceIds }
    }

    // SY -->
    override suspend fun getVisibleOnlineSources() = sourcesMap().values
        .filterIsInstance<HttpSource>()
        .filter {
            it.id !in BlacklistedSources.HIDDEN_SOURCES
        }

    override suspend fun getVisibleSources() = sourcesMap().values
        .filter {
            it.id !in BlacklistedSources.HIDDEN_SOURCES
        }

    fun getDelegatedSources() = sourcesMapFlow.value.orEmpty().values
        .filterIsInstance<EnhancedHttpSource>()
        .mapNotNull { enhancedHttpSource ->
            enhancedHttpSource.enhancedSource as? DelegatedHttpSource
        }
    // SY <--

    // KUN -->
    /**
     * Non-suspending, non-blocking snapshot of the sources loaded so far, or null while the
     * extensions are still loading (i.e. before the first package scan completes).
     *
     * Synchronous surfaces that lack a coroutine scope (Android ViewHolder/ArrayAdapter binds,
     * non-composable helpers) should read this and fall back when it is null instead of calling
     * [get]/[getOrStub] through `runBlocking`, which freezes the main thread for the whole scan.
     */
    val sourcesMapOrNull: Map<Long, Source>?
        get() = sourcesMapFlow.value

    /**
     * Non-suspending variant of [getOrStub]. Returns the stub registered for [sourceKey] (if any)
     * while the main map is not loaded yet, and null when nothing is known about it — callers must
     * then fall back rather than block.
     */
    override fun getOrStubOrNull(sourceKey: Long): Source? =
        sourcesMapFlow.value?.get(sourceKey) ?: stubSourcesMap[sourceKey]
    // KUN <--

    // KMK -->
    override suspend fun getMergedSources(mangaId: Long): List<Source> {
        val sources = getMergedReferencesById.await(mangaId)
        return sources.distinctBy { it.mangaSourceId }
            .map { getOrStub(it.mangaSourceId) }
    }
    // KMK <--

    private fun registerStubSource(source: StubSource) {
        scope.launch {
            val dbSource = sourceRepository.getStubSource(source.id)
            if (dbSource == source) return@launch
            sourceRepository.upsertStubSource(source.id, source.lang, source.name)
            if (dbSource != null) {
                downloadManager.value.renameSource(dbSource, source)
            }
        }
    }

    private suspend fun createStubSource(id: Long): StubSource {
        sourceRepository.getStubSource(id)?.let {
            return it
        }
        extensionManager.getSourceData(id)?.let {
            registerStubSource(it)
            return it
        }
        return StubSource(id = id, lang = "", name = "")
    }

    // SY -->
    companion object {
        private const val fillInSourceId = Long.MAX_VALUE

        /*
         * If an extension is declaring sub-classes based on the main class, then set `factory=true` and
         * only put the package without the class name. For example:
         * "eu.kanade.tachiyomi.extension.all.mangadex" instead of "eu.kanade.tachiyomi.extension.all.mangadex.MangaDex"
         */
        val DELEGATED_SOURCES = listOf(
            DelegatedSource(
                "Pururin",
                fillInSourceId,
                "eu.kanade.tachiyomi.extension.all.pururin.Pururin",
                Pururin::class,
                // KUN -->
                createSource = ::Pururin,
                // KUN <--
            ),
            DelegatedSource(
                "MangaDex",
                fillInSourceId,
                "eu.kanade.tachiyomi.extension.all.mangadex.MangaDex",
                MangaDex::class,
                // KUN -->
                createSource = ::MangaDex,
                // KUN <--
            ),
            DelegatedSource(
                "8Muses",
                EIGHTMUSES_SOURCE_ID,
                "eu.kanade.tachiyomi.extension.en.eightmuses.EightMuses",
                EightMuses::class,
                // KUN -->
                createSource = ::EightMuses,
                // KUN <--
            ),
            DelegatedSource(
                "NHentai",
                fillInSourceId,
                "eu.kanade.tachiyomi.extension.all.nhentai.NHentai",
                NHentai::class,
                // KUN -->
                createSource = ::NHentai,
                // KUN <--
            ),
            DelegatedSource(
                "LANraragi",
                fillInSourceId,
                "eu.kanade.tachiyomi.extension.all.lanraragi.LANraragi",
                Lanraragi::class,
                // KUN -->
                createSource = ::Lanraragi,
                // KUN <--
            ),
        )

        val currentDelegatedSources: MutableMap<Long, DelegatedSource> =
            ListenMutableMap(mutableMapOf(), ::handleSourceLibrary)

        data class DelegatedSource(
            val sourceName: String,
            val sourceId: Long,
            val originalSourceQualifiedClassName: String,
            val newSourceClass: KClass<out DelegatedHttpSource>,
            val factory: Boolean = false,
            // KUN -->
            val createSource: ((HttpSource, Context) -> DelegatedHttpSource)? = null,
            // KUN <--
        )
    }

    private class ListenMutableMap<K, V>(
        private val internalMap: MutableMap<K, V>,
        private val listener: () -> Unit,
    ) : MutableMap<K, V> by internalMap {
        override fun clear() {
            val clearResult = internalMap.clear()
            listener()
            return clearResult
        }

        override fun put(key: K, value: V): V? {
            val putResult = internalMap.put(key, value)
            if (putResult == null) {
                listener()
            }
            return putResult
        }

        override fun putAll(from: Map<out K, V>) {
            internalMap.putAll(from)
            listener()
        }

        override fun remove(key: K): V? {
            val removeResult = internalMap.remove(key)
            if (removeResult != null) {
                listener()
            }
            return removeResult
        }
    }

    // SY <--
}

// KUN -->
/**
 * Non-suspending access to the loaded-source snapshot behind [SourceManager].
 *
 * Returns null while the extensions are still loading, so callers that cannot suspend (e.g. Android
 * ViewHolder/ArrayAdapter binds, non-composable helpers) can fall back instead of blocking the main
 * thread with `runBlocking`.
 */
val SourceManager.sourcesMapOrNull: Map<Long, Source>?
    get() = (this as? AndroidSourceManager)?.sourcesMapOrNull

/**
 * Non-suspending variant of [SourceManager.getOrStub]. Returns null when the sources are not loaded
 * yet and nothing is known about [sourceKey], so callers can fall back instead of blocking.
 */
fun SourceManager.getOrStubOrNull(sourceKey: Long): Source? =
    (this as? AndroidSourceManager)?.getOrStubOrNull(sourceKey)
// KUN <--
