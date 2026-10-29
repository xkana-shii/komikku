// KUN -->
package eu.kanade.tachiyomi.ui.reader.setting

import android.graphics.Color
import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.ui.reader.ReaderViewModel
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences.NavigationMode
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences.TappingInvertMode
import eu.kanade.tachiyomi.ui.reader.viewer.NavigationSettings
import eu.kanade.tachiyomi.ui.reader.viewer.ViewerNavigation
import eu.kanade.tachiyomi.ui.reader.viewer.ViewerNavigation.NavigationRegion
import eu.kanade.tachiyomi.ui.reader.viewer.ViewerNavigation.Region
import eu.kanade.tachiyomi.ui.reader.viewer.navigation.DisabledNavigation
import eu.kanade.tachiyomi.ui.reader.viewer.navigation.EdgeNavigation
import eu.kanade.tachiyomi.ui.reader.viewer.navigation.KindlishNavigation
import eu.kanade.tachiyomi.ui.reader.viewer.navigation.LNavigation
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
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
        // Constructing a navigator in these tests initialises `NavigationRegion` on the way, and its data
        // objects compute their preview colour once, through the stub: they cache 0 for the rest of this JVM
        // worker, and `unmockkAll()` cannot undo that. A test that asserts a region colour must not run after
        // this class - it needs its own class (or this class needs to stop mocking `Color`).
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
    fun `the smaller tap zone re-emits the long strip navigation`() = runTest(dispatcher) {
        // The webtoon twin of the paged test: same fixed tap-zone shape, resolved from the long strip
        // settings because the built viewer is a webtoon one.
        preferences.navigationModeWebtoon.set(ReaderPreferences.NavigationMode.RIGHT_AND_LEFT.value)
        preferences.webtoonNavInverted.set(ReaderPreferences.TappingInvertMode.VERTICAL)
        val viewModel = createViewModel()
        viewModel.setViewerReadingMode(ReadingMode.WEBTOON, isWebtoonViewer = true, isWebGpuViewer = false)
        advanceUntilIdle()

        val emitted = mutableListOf<ViewerNavigation?>()
        backgroundScope.launch { viewModel.navigation.toList(emitted) }
        advanceUntilIdle()

        assertEquals(1, emitted.size)
        val before = emitted.single()
        assertInstanceOf(RightAndLeftNavigation::class.java, before)
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

        // An unrelated long strip preference rebuilds the settings but not the zones.
        preferences.webtoonSidePadding.set(64)
        advanceUntilIdle()

        assertEquals(64, viewModel.simpleLongStrip.value.page.sidePadding)
        assertEquals(2, emitted.size)
        assertEquals(after, emitted.last())
    }

    /**
     * What this test proves, and what it does not: `navigation` is a `StateFlow`, which conflates equal values, so
     * a navigator rebuilt downstream for an equal one never reaches a collector either way - the
     * `.distinctUntilChanged()` in `ReaderSettingsViewModel` (`ReaderSettingsViewModel.kt:384`) is therefore not
     * observable from the flow's public surface, and deleting it leaves this test green (checked). What it does
     * assert is the behaviour the overlay and the viewers depend on: an unrelated preference rebuilds the paged
     * settings, and the published navigator is still the instance that was there - an unrelated preference
     * cannot leak into the zones, and no new navigator is published for one.
     */
    @Test
    fun `an unrelated paged preference rebuilds the paged settings without replacing the navigator`() =
        runTest(dispatcher) {
            preferences.navigationModePager.set(ReaderPreferences.NavigationMode.RIGHT_AND_LEFT.value)
            preferences.pagerNavInverted.set(ReaderPreferences.TappingInvertMode.VERTICAL)
            val viewModel = createViewModel()
            viewModel.setViewerReadingMode(ReadingMode.RIGHT_TO_LEFT, isWebtoonViewer = false, isWebGpuViewer = false)

            // The navigator the overlay draws and the viewers tap against.
            val before = viewModel.navigation.value
            assertInstanceOf(RightAndLeftNavigation::class.java, before)

            assertFalse(viewModel.paged.value.page.cropBorders)

            preferences.cropBorders.set(true)
            advanceUntilIdle()

            // The pages were rebuilt - the preference did reach the paged settings - but the navigator the
            // viewer and the overlay hold is the one they had: an unrelated preference cannot leak into the
            // zones.
            assertTrue(viewModel.paged.value.page.cropBorders)
            val after = viewModel.navigation.value
            assertSame(before, after)
            assertEquals(
                RightAndLeftNavigation(ReaderPreferences.TappingInvertMode.VERTICAL, smallerTapZone = false),
                after,
            )
        }

    @Test
    fun `a viewer publishes the wide viewport it is drawn in`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        // Nothing has drawn yet, so the sheet reads not-wide.
        assertNull(viewModel.viewerWideViewport.value)

        val published = mutableListOf<Boolean?>()
        backgroundScope.launch { viewModel.viewerWideViewport.toList(published) }
        advanceUntilIdle()
        assertEquals(listOf(null), published)

        // Both answers reach the sheet, and each is the one the viewer published.
        viewModel.setViewerWideViewport(true)
        advanceUntilIdle()
        assertEquals(true, viewModel.viewerWideViewport.value)
        viewModel.setViewerWideViewport(false)
        advanceUntilIdle()
        assertEquals(false, viewModel.viewerWideViewport.value)
        assertEquals(listOf(null, true, false), published)
    }

    @Test
    fun `a viewer rebuild clears the wide viewport before the new one publishes`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        viewModel.setViewerReadingMode(ReadingMode.RIGHT_TO_LEFT, isWebtoonViewer = false, isWebGpuViewer = true)
        viewModel.setViewerWideViewport(true)

        val published = mutableListOf<Boolean?>()
        backgroundScope.launch { viewModel.viewerWideViewport.toList(published) }
        advanceUntilIdle()
        assertEquals(listOf(true), published)

        // The old viewer's box is forgotten as the new one is made: the sheet must not read a landscape
        // answer for a viewer that has not drawn yet.
        viewModel.setViewerReadingMode(ReadingMode.VERTICAL, isWebtoonViewer = false, isWebGpuViewer = true)
        advanceUntilIdle()
        assertNull(viewModel.viewerWideViewport.value)
        assertEquals(listOf(true, null), published)

        // And the new viewer's own answer is published on top of the reset.
        viewModel.setViewerWideViewport(false)
        advanceUntilIdle()
        assertEquals(false, viewModel.viewerWideViewport.value)
        assertEquals(listOf(true, null, false), published)
    }

    @Test
    fun `the tap-zone fractions follow the smaller tap zone`() {
        // R31: every navigator places its zones with these two fractions, 33% of the box normally and 25% with
        // the smaller tap zone on, and the second is the first's complement.
        assertEquals(0.33f to (1f - 0.33f), fractionsOf(smallerTapZone = false))
        assertEquals(0.25f to (1f - 0.25f), fractionsOf(smallerTapZone = true))
    }

    @Test
    fun `every navigation scheme builds its zones under both sizes and every inversion`() {
        // The six schemes the settings plane dispatches, built the way it builds them (`DEFAULT` resolves
        // through the viewer's own direction, horizontal here). What a zone exposes in these unit tests is its
        // type and its place in the list - the order the overlay draws them in and `getAction` searches in -
        // so that is what is pinned, per scheme, size and inversion.
        val expected = mapOf(
            NavigationMode.DEFAULT to listOf(NavigationRegion.LEFT, NavigationRegion.RIGHT),
            NavigationMode.L_SHAPED to
                listOf(NavigationRegion.PREV, NavigationRegion.PREV, NavigationRegion.NEXT, NavigationRegion.NEXT),
            NavigationMode.KINDLISH to listOf(NavigationRegion.NEXT, NavigationRegion.PREV),
            NavigationMode.EDGE to
                listOf(NavigationRegion.NEXT, NavigationRegion.PREV, NavigationRegion.NEXT),
            NavigationMode.RIGHT_AND_LEFT to listOf(NavigationRegion.LEFT, NavigationRegion.RIGHT),
            NavigationMode.DISABLED to emptyList(),
        )

        for (smallerTapZone in listOf(false, true)) {
            for ((mode, layout) in expected) {
                for (invert in TappingInvertMode.entries) {
                    val navigation = NavigationSettings(mode, invert, smallerTapZone)
                        .navigator(::RightAndLeftNavigation)
                    assertEquals(
                        layout,
                        navigation.getRegions().map { it.type },
                        "$mode at smallerTapZone=$smallerTapZone with $invert",
                    )
                }
            }
        }
    }

    /**
     * The fractions [ViewerNavigation] places a zone with, `regionSize1` and `1 - regionSize1` (R31).
     *
     * The rectangles themselves are not readable here. The unit test classpath is AGP's mockable `android.jar`,
     * which empties constructors: `RectF(0f, 0f, 0.33f, 1f)` constructs without throwing and then reads back as
     * `0f, 0f, 0f, 0f` (the first version of the tests above asserted the real edges and failed with
     * `[(LEFT, [0.0, 0.0, 0.0, 0.0]), (RIGHT, [0.0, 0.0, 0.0, 0.0])]`), and a `mockkConstructor` over `RectF`
     * records no calls at all. So the coordinates each navigator passes in, and the mirroring `Region.invert`
     * applies, stay on the manual pass - the values the navigators fill those rects with do not.
     */
    private fun fractionsOf(smallerTapZone: Boolean): Pair<Float, Float> =
        object : ViewerNavigation(TappingInvertMode.NONE, smallerTapZone) {
            override val regionList: List<Region> = emptyList()

            fun fractions() = regionSize1 to regionSize2
        }.fractions()

    @Test
    fun `the webgpu flag follows the viewer, the preference only until one is made`() = runTest(dispatcher) {
        highQualityRenderer.set(true)
        val viewModel = createViewModel()

        // No viewer yet: the sheet has only the preference.
        advanceUntilIdle()
        assertTrue(viewModel.isWebGpuViewer.value)

        // A viewer that fell back from WebGPU wins over the preference it disagrees with.
        viewModel.setViewerReadingMode(ReadingMode.RIGHT_TO_LEFT, isWebtoonViewer = false, isWebGpuViewer = false)
        advanceUntilIdle()
        assertFalse(viewModel.isWebGpuViewer.value)

        // And a WebGPU viewer is WebGPU whatever the preference says.
        highQualityRenderer.set(false)
        viewModel.setViewerReadingMode(ReadingMode.RIGHT_TO_LEFT, isWebtoonViewer = false, isWebGpuViewer = true)
        advanceUntilIdle()
        assertTrue(viewModel.isWebGpuViewer.value)
    }

    @Test
    fun `the tap zones follow the navigation mode and the pager direction`() = runTest(dispatcher) {
        // Which navigator the settings plane builds: upstream's mode dispatch, whose DEFAULT arm is the
        // viewer's own direction - horizontal pager gives right/left zones, the vertical one L-shaped ones.
        val cases = listOf(
            Triple(
                ReadingMode.RIGHT_TO_LEFT,
                ReaderPreferences.NavigationMode.DEFAULT,
                RightAndLeftNavigation::class.java,
            ),
            Triple(ReadingMode.VERTICAL, ReaderPreferences.NavigationMode.DEFAULT, LNavigation::class.java),
            Triple(ReadingMode.RIGHT_TO_LEFT, ReaderPreferences.NavigationMode.L_SHAPED, LNavigation::class.java),
            Triple(
                ReadingMode.RIGHT_TO_LEFT,
                ReaderPreferences.NavigationMode.KINDLISH,
                KindlishNavigation::class.java,
            ),
            Triple(ReadingMode.RIGHT_TO_LEFT, ReaderPreferences.NavigationMode.EDGE, EdgeNavigation::class.java),
            Triple(
                ReadingMode.RIGHT_TO_LEFT,
                ReaderPreferences.NavigationMode.RIGHT_AND_LEFT,
                RightAndLeftNavigation::class.java,
            ),
            Triple(
                ReadingMode.RIGHT_TO_LEFT,
                ReaderPreferences.NavigationMode.DISABLED,
                DisabledNavigation::class.java,
            ),
        )

        for ((readingMode, mode, expected) in cases) {
            preferences.navigationModePager.set(mode.value)
            val viewModel = createViewModel()
            viewModel.setViewerReadingMode(readingMode, isWebtoonViewer = false, isWebGpuViewer = false)
            advanceUntilIdle()

            val navigator = viewModel.navigation.value
            assertEquals(
                expected,
                navigator?.javaClass,
                "$mode for a $readingMode viewer made a $navigator",
            )
        }
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
