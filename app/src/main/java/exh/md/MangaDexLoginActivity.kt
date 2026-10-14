package exh.md

import android.net.Uri
import androidx.lifecycle.lifecycleScope
import eu.kanade.tachiyomi.ui.setting.track.BaseOAuthLoginActivity
import exh.md.utils.MdUtil
import mihon.app.di.appGraph
import tachiyomi.core.common.util.lang.launchIO

class MangaDexLoginActivity : BaseOAuthLoginActivity() {

    override fun handleResult(uri: Uri) {
        val code = uri.getQueryParameter("code")
        if (code != null) {
            lifecycleScope.launchIO {
                val sourceManager = appGraph.sourceManager
                MdUtil.getEnabledMangaDex(appGraph.sourcePreferences, sourceManager)?.login(code)
                returnToSettings()
            }
        } else {
            lifecycleScope.launchIO {
                val sourceManager = appGraph.sourceManager
                MdUtil.getEnabledMangaDex(appGraph.sourcePreferences, sourceManager)?.logout()
                returnToSettings()
            }
        }
    }
}
