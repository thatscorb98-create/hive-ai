package com.hive.agents

import android.app.Application
import android.util.Log
import java.io.File

/**
 * Diagnostic build helper: records the next uncaught exception so the following launch can
 * show and share the stack instead of only saying "Hive has stopped".
 */
class HiveApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                File(filesDir, "last-crash.txt").writeText(
                    buildString {
                        appendLine("${error.javaClass.name}: ${error.message}")
                        appendLine("thread: ${thread.name}")
                        appendLine()
                        appendLine(Log.getStackTraceString(error))
                    }
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }
}
