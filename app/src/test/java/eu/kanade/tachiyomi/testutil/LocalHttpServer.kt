package eu.kanade.tachiyomi.testutil

import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentLinkedQueue

class LocalHttpServer : AutoCloseable {
    data class Request(val path: String, val method: String, val authorization: List<String>, val cacheControl: String?, val body: String)
    data class Reply(val code: Int = 200, val body: String = "{}", val cacheControl: String = "no-store")
    val requests = ConcurrentLinkedQueue<Request>()

    @Volatile var respond: (Request) -> Reply = { Reply() }
    private val server = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val received = Request(request.url.encodedPath, request.method, request.headers.values("Authorization"), request.headers["Cache-Control"], request.body?.utf8().orEmpty())
                requests.add(received)
                val reply = respond(received)
                return MockResponse.Builder().code(reply.code).body(reply.body)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Cache-Control", reply.cacheControl).build()
            }
        }
        start()
    }
    fun clientBuilder() = OkHttpClient.Builder().addInterceptor { chain ->
        val request = chain.request()
        chain.proceed(request.newBuilder().url(request.url.newBuilder().scheme("http").host(server.hostName).port(server.port).build()).build())
    }
    override fun close() {
        server.close()
    }
}
