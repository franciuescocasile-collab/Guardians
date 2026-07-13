package com.guardians.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Stato di utilizzo pubblicato dal MonitorService e osservato dalla UI.
 * Le mappe sono indicizzate per id del timer, i valori sono millisecondi.
 */
object UsageStateStore {

    data class Snapshot(
        val dailyMs: Map<String, Long> = emptyMap(),
        val continuousMs: Map<String, Long> = emptyMap(),
        val blocked: Set<String> = emptySet(),
        /** Fine della pausa obbligatoria della Sentinella, per id timer (epoch ms). */
        val cooldownUntil: Map<String, Long> = emptyMap(),
        /** Aperture di oggi contate dal Gendarme, per id timer. */
        val opens: Map<String, Int> = emptyMap(),
        val serviceRunning: Boolean = false,
        /** Timestamp dell'ultimo aggiornamento: forza la UI a ricalcolare i conti alla rovescia. */
        val updatedAt: Long = 0L,
    )

    private val _state = MutableStateFlow(Snapshot())
    val state: StateFlow<Snapshot> = _state

    fun publish(
        dailyMs: Map<String, Long>,
        continuousMs: Map<String, Long>,
        blocked: Set<String>,
        cooldownUntil: Map<String, Long>,
        opens: Map<String, Int>,
    ) {
        _state.value = Snapshot(
            dailyMs.toMap(),
            continuousMs.toMap(),
            blocked.toSet(),
            cooldownUntil.toMap(),
            opens.toMap(),
            serviceRunning = true,
            updatedAt = System.currentTimeMillis(),
        )
    }

    fun serviceStopped() {
        _state.value = _state.value.copy(serviceRunning = false)
    }
}
