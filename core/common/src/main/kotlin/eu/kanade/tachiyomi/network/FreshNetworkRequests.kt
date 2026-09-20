package eu.kanade.tachiyomi.network

import kotlinx.coroutines.asContextElement
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.Collections
import java.util.WeakHashMap

// KMK --> Carry an explicit refresh across OkHttp's dispatcher without evicting shared caches.
object FreshNetworkRequests : Interceptor, EventListener.Factory {
    private val refresh = ThreadLocal<Boolean>()
    private val refreshing = Collections.synchronizedMap(WeakHashMap<Call, Boolean>())

    fun context(enabled: Boolean) = refresh.asContextElement(enabled)

    override fun create(call: Call): EventListener {
        // Called synchronously by newCall, before OkHttp switches to its own executor.
        if (refresh.get() != true) return EventListener.NONE
        return object : EventListener() {
            override fun callStart(call: Call) {
                refreshing[call] = true
            }
            override fun callEnd(call: Call) {
                refreshing.remove(call)
            }
            override fun callFailed(call: Call, ioe: IOException) {
                refreshing.remove(call)
            }
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (refreshing[chain.call()] != true) return chain.proceed(request)
        return chain.proceed(
            request.newBuilder()
                .header("Cache-Control", if (request.cacheControl.noStore) "no-cache, no-store" else "no-cache")
                .build(),
        )
    }
}
// KMK <--
