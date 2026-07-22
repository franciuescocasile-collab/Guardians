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

    /** Tetto massimo di una pausa: 12 ore. */
    const val MAX_MINUTES = 12 * 60

    /** Quando finisce la pausa (epoch ms); 0 = nessuna pausa. */
    private val _pauseUntil = MutableStateFlow(0L)
    val pauseUntil: StateFlow<Long> = _pauseUntil

    /** Minuti della pausa preimpostata; 0 = non ancora scelta (primo uso). */
    private val _presetMinutes = MutableStateFlow(0)
    val presetMinutes: StateFlow<Int> = _presetMinutes

    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val p = prefs(context)
        _pauseUntil.value = p.getLong(KEY_UNTIL, 0L)
        _presetMinutes.value = p.getInt(KEY_PRESET, 0)
    }

    /** True se la pausa è in corso ADESSO (i guardiani non intervengono). */
    fun isPaused(): Boolean = System.currentTimeMillis() < _pauseUntil.value

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
