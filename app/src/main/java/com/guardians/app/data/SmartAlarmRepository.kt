package com.guardians.app.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * La Sveglia Intelligente della pagina Sonno: si imposta a CICLI di sonno
 * (~90 minuti l'uno) invece che a orario. L'orario di suono = adesso
 * + ~15 minuti per addormentarsi + cicli × 90 minuti, così ci si sveglia a
 * fine ciclo (sonno leggero) e non nel mezzo del sonno profondo.
 *
 * Usa AlarmManager.setAlarmClock: è l'API delle sveglie vere — esatta, esente
 * dal risparmio energetico e mostra l'icona di sveglia nel sistema.
 */
object SmartAlarmRepository {

    private const val PREFS = "guardians_prefs"
    private const val KEY_ALARM_AT = "smart_alarm_at"
    private const val KEY_CYCLES = "smart_alarm_cycles"
    private const val KEY_DAYS = "smart_alarm_days"
    private const val REQUEST_CODE = 4242

    /** Un ciclo di sonno completo (~90 minuti). */
    const val CYCLE_MS = 90L * 60_000L

    /** Tempo medio per addormentarsi, aggiunto in coda al calcolo. */
    const val FALL_ASLEEP_MS = 15L * 60_000L

    /** Quando suonerà (epoch ms); 0 = nessuna sveglia attiva. */
    private val _alarmAt = MutableStateFlow(0L)
    val alarmAt: StateFlow<Long> = _alarmAt

    /** Cicli scelti (3..7); consigliati 5 (7h30) o 6 (9h). */
    private val _cycles = MutableStateFlow(5)
    val cycles: StateFlow<Int> = _cycles

    /**
     * I giorni in cui la sveglia si RIARMA da sola (ISO 1=lun..7=dom, riferiti
     * al giorno del RISVEGLIO). Vuoto = sveglia una-tantum. Es: {6,7} = solo
     * weekend: dopo lo Spegni, si riprogramma da sola per la prossima mattina
     * di sabato o domenica, alla stessa ora dell'ultimo suono.
     */
    private val _days = MutableStateFlow<Set<Int>>(emptySet())
    val days: StateFlow<Set<Int>> = _days

    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val p = prefs(context)
        _alarmAt.value = p.getLong(KEY_ALARM_AT, 0L)
        _cycles.value = p.getInt(KEY_CYCLES, 5).coerceIn(3, 7)
        _days.value = try {
            val arr = org.json.JSONArray(p.getString(KEY_DAYS, "[]"))
            (0 until arr.length()).map { arr.getInt(it) }.toSet()
        } catch (_: Exception) {
            emptySet()
        }
        // Sveglia già suonata mentre l'app era chiusa: pulizia.
        if (_alarmAt.value in 1 until System.currentTimeMillis()) {
            _alarmAt.value = 0L
            p.edit().putLong(KEY_ALARM_AT, 0L).apply()
        }
    }

    fun setCycles(context: Context, n: Int) {
        _cycles.value = n.coerceIn(3, 7)
        prefs(context).edit().putInt(KEY_CYCLES, _cycles.value).apply()
    }

    fun setDays(context: Context, days: Set<Int>) {
        _days.value = days.filter { it in 1..7 }.toSet()
        prefs(context).edit()
            .putString(KEY_DAYS, org.json.JSONArray(_days.value.toList()).toString())
            .apply()
    }

    /**
     * Dopo lo "Spegni": se ci sono giorni di ripetizione, riarma per la
     * PROSSIMA mattina tra quelli scelti, alla stessa ora dell'ultimo suono.
     * Ritorna l'epoch della prossima sveglia, o 0 se non c'è ripetizione.
     */
    fun rearmNextIfRepeating(context: Context, lastRingAt: Long): Long {
        load(context)
        val set = _days.value
        if (set.isEmpty() || lastRingAt <= 0L) return 0L
        val zone = java.time.ZoneId.systemDefault()
        val lastRing = java.time.Instant.ofEpochMilli(lastRingAt).atZone(zone)
        var day = lastRing.toLocalDate().plusDays(1)
        // Cerca il prossimo giorno selezionato (max una settimana avanti).
        repeat(7) {
            if (day.dayOfWeek.value in set) {
                val at = day.atTime(lastRing.toLocalTime()).atZone(zone)
                    .toInstant().toEpochMilli()
                scheduleAt(context, at)
                return at
            }
            day = day.plusDays(1)
        }
        return 0L
    }

    /** L'orario a cui suonerebbe attivandola ADESSO con [n] cicli. */
    fun wakeAtFor(n: Int, from: Long = System.currentTimeMillis()): Long =
        from + FALL_ASLEEP_MS + n * CYCLE_MS

    /** Attiva la sveglia coi cicli correnti; ritorna l'orario programmato. */
    fun schedule(context: Context): Long {
        val at = wakeAtFor(_cycles.value)
        scheduleAt(context, at)
        return at
    }

    /** Programma (o riprogramma, es. per il Rimanda) la sveglia a [atEpochMs]. */
    fun scheduleAt(context: Context, atEpochMs: Long) {
        load(context)
        try {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            // Toccando l'icona-sveglia di sistema si apre Guardians.
            val show = PendingIntent.getActivity(
                context, REQUEST_CODE,
                Intent(context, com.guardians.app.MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            am.setAlarmClock(
                AlarmManager.AlarmClockInfo(atEpochMs, show),
                firePending(context),
            )
            _alarmAt.value = atEpochMs
            prefs(context).edit().putLong(KEY_ALARM_AT, atEpochMs).apply()
        } catch (_: Throwable) {
        }
    }

    /** Spegne/annulla la sveglia (sia futura sia appena suonata). */
    fun cancel(context: Context) {
        load(context)
        try {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.cancel(firePending(context))
        } catch (_: Throwable) {
        }
        _alarmAt.value = 0L
        prefs(context).edit().putLong(KEY_ALARM_AT, 0L).apply()
    }

    private fun firePending(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, REQUEST_CODE,
            Intent(context, com.guardians.app.service.AlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
