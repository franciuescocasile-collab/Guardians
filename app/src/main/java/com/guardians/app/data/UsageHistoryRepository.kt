package com.guardians.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

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
    private const val MAX_DAYS = 400

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
        val updated = (_history.value + (date to totalMs))
            .toSortedMap()
            .let { map ->
                if (map.size > MAX_DAYS) {
                    map.entries.drop(map.size - MAX_DAYS).associate { it.key to it.value }
                } else {
                    map
                }
            }
        _history.value = updated
        prefs(context).edit()
            .putString(KEY_HISTORY, JSONObject(updated).toString())
            .apply()
    }

    /** Media giornaliera storica (ms/giorno) sui giorni registrati, 0 se vuoto. */
    fun dailyAverageMs(): Long {
        val values = _history.value.values
        return if (values.isEmpty()) 0L else values.sum() / values.size
    }

    // ---- Calendario dell'obiettivo: istantanee IMMUTABILI giorno per giorno.
    private const val KEY_GOAL_SNAPSHOTS = "goal_snapshots"

    private val _goalSnapshots = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val goalSnapshots: StateFlow<Map<String, Boolean>> = _goalSnapshots

    fun loadGoalSnapshots(context: Context) {
        if (_goalSnapshots.value.isNotEmpty()) return
        val raw = prefs(context).getString(KEY_GOAL_SNAPSHOTS, null) ?: return
        _goalSnapshots.value = try {
            val o = JSONObject(raw)
            buildMap { o.keys().forEach { k -> put(k, o.getBoolean(k)) } }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    /**
     * REGOLA D'ORO: lo scatto di un giorno si scrive UNA volta sola, con
     * l'obiettivo in vigore QUEL giorno. Cambiare l'obiettivo domani non
     * ricolora il passato.
     */
    fun recordGoalSnapshot(context: Context, date: String, underGoal: Boolean) {
        loadGoalSnapshots(context)
        if (_goalSnapshots.value.containsKey(date)) return
        val updated = _goalSnapshots.value + (date to underGoal)
        _goalSnapshots.value = updated
        val o = JSONObject()
        updated.forEach { (k, v) -> o.put(k, v) }
        prefs(context).edit().putString(KEY_GOAL_SNAPSHOTS, o.toString()).apply()
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
