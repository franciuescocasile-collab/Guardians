package com.guardians.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray

/**
 * Squadre create esplicitamente dall'utente (col pulsante "+"). Vivono anche
 * VUOTE: una squadra qui dentro resta in elenco pure senza guardiani, finché
 * non viene eliminata. Le squadre nate assegnando un guardiano a un nome nuovo
 * dall'editor, invece, esistono finché hanno almeno un membro (come sempre).
 */
object TeamsRepository {

    private const val PREFS = "guardians_prefs"
    private const val KEY_TEAMS = "custom_teams"
    private const val KEY_TEAM_DAYS = "team_days"

    private val _teams = MutableStateFlow<Set<String>>(emptySet())
    val teams: StateFlow<Set<String>> = _teams

    /** Giorni attivi per squadra (ISO 1=lun … 7=dom); assente = tutti i giorni. */
    private val _teamDays = MutableStateFlow<Map<String, Set<Int>>>(emptyMap())
    val teamDays: StateFlow<Map<String, Set<Int>>> = _teamDays

    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        prefs(context).getString(KEY_TEAM_DAYS, null)?.let { rawDays ->
            _teamDays.value = try {
                val o = org.json.JSONObject(rawDays)
                buildMap {
                    o.keys().forEach { t ->
                        val arr = o.getJSONArray(t)
                        put(t, (0 until arr.length()).map { arr.getInt(it) }.toSet())
                    }
                }
            } catch (_: Exception) {
                emptyMap()
            }
        }
        val raw = prefs(context).getString(KEY_TEAMS, null) ?: return
        _teams.value = try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }.toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    /** I giorni in cui la squadra è di servizio (default: tutti e sette). */
    fun daysFor(team: String): Set<Int> =
        _teamDays.value[team] ?: setOf(1, 2, 3, 4, 5, 6, 7)

    /** True se oggi la squadra è di servizio secondo la sua pianificazione. */
    fun isTeamActiveToday(team: String): Boolean =
        daysFor(team).contains(java.time.LocalDate.now().dayOfWeek.value)

    fun setDays(context: Context, team: String, days: Set<Int>) {
        // Mai un insieme vuoto: una squadra senza giorni sarebbe morta per sempre.
        val safe = if (days.isEmpty()) setOf(1, 2, 3, 4, 5, 6, 7) else days
        _teamDays.value = _teamDays.value + (team to safe)
        val o = org.json.JSONObject()
        _teamDays.value.forEach { (t, d) -> o.put(t, JSONArray(d.toList())) }
        prefs(context).edit().putString(KEY_TEAM_DAYS, o.toString()).apply()
    }

    fun add(context: Context, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        persist(context, _teams.value + trimmed)
    }

    fun remove(context: Context, name: String) {
        persist(context, _teams.value - name)
    }

    private fun persist(context: Context, set: Set<String>) {
        _teams.value = set
        prefs(context).edit()
            .putString(KEY_TEAMS, JSONArray(set.toList()).toString())
            .apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
