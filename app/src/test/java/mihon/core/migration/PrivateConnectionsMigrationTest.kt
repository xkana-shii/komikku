package mihon.core.migration

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import mihon.core.migration.migrations.PrivateConnectionsMigration
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

// KNS -->
class PrivateConnectionsMigrationTest {

    @Test
    fun `public account and password values move to private keys and are removed`() = runBlocking<Unit> {
        val values = mapOf(
            "discord_accounts" to "[{\"token\":\"t\"}]",
            "pref_connections_password_3" to "secret",
            "pref_discord_custom_message" to "hi",
        )
        val store = mockk<PreferenceStore>()
        every { store.getAll() } returns values
        val public = values.keys.associateWith { key ->
            mockk<Preference<String>>(relaxed = true).also { every { it.get() } returns values.getValue(key) }
        }
        val private = values.keys.associateWith { mockk<Preference<String>>(relaxed = true).also { every { it.get() } returns "" } }
        values.keys.forEach { key ->
            every { store.getString(key, "") } returns public.getValue(key)
            every { store.getString(Preference.privateKey(key), "") } returns private.getValue(key)
        }

        PrivateConnectionsMigration(store)(MigrationContext(dryrun = false, previousVersion = 172))

        verify { private.getValue("discord_accounts").set("[{\"token\":\"t\"}]") }
        verify { public.getValue("discord_accounts").delete() }
        verify { private.getValue("pref_connections_password_3").set("secret") }
        verify { public.getValue("pref_connections_password_3").delete() }
        verify(exactly = 0) { public.getValue("pref_discord_custom_message").delete() }
    }

    @Test
    fun `an existing private value is kept`() = runBlocking<Unit> {
        val store = mockk<PreferenceStore>()
        every { store.getAll() } returns mapOf("discord_accounts" to "old")
        val public = mockk<Preference<String>>(relaxed = true).also { every { it.get() } returns "old" }
        val private = mockk<Preference<String>>(relaxed = true).also { every { it.get() } returns "new" }
        every { store.getString("discord_accounts", "") } returns public
        every { store.getString(Preference.privateKey("discord_accounts"), "") } returns private

        PrivateConnectionsMigration(store)(MigrationContext(dryrun = false, previousVersion = 172))

        verify(exactly = 0) { private.set(any()) }
        verify { public.delete() }
    }
}
// KNS <--
