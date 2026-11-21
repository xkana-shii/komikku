package eu.kanade.tachiyomi.data.sync

import eu.kanade.tachiyomi.data.backup.models.BackupManga
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga

/**
 * Unified manga tombstones are observed only as absence from a full v2 snapshot. These tests pin
 * the two guards that make that safe: a delta or a v1 response must never delete, and a row the
 * server still returns as a non-favourite must never delete.
 */
class SyncMangaDeletionsTest {

    private fun localManga(id: Long, source: Long, url: String, favorite: Boolean) = Manga.create().copy(
        id = id,
        source = source,
        url = url,
        favorite = favorite,
    )

    private fun remoteManga(source: Long, url: String, favorite: Boolean) = BackupManga(
        source = source,
        url = url,
        favorite = favorite,
    )

    @Test
    fun `a full v2 library snapshot may delete`() {
        assertTrue(canApplySnapshotDeletions(full = true, protocolV2 = true, libraryEntries = true))
    }

    @Test
    fun `a delta never deletes`() {
        assertFalse(canApplySnapshotDeletions(full = false, protocolV2 = true, libraryEntries = true))
    }

    @Test
    fun `a v1 response never deletes`() {
        assertFalse(canApplySnapshotDeletions(full = true, protocolV2 = false, libraryEntries = true))
    }

    @Test
    fun `library sync disabled never deletes`() {
        assertFalse(canApplySnapshotDeletions(full = true, protocolV2 = true, libraryEntries = false))
    }

    @Test
    fun `a local favourite absent from the snapshot is returned`() {
        val local = listOf(
            localManga(id = 7L, source = 1L, url = "gone", favorite = true),
            localManga(id = 8L, source = 1L, url = "kept", favorite = true),
        )
        val uploaded = listOf(
            remoteManga(source = 1L, url = "gone", favorite = true),
            remoteManga(source = 1L, url = "kept", favorite = true),
        )
        val remote = listOf(remoteManga(source = 1L, url = "kept", favorite = true))

        assertEquals(
            listOf(7L),
            mangaIdsMissingFromFullSnapshot(local, remote, uploaded, fullSnapshot = true),
        )
    }

    @Test
    fun `a local favourite still present as a remote non-favourite is not returned`() {
        val local = listOf(localManga(id = 7L, source = 1L, url = "unfavorited", favorite = true))
        val uploaded = listOf(remoteManga(source = 1L, url = "unfavorited", favorite = true))
        val remote = listOf(remoteManga(source = 1L, url = "unfavorited", favorite = false))

        assertEquals(
            emptyList<Long>(),
            mangaIdsMissingFromFullSnapshot(local, remote, uploaded, fullSnapshot = true),
        )
    }

    @Test
    fun `a local non-favourite absent from the snapshot is not returned`() {
        val local = listOf(localManga(id = 7L, source = 1L, url = "not-in-library", favorite = false))
        val uploaded = listOf(
            remoteManga(source = 1L, url = "not-in-library", favorite = true),
            remoteManga(source = 1L, url = "something-else", favorite = true),
        )
        val remote = listOf(remoteManga(source = 1L, url = "something-else", favorite = true))

        assertEquals(
            emptyList<Long>(),
            mangaIdsMissingFromFullSnapshot(local, remote, uploaded, fullSnapshot = true),
        )
    }

    @Test
    fun `an empty snapshot deletes nothing`() {
        val local = listOf(
            localManga(id = 7L, source = 1L, url = "a", favorite = true),
            localManga(id = 8L, source = 2L, url = "b", favorite = true),
        )
        val uploaded = listOf(
            remoteManga(source = 1L, url = "a", favorite = true),
            remoteManga(source = 2L, url = "b", favorite = true),
        )

        assertEquals(
            emptyList<Long>(),
            mangaIdsMissingFromFullSnapshot(local, emptyList(), uploaded, fullSnapshot = true),
        )
    }

    @Test
    fun `a snapshot containing every local favourite returns nothing`() {
        val local = listOf(localManga(id = 7L, source = 1L, url = "kept", favorite = true))
        val uploaded = listOf(remoteManga(source = 1L, url = "kept", favorite = true))
        val remote = listOf(remoteManga(source = 1L, url = "kept", favorite = true))

        assertEquals(
            emptyList<Long>(),
            mangaIdsMissingFromFullSnapshot(local, remote, uploaded, fullSnapshot = true),
        )
    }

