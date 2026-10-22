package eu.kanade.tachiyomi.ui.manga

import eu.kanade.domain.manga.interactor.SetExcludedScanlators
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.presentation.manga.components.ScanlatorPreferencesDraft
import eu.kanade.presentation.manga.components.ScanlatorPreferencesDraft.BlacklistItem
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.ScanlatorRangeRule
import tachiyomi.domain.chapter.model.parseScanlatorRangeRule
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate

// KNS -->
/**
 * The scanlator preferences dialog stores every edit at once: draft edits go through the ordered
 * writer and [ScanlatorPreferenceChanges] into the manga, and reopening the dialog shows them.
 */
class ScanlatorPreferencePersistenceTest {

    /** The stored manga and exclusions, updated like the repository would. */
    private var manga = Manga.create().copy(
        id = 1,
        scanlatorPriority = listOf("Delta", "Group A"),
        blacklistedChapters = listOf("url:/1", "url:/2", "url:/3"),
    )
    private var excluded = setOf("Group C")
    private val writes = mutableListOf<String>()

    private val updateManga = mockk<UpdateManga> {
        coEvery { await(any()) } answers {
            val update = firstArg<MangaUpdate>()
            if (update.isSet(MangaUpdate::scanlatorPriority)) manga = manga.copy(scanlatorPriority = update.scanlatorPriority!!)
            if (update.isSet(MangaUpdate::smartMergeOverride)) manga = manga.copy(smartMergeOverride = update.smartMergeOverride)
            if (update.isSet(MangaUpdate::scanlatorRangeRules)) {
                manga = manga.copy(scanlatorRangeRules = update.scanlatorRangeRules!!)
            }
            if (update.isSet(MangaUpdate::blacklistedChapters)) {
                manga = manga.copy(blacklistedChapters = update.blacklistedChapters!!)
            }
            writes += "manga"
            true
        }
    }
    private val setExcludedScanlators = mockk<SetExcludedScanlators> {
        coEvery { await(any(), any()) } answers {
            excluded = secondArg()
            writes += "excluded"
        }
    }
    private val getManga = mockk<GetManga> { coEvery { await(1L) } answers { manga } }
    private val changes = ScanlatorPreferenceChanges(updateManga, setExcludedScanlators, getManga)

    private fun open() = ScanlatorPreferencesDraft.create(
        scanlators = listOf("Delta", "Group A", "group b", "Group C"),
        priority = manga.scanlatorPriority,
        excluded = excluded,
        smartMergeOverride = manga.smartMergeOverride,
        rangeRules = manga.scanlatorRangeRules.mapNotNull(::parseScanlatorRangeRule),
        blacklist = manga.blacklistedChapters.map { BlacklistItem(it, listOf("Chapter")) },
    )

    /** Edits a dialog opened on the stored state, as the dialog's update does, then closes it. */
    private fun edit(vararg steps: (ScanlatorPreferencesDraft) -> ScanlatorPreferencesDraft) = runBlocking {
        val job = Job()
        val writer = SequentialWriter<ScanlatorPreferencesDraft.Change>(CoroutineScope(coroutineContext + job)) {
            changes.apply(1, it)
        }
        val writesBefore = writes.size
        var submitted = 0
        steps.fold(open()) { draft, step ->
            step(draft).also { next ->
                next.changesFrom(draft).forEach {
                    submitted++
                    writer.submit(it)
                }
            }
        }
        // The dialog is gone now; the writes it submitted still land.
        withTimeout(5_000) { while (writes.size < writesBefore + submitted) delay(5) }
        job.cancel()
    }

    @Test
    fun `a drag is stored at once and survives closing the dialog right away`() {
        edit({ it.move(2, 0) })
        manga.scanlatorPriority shouldBe listOf("group b", "Delta", "Group A", "Group C")
        open().order shouldBe listOf("group b", "Delta", "Group A", "Group C")
    }

    @Test
    fun `exclusions, Smart Merge and range rules are stored without saving and show on reopening`() {
        edit(
            { it.toggleExcluded("Group C") },
            { it.toggleExcluded("Delta") },
            { it.copy(smartMergeOverride = false) },
            { it.copy(rangeInput = ScanlatorPreferencesDraft.RangeInput("1", "10", "Group A")).addPendingRangeRule() },
        )
        excluded shouldBe setOf("Delta")
        manga.smartMergeOverride shouldBe false
        open().let {
            it.excluded shouldBe setOf("Delta")
            it.smartMergeOverride shouldBe false
            it.rangeRules shouldBe listOf(ScanlatorRangeRule(1.0, 10.0, "Group A"))
        }

        edit({ it.removeRangeRule(0) }, { it.copy(smartMergeOverride = null) })
        manga.scanlatorRangeRules shouldBe emptyList()
        manga.smartMergeOverride shouldBe null
    }

    @Test
    fun `quick consecutive edits are stored in order, the last one wins`() {
        edit(
            { it.copy(smartMergeOverride = true) },
            { it.copy(smartMergeOverride = false) },
            { it.copy(smartMergeOverride = null) },
            { it.move(0, 1) },
            { it.move(1, 0) },
        )
        manga.smartMergeOverride shouldBe null
        manga.scanlatorPriority shouldBe listOf("Delta", "Group A", "group b", "Group C")
    }

    @Test
    fun `consecutive blacklist removals all stick, others are kept`() {
        edit({ it.removeBlacklistEntry("url:/1") }, { it.removeBlacklistEntry("url:/3") })
        manga.blacklistedChapters shouldBe listOf("url:/2")
        open().visibleBlacklist.map { it.key } shouldBe listOf("url:/2")
    }

    @Test
    fun `switching modes and typing store nothing`() {
        edit({ it.switchMode() }, { it.copy(rangeInput = ScanlatorPreferencesDraft.RangeInput("4")) }, { it.switchMode() })
        writes shouldBe emptyList()
    }

    @Test
    fun `the writer keeps submission order even when earlier writes are slower`() = runBlocking<Unit> {
        val stored = mutableListOf<Int>()
        val job = Job()
        val writer = SequentialWriter<Int>(CoroutineScope(coroutineContext + job)) {
            delay((6 - it) * 15L)
            stored += it
        }
        (1..5).forEach(writer::submit)
        withTimeout(5_000) { while (stored.size < 5) delay(5) }
        job.cancel()
        stored shouldBe listOf(1, 2, 3, 4, 5)
    }
}
// KNS <--
