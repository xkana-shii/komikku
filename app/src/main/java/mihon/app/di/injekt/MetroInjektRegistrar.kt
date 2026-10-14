package mihon.app.di.injekt

import android.app.Application
import android.content.Context
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.network.JavaScriptEngine
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.NetworkPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import exh.pref.DelegateSourcePreferences
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import mihon.app.di.AppGraph
import mihon.core.metro.GraphProvider
import nl.adaptivity.xmlutil.serialization.XML
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.InjektionException
import uy.kohesive.injekt.api.TypeReference
import java.lang.reflect.Type
import java.util.concurrent.ConcurrentHashMap

/**
 * An Injekt registrar that resolves a small fixed set of singletons directly from the Metro
 * [AppGraph], and stores the remaining (Komikku/SY/KMK) registrations in a mutable map.
 *
 * This mirrors Mihon's `MetroInjektRegistrar`, but is writable so [mihon.app.di.injekt.MetroInteropModule]
 * and [mihon.app.di.injekt.KMKMetroInteropModule] can still register the singletons that the
 * fork's residual Injekt consumers need.
 *
 * It must be installed (`Injekt = InjektScope(MetroInjektRegistrar(...))`) **before** `graph.inject(this)`,
 * because member injection constructs `TrackerManager`, whose `BaseTracker` resolves `Context` through
 * Injekt during construction.
 */
