package eu.kanade.tachiyomi.ui.setting.connections

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import eu.kanade.domain.connections.service.ConnectionsPreferences
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.connections.ConnectionsManager
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import eu.kanade.tachiyomi.util.system.toast
import tachiyomi.i18n.MR
import uy.kohesive.injekt.injectLazy
import java.io.File

class DiscordLoginActivity : BaseActivity() {

    private val connectionsManager: ConnectionsManager by injectLazy()
    private val connectionsPreferences: ConnectionsPreferences by injectLazy()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.discord_login_activity)
        val webView = findViewById<WebView>(R.id.webview)

        webView.apply {
            settings.javaScriptEnabled = true
            settings.databaseEnabled = true
            settings.domStorageEnabled = true
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                if (url != null && url.endsWith("/app")) {
                    webView.stopLoading()
                    webView.evaluateJavascript(
                        """
                            (()=>{const i=document.createElement('iframe');document.body.append(i);const t=JSON.parse(i.contentWindow.localStorage.token);i.remove();return t})()
                        """.trimIndent(),
                    ) {
                        login(it.trim('"'))
                    }
                }
            }
        }
        webView.loadUrl("https://discord.com/login")
    }

    private fun login(token: String) {
        if (!validateToken(token)) {
            toast("Login Failed: Failed to retrieve token")
        } else {
            connectionsPreferences.connectionsToken(connectionsManager.discord).set(token)
            connectionsPreferences.setConnectionsCredentials(connectionsManager.discord, "Discord", "Logged In")
            toast(MR.strings.login_success)
            Log.d("discord_login_tachiyomisy", "Logged in with token: ${token.take(6)}...${token.takeLast(6)}")
        }
        applicationInfo.dataDir.let { File("$it/app_webview/").deleteRecursively() }
        finish()
    }

    private fun validateToken(token: String): Boolean {
        // Basic validation for Discord tokens
        return Regex("""^[\w-]{24}\.[\w-]{6}\.[\w-]{27}\w+?$""").matches(token)
    }
}
