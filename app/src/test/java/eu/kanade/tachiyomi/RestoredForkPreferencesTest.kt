package eu.kanade.tachiyomi

import eu.kanade.tachiyomi.data.library.LibraryUpdateSourceExclusion
import eu.kanade.tachiyomi.source.online.all.EhCleanTitle
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import exh.source.ExhPreferences
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.domain.library.service.LibraryPreferences

// KNS -->
/** Library update source exclusions, E-Hentai clean titles and the 100-page preload, from the previous fork. */
class RestoredForkPreferencesTest {

    @Test
    fun `excluded sources keep the previous fork's key and persist several names`() {
        val store = InMemoryPreferenceStore()
        val preference = LibraryPreferences(store).updateSourcesExclude
        preference.key() shouldBe "library_update_sources_exclude"
        preference.get() shouldBe emptySet()
        preference.set(setOf("MangaDex (EN)", "Mangago"))
        preference.get() shouldBe setOf("MangaDex (EN)", "Mangago")
    }

    @Test
    fun `library updates skip every source whose name is excluded`() {
        val names = mapOf(1L to "MangaDex (EN)", 2L to "Mangago", 3L to "Comick", 4L to "4")
        LibraryUpdateSourceExclusion.excludedIds(names.keys, setOf("Mangago", "Comick"), names::getValue) shouldBe
            setOf(2L, 3L)
        LibraryUpdateSourceExclusion.excludedIds(names.keys, emptySet()) { error("not looked up") } shouldBe emptySet()
        // A source whose extension is missing is a stub without its old name: it is not skipped by mistake.
        LibraryUpdateSourceExclusion.excludedIds(listOf(4L), setOf("Mangago"), names::getValue) shouldBe emptySet()
    }

    @Test
    fun `the setting lists library sources and keeps excluded names that are gone`() {
        LibraryUpdateSourceExclusion.entries(listOf("b", "A", "b"), setOf("Old source")).keys.toList() shouldBe
            listOf("A", "b", "Old source")
    }

    @Test
    fun `clean title keeps its key and default and removes bracketed tags only when on`() {
        val preference = ExhPreferences(InMemoryPreferenceStore()).useCleanTitle
        preference.key() shouldBe "use_clean_title"
        preference.get() shouldBe true
        val title = "(C99) [Circle (Artist)] Some Title (Original) [English] {Translator}"
        EhCleanTitle.apply(title, enabled = true) shouldBe "Some Title"
        EhCleanTitle.apply(title, enabled = false) shouldBe title
        EhCleanTitle.apply("Plain title", enabled = true) shouldBe "Plain title"
    }

    @Test
    fun `100 pages can be stored as the preload amount without changing the default`() {
        val preference = ReaderPreferences(InMemoryPreferenceStore()).preloadSize
        preference.key() shouldBe "eh_preload_size"
        preference.get() shouldBe 10
        preference.set(100)
        preference.get() shouldBe 100
    }
}
// KNS <--
