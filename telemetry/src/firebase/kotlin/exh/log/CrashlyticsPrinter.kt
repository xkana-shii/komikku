package exh.log

import com.elvishew.xlog.printer.Printer
import com.google.firebase.Firebase
import com.google.firebase.crashlytics.crashlytics

class CrashlyticsPrinter(private val logLevel: Int) : Printer {
    override fun println(logLevel: Int, tag: String?, msg: String?) {
        if (logLevel >= this.logLevel) {
            try {
                Firebase.crashlytics.log("$logLevel/$tag: $msg")
            } catch (_: Throwable) {
            }
        }
    }
}
