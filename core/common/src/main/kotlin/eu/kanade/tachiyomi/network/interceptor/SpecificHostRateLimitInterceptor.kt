package eu.kanade.tachiyomi.network.interceptor

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toDuration
import kotlin.time.toDurationUnit

/**
 * TimeUnit-based overload kept for compatibility.
 *
 * Example:
 * httpUrl = "https://api.manga.com".toHttpUrlOrNull(), permits = 5, period = 1, unit = seconds
 * => 5 requests per second to the api.manga.com host.
 */
@Deprecated("Use the version with kotlin.time APIs instead.")
fun OkHttpClient.Builder.rateLimitHost(
    httpUrl: HttpUrl,
    permits: Int,
    period: Long = 1,
    unit: TimeUnit = TimeUnit.SECONDS,
) = addInterceptor(
    RateLimitInterceptor(httpUrl.host, permits, period.toDuration(unit.toDurationUnit())),
)

/**
 * Preferred kotlin.time overload that respects the global ignore preference.
 *
 * Example:
 * httpUrl = "https://api.manga.com".toHttpUrlOrNull(), permits = 5, period = 1.seconds
 * => 5 requests per second to the api.manga.com host.
 */
fun OkHttpClient.Builder.rateLimitHost(
    httpUrl: HttpUrl,
    permits: Int,
    period: Duration = 1.seconds,
) = addInterceptor(RateLimitInterceptor(httpUrl.host, permits, period, true))

/**
 * Convenience overload accepting a raw URL string.
 *
 * Example:
 * url = "https://imagecdn.manga.com", permits = 10, period = 2.minutes
 * => 10 requests per 2 minutes to imagecdn.manga.com
 */
@Suppress("UNUSED")
fun OkHttpClient.Builder.rateLimitHost(
    url: String,
    permits: Int,
    period: Duration = 1.seconds,
) = addInterceptor(RateLimitInterceptor(url.toHttpUrlOrNull()?.host, permits, period, true))
