package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupPreference
import eu.kanade.tachiyomi.data.backup.models.BooleanPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.IntPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.StringPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.StringSetPreferenceValue
import exh.log.xLogW
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.model.Category

// KNS -->
/**
 * App settings restore. Backups of the previous personal fork carry one `pref_hide_blacklisted_chapters_<id>`
 * per manga (20,323 in the backup that crashed): written one by one they made the store copy itself
 * once per value until the app ran out of memory. They are skipped (their ids belong to another
 * installation) and everything else is written in one batch.
 */
class PreferenceRestorerTest {

    private val written = slot<Map<String, Any>>()
    private val store = mockk<PreferenceStore>(relaxed = true) {
        every { getAll() } returns mapOf(
            "pref_int_locally" to 3,
            "dark_theme" to "light",
            "pinned_catalogues" to setOf("1"),
        )
        every { putAll(capture(written)) } returns Unit
        every { getStringSet("pinned_catalogues", any()) } returns mockk<Preference<Set<String>>> {
            every { get() } returns setOf("1")
        }
    }

    // Backup (old ids): Shounen 1 > Reading 2, Josei 3 > Reading 4. Local: Josei 30 > Reading 40, Shounen 10 > Reading 20.
    private val backupCategories = listOf(
        backupCategory(1, "Shounen", order = 0),
        backupCategory(2, "Reading", order = 1, parent = 1),
        backupCategory(3, "Josei", order = 2),
        backupCategory(4, "Reading", order = 3, parent = 3),
    )
    private val localCategories = listOf(
        Category(30, "Josei", 0, 0, false),
        Category(40, "Reading", 1, 0, false, parentId = 30),
        Category(10, "Shounen", 2, 0, false),
        Category(20, "Reading", 3, 0, false, parentId = 10),
    )
    private val restorer = PreferenceRestorer(
        context = mockk(relaxed = true),
        getCategories = mockk<GetCategories> { coEvery { await() } returns localCategories },
        preferenceStore = store,
    )

    @BeforeEach
    fun stubLogging() {
        // The skip is logged through android.util.Log, which unit tests do not have.
        mockkStatic(LOGGING)
        every { any<Any>().xLogW(any<String>()) } returns Unit
    }

    @AfterEach
    fun unstubLogging() = unmockkStatic(LOGGING)

    @Test
    fun `thousands of per-manga values from the old fork are skipped and the rest is one batch write`() = runBlocking<Unit> {
        val backup = (1..20_323).map { BackupPreference("pref_hide_blacklisted_chapters_$it", BooleanPreferenceValue(true)) } +
            (1..51).map { BackupPreference("pref_smart_scanlator_merge_override_$it", StringPreferenceValue("enabled")) } +
            BackupPreference("manga_bookmark_color_filter_exclude_7", StringSetPreferenceValue(setOf("RED"))) +
            BackupPreference("dark_theme", StringPreferenceValue("dark")) +
            BackupPreference("pref_smart_scanlator_merge", BooleanPreferenceValue(true))

        restorer.restorePreferences(backup, store, backupCategories)

        verify(exactly = 1) { store.putAll(any()) }
        verify(exactly = 0) { store.getBoolean(any(), any()) }
        verify(exactly = 0) { store.getString(any(), any()) }
        written.captured shouldBe mapOf("dark_theme" to "dark", "pref_smart_scanlator_merge" to true)
    }

    @Test
    fun `a value whose type changed is not written over the current setting`() = runBlocking<Unit> {
        restorer.restorePreferences(
            listOf(
                BackupPreference("pref_int_locally", StringPreferenceValue("3")),
                BackupPreference("dark_theme", BooleanPreferenceValue(true)),
                BackupPreference("unknown_old_key", IntPreferenceValue(5)),
            ),
            store,
        )
        // Keys this version does not have are kept as before (they are harmless and unread).
        written.captured shouldBe mapOf("unknown_old_key" to 5)
    }

    @Test
    fun `category preferences follow the category's name path to local ids`() = runBlocking<Unit> {
        restorer.restorePreferences(
            listOf(
                BackupPreference("default_category", IntPreferenceValue(4)),
                BackupPreference("library_update_categories", StringSetPreferenceValue(setOf("2", "3", "99"))),
                BackupPreference("pinned_catalogues", StringSetPreferenceValue(setOf("2"))),
            ),
            store,
            backupCategories,
        )
        // 4 = Josei › Reading (local 40), 2 = Shounen › Reading (local 20), 3 = Josei (30), 99 unknown.
        written.captured shouldBe mapOf(
            "default_category" to 40,
            "library_update_categories" to setOf("20", "30"),
            "pinned_catalogues" to setOf("1", "2"),
        )
    }

    @Test
    fun `a category preference with no local match leaves the current value`() = runBlocking<Unit> {
        restorer.restorePreferences(
            listOf(
                BackupPreference("default_category", IntPreferenceValue(99)),
                BackupPreference("library_update_categories", StringSetPreferenceValue(setOf("99"))),
            ),
            store,
            backupCategories,
        )
        written.captured shouldBe emptyMap()
    }

    @Test
    fun `only keys ending in a database id are treated as per-manga`() {
        listOf(
            "pref_hide_blacklisted_chapters_12" to true,
            "pref_smart_scanlator_merge_override_5" to true,
            "manga_bookmark_color_filter_9" to true,
            "manga_bookmark_color_filter_exclude_9" to true,
            "pref_hide_blacklisted_chapters_" to false,
            "pref_smart_scanlator_merge" to false,
            "pref_smart_scanlator_merge_override_abc" to false,
            "pref_filter_library_bookmark_colors" to false,
        ).forEach { (key, expected) -> PreferenceRestorer.isKeyedByLocalId(key) shouldBe expected }
    }

    @Test
    fun `an enum value this version does not know reads as the default`() {
        val deserializer = slot<(String) -> Mode>()
        val enumStore = mockk<PreferenceStore> {
            every { getObjectFromString("mode", Mode.A, any(), capture(deserializer)) } returns mockk()
        }
        enumStore.getEnum("mode", Mode.A)
        deserializer.captured("B") shouldBe Mode.B
        deserializer.captured("REMOVED_IN_THIS_VERSION") shouldBe Mode.A
    }

    private enum class Mode { A, B }

    private companion object {
        const val LOGGING = "exh.log.LoggingKt"
    }

    private fun backupCategory(id: Long, name: String, order: Long, parent: Long = 0) =
        BackupCategory(name = name, order = order, id = id, parentId = parent, hierarchyKnown = true)
}
// KNS <--
