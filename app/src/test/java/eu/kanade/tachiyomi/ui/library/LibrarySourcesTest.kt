package eu.kanade.tachiyomi.ui.library

import eu.kanade.tachiyomi.source.Source
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.source.model.StubSource
import tachiyomi.domain.source.service.SourceManager

class LibrarySourcesTest {
    @Test
    fun `unchanged library replaces startup stub when source registration completes and restores fallback on removal`() = runTest {
        val installed = MutableStateFlow<List<Source>>(emptyList())
        val stub = StubSource(7, "en", "Unavailable")
        val real = mockk<Source> { every { id } returns 7L }
        val manager = mockk<SourceManager> {
            every { sources } returns installed
            every { getOrStub(7) } answers { installed.value.firstOrNull() ?: stub }
        }
        val library = MutableStateFlow(listOf(7L))
        val resolved = mutableListOf<Source>()
        backgroundScope.launch {
            library.withSourceUpdates(manager).collect { ids -> resolved += manager.getOrStub(ids.single()) }
        }
        testScheduler.runCurrent()
        resolved shouldBe listOf(stub)
        installed.value = listOf(real)
        testScheduler.runCurrent()
        resolved shouldBe listOf(stub, real)
        installed.value = emptyList()
        testScheduler.runCurrent()
        resolved shouldBe listOf(stub, real, stub)
    }
}
