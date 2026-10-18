package eu.kanade.tachiyomi.extension.model

import io.kotest.matchers.shouldBe
import mihon.domain.extension.model.ContentWarning
import mihon.domain.extension.model.ExtensionStore
import org.junit.jupiter.api.Test

class ExtensionMatchingTest {

    private val installed = Extension.Loaded(
        name = "Test",
        pkgName = "pkg",
        versionName = "1",
        versionCode = 1L,
        libVersion = 1.4,
        lang = "en",
        contentWarning = ContentWarning.SAFE,
        isShared = false,
        signatures = listOf("trusted"),
        pkgFactory = null,
        sources = emptyList(),
    )

    private fun store(signature: String) = ExtensionStore(
        indexUrl = "https://example.com/$signature/index.min.json",
        name = "Test",
        badgeLabel = "",
        signingKey = signature,
        contact = ExtensionStore.Contact(
            website = "https://example.com",
            discord = null,
        ),
        isLegacy = false,
        extensionListUrl = null,
    )

    private fun available(
        signature: String,
        version: Long = 2L,
        libVersion: Double = 1.4,
        pkgName: String = "pkg",
    ) = Extension.Available(
        name = "Test",
        pkgName = pkgName,
        versionName = version.toString(),
        versionCode = version,
        libVersion = libVersion,
        lang = "en",
        contentWarning = ContentWarning.SAFE,
        sources = emptyList(),
        apkUrl = "",
        iconUrl = "",
        store = store(signature),
    )

    @Test
    fun `matching uses package and signature and selects highest version`() {
        val trusted = available("trusted")
        val newer = available("trusted", version = 3L)
        val wrongSignature = available("other", version = 9L)

        installed.findListing(
            listOf(trusted, wrongSignature, newer),
        ) shouldBe newer

        installed.findUpdate(
            listOf(trusted, wrongSignature, newer),
        ) shouldBe newer

        installed.findUpdate(
            listOf(wrongSignature),
        ) shouldBe null

        installed.findUpdate(
            listOf(trusted.copy(versionCode = 1L)),
        ) shouldBe null

        installed.findUpdate(
            listOf(available("trusted", pkgName = "different")),
        ) shouldBe null
    }

    @Test
    fun `untrusted stores cannot supply an update`() {
        val untrusted = available("other", version = 10L)

        installed.findListing(listOf(untrusted)) shouldBe null
        installed.findUpdate(listOf(untrusted)) shouldBe null
    }

    @Test
    fun `trusted extension without a newer version has no update`() {
        val sameVersion = available("trusted", version = 1L)
        val olderVersion = available("trusted", version = 0L)

        installed.findListing(
            listOf(sameVersion, olderVersion),
        ) shouldBe sameVersion

        installed.findUpdate(
            listOf(sameVersion, olderVersion),
        ) shouldBe null
    }

    @Test
    fun `newer compatible library version can count as an update`() {
        val newerLibrary = available(
            signature = "trusted",
            version = 1L,
            libVersion = 1.5,
        )

        installed.findUpdate(listOf(newerLibrary)) shouldBe newerLibrary
    }

    @Test
    fun `empty extension list produces no listing or update`() {
        installed.findListing(emptyList()) shouldBe null
        installed.findUpdate(emptyList()) shouldBe null
    }
}
