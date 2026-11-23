package eu.kanade.tachiyomi.data.sync

import app.cash.sqldelight.async.coroutines.await
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import eu.kanade.domain.connections.service.ConnectionsPreferences
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupDeletedItem
import eu.kanade.tachiyomi.data.backup.models.BackupFeed
import eu.kanade.tachiyomi.data.backup.models.BackupHistory
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupSavedSearch
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.data.Database
import tachiyomi.data.DatabaseBindings
import tachiyomi.data.category.CategoryRepositoryImpl
import tachiyomi.data.chapter.ChapterRepositoryImpl
import tachiyomi.data.history.HistoryRepositoryImpl
import tachiyomi.data.source.SavedSearchRepositoryImpl
import tachiyomi.data.sync.SyncRepositoryImpl
import tachiyomi.domain.category.interactor.PruneCategoryReferences
import tachiyomi.domain.category.service.CategoryIdPreferences
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.history.service.HistoryPreferences
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.upcoming.service.UpcomingPreferences
import tachiyomi.domain.updates.service.UpdatesPreferences

/**
 * The explicit tombstone list a SyncYomi v2 delta carries in `Backup.deletions` (field `11000`).
 *
 * The deletions run against the real repositories over an in-memory database rather than mocks, so
 * the foreign-key cascades, the soft history reset and the category reference pruning are the ones
 * the app actually performs. Only the preference store is a stand-in ([InMemoryPreferenceStore], a
 * production class), which leaves `PruneCategoryReferences` real too.
 */
class SyncDeletionsTest {

    private fun item(kind: String, key: String, seq: Long = 1, at: Long = 0) =
        BackupDeletedItem(kind = kind, key = key, seq = seq, at = at)

    private fun ByteArray.containsSequence(sequence: ByteArray): Boolean =
        indices.any { start -> sequence.indices.all { offset -> getOrNull(start + offset) == sequence[offset] } }

    // region serialisation

    @Test
    fun `a deletion survives a protobuf round trip at tag 11000`() {
        val backup = Backup(
            backupManga = listOf(BackupManga(source = 1, url = "/m", title = "/m")),
            deletions = listOf(item("manga", "1|/m", seq = 42)),
        )

        val bytes = ProtoBuf.encodeToByteArray(Backup.serializer(), backup)

        // (11000 shl 3) or 2, the varint field header of `repeated BackupDeletedItem deletions = 11000`
        assertTrue(bytes.containsSequence(byteArrayOf(0xC2.toByte(), 0xAF.toByte(), 0x05)))
        val decoded = ProtoBuf.decodeFromByteArray(Backup.serializer(), bytes)
        decoded.deletions shouldBe listOf(BackupDeletedItem(kind = "manga", key = "1|/m", seq = 42))
    }

    @Test
    fun `the server's field 4 is the tombstone's action time`() {
        // Hand-built wire bytes, so the number is pinned against SyncYomi's schema and not against
        // our own annotation: field 1 (kind) as "history", then `int64 at = 4` = 1234.
        // (4 shl 3) or 0 is 0x20, and 1234 encodes as the varint 0xD2 0x09.
        val payload = byteArrayOf(0x0A, 0x07) + "history".toByteArray() + byteArrayOf(0x20, 0xD2.toByte(), 0x09)

        val decoded = ProtoBuf.decodeFromByteArray(BackupDeletedItem.serializer(), payload)

        decoded.kind shouldBe "history"
        decoded.at shouldBe 1234
    }

