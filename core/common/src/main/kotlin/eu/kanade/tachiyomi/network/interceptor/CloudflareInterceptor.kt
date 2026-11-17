package eu.kanade.tachiyomi.network.interceptor

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import eu.kanade.tachiyomi.network.AndroidCookieJar
import eu.kanade.tachiyomi.util.system.isOutdated
import eu.kanade.tachiyomi.util.system.toWebViewHeaders
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.CoroutineScope
import logcat.LogPriority
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class CloudflareInterceptor(
    private val context: Context,
    private val cookieManager: AndroidCookieJar,
    scope: CoroutineScope,
    defaultUserAgentProvider: () -> String,
) : WebViewInterceptor(context, scope, defaultUserAgentProvider) {

    private val executor = ContextCompat.getMainExecutor(context)

    // KNS -->
    private val mainHandler = Handler(Looper.getMainLooper())
    private val failures = HostFailureLog()
    // KNS <--

    // Requests to a host share its lock while a challenge is solved under it alone, so concurrent requests wait for
    // one WebView instead of each opening their own
    private val hostLocks = ConcurrentHashMap<String, ReentrantReadWriteLock>()

    private val listenerScript = """
        addEventListener("message", ({data}) => {
            if (data?.source === "cloudflare-challenge") {
                mihon?.postMessage(data.event);
            }
        })
        // KNS -->
        // Reports where the verification widget is, so the dialog can show only that
        ;(() => {
            const selectors = [
                'iframe[src*="challenges.cloudflare.com"]',
                '#turnstile-wrapper', '.cf-turnstile', '#cf-turnstile', '[id^="cf-chl-widget"]', '#challenge-stage',
            ]
            let last = ''
            const report = () => {
                for (const selector of selectors) {
                    const el = document.querySelector(selector)
                    if (!el) continue
                    const r = el.getBoundingClientRect()
                    if (r.width < 20 || r.height < 20) continue
                    const text = 'widget:' + [r.left + scrollX, r.top + scrollY, r.width, r.height]
                        .map((v) => Math.round(v)).join(',')
                    if (text !== last) {
                        last = text
                        mihon?.postMessage(text)
                    }
                    return
                }
            }
            new MutationObserver(report).observe(document, { subtree: true, childList: true, attributes: true })
            addEventListener('resize', report)
            addEventListener('load', report)
        })()
        // KNS <--
    """.trimIndent()

    override fun shouldIntercept(response: Response): Boolean {
        // Check if Cloudflare anti-bot is on
        // Checking the cf-mitigated header is the official way to detect a Cloudflare challenge:
        // https://developers.cloudflare.com/cloudflare-challenges/challenge-types/challenge-pages/detect-response/
        return response.header("cf-mitigated") == "challenge" && response.header("Server") in SERVER_CHECK
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val host = request.url.host
        val lock = hostLocks.computeIfAbsent(host) { ReentrantReadWriteLock() }
        // KNS -->
        val startedWaiting = System.nanoTime()
        // KNS <--

        val (response, clearance) = lock.read {
            val response = chain.proceed(request)
            if (!shouldIntercept(response)) return response
            response to clearance(request.url)
        }

        // write releases the read hold of a request made from within another to the same host, like an extension
        // refreshing a token, which would otherwise wait on itself
        lock.write {
            // Another request may have solved the challenge while this one waited
            if (isCleared(request.url, clearance)) {
                response.close()
            } else {
                // KNS -->
                // The user cancelled (or it failed) while this request waited: share that outcome instead of opening
                // another dialog straight away
                if (failures.failedSince(host, startedWaiting)) {
                    response.close()
                    throw IOException(context.stringResource(MR.strings.information_cloudflare_bypass_failure))
                }
                // KNS <--
                if (!prepareWebView()) return response
                solve(request, response, clearance)
            }
        }

        // KNS -->
        // Retry once. A challenge that is still there means the clearance was rejected: fail rather than loop.
        val retried = chain.proceed(request)
        if (shouldIntercept(retried)) {
            retried.close()
            cookieManager.remove(request.url, COOKIE_NAMES, 0)
            failures.record(host)
            throw IOException(context.stringResource(MR.strings.information_cloudflare_bypass_failure))
        }
        return retried
        // KNS <--
    }

    override fun intercept(chain: Interceptor.Chain, request: Request, response: Response): Response {
        solve(request, response, clearance(request.url))
        return chain.proceed(request)
    }

    private fun clearance(url: HttpUrl): String? {
        return cookieManager.get(url).firstOrNull { it.name == "cf_clearance" }?.value
    }

    private fun isCleared(url: HttpUrl, previousClearance: String?): Boolean {
        val clearance = clearance(url)
        return !clearance.isNullOrBlank() && clearance != previousClearance
    }

    private fun solve(request: Request, response: Response, clearance: String?) {
        try {
            response.close()
            cookieManager.remove(request.url, COOKIE_NAMES, 0)
            resolveWithWebView(request, clearance)
        }
        // Because OkHttp's enqueue only handles IOExceptions, wrap the exception so that
        // we don't crash the entire app
        catch (e: CloudflareBypassException) {
            throw IOException(context.stringResource(MR.strings.information_cloudflare_bypass_failure), e)
        } catch (e: Exception) {
            throw IOException(e)
        }
    }

    // KNS -->
    // The attempt is the single source of truth: callbacks only ever move it forward, the first terminal state wins,
    // and everything touching the WebView runs on the main thread, where `webview`, `session` and `cleaned` live.
    private fun resolveWithWebView(originalRequest: Request, originalClearance: String?) {
        val host = originalRequest.url.host
        val attempt = ChallengeAttempt(host)
        attempt.advance(ChallengeAttempt.State.Preparing)

        val useIsolatedWorld = WebViewFeature.isFeatureSupported(WebViewFeature.JS_INJECTION_IN_FRAME_AND_WORLD)
        val challengeFound = AtomicBoolean(false)
        val rendererGone = AtomicBoolean(false)

        // Main thread only
        var webview: WebView? = null
        var session: CloudflareChallengeCoordinator.Session? = null
        var listenerScriptHandler: ScriptHandler? = null
        var cleaned = false
        var fallback: Runnable? = null

        val origRequestUrl = originalRequest.url.toString()
        // A WebView can only load a page with GET, and loading the url of another method usually reaches an
        // endpoint that answers GET without a challenge to solve. The clearance covers the whole site, so it's
        // earned on the site's root page instead. A body is never replayed through the WebView.
        val challengeUrl = if (originalRequest.method == "GET") {
            origRequestUrl
        } else {
            originalRequest.url.newBuilder().encodedPath("/").query(null).fragment(null).build().toString()
        }
        val headers = originalRequest.headers.toWebViewHeaders()

        fun cleanup(state: ChallengeAttempt.State) {
            if (cleaned) return
            cleaned = true
            fallback?.let(mainHandler::removeCallbacks)
            session?.let { CloudflareChallengeCoordinator.queue.remove(it) }
            val view = webview ?: return
            webview = null
            // Taken off the dialog first so Compose is not left rendering a destroyed view
            (view.parent as? ViewGroup)?.removeView(view)
            if (!rendererGone.get()) {
                if (state == ChallengeAttempt.State.Failed || state == ChallengeAttempt.State.TimedOut) {
                    if (view.isOutdated()) context.toast(MR.strings.information_webview_outdated, Toast.LENGTH_LONG)
                }
                // Only destroy can be called on a WebView whose renderer is gone
                if (useIsolatedWorld) {
                    WebViewCompat.removeWebMessageListener(view, WebViewCompat.getExecutionWorld(view, "mihon"), "mihon")
                } else {
                    view.removeJavascriptInterface("mihon")
                }
                listenerScriptHandler?.remove()
                view.stopLoading()
            }
            view.destroy()
        }

        attempt.onTerminal { state -> executor.execute { cleanup(state) } }

        // Hands the challenge to the user, only while the app can show it. Main thread.
        fun requestUser() {
            val view = webview ?: return
            if (attempt.isTerminal || attempt.state == ChallengeAttempt.State.AwaitingUser) return
            if (!CloudflareChallengeCoordinator.canPrompt) {
                // Library updates, downloads and other background work keep failing as before
                attempt.finish(ChallengeAttempt.State.Failed)
                return
            }
            if (!attempt.advance(ChallengeAttempt.State.AwaitingUser)) return
            val newSession = CloudflareChallengeCoordinator.Session(host, attempt, view) { executor.execute(it) }
            session = newSession
            if (!CloudflareChallengeCoordinator.enqueue(newSession)) attempt.finish(ChallengeAttempt.State.Failed)
        }

        fun onChallengeEvent(event: String) {
            when (event) {
                // The challenge cannot be solved non-interactively
                "interactiveBegin" -> executor.execute { requestUser() }
                "fail" -> attempt.finish(ChallengeAttempt.State.Failed)
                // KNS -->
                else -> if (event.startsWith(WidgetBounds.EVENT_PREFIX)) attempt.updateWidget(WidgetBounds.parse(event))
                // KNS <--
            }
        }

        executor.execute {
            if (attempt.isTerminal) return@execute
            val view = createWebView(originalRequest)
            webview = view
            attempt.advance(ChallengeAttempt.State.AutomaticVerification)

            if (useIsolatedWorld) {
                // An isolated world keeps the bridge out of the page's reach
                val world = WebViewCompat.getExecutionWorld(view, "mihon")
                val origins = setOf(originalRequest.url.origin())
                WebViewCompat.addWebMessageListener(view, "mihon", origins, world) { _, message, _, isMainFrame, _ ->
                    if (isMainFrame) message.data?.let(::onChallengeEvent)
                }
                listenerScriptHandler = WebViewCompat.addJavaScriptOnEvent(
                    view,
                    listenerScript,
                    WebViewCompat.INJECTION_EVENT_DOCUMENT_START,
                    origins,
                    world,
                )
            } else {
                view.addJavascriptInterface(
                    object {
                        @Suppress("unused")
                        @JavascriptInterface
                        fun postMessage(event: String) = onChallengeEvent(event)
                    },
                    "mihon",
                )
            }

            fun checkCleared() {
                if (!attempt.isTerminal && isCleared(originalRequest.url, originalClearance)) {
                    attempt.finish(ChallengeAttempt.State.Succeeded)
                }
            }

            view.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    if (attempt.isTerminal) return
                    checkCleared()

                    if (url == challengeUrl) {
                        if (!challengeFound.get()) {
                            // The first request didn't return the challenge, abort.
                            attempt.finish(ChallengeAttempt.State.Failed)
                        } else if (!useIsolatedWorld) {
                            view.evaluateJavascript(listenerScript, null)
                        }
                    }
                }

                override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                    if (!attempt.isTerminal && challengeFound.get()) checkCleared()
                }

                override fun onReceivedHttpError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    errorResponse: WebResourceResponse?,
                ) {
                    if (attempt.isTerminal || request?.isForMainFrame != true) return
                    if (errorResponse?.responseHeaders?.get("cf-mitigated") == "challenge") {
                        // Found the Cloudflare challenge page.
                        if (challengeFound.compareAndSet(false, true)) {
                            // Bounded fallback for a positively detected challenge that neither finishes nor
                            // announces itself: hand it to the user after a while
                            val runnable = Runnable {
                                if (attempt.state == ChallengeAttempt.State.AutomaticVerification) requestUser()
                            }
                            fallback = runnable
                            mainHandler.postDelayed(runnable, INTERACTIVE_FALLBACK_MS)
                        }
                    } else if (isCleared(originalRequest.url, originalClearance)) {
                        // The solved page may itself answer with an error status
                        attempt.finish(ChallengeAttempt.State.Succeeded)
                    } else {
                        // The challenge wasn't found.
                        attempt.finish(ChallengeAttempt.State.Failed)
                    }
                }

                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                    // Not handling it kills the app, failing the bypass is enough
                    logcat(LogPriority.ERROR) {
                        "WebView renderer gone during Cloudflare bypass: crashed=${detail.didCrash()}, " +
                            "priority=${detail.rendererPriorityAtExit()}, url=$origRequestUrl"
                    }
                    rendererGone.set(true)
                    attempt.finish(ChallengeAttempt.State.Failed)
                    return true
                }
            }

            view.loadUrl(challengeUrl, headers)
        }

        val outcome = attempt.await()

        if (outcome != ChallengeAttempt.State.Succeeded) {
            // A clearance from a failed challenge would only be rejected again
            cookieManager.remove(originalRequest.url, COOKIE_NAMES, 0)
            failures.record(host)
            throw CloudflareBypassException()
        }
    }
    // KNS <--
}

// An origin rule without a port only matches the scheme's default one
private fun HttpUrl.origin(): String {
    return if (port == HttpUrl.defaultPort(scheme)) "$scheme://$host" else "$scheme://$host:$port"
}

private const val INTERACTIVE_FALLBACK_MS = 12_000L
private val SERVER_CHECK = arrayOf("cloudflare-nginx", "cloudflare")
private val COOKIE_NAMES = listOf("cf_clearance")

private class CloudflareBypassException : Exception()
