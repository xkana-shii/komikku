package eu.kanade.tachiyomi.extension

import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.source.Source
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class SourceIconsTest {
    private val extension = mockk<Extension.Installed> {
        every { pkgName } returns "extension.pkg"
        every { sources } returns listOf(mockk<Source> { every { id } returns 7L })
    }

    @Test
    fun `cold icon lookup works while Browse lazy list has never been collected`() = runTest {
        val installed = MutableStateFlow<Map<String, Extension.Installed>>(emptyMap())
        val browse = installed.map { it.values.toList() }.stateIn(backgroundScope, SharingStarted.Lazily, emptyList())
        installed.value = mapOf("extension.pkg" to extension)
        browse.value shouldBe emptyList()
        installed.value.sourcePackage(7) shouldBe "extension.pkg"
        observeSourceIcon(installed) { installed.value.sourcePackage(7)?.let { "icon" } }.first() shouldBe "icon"
        browse.value shouldBe emptyList()
    }

    @Test
    fun `library icon reacts to late installation and removal without navigating to Browse`() = runTest {
        val installed = MutableStateFlow<Map<String, Extension.Installed>>(emptyMap())
        val icons = mutableListOf<String?>()
        backgroundScope.launch {
            observeSourceIcon(installed) { installed.value.sourcePackage(7)?.let { "icon" } }
                .collect { icons += it }
        }
        testScheduler.runCurrent()
        icons shouldBe listOf(null)
        installed.value = mapOf("extension.pkg" to extension)
        testScheduler.runCurrent()
        icons shouldBe listOf(null, "icon")
        installed.value = emptyMap()
        testScheduler.runCurrent()
        icons shouldBe listOf(null, "icon", null)
    }

    @Test
    fun `missing source and installed source without an icon preserve null fallback`() = runTest {
        val installed = MutableStateFlow(mapOf("extension.pkg" to extension))
        installed.value.sourcePackage(999) shouldBe null
        observeSourceIcon<String>(installed) { null }.first() shouldBe null
    }
}