    @Test
    fun `a non-snapshot response returns nothing even when the local library is absent from it`() {
        val local = listOf(localManga(id = 7L, source = 1L, url = "a", favorite = true))
        val uploaded = listOf(
            remoteManga(source = 1L, url = "a", favorite = true),
            remoteManga(source = 1L, url = "other", favorite = true),
        )
        val remote = listOf(remoteManga(source = 1L, url = "other", favorite = true))

        assertEquals(
            emptyList<Long>(),
            mangaIdsMissingFromFullSnapshot(local, remote, uploaded, fullSnapshot = false),
        )
    }

    @Test
    fun `a favourite never uploaded is not deleted`() {
        // Added to the library after the upload was built: the server cannot have tombstoned it.
        val local = listOf(localManga(id = 7L, source = 1L, url = "added-mid-sync", favorite = true))
        val uploaded = listOf(remoteManga(source = 1L, url = "other", favorite = true))
        val remote = listOf(remoteManga(source = 1L, url = "other", favorite = true))

        assertEquals(
            emptyList<Long>(),
            mangaIdsMissingFromFullSnapshot(local, remote, uploaded, fullSnapshot = true),
        )
    }

    @Test
    fun `a favourite absent from the snapshot but present in the upload is returned`() {
        // The upload carried it and the server's full snapshot omits it: a real server tombstone.
        val local = listOf(localManga(id = 7L, source = 1L, url = "gone", favorite = true))
        val uploaded = listOf(
            remoteManga(source = 1L, url = "gone", favorite = true),
            remoteManga(source = 1L, url = "other", favorite = true),
        )
        val remote = listOf(remoteManga(source = 1L, url = "other", favorite = true))

        assertEquals(
            listOf(7L),
            mangaIdsMissingFromFullSnapshot(local, remote, uploaded, fullSnapshot = true),
        )
    }

    @Test
    fun `a full v2 response with history sync on may prune history`() {
        assertTrue(
            canApplySnapshotHistoryPrune(
                full = true,
                protocolV2 = true,
                libraryEntries = true,
                historyEntries = true,
            ),
        )
    }

    @Test
    fun `a delta response never prunes history`() {
        // Its history sections only carry the entries that changed.
        assertFalse(
            canApplySnapshotHistoryPrune(
                full = false,
                protocolV2 = true,
                libraryEntries = true,
                historyEntries = true,
            ),
        )
    }

    @Test
    fun `a v1 response never prunes history`() {
        assertFalse(
            canApplySnapshotHistoryPrune(
                full = true,
                protocolV2 = false,
                libraryEntries = true,
                historyEntries = true,
            ),
        )
    }

    @Test
    fun `history sync disabled never prunes history`() {
        assertFalse(
            canApplySnapshotHistoryPrune(
                full = true,
                protocolV2 = true,
                libraryEntries = true,
                historyEntries = false,
            ),
        )
    }

    @Test
    fun `library sync disabled never prunes history`() {
        // A disabled library uploads no manga, so the response's history sections answer for rows
        // this device never sent.
        assertFalse(
            canApplySnapshotHistoryPrune(
                full = true,
                protocolV2 = true,
                libraryEntries = false,
                historyEntries = true,
            ),
        )
    }

    @Test
    fun `an upload that lost the version merge does not let the history list be read as complete`() {
        // Pre-1.7.3 servers merge history inside the manga payload, so a server copy that won the
        // version comparison carries the server's history section, not ours.
        assertFalse(
            uploadedMangaWonMerge(
                uploaded = backupManga(version = 8, modifiedAt = 800),
                remote = backupManga(version = 9, modifiedAt = 900),
            ),
        )
        assertFalse(
            uploadedMangaWonMerge(
                uploaded = backupManga(version = 9, modifiedAt = 900),
                remote = backupManga(version = 9, modifiedAt = 901),
            ),
        )
    }

    @Test
    fun `an upload that won the merge, or tied it, may prune`() {
        assertTrue(
            uploadedMangaWonMerge(
                uploaded = backupManga(version = 9, modifiedAt = 800),
                remote = backupManga(version = 8, modifiedAt = 900),
            ),
        )
        // equal version and an equal timestamp is a tie the upload keeps
        assertTrue(
            uploadedMangaWonMerge(
                uploaded = backupManga(version = 9, modifiedAt = 900),
                remote = backupManga(version = 9, modifiedAt = 900),
            ),
        )
        assertTrue(
            uploadedMangaWonMerge(
                uploaded = backupManga(version = 9, modifiedAt = 900),
                remote = backupManga(version = 9, modifiedAt = 800),
            ),
        )
    }

    private fun backupManga(version: Long, modifiedAt: Long) = BackupManga(
        source = 1L,
        url = "/m",
        title = "/m",
        version = version,
        lastModifiedAt = modifiedAt,
    )
}
