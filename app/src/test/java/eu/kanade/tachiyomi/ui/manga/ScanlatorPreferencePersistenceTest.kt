package eu.kanade.tachiyomi.ui.manga

import eu.kanade.domain.manga.interactor.SetExcludedScanlators
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.presentation.manga.components.ScanlatorPreferencesDraft
import eu.kanade.presentation.manga.components.ScanlatorPreferencesDraft.BlacklistItem
import eu.kanade.presentation.manga.components.ScanlatorPreferencesEditor
import eu.kanade.presentation.manga.components.moveScanlatorPriority
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

    @Test
    fun `global persists after enabled and disabled, also when switched quickly`() {
        manga = manga.copy(smartMergeOverride = true)
        edit({ it.copy(smartMergeOverride = null) })
        manga.smartMergeOverride shouldBe null
        open().smartMergeOverride shouldBe null

        edit(
            { it.copy(smartMergeOverride = true) },
            { it.copy(smartMergeOverride = false) },
            { it.copy(smartMergeOverride = null) },
        )
        manga.smartMergeOverride shouldBe null
        // The choice is stored as is; the effective value follows the global setting separately.
        open().smartMergeOverride shouldBe null
    }

    @Test
    fun `a drag keeps the stored place of scanlators the dialog did not list`() {
        // "Myth Toons" has no chapters right now (e.g. during a refresh), so the dialog leaves it out.
        manga = manga.copy(scanlatorPriority = listOf("Kaizen", "bloomingdale", "Myth Toons", "Paradise scans"))
        val draft = ScanlatorPreferencesDraft.create(
            scanlators = listOf("Kaizen", "bloomingdale", "Paradise scans", "Unknown"),
            priority = manga.scanlatorPriority,
            excluded = emptySet(),
            smartMergeOverride = null,
            rangeRules = emptyList(),
            blacklist = emptyList(),
        )
        draft.order shouldBe listOf("Kaizen", "bloomingdale", "Paradise scans", "Unknown")
        runBlocking { changes.apply(1, ScanlatorPreferencesDraft.Change.Priority(draft.move(1, 0).order)) }
        manga.scanlatorPriority shouldBe listOf("bloomingdale", "Kaizen", "Myth Toons", "Paradise scans", "Unknown")
    }

    @Test
    fun `merging a new order keeps absent scanlators, appends new ones and drops duplicates`() {
        val merge = ScanlatorPreferenceChanges.Companion::mergePriority
        merge(listOf("A", "B", "C"), listOf("C", "A")) shouldBe listOf("C", "B", "A")
        merge(listOf("A", "B", "C"), listOf("B", "C")) shouldBe listOf("A", "B", "C")
        merge(listOf("A", "B"), listOf("B", "A", "New")) shouldBe listOf("B", "A", "New")
        merge(listOf("A", "A", "B"), listOf("B", "B")) shouldBe listOf("A", "B")
        merge(emptyList(), listOf("X", "Y")) shouldBe listOf("X", "Y")
        // A missing scanlator keeps its position and is listed there again when it reappears.
        val stored = merge(listOf("Kaizen", "Myth Toons", "Paradise scans"), listOf("Paradise scans", "Kaizen"))
        stored shouldBe listOf("Paradise scans", "Myth Toons", "Kaizen")
        ScanlatorPreferencesDraft.create(
            scanlators = listOf("Kaizen", "Myth Toons", "Paradise scans", "Brand new"),
            priority = stored,
            excluded = emptySet(),
            smartMergeOverride = null,
            rangeRules = emptyList(),
            blacklist = emptyList(),
        ).order shouldBe listOf("Paradise scans", "Myth Toons", "Kaizen", "Brand new")
    }

    @Test
    fun `a write the database rejects is reported, and later writes still land`() = runBlocking<Unit> {
        val failing = mockk<UpdateManga> {
            coEvery { await(any()) } answers {
                val update = firstArg<MangaUpdate>()
                if (update.isSet(MangaUpdate::smartMergeOverride)) {
                    manga = manga.copy(smartMergeOverride = update.smartMergeOverride)
                }
                !update.isSet(MangaUpdate::scanlatorRangeRules)
            }
        }
        val failures = mutableListOf<ScanlatorPreferencesDraft.Change>()
        val job = Job()
        val writer = SequentialWriter<ScanlatorPreferencesDraft.Change>(
            CoroutineScope(coroutineContext + job),
            onError = { change, _ -> failures += change },
        ) { ScanlatorPreferenceChanges(failing, setExcludedScanlators, getManga).apply(1, it) }
        writer.submit(ScanlatorPreferencesDraft.Change.RangeRules(emptyList()))
        writer.submit(ScanlatorPreferencesDraft.Change.SmartMerge(null))
        withTimeout(5_000) { writer.awaitIdle() }
        job.cancel()
        failures shouldBe listOf(ScanlatorPreferencesDraft.Change.RangeRules(emptyList()))
        manga.smartMergeOverride shouldBe null
    }

    // ---- The dialog's editor, as the screen recording used it (To You at Dawn) ----

    private val dawn = listOf("kaoru", "desire scans", "Kaizen", "Myth Toons", "Paradise scans", "Unknown")

    /** Opens the dialog on the stored values, as reopening it does. */
    private fun reopen(scanlators: List<String> = dawn) = ScanlatorPreferencesDraft.create(
        scanlators = scanlators,
        priority = manga.scanlatorPriority,
        excluded = excluded,
        smartMergeOverride = manga.smartMergeOverride,
        rangeRules = manga.scanlatorRangeRules.mapNotNull(::parseScanlatorRangeRule),
        blacklist = emptyList(),
    )

    /** Uses an open dialog through [ScanlatorPreferencesEditor] and the real writer, then closes it at once. */
    private fun dialog(block: (ScanlatorPreferencesEditor) -> Unit) = runBlocking {
        val job = Job()
        val writer = SequentialWriter<ScanlatorPreferencesDraft.Change>(CoroutineScope(coroutineContext + job)) {
            changes.apply(1, it)
        }
        block(ScanlatorPreferencesEditor(reopen()) { it.forEach(writer::submit) })
        // Closing only dismisses; what was submitted is stored before the dialog reads it again.
        withTimeout(5_000) { writer.awaitIdle() }
        job.cancel()
    }

    private fun startDawn() {
        manga = manga.copy(scanlatorPriority = dawn, smartMergeOverride = true)
        excluded = emptySet()
    }

    @Test
    fun `kaoru moved from the top to the bottom stays there after closing and reopening`() {
        startDawn()
        dialog { it.edit { draft -> draft.move(0, 5) } }
        reopen().order shouldBe listOf("desire scans", "Kaizen", "Myth Toons", "Paradise scans", "Unknown", "kaoru")
    }

    @Test
    fun `rapid drag moves are queued before release animation and survive immediate close`() {
        startDawn()
        dialog { editor ->
            // The lazy reorder callback fires while the handle crosses each row, before its release
            // animation. Deliberately do not simulate an onSettle callback after these moves.
            editor.edit { draft ->
                draft.copy(order = moveScanlatorPriority(draft.order, "kaoru", "desire scans"))
            }
            editor.edit { draft ->
                draft.copy(order = moveScanlatorPriority(draft.order, "kaoru", "Kaizen"))
            }
            editor.edit { draft ->
                draft.copy(order = moveScanlatorPriority(draft.order, "kaoru", "Paradise scans"))
            }
        }
        reopen().order shouldBe listOf("desire scans", "Kaizen", "Myth Toons", "Paradise scans", "kaoru", "Unknown")
    }

    @Test
    fun `drag moves resolve stable names and ignore rows no longer present`() {
        moveScanlatorPriority(dawn, "kaoru", "Kaizen") shouldBe
            listOf("desire scans", "Kaizen", "kaoru", "Myth Toons", "Paradise scans", "Unknown")
        moveScanlatorPriority(dawn, "gone", "Kaizen") shouldBe dawn
        moveScanlatorPriority(dawn, "kaoru", "gone") shouldBe dawn
    }

    @Test
    fun `moves to the top, to the middle and repeated moves of one scanlator all persist`() {
        startDawn()
        dialog { it.edit { draft -> draft.move(5, 0) } }
        reopen().order shouldBe listOf("Unknown", "kaoru", "desire scans", "Kaizen", "Myth Toons", "Paradise scans")
        dialog { it.edit { draft -> draft.move(1, 3) } }
        reopen().order shouldBe listOf("Unknown", "desire scans", "Kaizen", "kaoru", "Myth Toons", "Paradise scans")
        dialog { editor ->
            editor.edit { it.move(3, 0) }
            editor.edit { it.move(0, 5) }
            editor.edit { it.move(5, 2) }
            editor.edit { it.move(4, 1) }
        }
        reopen().order shouldBe listOf("Unknown", "Myth Toons", "desire scans", "kaoru", "Kaizen", "Paradise scans")
    }

    @Test
    fun `a drop callback created before a Global choice does not undo it`() {
        startDawn()
        dialog { editor ->
            // The reorderable list keeps its drop callback until the order changes: here it was created
            // before Global was chosen, as when the dialog opened.
            val onSettle: (Int, Int) -> Unit = { from, to -> editor.edit { it.move(from, to) } }
            editor.edit { it.copy(smartMergeOverride = null) }
            editor.edit { it.toggleExcluded("Unknown") }
            onSettle(0, 5)
        }
        manga.smartMergeOverride shouldBe null
        excluded shouldBe setOf("Unknown")
        reopen().run {
            smartMergeOverride shouldBe null
            order.last() shouldBe "kaoru"
        }
    }

    @Test
    fun `every Smart Merge choice persists when the dialog is closed right after, the last of a quick series wins`() {
        startDawn()
        listOf<Boolean?>(null, true, false, null).forEach { choice ->
            dialog { editor -> editor.edit { it.copy(smartMergeOverride = choice) } }
            reopen().smartMergeOverride shouldBe choice
        }
        dialog { editor ->
            editor.edit { it.copy(smartMergeOverride = null) }
            editor.edit { it.copy(smartMergeOverride = true) }
            editor.edit { it.copy(smartMergeOverride = false) }
            editor.edit { it.copy(smartMergeOverride = null) }
        }
        reopen().smartMergeOverride shouldBe null
    }

    @Test
    fun `the saved order does not depend on the order the source lists scanlators in`() {
        startDawn()
        dialog { it.edit { draft -> draft.move(0, 5) } }
        val saved = reopen().order
        reopen(dawn.reversed()).order shouldBe saved
        reopen(dawn.shuffled(java.util.Random(7))).order shouldBe saved
        // A new scanlator goes after the saved ones; one that is missing for now keeps its place.
        reopen(dawn + "New group").order shouldBe saved + "New group"
        reopen(dawn - "Kaizen").order shouldBe saved - "Kaizen"
        dialog { it.edit { draft -> draft.move(4, 0) } }
        manga.scanlatorPriority.size shouldBe 6
    }
}
// KNS <--