    @Test
    fun `an old server without the list still feeds the snapshot fallback`(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            fixture.insertManga(id = 7L, sourceId = 1L, url = "/gone", favorite = true)
            val uploaded = listOf(
                BackupManga(source = 1, url = "/gone", title = "/gone", favorite = true),
                BackupManga(source = 1, url = "/kept", title = "/kept", favorite = true),
            )
            val remote = ProtoBuf.decodeFromByteArray(
                Backup.serializer(),
                ProtoBuf.encodeToByteArray(Backup.serializer(), Backup(backupManga = listOf(uploaded[1]))),
            )

            // a payload written before the field existed decodes with no tombstones at all
            remote.deletions.shouldBeEmpty()
            deletionPlan(remote.deletions).manga.shouldBeEmpty()
            // so the full-snapshot heuristic is the only thing left that can drop the removed manga
            mangaIdsMissingFromFullSnapshot(
                localManga = fixture.syncRepository.getAllManga(),
                remoteManga = remote.backupManga,
                uploadedManga = uploaded,
                fullSnapshot = true,
            ) shouldBe listOf(7L)
        } finally {
            fixture.close()
        }
    }

    // endregion

    // region parsing

    @Test
    fun `parses every kind this client applies`() {
        val plan = deletionPlan(
            listOf(
                item("manga", "1|/m"),
                // only the first separator splits: a url may hold one of its own
                item("manga", "3|/a|b"),
                item("chapter", "1|/m\u001f/m/ch1"),
                item("history", "2|/other\u001f/other/ch", at = 1_767_225_600),
                item("category", "uid:12"),
                item("category", "name:Reading"),
                item("saved_search", "q|1"),
            ),
        )

        plan.manga shouldBe listOf(1L to "/m", 3L to "/a|b")
        plan.chapters shouldBe listOf(ChapterTombstone(sourceId = 1, mangaUrl = "/m", chapterUrl = "/m/ch1"))
        plan.history shouldBe listOf(
            HistoryDeletion(
                chapter = ChapterTombstone(sourceId = 2, mangaUrl = "/other", chapterUrl = "/other/ch"),
                atSeconds = 1_767_225_600,
            ),
        )
        plan.categories shouldBe listOf(CategoryTombstone(uid = 12), CategoryTombstone(name = "Reading"))
        plan.savedSearches shouldBe listOf(SavedSearchTombstone(name = "q", sourceId = 1))
        plan.skippedKinds shouldBe emptySet()
    }

    @Test
    fun `a saved search key splits on its last separator`() {
        savedSearchKeyOf("q|1") shouldBe SavedSearchTombstone(name = "q", sourceId = 1)
        // the name may hold the separator itself; only the source id follows the last one
        savedSearchKeyOf("a|b|2") shouldBe SavedSearchTombstone(name = "a|b", sourceId = 2)
        savedSearchKeyOf("q") shouldBe null
        savedSearchKeyOf("|1") shouldBe null
        savedSearchKeyOf("q|") shouldBe null
        savedSearchKeyOf("q|source") shouldBe null
    }

    @Test
    fun `unreadable keys become no local deletions`(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            fixture.insertManga(id = 7L, sourceId = 1L, url = "/m")
            fixture.insertChapter(mangaId = 7L, chapterId = 107L, url = "/m/ch1")

            val plan = deletionPlan(
                listOf(
                    item("manga", "no-separator"),
                    item("manga", "1|"),
                    item("manga", "abc|/m"),
                    // a chapter key has to carry its chapter url too
                    item("chapter", "1|/m"),
                    item("history", "1|/m"),
                    item("category", "other:1"),
                    item("category", "uid:x"),
                    item("category", "name:"),
                    item("saved_search", "no-separator"),
                    item("saved_search", "q|source"),
                ),
            )

            plan.manga.shouldBeEmpty()
            plan.chapters.shouldBeEmpty()
            plan.history.shouldBeEmpty()
            plan.categories.shouldBeEmpty()
            plan.savedSearches.shouldBeEmpty()
            plan.skippedKinds shouldBe emptySet()

            fixture.apply(plan) shouldBe emptySet()

            // none of those keys named a row, so a matching local manga and chapter survive
            fixture.mangaIds() shouldBe listOf(7L)
            fixture.chapterIds() shouldBe listOf(107L)
        } finally {
            fixture.close()
        }
    }

    // endregion

    // region applying

    @Test
    fun `a manga tombstone deletes the row and its chapters and history with it`(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            fixture.insertManga(id = 7L, sourceId = 1L, url = "/m")
            fixture.insertChapter(mangaId = 7L, chapterId = 107L, url = "/m/ch1")
            fixture.insertHistory(mangaId = 7L, chapterId = 107L, historyId = 1007L)

            fixture.apply(deletionPlan(listOf(item("manga", "1|/m")))) shouldBe emptySet()

            fixture.mangaIds().shouldBeEmpty()
            // the foreign keys carry the children: no separate chapter or history call is needed
            fixture.chapterIds().shouldBeEmpty()
            fixture.historyIds().shouldBeEmpty()
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `a chapter tombstone deletes only that chapter`(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            fixture.insertManga(id = 7L, sourceId = 1L, url = "/m")
            fixture.insertChapter(mangaId = 7L, chapterId = 107L, url = "/m/ch1")
            fixture.insertChapter(mangaId = 7L, chapterId = 207L, url = "/m/ch2", index = 2)

            fixture.apply(deletionPlan(listOf(item("chapter", "1|/m\u001f/m/ch1")))) shouldBe emptySet()

            // the sibling chapter and the manga itself stay
            fixture.mangaIds() shouldBe listOf(7L)
            fixture.chapterIds() shouldBe listOf(207L)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `a history tombstone clears only that history entry`(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            fixture.insertManga(id = 7L, sourceId = 1L, url = "/m")
            fixture.insertChapter(mangaId = 7L, chapterId = 107L, url = "/m/ch1")
            fixture.insertChapter(mangaId = 7L, chapterId = 207L, url = "/m/ch2", index = 2)
            fixture.insertHistory(mangaId = 7L, chapterId = 107L, historyId = 1007L)
            fixture.insertHistory(mangaId = 7L, chapterId = 207L, historyId = 2007L)

            fixture.apply(deletionPlan(listOf(item("history", "1|/m\u001f/m/ch1")))) shouldBe emptySet()

            // the tombstone names a chapter, so the history id has to come from that chapter's row:
            // clearing 2007 instead of 1007 is the mistake a chapter/history id mix-up makes
            fixture.readAtOf(historyId = 1007L) shouldBe null
            fixture.readAtOf(historyId = 2007L) shouldBe 3000L
            // a peer's tombstone is already applied there, so it must not be queued back as one of
            // ours: that would keep re-sending a removal the server made
            fixture.pendingDeletionOf(historyId = 1007L) shouldBe 0L
            // a history tombstone removes no chapter and no manga
            fixture.chapterIds() shouldBe listOf(107L, 207L)
            fixture.mangaIds() shouldBe listOf(7L)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `a history tombstone older than the local read keeps the row`(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            fixture.insertManga(id = 7L, sourceId = 1L, url = "/m")
            fixture.insertChapter(mangaId = 7L, chapterId = 107L, url = "/m/ch1")
            // read at 8001 s, a whole second after the removal the peer sends at 8000 s
            fixture.insertHistory(mangaId = 7L, chapterId = 107L, historyId = 1007L, readAt = 8_001_000L)

            fixture.apply(
                deletionPlan(listOf(item("history", "1|/m\u001f/m/ch1", at = 8000))),
            ) shouldBe emptySet()

            // a read in a later second is one the server declines to delete too, so the row survives
            fixture.readAtOf(historyId = 1007L) shouldBe 8_001_000L
            // and a peer's tombstone applied there must not be queued back as one of ours
            fixture.pendingDeletionOf(historyId = 1007L) shouldBe 0L
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `a history tombstone newer than the local read removes the row`(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            fixture.insertManga(id = 7L, sourceId = 1L, url = "/m")
            fixture.insertChapter(mangaId = 7L, chapterId = 107L, url = "/m/ch1")
            fixture.insertChapter(mangaId = 7L, chapterId = 207L, url = "/m/ch2", index = 2)
            fixture.insertChapter(mangaId = 7L, chapterId = 307L, url = "/m/ch3", index = 3)
            // 7999.5 s, before the removal: read_at is milliseconds, so a comparison that fed those
            // milliseconds straight against the seconds `at` would keep this row
            fixture.insertHistory(mangaId = 7L, chapterId = 107L, historyId = 1007L, readAt = 7_999_500L)
            // half a second into the removal's own second: the server stores the read truncated to
            // 8000 s and drops the row, so keeping it here would leave a row only this device sees
            fixture.insertHistory(mangaId = 7L, chapterId = 207L, historyId = 2007L, readAt = 8_000_500L)
            // exactly on the boundary: the server applies a deletion to `modified_at <= at`
            fixture.insertHistory(mangaId = 7L, chapterId = 307L, historyId = 3007L, readAt = 8_000_000L)

            fixture.apply(
                deletionPlan(
                    listOf(
                        item("history", "1|/m\u001f/m/ch1", at = 8000),
                        item("history", "1|/m\u001f/m/ch2", at = 8000),
                        item("history", "1|/m\u001f/m/ch3", at = 8000),
                    ),
                ),
            ) shouldBe emptySet()

            fixture.readAtOf(historyId = 1007L) shouldBe null
            fixture.readAtOf(historyId = 2007L) shouldBe null
            fixture.readAtOf(historyId = 3007L) shouldBe null
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `a history tombstone without an action time removes the row`(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            fixture.insertManga(id = 7L, sourceId = 1L, url = "/m")
            fixture.insertChapter(mangaId = 7L, chapterId = 107L, url = "/m/ch1")
            // newer than any second a server could have recorded, so only the `at = 0` rule saves it
            fixture.insertHistory(mangaId = 7L, chapterId = 107L, historyId = 1007L, readAt = 9_000_000L)

            // a server that predates the field sends no time, and a tombstone recorded before it
            // existed decodes as 0: both keep the unconditional removal
            fixture.apply(deletionPlan(listOf(item("history", "1|/m\u001f/m/ch1")))) shouldBe emptySet()

            fixture.readAtOf(historyId = 1007L) shouldBe null
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `a history tombstone leaves an already queued removal alone`(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            fixture.insertManga(id = 7L, sourceId = 1L, url = "/m")
            fixture.insertChapter(mangaId = 7L, chapterId = 107L, url = "/m/ch1")
            fixture.insertHistory(mangaId = 7L, chapterId = 107L, historyId = 1007L)
            // removed here and queued: the row has no read left to compare
            fixture.historyRepository.removeHistory(queueForSync = true, historyIds = listOf(1007L))
            fixture.readAtOf(historyId = 1007L) shouldBe null
            fixture.pendingDeletionOf(historyId = 1007L) shouldBe 1L

            // the peer's tombstone must not drop the marker: our own removal still has to be delivered
            fixture.apply(
                deletionPlan(listOf(item("history", "1|/m\u001f/m/ch1", at = 8_000_000))),
            ) shouldBe emptySet()

            fixture.readAtOf(historyId = 1007L) shouldBe null
            fixture.pendingDeletionOf(historyId = 1007L) shouldBe 1L
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `a mixed page applies only the removals newer than their read`(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            fixture.insertManga(id = 7L, sourceId = 1L, url = "/m")
            fixture.insertChapter(mangaId = 7L, chapterId = 107L, url = "/m/ch1")
            fixture.insertChapter(mangaId = 7L, chapterId = 207L, url = "/m/ch2", index = 2)
            fixture.insertChapter(mangaId = 7L, chapterId = 307L, url = "/m/ch3", index = 3)
            fixture.insertChapter(mangaId = 7L, chapterId = 407L, url = "/m/ch4", index = 4)
            // read a second after its tombstone: kept
            fixture.insertHistory(mangaId = 7L, chapterId = 107L, historyId = 1007L, readAt = 8_001_000L)
            // read before its tombstone: removed
            fixture.insertHistory(mangaId = 7L, chapterId = 207L, historyId = 2007L, readAt = 7_000_000L)
            // no time from the server: removed
            fixture.insertHistory(mangaId = 7L, chapterId = 307L, historyId = 3007L, readAt = 8_001_000L)

            fixture.apply(
                deletionPlan(
                    listOf(
                        item("history", "1|/m\u001f/m/ch1", at = 8000),
                        item("history", "1|/m\u001f/m/ch2", at = 8000),
                        item("history", "1|/m\u001f/m/ch3"),
                        // a chapter tombstone in the same page is unrelated to the timing rule
                        item("chapter", "1|/m\u001f/m/ch4"),
                    ),
                ),
            ) shouldBe emptySet()

            fixture.readAtOf(historyId = 1007L) shouldBe 8_001_000L
            fixture.readAtOf(historyId = 2007L) shouldBe null
            fixture.readAtOf(historyId = 3007L) shouldBe null
            fixture.chapterIds() shouldBe listOf(107L, 207L, 307L)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `category tombstones resolve by uid and by name and prune the stale filter ids`(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            // is_syncing = 1 keeps the schema's uid trigger from replacing the explicit uids
            fixture.insertCategory(id = 3L, name = "Reading", order = 0L, uid = 12L)
            fixture.insertCategory(id = 4L, name = "Old name", order = 1L, uid = 13L)
            fixture.insertCategory(id = 5L, name = "Untouched", order = 2L, uid = 14L)
            // the library and History filters store local category ids; a deleted id must not linger
            fixture.libraryPreferences.filterCategoriesInclude.set(setOf("3", "5"))

            fixture.apply(
                deletionPlan(
                    listOf(
                        item("category", "uid:12"),
                        item("category", "name:Old name"),
                        // the system category has uid 0 and is never a tombstone target
                        item("category", "uid:0"),
                        item("category", "name:"),
                    ),
                ),
            ) shouldBe emptySet()

            // id 0 is the seeded system category and 5 was never tombstoned
            fixture.categoryIds() shouldBe listOf(0L, 5L)
            fixture.libraryPreferences.filterCategoriesInclude.get() shouldBe setOf("5")
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `several tombstones resolve their chapters in one batch read`(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            fixture.insertManga(id = 7L, sourceId = 1L, url = "/m")
            fixture.insertChapter(mangaId = 7L, chapterId = 107L, url = "/m/ch1")
            fixture.insertChapter(mangaId = 7L, chapterId = 207L, url = "/m/ch2", index = 2)
            fixture.insertChapter(mangaId = 7L, chapterId = 307L, url = "/m/ch3", index = 3)
            fixture.insertManga(id = 8L, sourceId = 2L, url = "/n")
            fixture.insertChapter(mangaId = 8L, chapterId = 108L, url = "/n/ch1")
            fixture.insertHistory(mangaId = 8L, chapterId = 108L, historyId = 1008L)
            fixture.insertManga(id = 9L, sourceId = 3L, url = "/p")
            fixture.insertChapter(mangaId = 9L, chapterId = 109L, url = "/p/ch1")
            fixture.insertHistory(mangaId = 9L, chapterId = 109L, historyId = 1009L)

            val skipped = fixture.apply(
                deletionPlan(
                    listOf(
                        item("chapter", "1|/m\u001f/m/ch1"),
                        item("chapter", "2|/n\u001f/n/ch1"),
                        item("history", "3|/p\u001f/p/ch1"),
                        // a key whose manga this device never had stays a no-op on the batch path
                        item("chapter", "4|/unknown\u001f/unknown/ch"),
                    ),
                ),
            )

            skipped shouldBe emptySet()
            // every tombstone above came out of one chapter query per run, never one per key
            fixture.chapterRepository.batchReads shouldBe 1
            fixture.chapterRepository.singleReads shouldBe 0
            // 107 and 108 were tombstoned, and 108's history entry cascaded with it
            fixture.chapterIds() shouldBe listOf(109L, 207L, 307L)
            fixture.readAtOf(historyId = 1008L) shouldBe null
            // the history tombstone cleared 1009 through chapter 109, which itself stays
            fixture.readAtOf(historyId = 1009L) shouldBe null
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `a tombstoned category is not resurrected by the merge built from the pre-deletion upload`(): Unit =
        runBlocking {
            val fixture = Fixture()
            try {
                fixture.insertCategory(id = 3L, name = "Reading", order = 0L, uid = 12L)
                fixture.insertCategory(id = 4L, name = "Kept", order = 1L, uid = 13L)
                // the upload snapshot the sync took before it applied the tombstones, plus the manga
                // change that makes the delta reach the restore branch at all
                val uploaded = listOf(
                    BackupCategory(name = "Reading", order = 0, id = 3, uid = 12),
                    BackupCategory(name = "Kept", order = 1, id = 4, uid = 13),
                )
                val remote = listOf(BackupCategory(name = "Kept", order = 1, id = 4, uid = 13))

                val plan = deletionPlan(listOf(item("category", "uid:12")))
                fixture.apply(plan) shouldBe emptySet()
                fixture.categoryIds() shouldBe listOf(0L, 4L)

                // the defect this guards: the raw upload snapshot still names the deleted category,
                // and the v2 merge keeps a local entry the remote deltas do not mention
                mergeSyncCategories(uploaded, remote, protocolV2 = true).map { it.name } shouldBe
                    listOf("Reading", "Kept")

                mergeSyncCategories(
                    withoutTombstonedCategories(uploaded, plan.categories),
                    remote,
                    protocolV2 = true,
                ).map { it.name } shouldBe listOf("Kept")
            } finally {
                fixture.close()
            }
        }

    @Test
    fun `a tombstone of a row this device never had is a no-op`(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            fixture.insertCategory(id = 3L, name = "Reading", order = 0L, uid = 12L)

            fixture.apply(
                deletionPlan(
                    listOf(
                        item("manga", "1|/unknown"),
                        item("chapter", "1|/unknown\u001f/unknown/ch"),
                        item("history", "1|/unknown\u001f/unknown/ch"),
                        item("category", "uid:404"),
                    ),
                ),
            ) shouldBe emptySet()

            fixture.mangaIds().shouldBeEmpty()
            fixture.categoryIds() shouldBe listOf(0L, 3L)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `section kinds have no local deletion and are reported as skipped`(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            fixture.insertManga(id = 7L, sourceId = 1L, url = "/m")

            val skipped = fixture.apply(deletionPlan(unappliedTombstoneKinds.map { item(it, "1|/m") }))

            skipped shouldBe unappliedTombstoneKinds
            // a settings or source tombstone must never reach a deletion path by accident, even when
            // its key happens to read as a manga key
            fixture.mangaIds() shouldBe listOf(7L)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `sections the user disabled are not deleted on the server's word`(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            // manga 7 carries the chapter tombstone, manga 8 the history one, manga 9 a chapter
            // tombstone whose chapter still holds a local history entry
            fixture.insertManga(id = 7L, sourceId = 1L, url = "/m")
            fixture.insertChapter(mangaId = 7L, chapterId = 107L, url = "/m/ch1")
            fixture.insertManga(id = 8L, sourceId = 1L, url = "/n")
            fixture.insertChapter(mangaId = 8L, chapterId = 108L, url = "/n/ch1")
            fixture.insertHistory(mangaId = 8L, chapterId = 108L, historyId = 1008L)
            fixture.insertManga(id = 9L, sourceId = 1L, url = "/p")
            fixture.insertChapter(mangaId = 9L, chapterId = 109L, url = "/p/ch1")
            fixture.insertHistory(mangaId = 9L, chapterId = 109L, historyId = 1009L)

            val skipped = fixture.apply(
                deletionPlan(
                    listOf(
                        item("manga", "1|/m"),
                        item("history", "1|/n\u001f/n/ch1"),
                        item("chapter", "1|/m\u001f/m/ch1"),
                        item("chapter", "1|/p\u001f/p/ch1"),
                    ),
                ),
                librarySyncEnabled = false,
                historySyncEnabled = false,
            )

            skipped shouldBe setOf("manga", "history", "chapter")
            fixture.mangaIds() shouldBe listOf(7L, 8L, 9L)
            fixture.readAtOf(historyId = 1008L) shouldBe 3000L
            // chapter 109 carries history 1009, and `history.chapter_id` cascades with the chapter
            // row: deleting the chapter with history sync off would wipe history the user does not
            // sync, so the tombstone is reported instead of applied
            fixture.readAtOf(historyId = 1009L) shouldBe 3000L
            // chapter 107 has no history entry, so its tombstone still stands on its own
            fixture.chapterIds() shouldBe listOf(108L, 109L)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `chapter tombstones are skipped when chapter sync is off`(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            fixture.insertManga(id = 7L, sourceId = 1L, url = "/m")
            fixture.insertChapter(mangaId = 7L, chapterId = 107L, url = "/m/ch1")

            val skipped = fixture.apply(
                deletionPlan(listOf(item("chapter", "1|/m\u001f/m/ch1"))),
                chaptersEnabled = false,
            )

            skipped shouldBe setOf("chapter")
            fixture.chapterIds() shouldBe listOf(107L)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `a removed history row does not hold a chapter back from a remote tombstone`(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            fixture.insertManga(id = 7L, sourceId = 1L, url = "/m")
            fixture.insertChapter(mangaId = 7L, chapterId = 107L, url = "/m/ch1")
            fixture.insertChapter(mangaId = 7L, chapterId = 207L, url = "/m/ch2", index = 2)
            fixture.insertHistory(mangaId = 7L, chapterId = 107L, historyId = 1007L)
            // what a removal leaves behind: the row stays, with no read date and queued for a
            // tombstone. Since "clear all history" went soft, this is every chapter ever read.
            fixture.historyRepository.removeHistory(queueForSync = true, historyIds = listOf(1007L))
            fixture.readAtOf(historyId = 1007L) shouldBe null
            // a live entry on the other chapter still protects it while history sync is off
            fixture.insertHistory(mangaId = 7L, chapterId = 207L, historyId = 2007L)

            val skipped = fixture.apply(
                deletionPlan(
                    listOf(
                        item("chapter", "1|/m\u001f/m/ch1"),
                        item("chapter", "1|/m\u001f/m/ch2"),
                    ),
                ),
                historySyncEnabled = false,
            )

            // the dead row is not history anything could destroy, so its chapter goes
            skipped shouldBe setOf("chapter")
            fixture.chapterIds() shouldBe listOf(207L)
            // the live entry did hold its chapter back, and is untouched
            fixture.readAtOf(historyId = 2007L) shouldBe 3000L
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `category tombstones are skipped when category sync is off`(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            fixture.insertCategory(id = 3L, name = "Reading", order = 0L, uid = 12L)

            val skipped = fixture.apply(
                deletionPlan(listOf(item("category", "uid:12"))),
                categoriesEnabled = false,
            )

            skipped shouldBe setOf("category")
            fixture.categoryIds() shouldBe listOf(0L, 3L)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `a saved search tombstone deletes the local row its key names`(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            fixture.insertSavedSearch(id = 1L, name = "Reading", sourceId = 7L)
            // the same name on another source is a different saved search
            fixture.insertSavedSearch(id = 2L, name = "Reading", sourceId = 8L)
            fixture.insertSavedSearch(id = 3L, name = "Other", sourceId = 7L)

            fixture.apply(deletionPlan(listOf(item("saved_search", "Reading|7")))) shouldBe emptySet()

            fixture.savedSearchIds() shouldBe listOf(2L, 3L)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `a saved search tombstone is skipped when the section is off`(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            fixture.insertSavedSearch(id = 1L, name = "Reading", sourceId = 7L)

            val skipped = fixture.apply(
                deletionPlan(listOf(item("saved_search", "Reading|7"))),
                savedSearchesEnabled = false,
            )

            skipped shouldBe setOf("saved_search")
            fixture.savedSearchIds() shouldBe listOf(1L)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `a saved search tombstone takes the feed that referenced it`(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            fixture.insertSavedSearch(id = 1L, name = "Reading", sourceId = 7L)
            fixture.insertSavedSearch(id = 2L, name = "Other", sourceId = 7L)
            fixture.insertFeed(id = 11L, sourceId = 7L, savedSearchId = 1L)
            fixture.insertFeed(id = 12L, sourceId = 7L, savedSearchId = 2L)
            fixture.insertFeed(id = 13L, sourceId = 7L, savedSearchId = null)

            fixture.apply(deletionPlan(listOf(item("saved_search", "Reading|7")))) shouldBe emptySet()

            // a feed whose saved search is gone has nothing left to show; only the feeds of the
            // other saved search and the plain source feed stay
            fixture.feedIds() shouldBe listOf(12L, 13L)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `a tombstoned saved search leaves the restore payload with its feed`() {
        val tombstones = deletionPlan(listOf(item("saved_search", "Reading|7"))).savedSearches
        val searches = listOf(
            BackupSavedSearch(name = "Reading", source = 7L),
            // the same name on another source is a different item and stays
            BackupSavedSearch(name = "Reading", source = 8L),
        )
        val feeds = listOf(
            BackupFeed(
                source = 7L,
                global = false,
                savedSearch = BackupSavedSearch(name = "Reading", source = 7L),
            ),
            BackupFeed(
                source = 8L,
                global = false,
                savedSearch = BackupSavedSearch(name = "Reading", source = 8L),
            ),
            // a feed without a saved search is untouched
            BackupFeed(source = 7L, global = true),
        )

        // FeedRestorer creates the saved search a feed references when it is missing, so a payload
        // that still names the tombstoned one would undo the deletion in the same run.
        withoutTombstonedSavedSearches(searches, tombstones).map { it.source } shouldBe listOf(8L)
        withoutFeedsOfTombstonedSavedSearches(feeds, tombstones).map { it.source to it.global } shouldBe
            listOf(8L to false, 7L to true)
    }

    @Test
    fun `a manga tombstone runs the download cleanup while the row still exists`(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            fixture.insertManga(id = 7L, sourceId = 1L, url = "/m")

            fixture.apply(deletionPlan(listOf(item("manga", "1|/m")))) shouldBe emptySet()

            // The hook is the only thing that can still reach the download directory (keyed by the
            // row's title and source) and the queued chapters, so it has to run before the row goes.
            fixture.cleanupCalls shouldBe listOf(1L to "/m")
            fixture.cleanupSawRow shouldBe listOf(true)
            fixture.mangaIds().shouldBeEmpty()
        } finally {
            fixture.close()
        }
    }

    // endregion

    // region full-snapshot history pruning

    @Test
    fun `a full v2 snapshot clears the history entries a manga no longer lists`(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            fixture.insertManga(id = 7L, sourceId = 1L, url = "/m")
            fixture.insertChapter(mangaId = 7L, chapterId = 107L, url = "/m/ch1")
            fixture.insertChapter(mangaId = 7L, chapterId = 207L, url = "/m/ch2")
            fixture.insertHistory(mangaId = 7L, chapterId = 107L, historyId = 1007L, readAt = 3000L)
            fixture.insertHistory(mangaId = 7L, chapterId = 207L, historyId = 2007L, readAt = 5000L)
            // a second manga the response does not carry at all: nothing is known about its history
            fixture.insertManga(id = 8L, sourceId = 1L, url = "/n")
            fixture.insertChapter(mangaId = 8L, chapterId = 108L, url = "/n/ch1")
            fixture.insertHistory(mangaId = 8L, chapterId = 108L, historyId = 1008L, readAt = 3000L)

            val remote = listOf(
                BackupManga(source = 1, url = "/m", title = "/m", history = listOf(BackupHistory("/m/ch2", 5000))),
            )

            fixture.historyToPrune(remote, uploadedManga = remote, syncStart = 9000) shouldBe listOf(1007L)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `a history entry read before the sync is pruned however far into the current second it lands`(): Unit =
        runBlocking {
            val fixture = Fixture()
            try {
                fixture.insertManga(id = 7L, sourceId = 1L, url = "/m")
                fixture.insertChapter(mangaId = 7L, chapterId = 107L, url = "/m/ch1")
                fixture.insertChapter(mangaId = 7L, chapterId = 307L, url = "/m/ch3")
                fixture.insertChapter(mangaId = 7L, chapterId = 407L, url = "/m/ch4")
                // read before the sync: absent from the response, so it is a removal
                fixture.insertHistory(mangaId = 7L, chapterId = 107L, historyId = 1007L, readAt = 3000L)
                // also before the sync (5000 s), but far above a bare "5000 >= 9000"-style comparison
                fixture.insertHistory(mangaId = 7L, chapterId = 307L, historyId = 3007L, readAt = 5_000_000L)
                // read as the sync started (same second, epoch ms): it may predate the server's answer
                fixture.insertHistory(mangaId = 7L, chapterId = 407L, historyId = 4007L, readAt = 9_000_000L)

                val remote = listOf(BackupManga(source = 1, url = "/m", title = "/m", history = emptyList()))

                // 5_000_000 ms is before the 9000 s sync start, so it is a deletion; only the row at
                // the sync's own second survives.
                fixture.historyToPrune(remote, uploadedManga = remote, syncStart = 9000) shouldBe
                    listOf(1007L, 3007L)
            } finally {
                fixture.close()
            }
        }

    @Test
    fun `a history entry read after the sync started survives the prune`(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            fixture.insertManga(id = 7L, sourceId = 1L, url = "/m")
            fixture.insertChapter(mangaId = 7L, chapterId = 107L, url = "/m/ch1")
            fixture.insertChapter(mangaId = 7L, chapterId = 307L, url = "/m/ch3")
            // read before the sync: absent from the response, so it is a removal
            fixture.insertHistory(mangaId = 7L, chapterId = 107L, historyId = 1007L, readAt = 3000L)
            // read as the sync started (same second, epoch ms): it may predate the server's answer
            fixture.insertHistory(mangaId = 7L, chapterId = 307L, historyId = 3007L, readAt = 9_000_000L)

            val remote = listOf(BackupManga(source = 1, url = "/m", title = "/m", history = emptyList()))

            fixture.historyToPrune(remote, uploadedManga = remote, syncStart = 9000) shouldBe listOf(1007L)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `a delta response and a disabled history section prune nothing`(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            fixture.insertManga(id = 7L, sourceId = 1L, url = "/m")
            fixture.insertChapter(mangaId = 7L, chapterId = 107L, url = "/m/ch1")
            fixture.insertHistory(mangaId = 7L, chapterId = 107L, historyId = 1007L, readAt = 3000L)

            val remote = listOf(BackupManga(source = 1, url = "/m", title = "/m", history = emptyList()))

            // a delta omits the entries that did not change, so an absent one means nothing
            fixture.historyToPrune(remote, remote, syncStart = 9000, full = false).shouldBeEmpty()
            // a device that does not sync history never uploaded the rows being compared
            fixture.historyToPrune(remote, remote, syncStart = 9000, historySyncEnabled = false).shouldBeEmpty()
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `a manga the upload never carried keeps its history`(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            // read without ever being added to the library: never uploaded, so the server's history
            // list for it answers for nothing this device sent
            fixture.insertManga(id = 8L, sourceId = 1L, url = "/read-only")
            fixture.insertChapter(mangaId = 8L, chapterId = 108L, url = "/read-only/ch1")
            fixture.insertHistory(mangaId = 8L, chapterId = 108L, historyId = 1008L, readAt = 3000L)

            val remote = listOf(BackupManga(source = 1, url = "/read-only", title = "/read-only"))
            val uploaded = listOf(BackupManga(source = 1, url = "/other", title = "/other"))

            fixture.historyToPrune(remote, uploadedManga = uploaded, syncStart = 9000).shouldBeEmpty()
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `a manga whose upload lost the version comparison keeps its history`(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            fixture.insertManga(id = 7L, sourceId = 1L, url = "/m")
            fixture.insertChapter(mangaId = 7L, chapterId = 107L, url = "/m/ch1")
            fixture.insertHistory(mangaId = 7L, chapterId = 107L, historyId = 1007L, readAt = 3000L)

            // The server's copy is newer, so on a pre-1.7.3 server its per-manga version merge kept
            // the server's history section and dropped ours: the empty list below says nothing about
            // the local read.
            val remote = listOf(
                BackupManga(source = 1, url = "/m", title = "/m", version = 9, lastModifiedAt = 900),
            )
            val uploaded = listOf(
                BackupManga(source = 1, url = "/m", title = "/m", version = 8, lastModifiedAt = 800),
            )

            fixture.historyToPrune(remote, uploadedManga = uploaded, syncStart = 9000).shouldBeEmpty()

            // The same version with a newer server timestamp also lost the merge.
            fixture.historyToPrune(
                listOf(BackupManga(source = 1, url = "/m", title = "/m", version = 9, lastModifiedAt = 901)),
                uploadedManga = listOf(
                    BackupManga(source = 1, url = "/m", title = "/m", version = 9, lastModifiedAt = 900),
                ),
                syncStart = 9000,
            ).shouldBeEmpty()

            // An equal version with an equal server timestamp is a tie the upload keeps, so it prunes.
            fixture.historyToPrune(
                listOf(BackupManga(source = 1, url = "/m", title = "/m", version = 9, lastModifiedAt = 900)),
                uploadedManga = listOf(
                    BackupManga(source = 1, url = "/m", title = "/m", version = 9, lastModifiedAt = 900),
                ),
                syncStart = 9000,
            ) shouldBe listOf(1007L)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `library sync off prunes no history`(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            fixture.insertManga(id = 7L, sourceId = 1L, url = "/m")
            fixture.insertChapter(mangaId = 7L, chapterId = 107L, url = "/m/ch1")
            fixture.insertHistory(mangaId = 7L, chapterId = 107L, historyId = 1007L, readAt = 3000L)

            val remote = listOf(BackupManga(source = 1, url = "/m", title = "/m", history = emptyList()))

            // a disabled library upload carries no manga at all, so nothing was compared against it
            fixture.historyToPrune(remote, remote, syncStart = 9000, libraryEntries = false).shouldBeEmpty()
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `an empty response prunes no history`(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            fixture.insertManga(id = 7L, sourceId = 1L, url = "/m")
            fixture.insertChapter(mangaId = 7L, chapterId = 107L, url = "/m/ch1")
            fixture.insertHistory(mangaId = 7L, chapterId = 107L, historyId = 1007L, readAt = 3000L)

            fixture.historyToPrune(emptyList(), emptyList(), syncStart = 9000).shouldBeEmpty()
        } finally {
            fixture.close()
        }
    }

    // endregion

    // region full-snapshot category pruning

    @Test
    fun `a full v2 snapshot deletes the category it omits and keeps the one it lists`(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            fixture.insertCategory(id = 3L, name = "Reading", order = 0L, uid = 12L)
            fixture.insertCategory(id = 4L, name = "Kept", order = 1L, uid = 13L)
            // the History/Library filters store local ids: an id that no longer exists must go too
            fixture.libraryPreferences.filterCategoriesInclude.set(setOf("3", "4"))
            // as does the library's "add to library" default, which the merge never resets itself
            fixture.libraryPreferences.defaultCategory.set(3)

            val uploaded = listOf(
                BackupCategory(name = "Reading", order = 0, id = 3, uid = 12),
                BackupCategory(name = "Kept", order = 1, id = 4, uid = 13),
            )
            val snapshot = listOf(BackupCategory(name = "Kept", order = 1, id = 4, uid = 13))

            fixture.pruneCategories(snapshot, uploaded) shouldBe listOf(3L)
            fixture.categoryIds() shouldBe listOf(0L, 4L)
            fixture.libraryPreferences.filterCategoriesInclude.get() shouldBe setOf("4")
            assertFalse(fixture.libraryPreferences.defaultCategory.isSet())
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `a category created after the upload survives a full v2 snapshot`(): Unit = runBlocking {
        val fixture = Fixture()
        try {
            fixture.insertCategory(id = 3L, name = "Reading", order = 0L, uid = 12L)
            fixture.insertCategory(id = 5L, name = "Created mid-sync", order = 1L, uid = 15L)

            // only "Reading" was uploaded, so the snapshot's silence about the other one is a race
            val uploaded = listOf(BackupCategory(name = "Reading", order = 0, id = 3, uid = 12))
            val snapshot = listOf(BackupCategory(name = "Reading", order = 0, id = 3, uid = 12))

            fixture.pruneCategories(snapshot, uploaded).shouldBeEmpty()
            fixture.categoryIds() shouldBe listOf(0L, 3L, 5L)
        } finally {
            fixture.close()
        }
    }

    // endregion

    // region the early return

    @Test
    fun `a delta whose only change is a tombstone is not skipped`() {
        val deletion = item("manga", "1|/m")

        assertFalse(
            skipRemoteResponse(
                ownBackup = false,
                protocolV2 = true,
                changed = false,
                deletions = listOf(deletion),
            ),
        )
        // nothing changed and nothing tombstoned: the restore can be skipped
        assertTrue(
            skipRemoteResponse(ownBackup = false, protocolV2 = true, changed = false, deletions = emptyList()),
        )
    }

    @Test
    fun `our own backup is skipped even when it carries a deletion`() {
        assertTrue(
            skipRemoteResponse(
                ownBackup = true,
                protocolV2 = true,
                changed = false,
                deletions = listOf(item("manga", "1|/m")),
            ),
        )
    }

    // endregion

    /** Real repositories over an in-memory database, plus the real report-path preference store. */
    private class Fixture {
        private val driver: SqlDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        private val database = DatabaseBindings.providesDatabase(driver)
        private val preferenceStore = InMemoryPreferenceStore()

        val syncRepository = SyncRepositoryImpl(database)
        val libraryPreferences = LibraryPreferences(preferenceStore)

        /** Counts the chapter reads a run makes, so the batch resolution path stays pinned. */
        val chapterRepository = CountingChapterRepository(ChapterRepositoryImpl(database))
        val historyRepository = HistoryRepositoryImpl(database)
        val savedSearchRepository = SavedSearchRepositoryImpl(database)
        private val categoryRepository = CategoryRepositoryImpl(database)

        /** Manga handed to the download-cleanup hook, in order, as `(sourceId, url)`. */
        val cleanupCalls = mutableListOf<Pair<Long, String>>()

        /** Whether the row the cleanup hook ran for was still in the database at that moment. */
        val cleanupSawRow = mutableListOf<Boolean>()
        private val pruneCategoryReferences = PruneCategoryReferences(
            categoryIdPreferences = CategoryIdPreferences(
                libraryPreferences = libraryPreferences,
                downloadPreferences = DownloadPreferences(preferenceStore),
                historyPreferences = HistoryPreferences(preferenceStore),
                updatesPreferences = UpdatesPreferences(preferenceStore),
                upcomingPreferences = UpcomingPreferences(preferenceStore),
                connectionsPreferences = ConnectionsPreferences(preferenceStore),
            ),
        )

        init {
            runBlocking {
                // The app's driver sets `isForeignKeyConstraintsEnabled = true` (AppBindings), and the
                // deletions rely on the cascades it enables; JdbcSqliteDriver leaves them off.
                driver.execute(null, "PRAGMA foreign_keys = ON", 0).await()
                Database.Schema.create(driver).await()
            }
        }

        fun close() = driver.close()

        /** The history entries a full v2 response would clear, gated exactly as the sync gates them. */
        suspend fun historyToPrune(
            remoteManga: List<BackupManga>,
            uploadedManga: List<BackupManga>,
            syncStart: Long,
            full: Boolean = true,
            protocolV2: Boolean = true,
            libraryEntries: Boolean = true,
            historySyncEnabled: Boolean = true,
        ): List<Long> = historyIdsMissingFromFullSnapshot(
            full = full,
            protocolV2 = protocolV2,
            libraryEntries = libraryEntries,
            historySyncEnabled = historySyncEnabled,
            syncStart = syncStart,
            remoteManga = remoteManga,
            uploadedManga = uploadedManga,
            syncRepository = syncRepository,
            historyRepository = historyRepository,
        )

        /** The categories a full v2 snapshot would delete, through the prune the sync runs. */
        suspend fun pruneCategories(
            snapshot: List<BackupCategory>,
            uploaded: List<BackupCategory>,
            requireUploaded: Boolean = true,
        ): List<Long> = pruneCategoriesMissingFromSnapshot(
            localCategories = categoryRepository.getAll(),
            snapshot = snapshot,
            uploaded = uploaded,
            requireUploaded = requireUploaded,
            categoryRepository = categoryRepository,
            pruneCategoryReferences = pruneCategoryReferences,
        ).map { it.id }

        suspend fun apply(
            plan: DeletionPlan,
            librarySyncEnabled: Boolean = true,
            historySyncEnabled: Boolean = true,
            chaptersEnabled: Boolean = true,
            categoriesEnabled: Boolean = true,
            savedSearchesEnabled: Boolean = true,
        ): Set<String> = applyDeletions(
            plan = plan,
            syncRepository = syncRepository,
            chapterRepository = chapterRepository,
            historyRepository = historyRepository,
            categoryRepository = categoryRepository,
            pruneCategoryReferences = pruneCategoryReferences,
            savedSearchRepository = savedSearchRepository,
            // Stand-in for DownloadManager.deleteManga: records the call and whether the row was
            // still there, then removes the row like the real path does.
            deleteManga = { manga ->
                cleanupCalls += manga.source to manga.url
                cleanupSawRow += manga.id in mangaIds()
                syncRepository.deleteManga(manga.id)
            },
            librarySyncEnabled = librarySyncEnabled,
            historySyncEnabled = historySyncEnabled,
            chaptersEnabled = chaptersEnabled,
            categoriesEnabled = categoriesEnabled,
            savedSearchesEnabled = savedSearchesEnabled,
        )

        fun mangaIds(): List<Long> = longs("SELECT id FROM manga ORDER BY id")

        fun chapterIds(): List<Long> = longs("SELECT id FROM chapter ORDER BY id")

        fun historyIds(): List<Long> = longs("SELECT id FROM history ORDER BY id")

        fun categoryIds(): List<Long> = longs("SELECT id FROM category ORDER BY id")

        fun savedSearchIds(): List<Long> = longs("SELECT _id FROM saved_search ORDER BY _id")

        fun feedIds(): List<Long> = longs("SELECT _id FROM feed_saved_search ORDER BY _id")

        fun readAtOf(historyId: Long): Long? =
            nullableLongs("SELECT read_at FROM history WHERE id = $historyId").firstOrNull()

        fun pendingDeletionOf(historyId: Long): Long? =
            nullableLongs("SELECT pending_deletion FROM history WHERE id = $historyId").firstOrNull()

        private fun longs(sql: String): List<Long> = nullableLongs(sql).filterNotNull()

        private fun nullableLongs(sql: String): List<Long?> = driver.executeQuery(
            null,
            sql,
            { cursor ->
                val values = mutableListOf<Long?>()
                while (cursor.next().value) values += cursor.getLong(0)
                QueryResult.Value<List<Long?>>(values.toList())
            },
            0,
        ).value

        suspend fun insertManga(id: Long, sourceId: Long, url: String, favorite: Boolean = false) {
            driver.execute(
                null,
                """
                INSERT INTO manga(
                    id, source_id, remote_url, remote_title, remote_status, remote_update_strategy,
                    remote_memo, user_notes, user_reader_flags, user_chapter_flags,
                    state_chapter_fetch_interval, state_initialized, remote_cover,
                    state_cover_last_modified, last_modified_at, version, is_syncing,
                    user_favorite_at, favorite_modified_at
                ) VALUES (
                    $id, $sourceId, '$url', 'Title', 0, 0,
                    '{}', '', 0, 0,
                    0, 1, NULL,
                    123, 10, 1, 0,
                    ${if (favorite) 1000 else "NULL"}, ${if (favorite) 500 else "NULL"}
                )
                """.trimIndent(),
                0,
            ).await()
        }

        suspend fun insertChapter(mangaId: Long, chapterId: Long, url: String, index: Int = 1) {
            driver.execute(
                null,
                """
                INSERT INTO chapter(
                    id, manga_id, remote_url, remote_name, remote_chapter_number,
                    remote_order, remote_memo, user_read, user_bookmark,
                    user_last_page_read, state_date_fetch,
                    remote_date_upload, last_modified_at, version, is_syncing
                ) VALUES (
                    $chapterId, $mangaId, '$url', 'Chapter $index', 1.0,
                    1, '{}', 0, 0,
                    0, 2000,
                    2000, 10, 1, 0
                )
                """.trimIndent(),
                0,
            ).await()
        }

        suspend fun insertHistory(mangaId: Long, chapterId: Long, historyId: Long, readAt: Long? = 3000L) {
            driver.execute(
                null,
                "INSERT INTO history(id, chapter_id, manga_id, read_at, read_duration) " +
                    "VALUES ($historyId, $chapterId, $mangaId, ${readAt ?: "NULL"}, 60)",
                0,
            ).await()
        }

        suspend fun insertCategory(id: Long, name: String, order: Long, uid: Long) {
            driver.execute(
                null,
                "INSERT INTO category(id, name, `order`, flags, hidden, version, uid, last_modified_at, is_syncing) " +
                    "VALUES ($id, '$name', $order, 0, 0, 0, $uid, 0, 1)",
                0,
            ).await()
        }

        suspend fun insertSavedSearch(id: Long, name: String, sourceId: Long) {
            driver.execute(
                null,
                "INSERT INTO saved_search(_id, source, name, query, filters_json) " +
                    "VALUES ($id, $sourceId, '$name', NULL, NULL)",
                0,
            ).await()
        }

        suspend fun insertFeed(id: Long, sourceId: Long, savedSearchId: Long?) {
            driver.execute(
                null,
                "INSERT INTO feed_saved_search(_id, source, saved_search, global, feed_order) " +
                    "VALUES ($id, $sourceId, ${savedSearchId ?: "NULL"}, 0, 0)",
                0,
            ).await()
        }
    }

    /**
     * The real chapter repository with counters, so a test can tell a per-tombstone lookup from the
     * single batch read that resolves a whole delta.
     */
    private class CountingChapterRepository(
        private val delegate: ChapterRepository,
    ) : ChapterRepository by delegate {
        var batchReads = 0
            private set
        var singleReads = 0
            private set

        override suspend fun getChaptersByMangaIds(mangaIds: List<Long>): List<Chapter> {
            batchReads++
            return delegate.getChaptersByMangaIds(mangaIds)
        }

        override suspend fun getChapterByUrlAndMangaId(url: String, mangaId: Long): Chapter? {
            singleReads++
            return delegate.getChapterByUrlAndMangaId(url, mangaId)
        }
    }
}
