// KUN -->
package mihon.core.migration.migrations

import android.content.Context
import eu.kanade.domain.sync.SyncStateBumper
import io.kotest.matchers.shouldBe
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.MangaUserInfoUpdate
import tachiyomi.domain.manga.repository.MangaRepository
import java.nio.file.Files
import java.nio.file.Path

class CustomInfoJsonToDbMigrationTest {

    private val mangaRepository = mockk<MangaRepository>(relaxed = true)
    private val syncStateBumper = mockk<SyncStateBumper>(relaxed = true)
    private val context = mockk<Context>()

    /**
     * The repository is a mock because the migration needs a `Context` for the app's external files
     * dir; the database write it delegates to is covered against a real driver in
     * `tachiyomi.data.MangaUserOverridesTest`.
     */
    private fun migrationIn(dir: Path): CustomInfoJsonToDbMigration {
        every { context.getExternalFilesDir(null) } returns dir.toFile()
        return CustomInfoJsonToDbMigration(context, mangaRepository, syncStateBumper)
    }

    private fun newDir(): Path = Files.createTempDirectory("edits-json-migration")

    @Test
    fun `every entry of edits json is written to the database and the file is deleted`() {
        runBlocking {
            val dir = newDir()
            Files.writeString(
                dir.resolve("edits.json"),
                """
                {"mangas":[
                  {"id":1,"title":"Edited","author":"A","artist":"B",
                   "thumbnailUrl":"https://cover.example/1.jpg","description":"D",
                   "genre":["Action","Drama"],"status":3},
                  {"id":2,"title":"  ","status":0}
                ]}
                """.trimIndent(),
            )
            val updates = slot<List<MangaUserInfoUpdate>>()

            migrationIn(dir).invoke(mockk(relaxed = true)) shouldBe true

            coVerify(exactly = 1) { mangaRepository.updateUserInfoAll(capture(updates)) }
            updates.captured shouldBe listOf(
                MangaUserInfoUpdate(
                    id = 1L,
                    title = "Edited",
                    author = "A",
                    artist = "B",
                    thumbnailUrl = "https://cover.example/1.jpg",
                    description = "D",
                    genre = listOf("Action", "Drama"),
                    status = 3L,
                ),
                // A blank title and a zero status are not overrides, exactly like the JSON store.
                MangaUserInfoUpdate(
                    id = 2L,
                    title = null,
                    author = null,
                    artist = null,
                    thumbnailUrl = null,
                    description = null,
                    genre = null,
                    status = null,
                ),
            )
            // Imported overrides are a local edit: each one must bump the sync version, or the
            // upload goes out with an unchanged version and loses the server's merge.
            coVerify(exactly = 1) { syncStateBumper.bumpForCustomInfoEdit(1L, null, any()) }
            coVerify(exactly = 1) { syncStateBumper.bumpForCustomInfoEdit(2L, null, any()) }
            Files.exists(dir.resolve("edits.json")) shouldBe false
        }
    }

    @Test
    fun `an entry without an id is skipped so it cannot clear an unrelated manga`() {
        runBlocking {
            val dir = newDir()
            Files.writeString(
                dir.resolve("edits.json"),
                """{"mangas":[{"title":"No id"},{"id":7,"title":"Kept"}]}""",
            )
            val updates = slot<List<MangaUserInfoUpdate>>()

            migrationIn(dir).invoke(mockk(relaxed = true)) shouldBe true

            coVerify(exactly = 1) { mangaRepository.updateUserInfoAll(capture(updates)) }
            updates.captured.map { it.id } shouldBe listOf(7L)
            updates.captured.single().title shouldBe "Kept"
        }
    }

    @Test
    fun `a second run is a no-op once the file is gone`() {
        runBlocking {
            val dir = newDir()
            Files.writeString(dir.resolve("edits.json"), """{"mangas":[{"id":1,"title":"Edited"}]}""")
            val migration = migrationIn(dir)

            migration.invoke(mockk(relaxed = true)) shouldBe true
            // Nothing left to import: no further write, and the migration still reports success.
            migration.invoke(mockk(relaxed = true)) shouldBe true

            coVerify(exactly = 1) { mangaRepository.updateUserInfoAll(any()) }
        }
    }

    @Test
    fun `an unreadable file is discarded rather than retried forever`() {
        runBlocking {
            val dir = newDir()
            Files.writeString(dir.resolve("edits.json"), "not json")
            val migration = migrationIn(dir)

            migration.invoke(mockk(relaxed = true)) shouldBe true

            coVerify(exactly = 0) { mangaRepository.updateUserInfoAll(any()) }
            // Deleted, so the broken payload cannot fail the migration run again on every start.
            Files.exists(dir.resolve("edits.json")) shouldBe false
        }
    }
}
// KUN <--
