package eu.kanade.tachiyomi.extension

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.domain.extension.interactor.TrustExtension
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.extension.api.ExtensionUpdateNotifier
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.extension.util.ExtensionInstallReceiver
import eu.kanade.tachiyomi.extension.util.ExtensionInstaller
import eu.kanade.tachiyomi.extension.util.ExtensionLoader
import eu.kanade.tachiyomi.util.system.toast
import exh.log.xLogD
import exh.source.BlacklistedSources
import exh.source.EHENTAI_EXT_SOURCES
import exh.source.EXHENTAI_EXT_SOURCES
import exh.source.ExhPreferences
import exh.source.MERGED_SOURCE_ID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import logcat.LogPriority
import mihon.core.metro.AppCoroutineScope
import mihon.domain.extension.interactor.UpdateExtensionStores
import mihon.domain.extension.model.ExtensionStore
import mihon.domain.extension.repository.ExtensionStoreRepository
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.source.model.StubSource
import tachiyomi.i18n.MR
import java.util.Locale

/**
 * The manager of extensions installed as another apk which extend the available sources. It handles
 * the retrieval of remotely available extensions as well as installing, updating and removing them.
 * To avoid malicious distribution, every extension must be signed and it will only be loaded if its
 * signature is trusted, otherwise the user will be prompted with a warning to trust it before being
 * loaded.
 */
