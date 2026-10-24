package mihon.core.migration.migrations

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import eu.kanade.domain.sync.SyncStateBumper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.domain.manga.model.MangaUserInfoUpdate
import tachiyomi.domain.manga.repository.MangaRepository
import java.io.File

/**
 * SY-specific file
 *
 * Moves the user-edited manga details out of edits.json and into the manga row itself. Runs once:
 * every entry is written to the database and the file is deleted, so later starts are a no-op.
 */
@Inject
@ContributesIntoSet(AppScope::class)
class CustomInfoJsonToDbMigration(
    private val context: Context,
    private val mangaRepository: MangaRepository,
    // KUN -->
    // Imported overrides are a local edit: without the bump the payload would go out with an
    // unchanged version and lose the server's version-first merge.
    private val syncStateBumper: SyncStateBumper,
    // KUN <--
) : Migration {
    override val version: Float = 84f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withContext(Dispatchers.IO) {
        val editJson = File(context.getExternalFilesDir(null), "edits.json")
        if (editJson.exists() && editJson.isFile) {
            val json = try {
                Json.decodeFromString<MangaList>(
                    editJson.bufferedReader().use { it.readText() },
                )
            } catch (e: Exception) {
                // KUN -->
                // A decode failure means the only copy of the user's edits is this file (the store
                // that wrote it truncated it in place). Keep it under a name nothing reads, so it
                // can be repaired by hand, instead of deleting it.
                editJson.renameTo(File(editJson.parentFile, "edits.json.import-failed"))
                return@withContext true
                // KUN <--
            }
            val updates = json.mangas?.mapNotNull { mangaJson ->
                mangaJson.id?.let { id ->
                    MangaUserInfoUpdate(
                        id = id,
                        title = mangaJson.title?.takeUnless { it.isBlank() },
                        author = mangaJson.author,
                        artist = mangaJson.artist,
                        thumbnailUrl = mangaJson.thumbnailUrl,
                        description = mangaJson.description,
                        genre = mangaJson.genre,
                        status = mangaJson.status?.takeUnless { it == 0L },
                    )
                }
            }
            if (!updates.isNullOrEmpty()) {
                // KUN -->
                // Throws on a failed write; the file is only deleted below once everything landed, so
                // the next start retries.
                mangaRepository.updateUserInfoAll(updates)
                updates.forEach { syncStateBumper.bumpForCustomInfoEdit(it.id, null, it) }
                // KUN <--
            }
            editJson.delete()
        }
        true
    }

    @Serializable
    private data class MangaList(
        val mangas: List<MangaJson>? = null,
    )

    @Serializable
    private data class MangaJson(
        val id: Long? = null,
        val title: String? = null,
        val author: String? = null,
        val artist: String? = null,
        val thumbnailUrl: String? = null,
        val description: String? = null,
        val genre: List<String>? = null,
        val status: Long? = null,
    )
}
