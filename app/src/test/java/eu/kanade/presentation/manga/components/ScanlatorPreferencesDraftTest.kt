package eu.kanade.presentation.manga.components

import eu.kanade.presentation.manga.components.ScanlatorPreferencesDraft.BlacklistItem
import eu.kanade.presentation.manga.components.ScanlatorPreferencesDraft.Mode
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.ScanlatorRangeRule
import tachiyomi.domain.chapter.model.applySmartScanlatorMerge
import tachiyomi.domain.chapter.model.encodeScanlatorRangeRule

// KNS -->
/** The scanlator preferences dialog's draft: what each control changes and what Save stores. */
class ScanlatorPreferencesDraftTest {

    private val draft = ScanlatorPreferencesDraft.create(
        scanlators = listOf("Group C", "group b", "Group A", "Delta"),
        priority = listOf("Delta", "Gone", "Group A"),
        excluded = setOf("Group C", "Unavailable"),
        smartMergeOverride = null,
        rangeRules = listOf(ScanlatorRangeRule(1.0, 5.0, "Group A")),
        blacklist = listOf(BlacklistItem("url:/1", listOf("Chapter 1")), BlacklistItem("url:/gone", emptyList())),
    )

    @Test
    fun `the saved priority comes first, new scanlators follow alphabetically, gone ones are dropped`() {
        draft.order shouldBe listOf("Delta", "Group A", "group b", "Group C")
        draft.mode shouldBe Mode.Scanlators
    }

    @Test
    fun `dragging a scanlator changes the stored priority, out-of-range moves are ignored`() {
        draft.move(3, 0).order shouldBe listOf("Group C", "Delta", "Group A", "group b")
        draft.move(0, 2).order shouldBe listOf("Group A", "group b", "Delta", "Group C")
        draft.move(0, 9) shouldBe draft
        draft.move(1, 1) shouldBe draft
    }

    @Test
    fun `the eye toggles the existing exclusions, also for excluded scanlators that are dragged`() {
        draft.isExcluded("Group C") shouldBe true
        draft.toggleExcluded("Group C").excluded shouldBe setOf("Unavailable")
        draft.toggleExcluded("Delta").excluded shouldBe setOf("Group C", "Unavailable", "Delta")
        draft.toggleExcluded("Delta").toggleExcluded("Delta") shouldBe draft
        // Exclusion and priority are independent: an excluded scanlator keeps its place when moved.
        val moved = draft.move(3, 0)
        moved.order.first() shouldBe "Group C"
        moved.isExcluded("Group C") shouldBe true
    }

    @Test
    fun `the Smart Merge override keeps its three states`() {
        listOf(null, true, false).forEach { value ->
            draft.copy(smartMergeOverride = value).switchMode().switchMode().smartMergeOverride shouldBe value
        }
    }

    @Test
    fun `switching modes keeps every unsaved change`() {
        val edited = draft
            .move(3, 0)
            .toggleExcluded("Delta")
            .copy(smartMergeOverride = false, rangeInput = ScanlatorPreferencesDraft.RangeInput(from = "8"))
            .removeRangeRule(0)
            .removeBlacklistEntry("url:/1")

        val blacklistMode = edited.switchMode()
        blacklistMode.mode shouldBe Mode.Blacklist
        blacklistMode.switchMode() shouldBe edited
    }

    @Test
    fun `range rules are added after the existing ones, the form is validated and partly reset`() {
        draft.rangeScanlator shouldBe "Delta"
        draft.pendingRangeRule shouldBe null
        val typed = draft.copy(rangeInput = ScanlatorPreferencesDraft.RangeInput("3", "8", "group b"))
        typed.pendingRangeRule shouldBe ScanlatorRangeRule(3.0, 8.0, "group b")
        typed.copy(rangeInput = typed.rangeInput.copy(from = "9")).pendingRangeRule shouldBe null
        typed.copy(rangeInput = typed.rangeInput.copy(scanlator = "Gone")).rangeScanlator shouldBe "Delta"

        val added = typed.addPendingRangeRule()
        added.rangeRules shouldBe listOf(ScanlatorRangeRule(1.0, 5.0, "Group A"), ScanlatorRangeRule(3.0, 8.0, "group b"))
        added.rangeInput shouldBe ScanlatorPreferencesDraft.RangeInput("", "", "group b")
        added.removeRangeRule(0).rangeRules shouldBe listOf(ScanlatorRangeRule(3.0, 8.0, "group b"))
        draft.addPendingRangeRule() shouldBe draft
    }

    @Test
    fun `saved range rules still take precedence over the priority order`() {
        val saved = draft.copy(rangeInput = ScanlatorPreferencesDraft.RangeInput("2", "2", "group b")).addPendingRangeRule()
        val chapters = listOf("Delta", "Group A", "group b").flatMapIndexed { i, scanlator ->
            listOf(1.0, 2.0, 6.0).mapIndexed { j, number ->
                Chapter.create().copy(id = (i * 10 + j).toLong(), url = "/$i/$j", chapterNumber = number, scanlator = scanlator)
            }
        }
        val kept = chapters.applySmartScanlatorMerge(
            priority = saved.order,
            blacklistedChapters = emptyList(),
            rangeRules = saved.rangeRules.map(::encodeScanlatorRangeRule),
        )
        // 1: inside "1–5 Group A"; 2: the later "2–2 group b" wins; 6: no range, so the priority (Delta).
        kept.associate { it.chapterNumber to it.scanlator } shouldBe mapOf(1.0 to "Group A", 2.0 to "group b", 6.0 to "Delta")
    }

    @Test
    fun `removing a blacklist entry is pending until saved, unavailable entries are listed`() {
        draft.visibleBlacklist.map { it.key } shouldBe listOf("url:/1", "url:/gone")
        val removed = draft.removeBlacklistEntry("url:/gone")
        removed.visibleBlacklist.map { it.key } shouldBe listOf("url:/1")
        removed.removedBlacklistKeys shouldBe setOf("url:/gone")
        removed.blacklist shouldBe draft.blacklist
    }
}
// KNS <--
