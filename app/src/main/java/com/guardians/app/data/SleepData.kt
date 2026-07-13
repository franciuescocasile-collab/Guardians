package com.guardians.app.data

/**
 * PREDISPOSIZIONE Health Connect ("Il legame Mente-Sonno").
 * Modelli dati pronti a ricevere le SleepSessionRecord: durata E qualità
 * (fasi profonda/REM/leggera). Nessuna lettura reale finché non si aggancia
 * la SDK di Health Connect: questi tipi sono il contratto già stabile.
 */

/** Una notte di sonno, come arriverà da Health Connect. */
data class SleepSession(
    val startEpochMs: Long,
    val endEpochMs: Long,
    /** Millisecondi nelle singole fasi (0 = dato non fornito dal dispositivo). */
    val deepMs: Long = 0L,
    val remMs: Long = 0L,
    val lightMs: Long = 0L,
    val awakeMs: Long = 0L,
) {
    val totalMs: Long get() = endEpochMs - startEpochMs

    /**
     * Indice di qualità 0-100: quota di sonno "ristoratore" (profondo + REM)
     * sul totale, riproporzionata sui valori tipici (~45% = ottimo).
     */
    val qualityScore: Int
        get() {
            if (totalMs <= 0L || deepMs + remMs <= 0L) return 0
            val restorative = (deepMs + remMs).toDouble() / totalMs.toDouble()
            return ((restorative / 0.45) * 100).toInt().coerceIn(0, 100)
        }
}

/** Il legame di una giornata: schermo prima di dormire vs sonno che ne segue. */
data class MindSleepEntry(
    val date: String,
    /** Uso del telefono nei 60 minuti prima dell'addormentamento. */
    val screenBeforeBedMs: Long,
    val sleep: SleepSession,
)
