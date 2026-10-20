package eu.kanade.tachiyomi.data.sync

import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.BackupExtensionStore
import eu.kanade.tachiyomi.data.backup.models.BackupFeed
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Ported from TachiyomiSY (jobobby04/TachiyomiSY#1648).
 */
class SyncDeltaTest {

    private fun manga(url: String, modifiedAt: Long, vararg chapters: BackupChapter) = BackupManga(
        source = 1,
        url = url,
        title = url,
        lastModifiedAt = modifiedAt,
        chapters = chapters.toList(),
    )

    private fun chapter(url: String, modifiedAt: Long) = BackupChapter(
        url = url,
        name = url,
        lastModifiedAt = modifiedAt,
    )

    @Test
    fun `keeps only what changed since the last upload`() {
        val mangas = listOf(
            manga("/old", 10, chapter("/old/1", 10)),
            manga("/touched", 50, chapter("/touched/1", 10), chapter("/touched/2", 50)),
            manga("/chapter-only", 10, chapter("/chapter-only/1", 60)),
            manga("/boundary", 40),
        )

        val delta = changedSince(mangas, since = 40)

        assertEquals(listOf("/touched", "/chapter-only", "/boundary"), delta.map { it.url })
        assertEquals(listOf("/touched/2"), delta[0].chapters.map { it.url })
        assertEquals(listOf("/chapter-only/1"), delta[1].chapters.map { it.url })
        assertTrue(delta[2].chapters.isEmpty())
    }

    @Test
    fun `everything is changed since zero`() {
        val mangas = listOf(manga("/a", 0, chapter("/a/1", 0)), manga("/b", 5))

        assertEquals(2, changedSince(mangas, since = 0).size)
        assertEquals(1, changedSince(mangas, since = 0)[0].chapters.size)
    }

    @Test
    fun `categories compare by content, not identity`() {
        val local = listOf(BackupCategory(name = "Reading", order = 0, id = 3, uid = 7, lastModifiedAt = 10))
        val same = listOf(BackupCategory(name = "Reading", order = 0, id = 9, uid = 7, lastModifiedAt = 99))

        assertFalse(categoriesDiffer(local, same))
        assertTrue(categoriesDiffer(local, listOf(BackupCategory(name = "Renamed", order = 0, uid = 7))))
        assertTrue(categoriesDiffer(local, listOf(BackupCategory(name = "Reading", order = 1, uid = 7))))
        assertTrue(categoriesDiffer(local, emptyList()))
    }

    // A v2 delta response can carry categories but no manga; that must decode as an empty list.
    @Test
    fun `decodes a response without manga`() {
        val bytes = ProtoBuf.encodeToByteArray(
            Backup.serializer(),
            Backup(backupCategories = listOf(BackupCategory(name = "Reading", uid = 7))),
        )

        val decoded = ProtoBuf.decodeFromByteArray(Backup.serializer(), bytes)
        assertTrue(decoded.backupManga.isEmpty())
        assertEquals("Reading", decoded.backupCategories.single().name)

        val empty = ProtoBuf.decodeFromByteArray(Backup.serializer(), ByteArray(0))
        assertTrue(empty.backupManga.isEmpty())
    }

    // The key must match the server's byte for byte, and JSON must not mangle the 0x1f separator.
    // Ported from TachiyomiSY (jobobby04/TachiyomiSY#1658).
    @Test
    fun `chapter tombstone key matches the server key and survives the JSON body`() {
        val key = chapterTombstoneKey(
            sourceId = 9999999999,
            mangaUrl = "/e2e/E2E Alpha/01",
            chapterUrl = "/e2e/E2E Alpha/01/ch01",
        )

        assertEquals(
            "9999999999|/e2e/E2E Alpha/01\u001f/e2e/E2E Alpha/01/ch01",
            key,
        )

        val oddKey = chapterTombstoneKey(
            sourceId = 7,
            mangaUrl = "/manga/t\"a\u0001",
            chapterUrl = "/c/x\n\t—",
        )
        val serializer = ListSerializer(String.serializer())
        assertEquals(
            listOf(oddKey),
            Json.decodeFromString(serializer, Json.encodeToString(serializer, listOf(oddKey))),
        )
    }

    // KUN -->
    @Test
    fun `a complete remote chapter list marks local-only chapters as removed`() {
        assertEquals(
            setOf("/c2"),
            missingRemoteChapterUrls(listOf("/c1", "/c2"), listOf("/c1")),
        )
        assertEquals(
            emptySet<String>(),
            missingRemoteChapterUrls(listOf("/c1"), listOf("/c1", "/c2")),
        )
    }

    @Test
    fun `mergeSyncCategories preserves local categories when v2 delta is empty`() {
        val local = listOf(
            BackupCategory(name = "Reading", order = 0, uid = 1),
            BackupCategory(name = "Completed", order = 1, uid = 2),
        )
        val remote = emptyList<BackupCategory>()

        val merged = mergeSyncCategories(local, remote, protocolV2 = true)

        assertEquals(2, merged.size)
        assertEquals(listOf("Reading", "Completed"), merged.map { it.name })
        assertEquals(listOf(0L, 1L), merged.map { it.order })
    }

    @Test
    fun `mergeSyncCategories merges remote updates into local by UID in v2`() {
        val local = listOf(
            BackupCategory(name = "Reading", order = 0, uid = 1),
            BackupCategory(name = "Plan to read", order = 1, uid = 2),
        )
        val remote = listOf(
            BackupCategory(name = "Reading Renamed", order = 0, uid = 1),
        )

        val merged = mergeSyncCategories(local, remote, protocolV2 = true)

        assertEquals(2, merged.size)
        assertEquals(listOf("Reading Renamed", "Plan to read"), merged.map { it.name })
    }

    @Test
    fun `mergeSyncCategories appends new remote categories in v2`() {
        val local = listOf(
            BackupCategory(name = "Reading", order = 0, uid = 1),
        )
        val remote = listOf(
            BackupCategory(name = "On Hold", order = 1, uid = 3),
        )

        val merged = mergeSyncCategories(local, remote, protocolV2 = true)

        assertEquals(2, merged.size)
        assertEquals(listOf("Reading", "On Hold"), merged.map { it.name })
    }

    @Test
    fun `mergeSyncCategories falls back to local in v1 when remote is empty`() {
        val local = listOf(
            BackupCategory(name = "Reading", order = 0, uid = 1),
        )
        val remote = emptyList<BackupCategory>()

        val merged = mergeSyncCategories(local, remote, protocolV2 = false)

        assertEquals(listOf("Reading"), merged.map { it.name })
    }

    @Test
    fun `mergeSyncCategories uses remote in v1 when remote is present`() {
        val local = listOf(
            BackupCategory(name = "Reading", order = 0, uid = 1),
        )
        val remote = listOf(
            BackupCategory(name = "Remote Category", order = 0, uid = 5),
        )

        val merged = mergeSyncCategories(local, remote, protocolV2 = false)

        assertEquals(listOf("Remote Category"), merged.map { it.name })
    }

    @Test
    fun `mergeSyncCategories deduplicates categories by normalized name and converges UIDs`() {
        val local = listOf(
            BackupCategory(name = "Action", order = 0, uid = 222222222),
        )
        // Remote has category with same name but different UID (from another device)
        val remote = listOf(
            BackupCategory(name = "action", order = 0, uid = 111111111),
        )

        val merged = mergeSyncCategories(local, remote, protocolV2 = true)

        assertEquals(1, merged.size)
        assertEquals("action", merged.single().name)
        assertEquals(111111111L, merged.single().uid)
    }

    @Test
    fun `mergeSyncCategories collapses duplicate remote categories with differing UIDs`() {
        val local = listOf(
            BackupCategory(name = "Action", order = 0, uid = 222222222),
        )
        // Remote from server has accumulated two categories with same name and different UIDs
        val remote = listOf(
            BackupCategory(name = "Action", order = 0, uid = 111111111),
            BackupCategory(name = "action", order = 1, uid = 222222222),
        )

        val merged = mergeSyncCategories(local, remote, protocolV2 = true)

        assertEquals(1, merged.size)
        assertEquals("Action".lowercase(), merged.single().name.lowercase())
    }

    @Test
    fun `encodes and decodes backup with extension stores and feeds`() {
        val backup = Backup(
            backupExtensionStores = listOf(
                BackupExtensionStore(
                    indexUrl = "https://example.com/repo/index.min.json",
                    name = "Example Repo",
                ),
            ),
            backupFeeds = listOf(
                BackupFeed(
                    source = 100L,
                    global = true,
                ),
            ),
        )
        val bytes = ProtoBuf.encodeToByteArray(Backup.serializer(), backup)
        val decoded = ProtoBuf.decodeFromByteArray(Backup.serializer(), bytes)

        assertEquals(1, decoded.backupExtensionStores.size)
        assertEquals("Example Repo", decoded.backupExtensionStores[0].name)
        assertEquals("https://example.com/repo/index.min.json", decoded.backupExtensionStores[0].indexUrl)

        assertEquals(1, decoded.backupFeeds.size)
        assertEquals(100L, decoded.backupFeeds[0].source)
        assertTrue(decoded.backupFeeds[0].global)
    }
    // KUN <--
}
