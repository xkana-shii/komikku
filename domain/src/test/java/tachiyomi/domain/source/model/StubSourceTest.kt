package tachiyomi.domain.source.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.CONCURRENT)
class StubSourceTest {

    @Test
    fun `uses the name and the uppercased language when both are known`() {
        StubSource(id = 42L, lang = "en", name = "MangaDex").toString() shouldBe "MangaDex (EN)"
    }

    @Test
    fun `uses the name alone when the language is unknown`() {
        // A stub restored from a backup has a name but no language, since BackupSource carries only
        // the name. The id must not be shown instead.
        StubSource(id = 4024736764982024684L, lang = "", name = "MangaDex").toString() shouldBe "MangaDex"
    }

    @Test
    fun `falls back to the id when the name is unknown`() {
        StubSource(id = 4024736764982024684L, lang = "", name = "").toString() shouldBe "4024736764982024684"
        StubSource(id = 4024736764982024684L, lang = "en", name = "").toString() shouldBe "4024736764982024684"
    }
}
