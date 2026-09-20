package eu.kanade.domain.track.service

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

class TrackPreferencesTest {
    private val store = mockk<PreferenceStore>(relaxed = true)
    private val priority = mockk<Preference<Long>>()
    private val manga = mockk<Preference<String>>()
    private var priorityValue = 0L
    private var mangaValue = ""
    private val subject: TrackPreferences

    init {
        every { store.getLong("pref_priority_tracker_id", 0L) } returns priority
        every { store.getString(any(), "") } returns manga
        every { priority.get() } answers { priorityValue }
        every { priority.set(any()) } answers { priorityValue = firstArg() }
        every { manga.get() } answers { mangaValue }
        every { manga.set(any()) } answers { mangaValue = firstArg() }
        subject = TrackPreferences(store)
    }

    @Test
    fun `priority defaults unset can be selected cleared and rejects invalid ids`() {
        subject.getPriorityTrackerId() shouldBe null
        subject.setPriorityTrackerId(2)
        subject.resolvePreferredTracker(10, setOf(1, 2)) shouldBe 2L
        subject.resolvePreferredTracker(10, setOf(1)) shouldBe null
        subject.setPriorityTrackerId(null)
        subject.getPriorityTrackerId() shouldBe null
        subject.setPriorityTrackerId(-1)
        subject.getPriorityTrackerId() shouldBe null
    }

    @Test
    fun `settings priority replaces legacy manga overrides dynamically`() {
        subject.setPriorityTrackerId(2)
        subject.setPreferredTrackerForManga(10, 1)
        subject.resolvePreferredTracker(10, setOf(1, 2)) shouldBe 2L
        subject.resolvePreferredTracker(10, setOf(2)) shouldBe 2L
        subject.setPriorityTrackerId(1)
        subject.resolvePreferredTracker(10, setOf(1, 2)) shouldBe 1L
        subject.setPriorityTrackerId(2)
        subject.setPreferredTrackerForManga(10, null)
        subject.resolvePreferredTracker(10, setOf(1, 2)) shouldBe 2L
    }
}
