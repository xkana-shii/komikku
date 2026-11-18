// KUN -->
package mihon.data.extension.repository

import app.cash.sqldelight.async.coroutines.await
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import mihon.data.extension.service.ExtensionStoreService
import mihon.domain.extension.model.ChangedExtensionStore
import mihon.domain.extension.model.ExtensionStore
import org.junit.jupiter.api.Test
import tachiyomi.data.Database
import tachiyomi.data.DatabaseBindings

/**
 * Guards `refreshAll()`'s rename reporting: a store's new index URL may only be reported when the store
 * is actually applied, because the caller remaps the disabled-store preference with it
 * (`RefreshExtensionStores`). A store the user deleted mid-refresh must not leak its new URL into that
 * remap, or re-adding it later would come back silently disabled.
 */
class ExtensionStoreRepositoryRefreshTest {

    @Test
    fun `a store renamed by its index url reports the rename and moves`() {
        runBlocking {
            withRepository { repository, service, driver ->
                driver.insertStore(OLD_URL, name = "Old")
                coEvery { service.fetch(OLD_URL) } returns
                    Result.success(store(NEW_URL, name = "New"))

                val refresh = repository.refreshAll()

                refresh.renamed shouldBe mapOf(OLD_URL to NEW_URL)
                refresh.changed shouldBe emptyList()
                driver.storeUrls() shouldBe listOf(NEW_URL)
                driver.storeName(NEW_URL) shouldBe "New"
            }
        }
    }

    @Test
    fun `a store deleted mid-refresh reports no rename`() {
        runBlocking {
            withRepository { repository, service, driver ->
                driver.insertStore(OLD_URL, name = "Old")
                // The user deletes the store while its index is being fetched: after `refreshAll`
                // snapshotted the saved stores, before the fetched entry is applied.
                coEvery { service.fetch(OLD_URL) } coAnswers {
                    driver.deleteStore(OLD_URL)
                    Result.success(store(NEW_URL, name = "New"))
                }

                val refresh = repository.refreshAll()

                refresh.renamed shouldBe emptyMap()
                // Not re-added either: the store is deleted, not moved to its new URL.
                driver.storeUrls() shouldBe emptyList()
            }
        }
    }

    @Test
    fun `a store that now serves another signing key is changed and kept as is`() {
        runBlocking {
            withRepository { repository, service, driver ->
                driver.insertStore(OLD_URL, name = "Old")
                coEvery { service.fetch(OLD_URL) } returns
                    Result.success(store(OTHER_URL, name = "Handed over", signingKey = OTHER_KEY))

                val refresh = repository.refreshAll()

                refresh.changed shouldBe listOf(
                    ChangedExtensionStore(store(OLD_URL, name = "Old"), servedName = "Handed over"),
                )
                refresh.renamed shouldBe emptyMap()
                driver.storeUrls() shouldBe listOf(OLD_URL)
                driver.storeName(OLD_URL) shouldBe "Old"
            }
        }
    }

    private suspend fun <T> withRepository(
        block: suspend (ExtensionStoreRepositoryImpl, ExtensionStoreService, SqlDriver) -> T,
    ): T {
        val driver = newDriver()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val database = DatabaseBindings.providesDatabase(driver)
            val service = mockk<ExtensionStoreService>()
            val repository = ExtensionStoreRepositoryImpl(
                scope = scope,
                service = service,
                database = database,
            )
            return block(repository, service, driver)
        } finally {
            scope.cancel()
            driver.close()
        }
    }

    private suspend fun newDriver(): SqlDriver {
        return JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also {
            Database.Schema.create(it).await()
        }
    }

    private fun store(
        indexUrl: String,
        name: String,
        signingKey: String = KEY,
    ): ExtensionStore = ExtensionStore(
        indexUrl = indexUrl,
        name = name,
        badgeLabel = name,
        signingKey = signingKey,
        contact = ExtensionStore.Contact(website = indexUrl, discord = null),
        isLegacy = false,
        extensionListUrl = null,
    )

    private suspend fun SqlDriver.insertStore(indexUrl: String, name: String) {
        execute(
            null,
            """
            INSERT INTO extension_store(
                index_url, name, badge_label, signing_key, contact_website, contact_discord, is_legacy,
                extension_list_url
            )
            VALUES ('$indexUrl', '$name', '$name', '$KEY', '$indexUrl', NULL, 0, NULL)
            """.trimIndent(),
            0,
        ).await()
    }

    private suspend fun SqlDriver.deleteStore(indexUrl: String) {
        execute(null, "DELETE FROM extension_store WHERE index_url = '$indexUrl'", 0).await()
    }

    private fun SqlDriver.storeUrls(): List<String> =
        executeQuery(
            null,
            "SELECT index_url FROM extension_store ORDER BY index_url",
            { cursor ->
                QueryResult.Value(
                    buildList {
                        while (cursor.next().value) add(cursor.getString(0)!!)
                    },
                )
            },
            0,
        ).value

    private fun SqlDriver.storeName(indexUrl: String): String? =
        executeQuery(
            null,
            "SELECT name FROM extension_store WHERE index_url = '$indexUrl'",
            { cursor -> QueryResult.Value(if (cursor.next().value) cursor.getString(0) else null) },
            0,
        ).value

    private companion object {
        const val OLD_URL = "https://store.example/old/index.min.json"
        const val NEW_URL = "https://store.example/new/index.min.json"
        const val OTHER_URL = "https://other.example/index.min.json"
        const val KEY = "aaaaaaaa"
        const val OTHER_KEY = "bbbbbbbb"
    }
}
// KUN <--
