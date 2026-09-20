package eu.kanade.tachiyomi.extension

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExtensionInitializationTest {
    @Test
    fun `source registration sees finalized map first and subsequent installation changes`() = runTest {
        val installed = MutableStateFlow<Map<String, String>>(emptyMap())
        val ready = MutableStateFlow(false)
        val emitted = mutableListOf<List<String>>()
        val job = backgroundScope.launch { installed.afterInitialization(ready).collect { emitted += it } }
        testScheduler.runCurrent()
        emitted shouldBe emptyList()
        installed.value = mapOf("pkg" to "source")
        testScheduler.runCurrent()
        emitted shouldBe emptyList()
        ready.value = true
        testScheduler.runCurrent()
        emitted shouldBe listOf(listOf("source"))
        installed.value = emptyMap()
        testScheduler.runCurrent()
        emitted shouldBe listOf(listOf("source"), emptyList())
        job.cancel()
    }

    @Test
    fun `empty completed installation still initializes source registry`() = runTest {
        val installed = MutableStateFlow<Map<String, String>>(emptyMap())
        val ready = MutableStateFlow(true)
        val emitted = mutableListOf<List<String>>()
        backgroundScope.launch { installed.afterInitialization(ready).collect { emitted += it } }
        testScheduler.runCurrent()
        emitted shouldBe listOf(emptyList())
    }
}
