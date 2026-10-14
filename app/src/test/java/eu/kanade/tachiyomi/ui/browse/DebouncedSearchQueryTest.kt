package eu.kanade.tachiyomi.ui.browse

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

// KMK --> Sources and Migrate share this state holder, so these cover input behavior in both tabs.
@OptIn(ExperimentalCoroutinesApi::class)
class DebouncedSearchQueryTest {
    @Test
    fun `renders every rapid edit immediately and filters only after the final debounce`() = runTest {
        val query = DebouncedSearchQuery()
        val rendered = mutableListOf<String?>()
        val filtered = mutableListOf<String?>()
        val renderJob = launch(UnconfinedTestDispatcher(testScheduler)) { query.value.collect { rendered += it } }
        val filterJob = launch(UnconfinedTestDispatcher(testScheduler)) { query.debounced.collect { filtered += it } }
        runCurrent()

        query.update("m")
        query.update("ma")
        query.update("man")
        query.value.value shouldBe "man"
        rendered shouldBe listOf(null, "m", "ma", "man")
        filtered shouldBe emptyList()

        advanceTimeBy(249)
        filtered shouldBe emptyList()
        advanceTimeBy(1)
        runCurrent()
        filtered shouldBe listOf("man")

        renderJob.cancel()
        filterJob.cancel()
    }

    @Test
    fun `supports replacing deleting pasting and clearing without stale input`() = runTest {
        val query = DebouncedSearchQuery()
        val rendered = mutableListOf<String?>()
        val filtered = mutableListOf<String?>()
        val renderJob = launch(UnconfinedTestDispatcher(testScheduler)) { query.value.collect { rendered += it } }
        val filterJob = launch(UnconfinedTestDispatcher(testScheduler)) { query.debounced.collect { filtered += it } }
        runCurrent()

        query.update("MangaGo")
        query.update("Manga Go") // Replace text at the cursor.
        query.update("Manga") // Delete a selection.
        query.update("Manga, Kagane") // Paste additional search text.
        query.update("") // Clear while retaining the active text-field state.
        query.value.value shouldBe ""
        rendered shouldBe listOf(null, "MangaGo", "Manga Go", "Manga", "Manga, Kagane", "")

        advanceTimeBy(250)
        runCurrent()
        filtered shouldBe listOf("")

        renderJob.cancel()
        filterJob.cancel()
    }
}
// KMK <--
