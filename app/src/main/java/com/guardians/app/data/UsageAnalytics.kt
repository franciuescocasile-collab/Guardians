package com.guardians.app.data

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.guardians.app.model.MacroCategory
import com.guardians.app.model.categoryOf

/**
 * Analisi d'uso a EVENTI, scomposta per macro-categoria. Serve al Motore della
 * Condotta e alla sezione app delle Statistiche. Calcolo robusto: una sola app
 * in primo piano alla volta, cap 2h per intervallo, mai oltre 24h/giorno.
 */
object UsageAnalytics {

    /**
     * App di sistema da non contare mai: Guardians stesso, la UI di sistema,
     * ANDROID AUTO (va in primo piano da solo quando colleghi l'auto: non è
     * tempo passato al telefono) e il LAUNCHER (la schermata home non è "uso"
     * — anche Digital Wellbeing la esclude).
     */
    fun ignored(context: Context): Set<String> {
        val set = mutableSetOf(
            context.packageName, "com.android.systemui", "com.android.settings",
            // Android Auto e proiezione in macchina.
            "com.google.android.projection.gearhead",
            "com.google.android.apps.automotive.templates.host",
            // Launcher più comuni (ripiego se il resolver non risponde).
            "com.sec.android.app.launcher", "com.google.android.apps.nexuslauncher",
        )
        // Il VERO launcher predefinito del telefono, chiesto al sistema.
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_MAIN)
                .addCategory(android.content.Intent.CATEGORY_HOME)
            context.packageManager
                .resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName?.let { set.add(it) }
        } catch (_: Exception) {
        }
        return set
    }

    /** Millisecondi per macro-categoria nell'intervallo [startMs, endMs). */
    fun perMacroMs(context: Context, startMs: Long, endMs: Long): Map<MacroCategory, Long> {
        val perApp = perAppMs(context, startMs, endMs)
        val out = HashMap<MacroCategory, Long>()
        for ((pkg, ms) in perApp) {
            val macro = categoryOf(context, pkg).macro
            out[macro] = (out[macro] ?: 0L) + ms
        }
        return out
    }

    /**
     * Millisecondi d'uso per ORA del giorno [dayStartMs] (24 fasce, 00→23).
     * Ripartisce gli intervalli d'uso reali sulle ore che attraversano.
     */
    fun hourlyTotals(context: Context, dayStartMs: Long): LongArray {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val ign = ignored(context)
        val hours = LongArray(24)
        val dayEnd = dayStartMs + 24L * 3_600_000L
        val now = System.currentTimeMillis()
        var curPkg: String? = null
        var curStart = 0L
        fun addInterval(from: Long, to: Long) {
            var a = from
            while (a < to) {
                val hour = ((a - dayStartMs) / 3_600_000L).toInt().coerceIn(0, 23)
                val hourEnd = dayStartMs + (hour + 1) * 3_600_000L
                val b = minOf(to, hourEnd)
                hours[hour] += (b - a)
                a = b
            }
        }
        fun close(at: Long) {
            val p = curPkg ?: return
            val d = at - curStart
            if (d in 1 until 2L * 3_600_000L && p !in ign) {
                addInterval(curStart.coerceAtLeast(dayStartMs), at.coerceAtMost(dayEnd))
            }
            curPkg = null
        }
        try {
            val events = usm.queryEvents(dayStartMs, minOf(dayEnd, now))
            val e = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(e)
                when (e.eventType) {
                    UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                        close(e.timeStamp); curPkg = e.packageName; curStart = e.timeStamp
                    }
                    UsageEvents.Event.MOVE_TO_BACKGROUND ->
                        if (e.packageName == curPkg) close(e.timeStamp)
                    16, 17, 26 -> close(e.timeStamp)
                }
            }
            close(minOf(dayEnd, now))
        } catch (_: Exception) {
        }
        return hours
    }

    /** Millisecondi per pacchetto nell'intervallo [startMs, endMs). */
    fun perAppMs(context: Context, startMs: Long, endMs: Long): Map<String, Long> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val ign = ignored(context)
        val perApp = HashMap<String, Long>()
        var curPkg: String? = null
        var curStart = 0L
        fun close(at: Long) {
            val p = curPkg ?: return
            val d = at - curStart
            if (d in 1 until 2L * 3_600_000L && p !in ign) {
                perApp[p] = (perApp[p] ?: 0L) + d
            }
            curPkg = null
        }
        try {
            val events = usm.queryEvents(startMs, endMs)
            val e = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(e)
                when (e.eventType) {
                    UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                        close(e.timeStamp)
                        curPkg = e.packageName
                        curStart = e.timeStamp
                    }
                    UsageEvents.Event.MOVE_TO_BACKGROUND ->
                        if (e.packageName == curPkg) close(e.timeStamp)
                    16, 17, 26 -> close(e.timeStamp)
                }
            }
            close(minOf(endMs, System.currentTimeMillis()))
        } catch (_: Exception) {
        }
        return perApp
    }
}
