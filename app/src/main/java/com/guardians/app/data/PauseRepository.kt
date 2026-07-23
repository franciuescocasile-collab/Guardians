package com.guardians.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * La PAUSA dei guardiani (sostituisce del tutto i vecchi incantesimi):
 * due pulsanti sopra le squadre — uno chiede quanto, l'altro applica la pausa
 * PREIMPOSTATA (modificabile con la pressione prolungata). Durante la pausa i
 * guardiani vedono ma non intervengono; allo scadere esatto si riattivano da
 * soli (lo stato è solo una scadenza nel tempo). Massimo 12 ore.
 */
object PauseRepository {

    private const val PREFS = "guardians_prefs"
    private const val KEY_UNTIL = "pause_until"
    private const val KEY_PRESET = "pause_preset_min"
    private const val KEY_TEAM_PAUSES = "pause_teams"

    /** Tetto massimo di una pausa: 12 ore. */
    const val MAX_MINUTES = 12 * 60

    /** Quando finisce la pausa GLOBALE (epoch ms); 0 = nessuna. */
    private val _pauseUntil = MutableStateFlow(0L)
    val pauseUntil: StateFlow<Long> = _pauseUntil

    /** Minuti della pausa preimpostata; 0 = non ancora scelta (primo uso). */
    private val _presetMinutes = MutableStateFlow(0)
    val presetMinutes: StateFlow<Int> = _presetMinutes

    /** Pause per SINGOLA squadra: nome squadra → epoch di fine (Squadre 3). */
    private val _teamPauses = MutableStateFlow<Map<String, Long>>(emptyMap())
    val teamPauses: StateFlow<Map<String, Long>> = _teamPauses

    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val p = prefs(context)
        _pauseUntil.value = p.getLong(KEY_UNTIL, 0L)
        _presetMinutes.value = p.getInt(KEY_PRESET, 0)
        _teamPauses.value = try {
            val o = org.json.JSONObject(p.getString(KEY_TEAM_PAUSES, "{}")!!)
            buildMap { o.keys().forEach { t -> put(t, o.getLong(t)) } }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    /** True se la pausa GLOBALE è in corso (tutte le squadre ferme). */
    fun isPaused(): Boolean = System.currentTimeMillis() < _pauseUntil.value

    /** True se [team] è fermo adesso: per pausa globale O pausa di squadra. */
    fun isTeamPaused(team: String): Boolean {
        if (isPaused()) return true
        return System.currentTimeMillis() < (_teamPauses.value[team] ?: 0L)
    }

    /** Fine pausa (epoch) della singola squadra, o 0. */
    fun teamPauseUntil(team: String): Long = _teamPauses.value[team] ?: 0L

    /** Avvia una pausa solo per [team] (tetto 12h). */
    fun startTeamPause(context: Context, team: String, minutes: Int) {
        val until = System.currentTimeMillis() + minutes.coerceIn(1, MAX_MINUTES) * 60_000L
        _teamPauses.value = _teamPauses.value + (team to until)
        persistTeams(context)
    }

    fun endTeamPause(context: Context, team: String) {
        _teamPauses.value = _teamPauses.value - team
        persistTeams(context)
        com.guardians.app.service.MonitorService.start(context)
    }

    private fun persistTeams(context: Context) {
        val o = org.json.JSONObject()
        _teamPauses.value.forEach { (t, u) -> o.put(t, u) }
        prefs(context).edit().putString(KEY_TEAM_PAUSES, o.toString()).apply()
    }

    /** Avvia una pausa di [minutes] (tetto 12h). */
    fun startPause(context: Context, minutes: Int) {
        val m = minutes.coerceIn(1, MAX_MINUTES)
        _pauseUntil.value = System.currentTimeMillis() + m * 60_000L
        prefs(context).edit().putLong(KEY_UNTIL, _pauseUntil.value).apply()
    }

    /** Termina la pausa subito: i guardiani riprendono all'istante. */
    fun endPause(context: Context) {
        _pauseUntil.value = 0L
        prefs(context).edit().putLong(KEY_UNTIL, 0L).apply()
        // Sveglia il motore subito, senza aspettare il prossimo giro.
        com.guardians.app.service.MonitorService.start(context)
    }

    /** Salva i minuti della pausa preimpostata (pulsante di destra). */
    fun setPreset(context: Context, minutes: Int) {
        _presetMinutes.value = minutes.coerceIn(1, MAX_MINUTES)
        prefs(context).edit().putInt(KEY_PRESET, _presetMinutes.value).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
