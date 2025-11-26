package eu.kanade.tachiyomi.data.track.mangabaka

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class MangaBakaInterceptor(
    private val tracker: MangaBaka,
) : Interceptor {

    private var pat: String? = tracker.restoreSession()

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val token = pat ?: throw IOException("Not authenticated with MangaBaka")

        val authRequest = originalRequest.newBuilder()
            .addHeader("x-api-key", token)
            .header("User-Agent", "Komikku")
            .build()

        return chain.proceed(authRequest)
    }

    fun newAuth(pat: String?) {
        this.pat = pat
    }
}
