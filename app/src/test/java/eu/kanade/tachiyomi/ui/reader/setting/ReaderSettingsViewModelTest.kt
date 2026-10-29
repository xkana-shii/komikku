// KUN -->
package eu.kanade.tachiyomi.ui.reader.setting

import android.graphics.Color
import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.ui.reader.ReaderViewModel
import eu.kanade.tachiyomi.ui.reader.viewer.ViewerNavigation
import eu.kanade.tachiyomi.ui.reader.viewer.navigation.RightAndLeftNavigation
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

/**
 * The settings plane the reader activity builds: a change in a preference the viewer reads reaches the
 * viewer, and a change in one it does not read does not.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderSettingsViewModelTest {

    private val store = FakePreferenceStore()

    private val preferences = ReaderPreferences(store)

    /**
     * The real [BasePreferences] cannot be built here: its `extensionInstaller` field probes the package
     * manager, which loads `ContextExtensionsKt`, whose initialiser calls `android.graphics.Color.rgb`
     * (not mocked in these unit tests). Only [BasePreferences.highQualityRenderer] is read by the view
     * model, so that one property is answered with a real, settable preference.
     */
    private val highQualityRenderer = store.getBoolean("pref_high_quality_renderer_key", false)

    private val basePreferences = mockk<BasePreferences>()

    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { basePreferences.highQualityRenderer } returns highQualityRenderer
        // Every navigator's regions carry a preview colour, and `android.graphics.Color.argb` is a stub that
        // throws in these unit tests; the colour is not what is under test.
        mockkStatic(Color::class)
        every { Color.argb(any<Int>(), any<Int>(), any<Int>(), any<Int>()) } returns 0
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `the smaller tap zone re-emits the paged navigation`() = runTest(dispatcher) {
        // Fixed tap-zone shape: only the zone size flips below.
        preferences.navigationModePager.set(ReaderPreferences.NavigationMode.RIGHT_AND_LEFT.value)
        preferences.pagerNavInverted.set(ReaderPreferences.TappingInvertMode.VERTICAL)
        val viewModel = createViewModel()
        viewModel.setViewerReadingMode(ReadingMode.RIGHT_TO_LEFT, isWebtoonViewer = false, isWebGpuViewer = false)

        val emitted = mutableListOf<ViewerNavigation?>()
        backgroundScope.launch { viewModel.navigation.toList(emitted) }
        advanceUntilIdle()

        assertEquals(1, emitted.size)
        val before = emitted.single()
        assertInstanceOf(RightAndLeftNavigation::class.java, before)
        // The navigator carries the zone size it was built with: equal only to one of the same size.
        assertEquals(
            RightAndLeftNavigation(ReaderPreferences.TappingInvertMode.VERTICAL, smallerTapZone = false),
            before,
        )

        preferences.smallerTapZone.set(true)
        advanceUntilIdle()

        assertEquals(2, emitted.size)
        val after = emitted.last()
        assertInstanceOf(RightAndLeftNavigation::class.java, after)
        assertEquals(
            RightAndLeftNavigation(ReaderPreferences.TappingInvertMode.VERTICAL, smallerTapZone = true),
            after,
        )
        assertNotEquals(before, after)
    }

    @Test
    fun `an unrelated paged preference does not re-emit the navigation`() = runTest(dispatcher) {
        preferences.navigationModePager.set(ReaderPreferences.NavigationMode.RIGHT_AND_LEFT.value)
        preferences.pagerNavInverted.set(ReaderPreferences.TappingInvertMode.VERTICAL)
        val viewModel = createViewModel()
        viewModel.setViewerReadingMode(ReadingMode.RIGHT_TO_LEFT, isWebtoonViewer = false, isWebGpuViewer = false)

        val emitted = mutableListOf<ViewerNavigation?>()
        backgroundScope.launch { viewModel.navigation.toList(emitted) }
        advanceUntilIdle()

        assertFalse(viewModel.paged.value.page.cropBorders)

        preferences.cropBorders.set(true)
        advanceUntilIdle()

        // The pages were rebuilt - the preference did reach the paged settings - but the zones did not
        // change, so the navigator the viewer holds is left alone.
        assertTrue(viewModel.paged.value.page.cropBorders)
        assertEquals(1, emitted.size)
        assertEquals(
            RightAndLeftNavigation(ReaderPreferences.TappingInvertMode.VERTICAL, smallerTapZone = false),
            emitted.single(),
        )
    }

    @Test
    fun `the webgpu flag follows the viewer, the preference only until one is made`() = runTest(dispatcher) {
        highQualityRenderer.set(true)
        val viewModel = createViewModel()

        // No viewer yet: the sheet has only the preference.
        assertTrue(viewModel.isWebGpuViewer.value)

        // A viewer that fell back from WebGPU wins over the preference it disagrees with.
        viewModel.setViewerReadingMode(ReadingMode.RIGHT_TO_LEFT, isWebtoonViewer = false, isWebGpuViewer = false)
        assertFalse(viewModel.isWebGpuViewer.value)

        // And a WebGPU viewer is WebGPU whatever the preference says.
        highQualityRenderer.set(false)
        viewModel.setViewerReadingMode(ReadingMode.RIGHT_TO_LEFT, isWebtoonViewer = false, isWebGpuViewer = true)
        assertTrue(viewModel.isWebGpuViewer.value)
    }

    private fun createViewModel() = ReaderSettingsViewModel(
        readerState = MutableStateFlow(ReaderViewModel.State()),
        onChangeReadingMode = {},
        onChangeOrientation = {},
        preferences = preferences,
        basePreferences = basePreferences,
        resolveReadingMode = { ReadingMode.RIGHT_TO_LEFT },
    )
}

