package com.guardians.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Modalità Viaggio: sospende soltanto i guardiani per un intervallo (fino a un
 * istante o a una data), così in trasferta non devi disattivare e riattivare le
 * squadre a mano. Il tempo e i dati contano NORMALMENTE: viaggio = solo pausa
 * dei blocchi, niente di più. Allo scadere la protezione si ripristina da sola.
 */
object TravelRepository {

    private const val PREFS = "guardians_prefs"
    private const val KEY_UNTIL = "travel_until"       // epoch ms (0 = spenta)

    private val _activeUntil = MutableStateFlow(0L)
    val activeUntil: StateFlow<Long> = _activeUntil

    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        _activeUntil.value = prefs(context).getLong(KEY_UNTIL, 0L)
    }

    /** True se la Modalità Viaggio è in corso adesso. */
    fun isActive(): Boolean = System.currentTimeMillis() < _activeUntil.value

    /** Attiva il viaggio fino a [untilEpochMs] (0 = disattiva). */
    fun setUntil(context: Context, untilEpochMs: Long) {
        _activeUntil.value = untilEpochMs
        prefs(context).edit().putLong(KEY_UNTIL, untilEpochMs).apply()
    }

    fun stop(context: Context) = setUntil(context, 0L)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
