package exh.log

import com.elvishew.xlog.printer.Printer

@Suppress("UNUSED_PARAMETER", "unused")
class CrashlyticsPrinter(private val logLevel: Int) : Printer {
    override fun println(logLevel: Int, tag: String?, msg: String?) = Unit
}
