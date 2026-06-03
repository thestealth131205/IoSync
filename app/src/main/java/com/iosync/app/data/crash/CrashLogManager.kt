package com.iosync.app.data.crash

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Schreibt unbehandelte Exceptions in Textdateien unter filesDir/crashes/.
 * Max. [MAX_FILES] Dateien werden behalten (älteste werden gelöscht).
 */
object CrashLogManager {

    private const val CRASH_DIR  = "crashes"
    private const val MAX_FILES  = 5

    fun writeCrashLog(context: Context, thread: Thread, throwable: Throwable) {
        try {
            val crashDir = File(context.filesDir, CRASH_DIR).also { it.mkdirs() }
            val sdf      = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
            val timestamp = sdf.format(Date())
            val file     = File(crashDir, "crash_$timestamp.txt")

            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))

            file.writeText(buildString {
                appendLine("=== IoSync Crash Log ===")
                appendLine("Zeit:     $timestamp")
                appendLine("Thread:   ${thread.name}")
                appendLine("Gerät:    ${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("Android:  ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                appendLine("========================")
                appendLine()
                append(sw.toString())
            })

            // Nur die neuesten MAX_FILES Dateien behalten
            crashDir.listFiles()
                ?.sortedByDescending { it.lastModified() }
                ?.drop(MAX_FILES)
                ?.forEach { it.delete() }
        } catch (_: Exception) {
            // Crash-Handler selbst darf nie crashen
        }
    }

    fun readLatestCrashLog(context: Context): String? = try {
        File(context.filesDir, CRASH_DIR)
            .listFiles()
            ?.maxByOrNull { it.lastModified() }
            ?.readText()
    } catch (_: Exception) { null }

    fun readAllCrashLogs(context: Context): List<Pair<String, String>> = try {
        File(context.filesDir, CRASH_DIR)
            .listFiles()
            ?.sortedByDescending { it.lastModified() }
            ?.mapNotNull { f ->
                runCatching { f.name to f.readText() }.getOrNull()
            }
            ?: emptyList()
    } catch (_: Exception) { emptyList() }

    fun clearCrashLogs(context: Context) {
        try {
            File(context.filesDir, CRASH_DIR).listFiles()?.forEach { it.delete() }
        } catch (_: Exception) { }
    }

    fun hasCrashLogs(context: Context): Boolean = try {
        (File(context.filesDir, CRASH_DIR).listFiles()?.size ?: 0) > 0
    } catch (_: Exception) { false }
}