@Inject
@SingleIn(AppScope::class)
class ExtensionManager(
    @AppCoroutineScope private val scope: CoroutineScope,
    private val context: Context,
    private val preferences: SourcePreferences,
    private val trustExtension: TrustExtension,
    private val installer: ExtensionInstaller,
    private val extensionUpdateNotifier: ExtensionUpdateNotifier,
    private val extensionStoreRepository: ExtensionStoreRepository,
    private val updateExtensionStores: UpdateExtensionStores,
    // KMK -->
    private val exhPreferences: ExhPreferences,
    // KMK <--
) {

    private val isInitialized = MutableStateFlow(false)

    // A full load replaces both maps, so install events wait for it and apply on top instead of being overwritten
    private val loadMutex = Mutex()

    // KUN -->
    private companion object {
        const val PACKAGE_MANAGER_MAX_CHECKS = 12
        const val PACKAGE_MANAGER_STABLE_CHECKS = 3
        const val PACKAGE_MANAGER_CHECK_INTERVAL_MS = 500L
    }

    /**
     * The package manager can answer with a partial installed-extension list while the process is still
     * starting up. Poll its count until it has been stable for a few checks so the first load sees every
     * installed extension, instead of silently dropping sources until the next manual reload.
     */
    private suspend fun waitForPackageManager() {
        var previousCount = -1
        var stableChecks = 0
        repeat(PACKAGE_MANAGER_MAX_CHECKS) { check ->
            val count = ExtensionLoader.getExtensionCount(context)
            if (count == previousCount) {
                stableChecks++
                if (stableChecks >= PACKAGE_MANAGER_STABLE_CHECKS) return
            } else {
                previousCount = count
                stableChecks = 0
            }
            if (check < PACKAGE_MANAGER_MAX_CHECKS - 1) {
                delay(PACKAGE_MANAGER_CHECK_INTERVAL_MS)
            }
        }
    }
    // KUN <--

    @Volatile
    private var stores = emptyList<ExtensionStore>()

    private val loadedExtensionMapFlow = MutableStateFlow(emptyMap<String, Extension.Loaded>())
    val loadedExtensionsFlow = loadedExtensionMapFlow.mapExtensionsWhenInitialized()

    // KUN -->
    /** Synchronous snapshot of the currently loaded installed extensions. */
    val loadedExtensions: List<Extension.Loaded>
        get() = loadedExtensionMapFlow.value.values.toList()
    // KUN <--

    // Every store's listing, since more than one store can list the same extension
    private val availableExtensionListFlow = MutableStateFlow(emptyList<Extension.Available>())

    // SY -->
    // Stores sharing a signing key serve the same apks, so only the newest of their listings is shown. Stores
    // with different keys offer different apks, each installable, so each keeps its own.
    val availableExtensionsFlow = availableExtensionListFlow
        .map { extensions ->
            extensions
                .filterNot { it.isBlacklisted() }
                .groupBy { it.pkgName to it.store.signingKey }
                .values
                .map { listings ->
                    listings.maxWith(compareBy<Extension.Available> { it.versionCode }.thenBy { it.libVersion })
                }
        }
        .stateIn(scope, SharingStarted.Lazily, emptyList())
    // SY <--

    private val notLoadedExtensionMapFlow = MutableStateFlow(emptyMap<String, Extension.NotLoaded>())
    val notLoadedExtensionsFlow = notLoadedExtensionMapFlow.mapExtensionsWhenInitialized()

    init {
        scope.launch(Dispatchers.IO) {
            // KUN -->
            // Querying installed packages right after process start can return an incomplete list, and
            // nothing would retry the load afterwards (installed extensions don't broadcast an install),
            // leaving sources and library entries orphaned for the whole session. Wait for the count to
            // settle before the first load, as Mihon did in #2984.
            waitForPackageManager()
            // KUN <--
            loadExtensions()
            ExtensionInstallReceiver(InstallationListener(), scope).register(context)

            // Everything the load decision rests on can change while running, so decide again
            merge(
                trustExtension.changes(),
                preferences.enabledContentWarnings.changes().distinctUntilChanged().drop(1).map {},
                preferences.applyContentWarningsToInstalled.changes().distinctUntilChanged().drop(1).map {},
            )
                .collectLatest { loadExtensions() }
        }

        // Extensions are only reloaded when the set of signing keys changes, which misses a store being
        // removed while another with the same key stays, or a store being renamed
        scope.launch(Dispatchers.IO) {
            isInitialized.first { it }
            extensionStoreRepository.getAllAsFlow().collect(::assignStores)
        }
    }

    private var subLanguagesEnabledOnFirstRun = preferences.enabledLanguages.isSet()

    suspend fun getLoadedExtensions(): List<Extension.Loaded> {
        isInitialized.first { it }
        return loadedExtensionMapFlow.value.values.toList()
    }

    suspend fun getNotLoadedExtensions(): List<Extension.NotLoaded> {
        isInitialized.first { it }
        return notLoadedExtensionMapFlow.value.values.toList()
    }

    suspend fun getExtensionPackage(sourceId: Long): String? {
        return getLoadedExtensions().find { extension ->
            extension.sources.any { it.id == sourceId }
        }
            ?.pkgName
    }

    fun getExtensionPackageAsFlow(sourceId: Long): Flow<String?> {
        return loadedExtensionsFlow.map { extensions ->
            extensions.find { extension ->
                extension.sources.any { it.id == sourceId }
            }
                ?.pkgName
        }
    }

    // KUN -->
    // Mihon b15af71b94 moved installed-extension icon loading to Coil; only the fork's non-extension
    // delegated sources (E-Hentai/ExH/Merged) still need a drawable supplied by the app.
    fun getSpecificSourceIcon(sourceId: Long): Drawable? = when (sourceId) {
        in EHENTAI_EXT_SOURCES -> ContextCompat.getDrawable(context, R.mipmap.ic_ehentai_source)
        in EXHENTAI_EXT_SOURCES -> ContextCompat.getDrawable(context, R.mipmap.ic_exhentai_source)
        MERGED_SOURCE_ID -> ContextCompat.getDrawable(context, R.mipmap.ic_merged_source)
        else -> null
    }
    // KUN <--

    private var availableExtensionsSourcesData: Map<Long, StubSource> = emptyMap()

    private fun setupAvailableExtensionsSourcesDataMap(extensions: List<Extension.Available>) {
        if (extensions.isEmpty()) return
        availableExtensionsSourcesData = extensions
            .flatMap { ext -> ext.sources.map { it.toStubSource() } }
            .associateBy { it.id }
    }

    fun getSourceData(id: Long) = availableExtensionsSourcesData[id]

    /**
     * Loads and registers the installed extensions. Safe to call again: every extension is judged
     * again, so one can move between loaded and not loaded in either direction, while extensions
     * that still pass keep the instances they already had.
     */
    private suspend fun loadExtensions() = loadMutex.withLock {
        try {
            val extensions = ExtensionLoader.loadExtensions(context, loadedExtensionMapFlow.value)

            loadedExtensionMapFlow.update {
                extensions
                    .filterIsInstance<Extension.Loaded>()
                    .associateBy { it.pkgName }
            }

            notLoadedExtensionMapFlow.update {
                extensions
                    .filterIsInstance<Extension.NotLoaded>()
                    .associateBy { it.pkgName }
                    // SY -->
                    .filterNotBlacklisted()
                // SY <--
            }

            // Newly loaded extensions have no status derived from the store index yet
            refreshStatuses()
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "Failed to load extensions" }
        } finally {
            // Release anything waiting on the extensions whether or not the load worked
            isInitialized.value = true
        }
    }

    // EXH -->
    private fun <T : Extension> Map<String, T>.filterNotBlacklisted(): Map<String, T> {
        val blacklistEnabled = preferences.enableSourceBlacklist.get()
        return filterNot { (_, extension) ->
            extension.isBlacklisted(blacklistEnabled)
                .also {
                    if (it) this@ExtensionManager.xLogD("Removing blacklisted extension: (name: %s, pkgName: %s)!", extension.name, extension.pkgName)
                }
        }
    }

    private fun Extension.isBlacklisted(
        blacklistEnabled: Boolean = preferences.enableSourceBlacklist.get(),
        // KMK -->
        isHentaiEnabled: Boolean = exhPreferences.isHentaiEnabled.get(),
        // KMK <--
    ): Boolean {
        return pkgName in BlacklistedSources.BLACKLISTED_EXTENSIONS &&
            blacklistEnabled &&
            // KMK -->
            isHentaiEnabled
        // KMK <--
    }
    // EXH <--

    /**
     * Finds the available extensions in the stores and updates [availableExtensionListFlow].
     */
    suspend fun findAvailableExtensions() {
        val extensions: List<Extension.Available> = try {
            fetchExtensions()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            withContext(Dispatchers.Main) { context.toast(MR.strings.extension_api_error) }
            return
        }

        setAvailableExtensions(extensions)
    }

    /**
     * Refreshes the stores and their listings like [findAvailableExtensions], then notifies about the
     * installed extensions that have an update. Stays silent when the stores can't be reached.
     */
    suspend fun checkForUpdates() {
        val extensions = try {
            updateExtensionStores()
            fetchExtensions()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            return
        }

        isInitialized.first { it }
        setAvailableExtensions(extensions)

        val names = (loadedExtensionMapFlow.value.values + notLoadedExtensionMapFlow.value.values)
            .filter { it.hasUpdate }
            // SY -->
            .filterNot { it.isBlacklisted() }
            // SY <--
            .map { it.name }
        if (names.isNotEmpty()) {
            extensionUpdateNotifier.promptUpdates(names)
        }
    }

    private suspend fun fetchExtensions(): List<Extension.Available> {
        // KMK -->
        val disabledRepos = preferences.disabledRepos.get()
        return extensionStoreRepository.fetchExtensions(disabledRepos)
        // KMK <--
    }

    private fun setAvailableExtensions(extensions: List<Extension.Available>) {
        enableAdditionalSubLanguages(extensions)

        availableExtensionListFlow.value = extensions
        refreshStatuses()
        setupAvailableExtensionsSourcesDataMap(extensions)
    }

    /**
     * Enables the additional sub-languages in the app first run. This addresses
     * the issue where users still need to enable some specific languages even when
     * the device language is inside that major group. As an example, if a user
     * has a zh device language, the app will also enable zh-Hans and zh-Hant.
     *
     * If the user have already changed the enabledLanguages preference value once,
     * the new languages will not be added to respect the user enabled choices.
     */
    private fun enableAdditionalSubLanguages(extensions: List<Extension.Available>) {
        if (subLanguagesEnabledOnFirstRun || extensions.isEmpty()) {
            return
        }

        // Use the source lang as some aren't present on the extension level.
        val availableLanguages = extensions
            .flatMap(Extension.Available::sources)
            .distinctBy(Extension.Available.Source::lang)
            .map(Extension.Available.Source::lang)

        val deviceLanguage = Locale.getDefault().language
        val defaultLanguages = preferences.enabledLanguages.defaultValue()
        val languagesToEnable = availableLanguages.filter {
            it != deviceLanguage && it.startsWith(deviceLanguage)
        }

        preferences.enabledLanguages.set(defaultLanguages + languagesToEnable)
        subLanguagesEnabledOnFirstRun = true
    }

    /**
     * Returns a flow of the installation process for the given extension. It will complete
     * once the extension is installed or throws an error. The process will be canceled if
     * unsubscribed before its completion.
     *
     * @param extension The extension to be installed.
     */
    fun installExtension(extension: Extension.Available): Flow<InstallStep> {
        return installer.downloadAndInstall(extension)
    }

    /**
     * Returns a flow of the installation process for the given extension. It will complete
     * once the extension is updated or throws an error. The process will be canceled if
     * unsubscribed before its completion.
     *
     * @param extension The extension to be updated.
     */
    fun updateExtension(extension: Extension.Installed): Flow<InstallStep> {
        val update = extension.findUpdate(availableExtensionListFlow.value) ?: return emptyFlow()
        val isUpdateForPrivatelyInstalled = !extension.isShared
        return installer.downloadAndInstall(update, isUpdateForPrivatelyInstalled)
    }

    fun cancelInstallUpdateExtension(extension: Extension) {
        installer.cancelInstall(extension.pkgName)
    }

    /**
     * Sets to "installing" status of an extension installation.
     *
     * @param downloadId The id of the download.
     */
    fun setInstalling(downloadId: Long) {
        installer.updateInstallStep(downloadId, InstallStep.Installing)
    }

    fun updateInstallStep(downloadId: Long, step: InstallStep) {
        installer.updateInstallStep(downloadId, step)
    }

    /**
     * Uninstalls the extension that matches the given package name.
     *
     * @param extension The extension to uninstall.
     */
    fun uninstallExtension(extension: Extension.Installed) {
        installer.uninstallApk(extension.pkgName)
    }

    /**
     * Adds the given extension to the list of trusted extensions. It also loads in background the
     * now trusted extensions.
     *
     * @param extension the extension to trust
     */
    fun trust(extension: Extension.NotLoaded) {
        val reason = extension.reason as? Extension.NotLoaded.Reason.Untrusted ?: return
        notLoadedExtensionMapFlow.value[extension.pkgName] ?: return

        // Loading it again is left to the reload triggered by the trust change
        trustExtension.trust(extension.pkgName, extension.versionCode, reason.signatureHash)
    }

    /**
     * Registers the given extension in this and the source managers.
     *
     * @param extension The extension to be registered.
     */
    private fun registerExtension(extension: Extension.Loaded) {
        // SY -->
        if (extension.isBlacklisted()) {
            xLogD("Removing blacklisted extension: (name: String, pkgName: %s)!", extension.name, extension.pkgName)
            return
        }
        // SY <--

        loadedExtensionMapFlow.update { it + extension }
    }

    /**
     * Unregisters the extension in this and the source managers given its package name. Note this
     * method is called for every uninstalled application in the system.
     *
     * @param pkgName The package name of the uninstalled application.
     */
    private fun unregisterExtension(pkgName: String) {
        loadedExtensionMapFlow.update { it - pkgName }
        notLoadedExtensionMapFlow.update { it - pkgName }
    }

    /**
     * Listener which receives events of the extensions being installed, updated or removed.
     */
    private inner class InstallationListener : ExtensionInstallReceiver.Listener {

        override suspend fun onExtensionLoaded(extension: Extension.Loaded) = loadMutex.withLock {
            registerExtension(extension)
            notLoadedExtensionMapFlow.update { it - extension.pkgName }
            refreshStatuses()
        }

        override suspend fun onExtensionNotLoaded(extension: Extension.NotLoaded) = loadMutex.withLock {
            loadedExtensionMapFlow.update { it - extension.pkgName }
            notLoadedExtensionMapFlow.update { it + extension }
            refreshStatuses()
        }

        override suspend fun onPackageUninstalled(pkgName: String) = loadMutex.withLock {
            ExtensionLoader.uninstallPrivateExtension(context, pkgName)
            unregisterExtension(pkgName)
            updatePendingUpdatesCount()
        }
    }

    /**
     * Derives what the store listings say about every installed extension, loaded or not. Without any
     * listing there's nothing to derive from, so they're left as they are rather than marked obsolete.
     */
    private fun refreshStatuses() {
        val available = availableExtensionListFlow.value
        if (available.isNotEmpty()) {
            loadedExtensionMapFlow.update { current ->
                current.mapValues { (_, extension) ->
                    val listing = extension.findListing(available)
                    extension.copy(
                        hasUpdate = extension.findUpdate(available) != null,
                        isObsolete = listing == null,
                        store = if (stores.isEmpty()) extension.store else extension.pickStore(),
                        // SY -->
                        isRedundant = extension.isRedundant || extension.isBlacklisted(),
                        // SY <--
                    )
                }
            }
            notLoadedExtensionMapFlow.update { current ->
                current.mapValues { (_, extension) ->
                    extension.copy(
                        hasUpdate = extension.findUpdate(available) != null,
                        store = if (stores.isEmpty()) extension.store else extension.pickStore(),
                    )
                }
            }
        }
        updatePendingUpdatesCount()
    }

    /**
     * Several stores can share a signing key, so one that lists the extension names where it comes from better
     * than whichever of them was added first.
     */
    private fun Extension.Installed.pickStore(): ExtensionStore? {
        val signingStores = stores.filter { it.signingKey in signatures }
        val listedBy = availableExtensionListFlow.value
            .filter { it.pkgName == pkgName }
            .mapTo(HashSet()) { it.store.indexUrl }
        return signingStores.firstOrNull { it.indexUrl in listedBy } ?: signingStores.firstOrNull()
    }

    private fun assignStores(stores: List<ExtensionStore>) {
        this.stores = stores
        loadedExtensionMapFlow.update { extensions ->
            extensions.mapValues { (_, extension) -> extension.copy(store = extension.pickStore()) }
        }
        notLoadedExtensionMapFlow.update { extensions ->
            extensions.mapValues { (_, extension) -> extension.copy(store = extension.pickStore()) }
        }
    }

    private fun updatePendingUpdatesCount() {
        val pendingUpdateCount = (loadedExtensionMapFlow.value.values + notLoadedExtensionMapFlow.value.values)
            .count { it.hasUpdate }
        preferences.extensionUpdatesCount.set(pendingUpdateCount)
        if (pendingUpdateCount == 0) {
            extensionUpdateNotifier.dismiss()
        }
    }

    private operator fun <T : Extension> Map<String, T>.plus(extension: T) = plus(extension.pkgName to extension)

    /**
     * Extensions are loaded in the background, and [stateIn] would replay the empty list it was
     * seeded with at construction, so this only starts emitting once that finished.
     */
    private fun <T : Extension> StateFlow<Map<String, T>>.mapExtensionsWhenInitialized(): Flow<List<T>> {
        return onStart { isInitialized.first { it } }.map { it.values.toList() }
    }
}
