package eu.kanade.tachiyomi.extension.model

import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.junit.jupiter.api.Test

class ExtensionMatchingTest {
    private val installed = Extension.Installed("Test", "pkg", "1", 1, 1.4, "en", false, "trusted", pkgFactory = null, sources = emptyList(), icon = null, isShared = false)
    private fun available(signature: String, version: Long = 2) = Extension.Available("Test", "pkg", "2", version, 1.4, "en", false, signature, "Store", emptyList(), "", "", mockk())

    @Test
    fun `matching uses package and signature and selects highest version`() {
        val trusted = available("trusted")
        val newer = available("trusted", 3)
        val wrongSignature = available("other", 9)
        listOf(trusted, wrongSignature, newer).findMatchingExtension(installed) shouldBe newer
        installed.hasUpdateFrom(newer) shouldBe true
        installed.hasUpdateFrom(wrongSignature) shouldBe false
        installed.hasUpdateFrom(trusted.copy(versionCode = 1)) shouldBe false
        installed.hasUpdateFrom(trusted.copy(pkgName = "different")) shouldBe false
    }

    @Test
    fun `metadata fallback never authorizes an unsigned update`() {
        val fallback = available("other")
        listOf(fallback).findMatchingExtension(installed) shouldBe null
        listOf(fallback).findMatchingExtension(installed, allowSignatureFallback = true) shouldBe fallback
        installed.hasUpdateFrom(fallback) shouldBe false
        listOf(fallback, available("third")).findMatchingExtension(installed, allowSignatureFallback = true) shouldBe null
        emptyList<Extension.Available>().findMatchingExtension(installed, allowSignatureFallback = true) shouldBe null
    }
}
