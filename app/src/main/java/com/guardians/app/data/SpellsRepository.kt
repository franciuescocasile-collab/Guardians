package com.guardians.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Gli Incantesimi:
 * - CONGELAMENTO: blocco totale del telefono per X tempo (per concentrarsi).
 * - OMBRA: sospende la protezione (tutte le squadre, o solo quelle scelte).
 *   È l'UNICO modo per disattivare una Squadra. Allo scadere del countdown la
 *   protezione torna da sola com'era (lo stato è solo una scadenza nel tempo).
 */
object SpellsRepository {

    private const val PREFS = "guardians_prefs"
    private const val KEY_FREEZE = "spell_freeze_until"
    private const val KEY_SHADOW = "spell_shadow_until"
    private const val KEY_SHADOW_TEAMS = "spell_shadow_teams"
    private const val KEY_SHADOW_DURATIONS = "spell_shadow_duration_counts"
    private const val KEY_FREEZE_STARTED = "spell_freeze_started"
    private const val KEY_FREEZE_OVERTIME = "spell_freeze_overtime"

    /** Scelte rapide predefinite (minuti) quando non c'è ancora uno storico. */
    private val DEFAULT_QUICK_MINUTES = listOf(15, 60, 180)

    private val _freezeUntil = MutableStateFlow(0L)
    val freezeUntil: StateFlow<Long> = _freezeUntil

    /** Quando è iniziata la sessione di Congelamento (0 = nessuna). */
    private val _freezeStartedAt = MutableStateFlow(0L)
    val freezeStartedAt: StateFlow<Long> = _freezeStartedAt

    /**
     * "Continua a contare dopo la scadenza": allo 00:00 la sessione NON finisce,
     * il conteggio diventa un cronometro (+mm:ss) finché non la termini tu.
     */
    private val _freezeOvertime = MutableStateFlow(false)
    val freezeOvertime: StateFlow<Boolean> = _freezeOvertime

    private val _shadowUntil = MutableStateFlow(0L)
    val shadowUntil: StateFlow<Long> = _shadowUntil

    /** Squadre sospese dall'Ombra; vuoto = Ombra globale (tutte). */
    private val _shadowTeams = MutableStateFlow<Set<String>>(emptySet())
    val shadowTeams: StateFlow<Set<String>> = _shadowTeams

    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val p = prefs(context)
        _freezeUntil.value = p.getLong(KEY_FREEZE, 0L)
        _freezeStartedAt.value = p.getLong(KEY_FREEZE_STARTED, 0L)
        _freezeOvertime.value = p.getBoolean(KEY_FREEZE_OVERTIME, false)
        _shadowUntil.value = p.getLong(KEY_SHADOW, 0L)
        val raw = p.getString(KEY_SHADOW_TEAMS, null)
        if (raw != null) {
            _shadowTeams.value = try {
                val arr = JSONArray(raw)
                (0 until arr.length()).map { arr.getString(it) }.toSet()
            } catch (_: Exception) {
                emptySet()
            }
        }
    }

    fun castFreeze(context: Context, untilEpochMs: Long) {
        _freezeUntil.value = untilEpochMs
        _freezeStartedAt.value = if (untilEpochMs > 0L) System.currentTimeMillis() else 0L
        prefs(context).edit()
            .putLong(KEY_FREEZE, untilEpochMs)
            .putLong(KEY_FREEZE_STARTED, _freezeStartedAt.value)
            .apply()
    }

    fun breakFreeze(context: Context) = castFreeze(context, 0L)

    fun setFreezeOvertime(context: Context, enabled: Boolean) {
        _freezeOvertime.value = enabled
        prefs(context).edit().putBoolean(KEY_FREEZE_OVERTIME, enabled).apply()
    }

    /** True se la sessione di Congelamento è in corso (countdown O cronometro extra). */
    fun isFreezeSessionActive(): Boolean {
        val until = _freezeUntil.value
        if (until <= 0L) return false
        return System.currentTimeMillis() < until || _freezeOvertime.value
    }

    /** [teams] vuoto = Ombra globale. */
    fun castShadow(context: Context, untilEpochMs: Long, teams: Set<String>) {
        val wasActive = System.currentTimeMillis() < _shadowUntil.value
        _shadowUntil.value = untilEpochMs
        _shadowTeams.value = teams
        prefs(context).edit()
            .putLong(KEY_SHADOW, untilEpochMs)
            .putString(KEY_SHADOW_TEAMS, JSONArray(teams.toList()).toString())
            .apply()
        // Lanciare l'Ombra è un Bypass: infrazione critica per la Condotta.
        // (Solo un NUOVO lancio, non il ripristino/spegnimento.)
        if (untilEpochMs > System.currentTimeMillis() && !wasActive) {
            ConductRepository.registerSpellMalus(context)
        }
    }

    fun breakShadow(context: Context) = castShadow(context, 0L, emptySet())

    /** Conta una durata d'Ombra scelta, per proporre scelte rapide su misura. */
    fun recordShadowDuration(context: Context, minutes: Int) {
        if (minutes <= 0) return
        val counts = durationCounts(context).toMutableMap()
        counts[minutes] = (counts[minutes] ?: 0) + 1
        val o = JSONObject()
        counts.forEach { (min, count) -> o.put(min.toString(), count) }
        prefs(context).edit().putString(KEY_SHADOW_DURATIONS, o.toString()).apply()
    }

    /**
     * Le 3 durate (in minuti) da proporre come scelta rapida: le più usate
     * dall'utente, completate con i valori predefiniti (15 min, 1 ora, 3 ore).
     */
    fun quickShadowMinutes(context: Context): List<Int> {
        val favourites = durationCounts(context).entries
            .sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenBy { it.key })
            .map { it.key }
        return (favourites + DEFAULT_QUICK_MINUTES).distinct().take(3).sorted()
    }

    private fun durationCounts(context: Context): Map<Int, Int> {
        val raw = prefs(context).getString(KEY_SHADOW_DURATIONS, null) ?: return emptyMap()
        return try {
            val o = JSONObject(raw)
            buildMap { o.keys().forEach { k -> put(k.toInt(), o.getInt(k)) } }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    /** True se questo timer è al momento sospeso dall'Ombra. */
    fun isShadowed(teamName: String): Boolean {
        if (System.currentTimeMillis() >= _shadowUntil.value) return false
        val teams = _shadowTeams.value
        return teams.isEmpty() || teams.contains(teamName)
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
