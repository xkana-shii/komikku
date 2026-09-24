package eu.kanade.tachiyomi.data.track.mangaupdates

import eu.kanade.domain.track.interactor.trackerBatch
import eu.kanade.domain.track.model.toDbTrack
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.testutil.LocalHttpServer
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tachiyomi.domain.track.interactor.InsertTrack
import tachiyomi.domain.track.model.Track
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference

class MangaUpdatesAuthenticationTest {
    private fun track(id: Long = 7) = Track(id, 1, id, 123, null, "Title", 8.0, 100, 0, 7.0, "", 0, 0, false)

    @Test
    fun `update bind and removal use one current Bearer header while public search needs no session`() = runTest {
        LocalHttpServer().use { server ->
            val session = AtomicReference<String?>("first-session")
            val interceptor = MangaUpdatesInterceptor(session::get) { session.compareAndSet(it, null) }
            val client = server.clientBuilder().build()
            val api = MangaUpdatesApi(interceptor, client, Json { ignoreUnknownKeys = true })
            val service = MangaUpdates(7, api)
            server.respond = { request ->
                when (request.path) {
                    "/v1/series/search" -> LocalHttpServer.Reply(body = "{\"results\":[]}")
                    "/v1/lists/series/123" -> LocalHttpServer.Reply(code = 404)
                    else -> LocalHttpServer.Reply()
                }
            }
            try {
                service.bind(track().toDbTrack(), false)
                session.set("new-session")
                service.update(track().toDbTrack(), false)
                service.delete(track())
                service.search("public") shouldBe emptyList()
                val requests = server.requests.toList()
                requests.take(2).all { it.authorization == listOf("Bearer first-session") } shouldBe true
                requests.drop(2).dropLast(1).all { it.authorization == listOf("Bearer new-session") } shouldBe true
                requests.last().authorization shouldBe emptyList()
                session.set(null)
                val count = requests.size
                assertThrows<IOException> { service.update(track().toDbTrack(), false) }
                server.requests.size shouldBe count
            } finally {
                client.connectionPool.evictAll()
                client.dispatcher.executorService.shutdown()
            }
        }
    }

    @Test
    fun `401 is reported and invalidates only the rejected session without replaying writes`() = runTest {
        LocalHttpServer().use { server ->
            val session = AtomicReference<String?>("expired")
            val interceptor = MangaUpdatesInterceptor(session::get) { session.compareAndSet(it, null) }
            val client = server.clientBuilder().build()
            val service = MangaUpdates(7, MangaUpdatesApi(interceptor, client, Json { ignoreUnknownKeys = true }))
            server.respond = { LocalHttpServer.Reply(code = 401) }
            try {
                assertThrows<HttpException> { service.update(track().toDbTrack(), false) }.code shouldBe 401
                session.get() shouldBe null
                server.requests.size shouldBe 1
                session.set("old-session")
                server.respond = {
                    session.set("new-login")
                    LocalHttpServer.Reply(code = 401)
                }
                assertThrows<HttpException> { service.update(track().toDbTrack(), false) }.code shouldBe 401
                session.get() shouldBe "new-login"
                server.requests.size shouldBe 2
            } finally {
                client.connectionPool.evictAll()
                client.dispatcher.executorService.shutdown()
            }
        }
    }

    @Test
    fun `concurrent batch keeps MangaUpdates authentication and a 401 cannot cancel its sibling`() = runTest {
        for (code in listOf(200, 401)) {
            LocalHttpServer().use { server ->
                val session = AtomicReference<String?>("valid-session")
                val interceptor = MangaUpdatesInterceptor(session::get) { session.compareAndSet(it, null) }
                val client = server.clientBuilder().build()
                val service = MangaUpdates(7, MangaUpdatesApi(interceptor, client, Json { ignoreUnknownKeys = true }))
                server.respond = { LocalHttpServer.Reply(code = code) }
                val entered = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()
                val sibling = mockk<Tracker> {
                    every { id } returns 8L
                    coEvery { update(any(), any()) } coAnswers {
                        entered.complete(Unit)
                        release.await()
                        firstArg()
                    }
                }
                val insert = mockk<InsertTrack>(relaxed = true)
                val operation = async { trackerBatch(listOf(service to track(), sibling to track(8)), insert) { update(binding) } }
                try {
                    entered.await()
                    operation.isCompleted shouldBe false
                    release.complete(Unit)
                    val results = operation.await()
                    server.requests.isNotEmpty() shouldBe true
                    server.requests.all { it.authorization == listOf("Bearer valid-session") } shouldBe true
                    if (code == 401) {
                        (results.first().error as HttpException).code shouldBe 401
                        session.get() shouldBe null
                    } else {
                        results.first().error shouldBe null
                    }
                    results[1].error shouldBe null
                    coVerify(exactly = 1) { insert.awaitOrThrow(match { it.trackerId == 8L }) }
                } finally {
                    release.complete(Unit)
                    client.connectionPool.evictAll()
                    client.dispatcher.executorService.shutdown()
                }
            }
        }
    }
}
