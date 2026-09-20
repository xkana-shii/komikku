package eu.kanade.tachiyomi.data.track.mangaupdates

import eu.kanade.tachiyomi.BuildConfig
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class MangaUpdatesInterceptor internal constructor(
    private val session: () -> String?,
    private val invalidate: (String) -> Unit,
) : Interceptor {
    constructor(mangaUpdates: MangaUpdates) : this(mangaUpdates::restoreSession, mangaUpdates::invalidateSession)

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = session()?.takeIf { it.isNotBlank() } ?: throw IOException("Not authenticated with MangaUpdates")
        val request = chain.request().newBuilder()
            .header("Authorization", "Bearer $token")
            .header("User-Agent", "Komikku v${BuildConfig.VERSION_NAME} (${BuildConfig.APPLICATION_ID})")
            .build()
        return chain.proceed(request).also { response ->
            // Leave the original HTTP 401 visible to the caller; never retry a write or erase a newer login.
            if (response.code == 401) invalidate(token)
        }
    }
}
