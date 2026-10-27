package eu.kanade.tachiyomi.data.sync

import eu.kanade.domain.sync.models.SyncSettings
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.BackupExtensionStore
import eu.kanade.tachiyomi.data.backup.models.BackupFeed
import eu.kanade.tachiyomi.data.backup.models.BackupHistory
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupTracking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga

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
    fun `a forced full sync uploads everything even when the service does not need it`() {
        assertTrue(shouldUploadFullLibrary(forceFull = true, serviceNeedsFullSync = false))
        assertTrue(shouldUploadFullLibrary(forceFull = true, serviceNeedsFullSync = true))
    }

    @Test
    fun `the service request alone still triggers a full upload`() {
        assertTrue(shouldUploadFullLibrary(forceFull = false, serviceNeedsFullSync = true))
        assertFalse(shouldUploadFullLibrary(forceFull = false, serviceNeedsFullSync = false))
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

    @Test
    fun `force push preserves disabled manga payload sections from the server`() {
        val remote = BackupManga(
            source = 1,
            url = "/m",
            history = listOf(BackupHistory(url = "/m/1", lastRead = 10)),
            tracking = listOf(BackupTracking(syncId = 2, title = "remote track")),
            categories = listOf(3),
            customTitle = "Remote title",
            customStatus = 2,
            customInfoIncluded = true,
        )
        val local = BackupManga(source = 1, url = "/m", title = "Local title")

        preserveForcePushSections(
            localManga = listOf(local),
            remoteManga = listOf(remote),
            settings = SyncSettings(
                categories = false,
                tracking = false,
                history = false,
                customInfo = false,
            ),
        )

        assertEquals(remote.history, local.history)
        assertEquals(remote.tracking, local.tracking)
        assertEquals(remote.categories, local.categories)
        assertEquals("Remote title", local.customTitle)
        assertEquals(2, local.customStatus)
        assertEquals(true, local.customInfoIncluded)
    }

    @Test
    fun `force push keeps local manga payload sections when they are enabled`() {
        val remote = BackupManga(
            source = 1,
            url = "/m",
            history = listOf(BackupHistory(url = "/m/1", lastRead = 10)),
            tracking = listOf(BackupTracking(syncId = 2, title = "remote track")),
            categories = listOf(3),
            customTitle = "Remote title",
        )
        val local = BackupManga(source = 1, url = "/m", title = "Local title")

        preserveForcePushSections(
            localManga = listOf(local),
            remoteManga = listOf(remote),
            settings = SyncSettings(),
        )

        assertTrue(local.history.isEmpty())
        assertTrue(local.tracking.isEmpty())
        assertTrue(local.categories.isEmpty())
        assertEquals(null, local.customTitle)
    }

    @Test
    fun `a force push raises manga, chapter and category versions in the upload`() {
        val category = BackupCategory(name = "Reading", uid = 7, version = 2, lastModifiedAt = 10)
        val backup = Backup(
            backupManga = listOf(
                BackupManga(
                    source = 1,
                    url = "/m",
                    version = 3,
                    lastModifiedAt = 10,
                    favoriteModifiedAt = 20,
                    chapters = listOf(BackupChapter(url = "/m/1", lastModifiedAt = 5)),
                ),
            ),
            backupCategories = listOf(category),
        )

        applyForcePushVersions(
            backup = backup,
            forceVersion = 1_700_000_000_000,
            forceModifiedAt = 1_600_000_000,
            forceFavoriteAt = 1_700_000_001,
            bumpCategories = true,
        )

        val manga = backup.backupManga.single()
        assertEquals(1_700_000_000_000L, manga.version)
        assertEquals(1_600_000_000L, manga.lastModifiedAt)
        assertEquals(1_700_000_001L, manga.favoriteModifiedAt)
        assertEquals(1_700_000_000_000L, manga.chapters.single().version)
        assertEquals(1_600_000_000L, manga.chapters.single().lastModifiedAt)
        assertEquals(1_700_000_000_000L, category.version)
        assertEquals(1_600_000_000L, category.lastModifiedAt)
    }

    @Test
    fun `a force push leaves categories alone when category sync is disabled`() {
        val category = BackupCategory(name = "Reading", uid = 7, version = 42, lastModifiedAt = 99)
        val backup = Backup(
            backupManga = listOf(BackupManga(source = 1, url = "/m", version = 9, lastModifiedAt = 99)),
            backupCategories = listOf(category),
        )

        applyForcePushVersions(
            backup = backup,
            forceVersion = 1_700_000_000_000,
            forceModifiedAt = 1_600_000_000,
            forceFavoriteAt = 1_700_000_001,
            bumpCategories = false,
        )

        // The upload carries the server's categories as-is; only the manga/chapter rows are raised.
        assertEquals(42L, category.version)
        assertEquals(99L, category.lastModifiedAt)
        assertEquals(1_700_000_000_000L, backup.backupManga.single().version)
        assertEquals(1_600_000_000L, backup.backupManga.single().lastModifiedAt)
    }

    @Test
    fun `clearing disabled sections keeps the enabled ones for the restore`() {
        val manga = BackupManga(
            source = 1,
            url = "/m",
            history = listOf(BackupHistory(url = "/m/1", lastRead = 10)),
            tracking = listOf(BackupTracking(syncId = 2, title = "track")),
            categories = listOf(3),
            customTitle = "Custom title",
            customArtist = "Artist",
            customAuthor = "Author",
            customThumbnailUrl = "https://example.com/cover.jpg",
            customDescription = "Description",
            customGenre = listOf("Action"),
            customStatus = 2,
            customInfoIncluded = true,
        )
        val backup = Backup(backupManga = listOf(manga))

        // History and custom info are disabled; tracking and categories are enabled.
        clearDisabledSectionsForRestore(
            backup,
            SyncSettings(history = false, tracking = true, customInfo = false, categories = true),
        )

        assertTrue(manga.history.isEmpty())
        assertEquals(listOf(BackupTracking(syncId = 2, title = "track")), manga.tracking)
        assertEquals(listOf(3L), manga.categories)
        assertEquals(null, manga.customTitle)
        assertEquals(null, manga.customArtist)
        assertEquals(null, manga.customAuthor)
        assertEquals(null, manga.customThumbnailUrl)
        assertEquals(null, manga.customDescription)
        assertEquals(null, manga.customGenre)
        assertEquals(0, manga.customStatus)
        assertEquals(false, manga.customInfoIncluded)
    }

    @Test
    fun `clearing nothing keeps every section`() {
        val manga = BackupManga(
            source = 1,
            url = "/m",
            history = listOf(BackupHistory(url = "/m/1", lastRead = 10)),
            tracking = listOf(BackupTracking(syncId = 2, title = "track")),
            customTitle = "Custom title",
            customStatus = 2,
            customInfoIncluded = true,
        )

        clearDisabledSectionsForRestore(Backup(backupManga = listOf(manga)), SyncSettings())

        assertEquals(1, manga.history.size)
        assertEquals(1, manga.tracking.size)
        assertEquals("Custom title", manga.customTitle)
        assertEquals(2, manga.customStatus)
        assertEquals(true, manga.customInfoIncluded)
    }

    @Test
    fun `an older payload that omits chapter flags and notes is not a change`() {
        val local = Manga.create().copy(chapterFlags = 5, notes = "local note", version = 10, lastModifiedAt = 100)
        // An older client never sent these fields, so they decode as defaults.
        val remote = BackupManga(source = 1, url = "/m", version = 10, lastModifiedAt = 100)

        assertFalse(mangaPayloadFieldsDiffer(local, remote))
    }

    @Test
    fun `a newer payload that clears chapter flags and notes is a change`() {
        val local = Manga.create().copy(chapterFlags = 5, notes = "local note", version = 10, lastModifiedAt = 100)
        val remote = BackupManga(source = 1, url = "/m", version = 11, lastModifiedAt = 100)

        assertTrue(mangaPayloadFieldsDiffer(local, remote))
    }

    @Test
    fun `a non-default payload field is a change even at the same version`() {
        val local = Manga.create().copy(chapterFlags = 0, notes = "", version = 10, lastModifiedAt = 100)
        val remote = BackupManga(
            source = 1,
            url = "/m",
            chapterFlags = 5,
            notes = "remote note",
            version = 10,
            lastModifiedAt = 50,
        )

        assertTrue(mangaPayloadFieldsDiffer(local, remote))
    }

    @Test
    fun `a different viewer flags field is always a change`() {
        val local = Manga.create().copy(viewerFlags = 1, version = 10)
        val remote = BackupManga(source = 1, url = "/m", viewer_flags = 2, version = 5)

        assertTrue(mangaPayloadFieldsDiffer(local, remote))
    }

    @Test
    fun `only a SyncYomi run resumes a pending force push`() {
        assertTrue(resumesPendingForcePush(pendingForcePush = true, isSyncYomi = true))
        assertFalse(resumesPendingForcePush(pendingForcePush = true, isSyncYomi = false))
        assertFalse(resumesPendingForcePush(pendingForcePush = false, isSyncYomi = true))
    }

    @Test
    fun `a missing snapshot only aborts an established server that needs preservation`() {
        assertTrue(
            forcePushMissingSnapshotIsFatal(
                needsSnapshot = true,
                snapshotAvailable = false,
                hadRemoteSync = true,
            ),
        )
        // A brand-new server has nothing to preserve.
        assertFalse(
            forcePushMissingSnapshotIsFatal(
                needsSnapshot = true,
                snapshotAvailable = false,
                hadRemoteSync = false,
            ),
        )
        // Nothing was disabled, so no snapshot was needed.
        assertFalse(
            forcePushMissingSnapshotIsFatal(
                needsSnapshot = false,
                snapshotAvailable = false,
                hadRemoteSync = true,
            ),
        )
        assertFalse(
            forcePushMissingSnapshotIsFatal(
                needsSnapshot = true,
                snapshotAvailable = true,
                hadRemoteSync = true,
            ),
        )
    }

    @Test
    fun `only client errors that cannot succeed by retrying are permanent force push failures`() {
        assertTrue(isPermanentForcePushFailure(400, permanent = false))
        assertTrue(isPermanentForcePushFailure(401, permanent = false))
        assertTrue(isPermanentForcePushFailure(403, permanent = false))
        assertTrue(isPermanentForcePushFailure(413, permanent = false))
        assertTrue(isPermanentForcePushFailure(422, permanent = false))
        // Request timeout and rate limiting are transient.
        assertFalse(isPermanentForcePushFailure(408, permanent = false))
        assertFalse(isPermanentForcePushFailure(429, permanent = false))
        // The server may have committed before a 5xx, and an unknown failure may be a lost response.
        assertFalse(isPermanentForcePushFailure(500, permanent = false))
        assertFalse(isPermanentForcePushFailure(null, permanent = false))
        assertTrue(isPermanentForcePushFailure(null, permanent = true))
        assertTrue(isPermanentForcePushFailure(413, permanent = true))
    }
    // KUN <--

    // SY -->
    // KUN -->
    private fun local(
        favorite: Boolean = true,
        version: Long = 3,
        chapters: List<Chapter> = listOf(Chapter.create().copy(url = "/c1", version = 2)),
        lastRead: Map<String, Long> = mapOf("/c1" to 100L),
    ) = LocalMangaState(
        manga = Manga.create().copy(favorite = favorite, version = version),
        chapters = chapters,
        categoryOrders = emptySet(),
        lastReadByChapterUrl = lastRead,
    )

    private fun remote(
        favorite: Boolean = true,
        version: Long = 3,
        history: List<BackupHistory> = listOf(BackupHistory(url = "/c1", lastRead = 100L)),
    ) = BackupManga(
        source = 1,
        url = "/m",
        favorite = favorite,
        version = version,
        chapters = listOf(BackupChapter(url = "/c1", version = 2)),
        history = history,
    )

    @Test
    fun `an unchanged manga needs no restore`() {
        assertFalse(needsRestore(local(), remote()))
    }

    @Test
    fun `a library removal from the server needs a restore`() {
        assertTrue(needsRestore(local(favorite = true), remote(favorite = false)))
    }

    @Test
    fun `a newer manga version needs a restore`() {
        assertTrue(needsRestore(local(version = 3), remote(version = 4)))
    }

    @Test
    fun `a chapter version change needs a restore`() {
        assertTrue(needsRestore(local(chapters = listOf(Chapter.create().copy(url = "/c1", version = 1))), remote()))
    }

    @Test
    fun `newer reading history alone needs a restore`() {
        assertTrue(needsRestore(local(), remote(history = listOf(BackupHistory(url = "/c1", lastRead = 200L)))))
    }

    @Test
    fun `older reading history needs no restore`() {
        assertFalse(needsRestore(local(), remote(history = listOf(BackupHistory(url = "/c1", lastRead = 50L)))))
    }
    // KUN <--
    // SY <--
}
