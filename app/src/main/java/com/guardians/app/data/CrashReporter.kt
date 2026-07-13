package com.guardians.app.data

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Cattura-crash fatto in casa. Sui telefoni Samsung il logcat via adb spesso è
 * muto, quindi qui installiamo un handler globale che, a ogni eccezione non
 * gestita (in qualsiasi thread: UI o servizio), salva lo stack trace completo
 * in un file nella cartella privata dell'app. Poi ripassiamo il crash al gestore
 * originale del sistema, così il comportamento resta invariato.
 *
 * Il file si recupera via adb (senza root, è dentro la sandbox dell'app):
 *   adb exec-out run-as com.guardians.app cat files/last_crash.txt
 */
object CrashReporter {

    private const val FILE_NAME = "last_crash.txt"
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeCrash(appContext, thread, throwable)
            } catch (_: Throwable) {
                // Non possiamo permetterci che il cattura-crash crashi a sua volta.
            }
            // Rilancia al gestore di sistema (mostra il dialog "app terminata").
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun writeCrash(context: Context, thread: Thread, throwable: Throwable) {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val sw = java.io.StringWriter()
        throwable.printStackTrace(java.io.PrintWriter(sw))
        val text = buildString {
            append("=== Guardians crash ===\n")
            append("quando: ").append(stamp).append('\n')
            append("thread: ").append(thread.name).append('\n')
            append("versione: ").append(appVersion(context)).append('\n')
            append("modello: ")
                .append(android.os.Build.MANUFACTURER).append(' ')
                .append(android.os.Build.MODEL)
                .append(" (Android ").append(android.os.Build.VERSION.RELEASE).append(")\n\n")
            append(sw.toString())
        }
        File(context.filesDir, FILE_NAME).writeText(text)
    }

    /** Legge l'ultimo crash salvato (o null se non ce ne sono). */
    fun lastCrash(context: Context): String? {
        val f = File(context.applicationContext.filesDir, FILE_NAME)
        return if (f.exists()) f.readText() else null
    }

    /** Cancella l'ultimo crash salvato (dopo averlo letto/risolto). */
    fun clear(context: Context) {
        try {
            File(context.applicationContext.filesDir, FILE_NAME).delete()
        } catch (_: Throwable) {
        }
    }

    private fun appVersion(context: Context): String = try {
        val pm = context.packageManager
        val info = pm.getPackageInfo(context.packageName, 0)
        val code = if (android.os.Build.VERSION.SDK_INT >= 28) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION") info.versionCode.toLong()
        }
        "${info.versionName} ($code)"
    } catch (_: Throwable) {
        "?"
    }
}
