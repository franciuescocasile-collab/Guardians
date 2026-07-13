package com.guardians.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Sigillo: deterrente contro le modifiche d'impulso. Imposti un tempo di attesa
 * (0 = nessun sigillo). Il countdown scorre SOLO mentre l'app è in primo piano:
 * parte quando Guardians torna davanti ([startSession], chiamata da onResume) e
 * si annulla quando esci ([pauseSession], da onPause). Restando dentro l'app
 * l'attesa NON si resetta; uscendo e rientrando riparte da capo.
 */
object SealRepository {

    private const val PREFS = "guardians_prefs"
    private const val KEY_DELAY = "seal_delay_ms"

    /** Tetto invalicabile del Sigillo: mai più di 5 minuti (sicurezza). */
    const val MAX_DELAY_MS = 5 * 60_000L

    private val _delayMs = MutableStateFlow(0L)
    val delayMs: StateFlow<Long> = _delayMs

    /** Fine dell'attesa della sessione corrente (in memoria; 0 = nessuna attesa). */
    private val _waitReadyAt = MutableStateFlow(0L)
    val waitReadyAt: StateFlow<Long> = _waitReadyAt

    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        _delayMs.value = prefs(context).getLong(KEY_DELAY, 0L)
    }

    /**
     * (Ri)avvia l'attesa del Sigillo: da chiamare quando l'app torna in primo
     * piano. Se un Sigillo è impostato, blocca le modifiche per [delayMs] a
     * partire da adesso.
     */
    fun startSession() {
        _waitReadyAt.value =
            if (_delayMs.value > 0L) System.currentTimeMillis() + _delayMs.value else 0L
    }

    /**
     * Congela l'attesa: da chiamare quando l'app va in secondo piano. Il tempo
     * passato FUORI dall'app non conta: al rientro [startSession] riparte da capo.
     */
    fun pauseSession() {
        if (_delayMs.value > 0L) _waitReadyAt.value = Long.MAX_VALUE
    }

    /** True se in questo momento si può modificare. */
    fun canEditNow(): Boolean {
        if (_delayMs.value <= 0L) return true
        val ready = _waitReadyAt.value
        return ready in 1..System.currentTimeMillis()
    }

    /** Imposta (o rimuove con 0) il Sigillo, mai oltre [MAX_DELAY_MS]. */
    fun setDelay(context: Context, ms: Long) {
        val capped = ms.coerceIn(0L, MAX_DELAY_MS)
        _delayMs.value = capped
        _waitReadyAt.value = if (capped > 0L) System.currentTimeMillis() + capped else 0L
        prefs(context).edit().putLong(KEY_DELAY, capped).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
