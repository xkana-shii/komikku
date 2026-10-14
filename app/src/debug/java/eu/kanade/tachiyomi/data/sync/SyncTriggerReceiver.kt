package eu.kanade.tachiyomi.data.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.runBlocking

/**
 * Debug-only hook so automated tests can trigger a sync without UI:
 * `adb shell am broadcast -a <appId>.TRIGGER_SYNC -p <appId>`
 *
 * Ported from TachiyomiSY (jobobby04/TachiyomiSY#1648).
 */
class SyncTriggerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        runBlocking { SyncDataJob.startNow(context, manual = true) }
    }
}