/**
 * A preference store whose preferences keep their value and notify on [Preference.changes], so a value read
 * back is the one written and a change reaches whoever collects it.
 */
private class FakePreferenceStore : PreferenceStore {

    private val values = MutableStateFlow<Map<String, Any?>>(emptyMap())

    override fun getString(key: String, defaultValue: String): Preference<String> = pref(key, defaultValue)

    override fun getLong(key: String, defaultValue: Long): Preference<Long> = pref(key, defaultValue)

    override fun getInt(key: String, defaultValue: Int): Preference<Int> = pref(key, defaultValue)

    override fun getFloat(key: String, defaultValue: Float): Preference<Float> = pref(key, defaultValue)

    override fun getBoolean(key: String, defaultValue: Boolean): Preference<Boolean> = pref(key, defaultValue)

    override fun getStringSet(key: String, defaultValue: Set<String>): Preference<Set<String>> = pref(key, defaultValue)

    override fun <T> getObjectFromString(
        key: String,
        defaultValue: T,
        serializer: (T) -> String,
        deserializer: (String) -> T,
    ): Preference<T> = pref(key, defaultValue)

    override fun <T> getObjectFromInt(
        key: String,
        defaultValue: T,
        serializer: (T) -> Int,
        deserializer: (Int) -> T,
    ): Preference<T> = pref(key, defaultValue)

    override fun <T> getObjectSetFromStringSet(
        key: String,
        defaultValue: Set<T>,
        serializer: (T) -> String,
        deserializer: (String) -> T?,
    ): Preference<Set<T>> = pref(key, defaultValue)

    override fun getAll(): Map<String, *> = values.value

    private fun <T> pref(key: String, defaultValue: T): Preference<T> = StorePreference(key, defaultValue)

    private inner class StorePreference<T>(
        private val key: String,
        private val defaultValue: T,
    ) : Preference<T> {

        @Suppress("UNCHECKED_CAST")
        private fun read(): T = (values.value[key] ?: defaultValue) as T

        override fun key(): String = key

        override fun get(): T = read()

        override fun set(value: T) {
            values.update { it + (key to value) }
        }

        override fun isSet(): Boolean = values.value.containsKey(key)

        override fun delete() {
            values.update { it - key }
        }

        override fun defaultValue(): T = defaultValue

        override fun changes(): Flow<T> = values.map { read() }.distinctUntilChanged()

        override fun stateIn(scope: CoroutineScope): StateFlow<T> =
            changes().stateIn(scope, SharingStarted.Eagerly, read())
    }
}
// KUN <--
