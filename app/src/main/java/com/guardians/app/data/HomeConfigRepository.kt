package com.guardians.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray

/**
 * Configurazione della HOMEPAGE: quali card si vedono e in che ordine.
 * Le card "essenziali" (guardiani, squadre, congelamento, statistiche) si
 * possono solo riordinare; Guide, Notificatore e Sonno si possono anche
 * nascondere. Nascondere una card NON cancella mai i suoi dati.
 */
object HomeConfigRepository {

    const val CARD_GUARDIANI = "guardiani"
    const val CARD_SQUADRE = "squadre"
    const val CARD_FREEZE = "congelamento"
    const val CARD_STATS = "statistiche"
    const val CARD_SLEEP = "sonno"
    const val CARD_NOTIFIER = "notificatore"
    const val CARD_GUIDE = "guide"

    /** Ordine predefinito delle card in home. */
    val DEFAULT_ORDER = listOf(
        CARD_GUARDIANI, CARD_SQUADRE, CARD_FREEZE, CARD_STATS,
        CARD_SLEEP, CARD_NOTIFIER, CARD_GUIDE,
    )

    /** Solo queste card si possono nascondere: le altre sono essenziali. */
    val HIDEABLE = setOf(CARD_GUIDE, CARD_NOTIFIER, CARD_SLEEP)

    private const val PREFS = "guardians_prefs"
    private const val KEY_ORDER = "home_card_order"
    private const val KEY_HIDDEN = "home_card_hidden"
    private const val KEY_LEGACY_NOTIFIER = "show_notifier_card"

    private val _order = MutableStateFlow(DEFAULT_ORDER)
    val order: StateFlow<List<String>> = _order

    private val _hidden = MutableStateFlow<Set<String>>(setOf(CARD_NOTIFIER))
    val hidden: StateFlow<Set<String>> = _hidden

    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val p = prefs(context)
        _order.value = readList(p.getString(KEY_ORDER, null)).let { saved ->
            // Ordine salvato + eventuali card NUOVE aggiunte in coda (così un
            // aggiornamento dell'app non fa sparire le card appena introdotte).
            if (saved.isEmpty()) DEFAULT_ORDER
            else saved.filter { it in DEFAULT_ORDER } + DEFAULT_ORDER.filter { it !in saved }
        }
        _hidden.value = if (p.contains(KEY_HIDDEN)) {
            readList(p.getString(KEY_HIDDEN, null)).filter { it in HIDEABLE }.toSet()
        } else {
            // Migrazione dal vecchio switch "mostra il Notificatore in home".
            if (p.getBoolean(KEY_LEGACY_NOTIFIER, false)) emptySet() else setOf(CARD_NOTIFIER)
        }
    }

    /** Sposta la card di [delta] posizioni (-1 = su, +1 = giù). */
    fun move(context: Context, key: String, delta: Int) {
        val list = _order.value.toMutableList()
        val i = list.indexOf(key)
        val j = i + delta
        if (i < 0 || j < 0 || j >= list.size) return
        list[i] = list[j].also { list[j] = list[i] }
        _order.value = list
        prefs(context).edit().putString(KEY_ORDER, JSONArray(list).toString()).apply()
    }

    /** Mostra o nasconde una card (solo quelle in [HIDEABLE]). */
    fun setHidden(context: Context, key: String, hide: Boolean) {
        if (key !in HIDEABLE) return
        val set = _hidden.value.toMutableSet()
        if (hide) set.add(key) else set.remove(key)
        _hidden.value = set
        prefs(context).edit().putString(KEY_HIDDEN, JSONArray(set.toList()).toString()).apply()
    }

    /** True se la card Sonno è nascosta → l'Araldo va in standby (19.2). */
    fun isSleepHidden(): Boolean = CARD_SLEEP in _hidden.value

    /** Nome leggibile della card, per la pagina di configurazione. */
    fun displayName(key: String): String = when (key) {
        CARD_GUARDIANI -> tr("Nuovo guardiano", "New guardian")
        CARD_SQUADRE -> tr("Squadre", "Teams")
        CARD_FREEZE -> tr("Congelamento", "Freeze")
        CARD_STATS -> tr("Statistiche", "Statistics")
        CARD_SLEEP -> tr("Sonno", "Sleep")
        CARD_NOTIFIER -> tr("Il Notificatore", "The Notifier")
        CARD_GUIDE -> tr("Guide", "Guides")
        else -> key
    }

    private fun readList(raw: String?): List<String> = try {
        if (raw == null) emptyList()
        else JSONArray(raw).let { arr -> (0 until arr.length()).map { arr.getString(it) } }
    } catch (_: Exception) {
        emptyList()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