class MetroInjektRegistrar(
    private val application: Application,
    private val graphProvider: GraphProvider<AppGraph>,
) : InjektRegistrar {

    private val graph: AppGraph inline get() = graphProvider.graph

    /** Resolved lazily from the Metro graph so `graph.inject(this)` can already use them. */
    private val graphBindings = mapOf<Type, () -> Any>(
        Application::class.java to { application },
        Context::class.java to { application },

        Json::class.java to { graph.json },
        ProtoBuf::class.java to { graph.protoBuf },
        XML::class.java to { graph.xml },

        NetworkHelper::class.java to { graph.networkHelper },
        JavaScriptEngine::class.java to { graph.javaScriptEngine },

        // Source-api/source-local and a few residual app consumers resolve these through Injekt while
        // sources are being constructed, which can happen before the interop modules are imported.
        NetworkPreferences::class.java to { graph.networkPreferences },
        SourcePreferences::class.java to { graph.sourcePreferences },
        DelegateSourcePreferences::class.java to { graph.delegateSourcePreferences },
        ReaderPreferences::class.java to { graph.readerPreferences },
        ExtensionManager::class.java to { graph.extensionManager },
        CoverCache::class.java to { graph.coverCache },

        // KUN -->
        // ProvidesSqlDriver (AppBindings) builds the SqlDriver during graph.inject(this), before the
        // interop modules are imported. On encrypted devices it calls CbzCrypto.getDecryptedPasswordSql(),
        // which resolves SecurityPreferences through Injekt, so it must be available here.
        SecurityPreferences::class.java to { graph.securityPreferences },
        // KUN <--
    )

    private object NoKey

    private data class Instance(val forWhatType: Type, val forKey: Any)

    private val existingValues = ConcurrentHashMap<Instance, Any>()
    private val factories = ConcurrentHashMap<Type, () -> Any>()
    private val keyedFactories = ConcurrentHashMap<Type, (Any) -> Any>()

    @Suppress("UNCHECKED_CAST")
    override fun <R : Any> getInstanceOrNull(forType: Type): R? {
        val factory = graphBindings[forType] ?: factories[forType] ?: return null
        return factory.invoke() as R
    }

    override fun <R : Any> getInstance(forType: Type): R = getInstanceOrNull(forType)
        ?: throw InjektionException("$forType is not exposed to Injekt; add it to ${this::class.simpleName}")

    override fun <R : Any> getInstanceOrElse(forType: Type, default: R): R = getInstanceOrNull(forType) ?: default

    override fun <R : Any> getInstanceOrElse(forType: Type, default: () -> R): R = getInstanceOrNull(forType) ?: default()

    @Suppress("UNCHECKED_CAST")
    override fun <R : Any, K : Any> getKeyedInstance(forType: Type, key: K): R {
        val factory = keyedFactories[forType] ?: throw InjektionException("No registered keyed factory for type $forType")
        return factory.invoke(key) as R
    }

    @Suppress("UNCHECKED_CAST")
    override fun <R : Any, K : Any> getKeyedInstanceOrElse(forType: Type, key: K, default: R): R {
        val factory = keyedFactories[forType] ?: return default
        return factory.invoke(key) as R
    }

    @Suppress("UNCHECKED_CAST")
    override fun <R : Any, K : Any> getKeyedInstanceOrElse(forType: Type, key: K, default: () -> R): R {
        val factory = keyedFactories[forType] ?: return default()
        return factory.invoke(key) as R
    }

    @Suppress("UNCHECKED_CAST")
    override fun <R : Any, K : Any> getKeyedInstanceOrNull(forType: Type, key: K): R? {
        val factory = keyedFactories[forType] ?: return null
        return factory.invoke(key) as R
    }

    override fun <R : Any> getLogger(expectedLoggerType: Type, byName: String): R =
        throw UnsupportedOperationException("Logger injection is not supported")

    override fun <R : Any, T : Any> getLogger(expectedLoggerType: Type, forClass: Class<T>): R =
        throw UnsupportedOperationException("Logger injection is not supported")

    override fun <T : Any> addSingleton(forType: TypeReference<T>, singleInstance: T) {
        addSingletonFactory(forType) { singleInstance }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <R : Any> addSingletonFactory(forType: TypeReference<R>, factoryCalledOnce: () -> R) {
        factories[forType.type] = {
            val lazy = existingValues.getOrPut(Instance(forType.type, NoKey)) { lazy { factoryCalledOnce() } }
            (lazy as Lazy<R>).value
        }
    }

    override fun <R : Any> addFactory(forType: TypeReference<R>, factoryCalledEveryTime: () -> R) {
        factories[forType.type] = factoryCalledEveryTime
    }

    @Suppress("UNCHECKED_CAST")
    override fun <R : Any> addPerThreadFactory(forType: TypeReference<R>, factoryCalledOncePerThread: () -> R) {
        // Per-thread semantics are not needed by this fork; fall back to a per-type factory.
        factories[forType.type] = factoryCalledOncePerThread
    }

    @Suppress("UNCHECKED_CAST")
    override fun <R : Any, K : Any> addPerKeyFactory(forType: TypeReference<R>, factoryCalledPerKey: (K) -> R) {
        keyedFactories[forType.type] = { key -> factoryCalledPerKey(key as K) }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <R : Any, K : Any> addPerThreadPerKeyFactory(
        forType: TypeReference<R>,
        factoryCalledPerKeyPerThread: (K) -> R,
    ) {
        keyedFactories[forType.type] = { key -> factoryCalledPerKeyPerThread(key as K) }
    }

    override fun <R : Any> addLoggerFactory(
        forLoggerType: TypeReference<R>,
        factoryByName: (String) -> R,
        factoryByClass: (Class<Any>) -> R,
    ) = throw UnsupportedOperationException("Logger injection is not supported")

    override fun <O : Any, T : O> addAlias(
        existingRegisteredType: TypeReference<T>,
        otherAncestorOrInterface: TypeReference<O>,
    ) {
        factories[existingRegisteredType.type]?.let { factories[otherAncestorOrInterface.type] = it }
        keyedFactories[existingRegisteredType.type]?.let { keyedFactories[otherAncestorOrInterface.type] = it }
    }

    override fun <T : Any> hasFactory(forType: TypeReference<T>): Boolean =
        graphBindings.containsKey(forType.type) ||
            factories.containsKey(forType.type) ||
            keyedFactories.containsKey(forType.type)
}
