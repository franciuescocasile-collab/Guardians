package com.guardians.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.time.LocalDate

/**
 * Storico dell'uso del telefono: millisecondi totali per giorno (aaaa-mm-gg),
 * registrati dal servizio a ogni cambio di giornata. Serve per la media storica
 * da confrontare con la settimana corrente e per il resoconto settimanale.
 */
object UsageHistoryRepository {

    private const val PREFS = "guardians_prefs"
    private const val KEY_HISTORY = "usage_history"
    private const val KEY_VERSION = "usage_history_version"
    private const val CURRENT_VERSION = 2

    // MEMORIZZAZIONE A STRATI (regola dell'utente): entro 10 anni si tiene il
    // dettaglio GIORNALIERO; oltre i 10 anni i giorni si compattano in una
    // MEDIA MENSILE (un record al mese), così lo storico non cresce all'infinito.
    // Tetto di sicurezza a ~2 MB: se per qualche motivo i record esplodessero,
    // si compatta comunque.
    private const val FULL_RETENTION_DAYS = 3653L      // ~10 anni
    private const val MAX_RECORDS = 80_000             // ~2 MB di sicurezza

    private val _history = MutableStateFlow<Map<String, Long>>(emptyMap())
    val history: StateFlow<Map<String, Long>> = _history

    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val raw = prefs(context).getString(KEY_HISTORY, null) ?: return
        _history.value = try {
            val o = JSONObject(raw)
            buildMap { o.keys().forEach { k -> put(k, o.getLong(k)) } }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    /** Registra (o aggiorna) il totale di un giorno. */
    fun record(context: Context, date: String, totalMs: Long) {
        load(context)
        val updated = applyLayers(_history.value + (date to totalMs)).toSortedMap()
        _history.value = updated
        prefs(context).edit()
            .putString(KEY_HISTORY, JSONObject(updated).toString())
            .apply()
    }

    /**
     * Compatta i dati oltre i 10 anni in medie MENSILI (chiave "aaaa-mm-01").
     * Sotto i 10 anni e sotto il tetto di record, non tocca nulla (no-op veloce).
     */
    private fun applyLayers(map: Map<String, Long>): Map<String, Long> {
        val cutoff = LocalDate.now().minusDays(FULL_RETENTION_DAYS)
        val tooMany = map.size > MAX_RECORDS
        val hasOld = map.keys.any { k ->
            runCatching { LocalDate.parse(k).isBefore(cutoff) }.getOrDefault(false)
        }
        if (!hasOld && !tooMany) return map

        val kept = HashMap<String, Long>()
        val monthAgg = HashMap<String, Pair<Long, Int>>()
        for ((k, v) in map) {
            val d = runCatching { LocalDate.parse(k) }.getOrNull()
            if (d != null && d.isBefore(cutoff)) {
                val ym = "%04d-%02d-01".format(d.year, d.monthValue)
                val cur = monthAgg[ym] ?: (0L to 0)
                monthAgg[ym] = (cur.first + v) to (cur.second + 1)
            } else {
                kept[k] = v
            }
        }
        monthAgg.forEach { (ym, sc) -> kept[ym] = if (sc.second == 0) 0L else sc.first / sc.second }
        return kept
    }

    /** Media giornaliera storica (ms/giorno) sui giorni registrati, 0 se vuoto. */
    fun dailyAverageMs(): Long {
        val values = _history.value.values
        return if (values.isEmpty()) 0L else values.sum() / values.size
    }

    // ---- Calendario dell'obiettivo: istantanee IMMUTABILI giorno per giorno.
    private const val KEY_GOAL_SNAPSHOTS = "goal_snapshots"
    private const val KEY_GOAL_VALUES = "goal_value_snapshots"

    private val _goalSnapshots = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val goalSnapshots: StateFlow<Map<String, Boolean>> = _goalSnapshots

    // Il VALORE (minuti) dell'obiettivo in vigore quel giorno: serve al
    // commentino del calendario, che deve mostrare l'obiettivo DI ALLORA.
    private val _goalValues = MutableStateFlow<Map<String, Int>>(emptyMap())
    val goalValues: StateFlow<Map<String, Int>> = _goalValues

    fun loadGoalSnapshots(context: Context) {
        if (_goalSnapshots.value.isEmpty()) {
            prefs(context).getString(KEY_GOAL_SNAPSHOTS, null)?.let { raw ->
                _goalSnapshots.value = try {
                    val o = JSONObject(raw)
                    buildMap { o.keys().forEach { k -> put(k, o.getBoolean(k)) } }
                } catch (_: Exception) {
                    emptyMap()
                }
            }
        }
        if (_goalValues.value.isEmpty()) {
            prefs(context).getString(KEY_GOAL_VALUES, null)?.let { raw ->
                _goalValues.value = try {
                    val o = JSONObject(raw)
                    buildMap { o.keys().forEach { k -> put(k, o.getInt(k)) } }
                } catch (_: Exception) {
                    emptyMap()
                }
            }
        }
    }

    /**
     * REGOLA D'ORO: lo scatto di un giorno si scrive UNA volta sola, con
     * l'obiettivo in vigore QUEL giorno (sia il verdetto sotto/sopra sia il
     * VALORE in minuti). Cambiare l'obiettivo domani non ricolora il passato.
     */
    fun recordGoalSnapshot(context: Context, date: String, underGoal: Boolean, goalMinutes: Int) {
        loadGoalSnapshots(context)
        if (_goalSnapshots.value.containsKey(date)) return
        val updated = _goalSnapshots.value + (date to underGoal)
        _goalSnapshots.value = updated
        val o = JSONObject()
        updated.forEach { (k, v) -> o.put(k, v) }
        val updatedValues = _goalValues.value + (date to goalMinutes)
        _goalValues.value = updatedValues
        val ov = JSONObject()
        updatedValues.forEach { (k, v) -> ov.put(k, v) }
        prefs(context).edit()
            .putString(KEY_GOAL_SNAPSHOTS, o.toString())
            .putString(KEY_GOAL_VALUES, ov.toString())
            .apply()
    }

    /** JSON dello storico per il backup. */
    fun exportJson(context: Context): String {
        load(context)
        return JSONObject(_history.value).toString()
    }

    /** Ripristina lo storico da un backup (lancia eccezione se non valido). */
    fun importJson(context: Context, raw: String) {
        val o = JSONObject(raw)
        val parsed = buildMap { o.keys().forEach { k -> put(k, o.getLong(k)) } }
        _history.value = parsed
        prefs(context).edit().putString(KEY_HISTORY, JSONObject(parsed).toString()).apply()
    }

    /** Cancella tutto lo storico (usato dalla migrazione dei dati gonfiati). */
    fun clear(context: Context) {
        _history.value = emptyMap()
        prefs(context).edit().remove(KEY_HISTORY).apply()
    }

    /**
     * Azzera lo storico una volta sola quando cambia il metodo di calcolo, così i
     * vecchi totali gonfiati non falsano più la media. Va richiamata all'apertura
     * delle Statistiche prima di riseminare.
     */
    fun migrateIfNeeded(context: Context) {
        load(context)
        val p = prefs(context)
        if (p.getInt(KEY_VERSION, 1) < CURRENT_VERSION) {
            _history.value = emptyMap()
            p.edit().remove(KEY_HISTORY).putInt(KEY_VERSION, CURRENT_VERSION).apply()
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
