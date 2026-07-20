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
    private const val KEY_ARMED = "smart_alarm_armed"
    private const val KEY_SLEEP_START = "smart_alarm_sleep_start"
    private const val KEY_ARMED_AT = "smart_alarm_armed_at"
    private const val REQUEST_CODE = 4242

    /** Un ciclo di sonno completo (~90 minuti). */
    const val CYCLE_MS = 90L * 60_000L

    /** Tempo medio per addormentarsi, aggiunto in coda al calcolo. */
    const val FALL_ASLEEP_MS = 15L * 60_000L

    /**
     * Schermo spento in modo continuo per almeno questo tempo = "ti sei
     * addormentato" (la sveglia intelligente parte da qui, 12.1).
     */
    const val SLEEP_ONSET_MS = 20L * 60_000L

    /** Quando suonerà (epoch ms); 0 = nessuna sveglia attiva. */
    private val _alarmAt = MutableStateFlow(0L)
    val alarmAt: StateFlow<Long> = _alarmAt

    /**
     * ARMATA (12.1): l'utente ha attivato la sveglia intelligente ma NON è
     * ancora programmata a un orario; l'app aspetta di accorgersi che ti sei
     * addormentato e solo allora calcola l'orario del risveglio.
     */
    private val _armed = MutableStateFlow(false)
    val armed: StateFlow<Boolean> = _armed

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
        _armed.value = p.getBoolean(KEY_ARMED, false)
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

    fun isArmed(): Boolean = _armed.value

    /**
     * ARMA la sveglia intelligente (12.1): da ora l'app aspetta di accorgersi
     * che ti addormenti (schermo spento a lungo) e SOLO ALLORA programma il
     * risveglio dopo [cycles] cicli. Niente orario fisso adesso.
     */
    fun arm(context: Context) {
        load(context)
        cancelAlarmOnly(context)
        _armed.value = true
        prefs(context).edit()
            .putBoolean(KEY_ARMED, true)
            .putLong(KEY_ARMED_AT, System.currentTimeMillis())
            .putLong(KEY_SLEEP_START, 0L)
            .apply()
    }

    /**
     * Chiamata dal servizio quando lo schermo è spento da abbastanza tempo
     * ([sleepStart] = quando si è spento). Programma (o riprogramma se il sonno
     * è ricominciato più tardi) il risveglio a fine cicli.
     */
    fun onSleepDetected(context: Context, sleepStart: Long) {
        load(context)
        if (!_armed.value) return
        val p = prefs(context)
        // Solo per addormentamenti iniziati DOPO l'attivazione.
        if (sleepStart < p.getLong(KEY_ARMED_AT, 0L)) return
        // UNA SOLA VOLTA per notte (2.1): il primo addormentamento fissa
        // l'orario e NON si tocca più. I risvegli notturni (pipì, sguardo
        // all'ora) NON spostano la sveglia: altrimenti chi va a letto a
        // mezzanotte e si alza alle 4 rischierebbe di dormire 13 ore.
        if (p.getLong(KEY_SLEEP_START, 0L) != 0L) return
        val wakeAt = sleepStart + _cycles.value * CYCLE_MS
        // Se ci sono giorni di ripetizione, sveglia solo se il RISVEGLIO cade in
        // uno di quelli (i giorni si riferiscono al giorno del risveglio).
        val set = _days.value
        if (set.isNotEmpty()) {
            val wakeDow = java.time.Instant.ofEpochMilli(wakeAt)
                .atZone(java.time.ZoneId.systemDefault()).dayOfWeek.value
            if (wakeDow !in set) return
        }
        p.edit().putLong(KEY_SLEEP_START, sleepStart).apply()
        // Risveglio = inizio sonno + cicli interi (l'addormentamento è già
        // avvenuto, quindi niente margine di FALL_ASLEEP qui).
        scheduleAt(context, wakeAt)
    }

    /**
     * La sveglia è appena suonata: se non ci sono giorni di ripetizione si
     * disarma; se ci sono, resta armata e riparte l'attesa del prossimo sonno.
     */
    fun onFired(context: Context) {
        load(context)
        cancelAlarmOnly(context)
        if (_days.value.isEmpty()) {
            _armed.value = false
            prefs(context).edit()
                .putBoolean(KEY_ARMED, false)
                .putLong(KEY_SLEEP_START, 0L)
                .apply()
        } else {
            _armed.value = true
            prefs(context).edit()
                .putBoolean(KEY_ARMED, true)
                .putLong(KEY_SLEEP_START, 0L)
                .putLong(KEY_ARMED_AT, System.currentTimeMillis())
                .apply()
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

    /** Annulla SOLO l'allarme programmato (senza toccare lo stato "armata"). */
    private fun cancelAlarmOnly(context: Context) {
        try {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.cancel(firePending(context))
        } catch (_: Throwable) {
        }
        _alarmAt.value = 0L
        prefs(context).edit().putLong(KEY_ALARM_AT, 0L).apply()
    }

    /** Spegne/annulla la sveglia (futura, in attesa o appena suonata) e disarma. */
    fun cancel(context: Context) {
        load(context)
        cancelAlarmOnly(context)
        _armed.value = false
        prefs(context).edit()
            .putBoolean(KEY_ARMED, false)
            .putLong(KEY_SLEEP_START, 0L)
            .apply()
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
