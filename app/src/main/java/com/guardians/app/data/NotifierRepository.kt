package com.guardians.app.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.guardians.app.service.ReminderReceiver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalTime
import java.util.UUID

/**
 * Il Notificatore — promemoria USA E GETTA (one-time). L'utente pianifica un
 * avviso (scorciatoie +15m/+30m/+1h o orario preciso); quando scatta, la
 * notifica si auto-distrugge: l'allarme viene tolto dall'AlarmManager e il
 * record cancellato dal DB locale. Nessuna routine, nessuna ripetizione.
 *
 * Un Filtro Notturno ("Dalle/Alle") impedisce l'invio acustico durante la notte.
 */
object NotifierRepository {

    private const val PREFS = "guardians_prefs"
    private const val KEY_REMINDERS = "notifier_reminders_v2"
    private const val KEY_NIGHT_FROM = "notifier_night_from"   // minuti dalla mezzanotte
    private const val KEY_NIGHT_TO = "notifier_night_to"
    private const val KEY_NIGHT_ON = "notifier_night_on"

    data class Reminder(
        val id: String,
        val text: String,
        /** Istante del prossimo scatto (epoch ms). */
        val fireAt: Long,
        /** Ripetizione in minuti (0 = una volta sola). Es. 120 = ogni 2 ore. */
        val intervalMin: Int = 0,
    ) {
        val recurring: Boolean get() = intervalMin > 0
    }

    private val _reminders = MutableStateFlow<List<Reminder>>(emptyList())
    val reminders: StateFlow<List<Reminder>> = _reminders

    private val _nightFilter = MutableStateFlow(false)
    val nightFilter: StateFlow<Boolean> = _nightFilter
    private val _nightFrom = MutableStateFlow(23 * 60)
    val nightFrom: StateFlow<Int> = _nightFrom
    private val _nightTo = MutableStateFlow(7 * 60)
    val nightTo: StateFlow<Int> = _nightTo

    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val p = prefs(context)
        _nightFilter.value = p.getBoolean(KEY_NIGHT_ON, false)
        _nightFrom.value = p.getInt(KEY_NIGHT_FROM, 23 * 60)
        _nightTo.value = p.getInt(KEY_NIGHT_TO, 7 * 60)
        p.getString(KEY_REMINDERS, null)?.let { raw ->
            val parsed = try {
                val arr = JSONArray(raw)
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    Reminder(
                        o.getString("id"), o.getString("text"), o.getLong("fireAt"),
                        o.optInt("intervalMin", 0),
                    )
                }
            } catch (_: Exception) {
                emptyList()
            }
            // Pulizia all'avvio: i one-shot scaduti si buttano; i RICORRENTI si
            // fanno avanzare al prossimo scatto futuro. Poi si ri-armano tutti
            // (dopo un riavvio del telefono gli allarmi vanno rimessi).
            val now = System.currentTimeMillis()
            val cleaned = parsed.mapNotNull { r ->
                if (!r.recurring) {
                    if (r.fireAt > now) r else null
                } else {
                    var f = r.fireAt
                    val stepMs = r.intervalMin * 60_000L
                    while (f <= now) f += stepMs
                    r.copy(fireAt = f)
                }
            }
            persist(context, cleaned)
            cleaned.forEach { setAlarm(context, it) }
        }
    }

    /**
     * Pianifica un promemoria per [fireAt]; se [intervalMin] > 0 è RICORRENTE
     * (si ripete ogni tot). Programma anche l'allarme di sistema.
     */
    fun schedule(context: Context, text: String, fireAt: Long, intervalMin: Int = 0) {
        load(context)
        val r = Reminder(UUID.randomUUID().toString(), text.trim(), fireAt, intervalMin)
        persist(context, _reminders.value + r)
        setAlarm(context, r)
    }

    /** Cancella un promemoria (dall'elenco e dall'AlarmManager). */
    fun cancel(context: Context, id: String) {
        load(context)
        cancelAlarm(context, id)
        persist(context, _reminders.value.filterNot { it.id == id })
    }

    /**
     * Chiamato dal receiver dopo aver mostrato la notifica. Un promemoria USA E
     * GETTA si auto-distrugge; uno RICORRENTE riprogramma il prossimo scatto.
     */
    fun onFired(context: Context, id: String) {
        load(context)
        val r = _reminders.value.firstOrNull { it.id == id } ?: return
        if (!r.recurring) {
            persist(context, _reminders.value.filterNot { it.id == id })
            return
        }
        var next = r.fireAt + r.intervalMin * 60_000L
        val now = System.currentTimeMillis()
        while (next <= now) next += r.intervalMin * 60_000L
        val updated = r.copy(fireAt = next)
        persist(context, _reminders.value.map { if (it.id == id) updated else it })
        setAlarm(context, updated)
    }

    /** True se ORA rientra nella fascia notturna silenziata. */
    fun isNightMuted(): Boolean {
        if (!_nightFilter.value) return false
        val now = LocalTime.now().let { it.hour * 60 + it.minute }
        val from = _nightFrom.value
        val to = _nightTo.value
        return if (from < to) now in from until to else now >= from || now < to
    }

    fun setNightFilter(context: Context, on: Boolean) {
        _nightFilter.value = on
        prefs(context).edit().putBoolean(KEY_NIGHT_ON, on).apply()
    }

    fun setNightFrom(context: Context, minute: Int) {
        _nightFrom.value = minute
        prefs(context).edit().putInt(KEY_NIGHT_FROM, minute).apply()
    }

    fun setNightTo(context: Context, minute: Int) {
        _nightTo.value = minute
        prefs(context).edit().putInt(KEY_NIGHT_TO, minute).apply()
    }

    // ------------------------------------------------------ AlarmManager
    private fun pendingIntent(context: Context, r: Reminder): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("id", r.id)
            putExtra("text", r.text)
        }
        return PendingIntent.getBroadcast(
            context, r.id.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun setAlarm(context: Context, r: Reminder) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            am.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, r.fireAt, pendingIntent(context, r),
            )
        } catch (_: SecurityException) {
            // Senza permesso esatto: ripiego su un allarme inesatto (best effort).
            am.set(AlarmManager.RTC_WAKEUP, r.fireAt, pendingIntent(context, r))
        }
    }

    private fun cancelAlarm(context: Context, id: String) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            context, id.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        am.cancel(pi)
    }

    private fun persist(context: Context, list: List<Reminder>) {
        val sorted = list.sortedBy { it.fireAt }
        _reminders.value = sorted
        val arr = JSONArray()
        sorted.forEach { r ->
            arr.put(JSONObject().apply {
                put("id", r.id)
                put("text", r.text)
                put("fireAt", r.fireAt)
                put("intervalMin", r.intervalMin)
            })
        }
        prefs(context).edit().putString(KEY_REMINDERS, arr.toString()).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
