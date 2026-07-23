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
    private const val KEY_TEAM_ICONS = "team_icons"
    private const val KEY_TEAM_BANDS = "team_bands"
    private const val KEY_TEAM_PLACES = "team_places"

    /** Una fascia oraria (minuti dalla mezzanotte); gestisce anche la mezzanotte. */
    data class Band(val startMin: Int, val endMin: Int) {
        fun activeAt(minuteOfDay: Int): Boolean =
            if (startMin < endMin) minuteOfDay in startMin until endMin
            else minuteOfDay >= startMin || minuteOfDay < endMin
    }

    /** Un luogo GPS del Comandante Vedetta. */
    data class Place(val lat: Double, val lon: Double, val radiusM: Int)

    /** La config del Comandante Vedetta: più luoghi GPS e più reti Wi-Fi (SSID). */
    data class TeamPlaces(val points: List<Place>, val ssids: List<String>) {
        val isEverywhere: Boolean get() = points.isEmpty() && ssids.isEmpty()
    }

    /** Icone possibili per una squadra: cartella, gruppo, o l'elmo di un tipo. */
    const val ICON_FOLDER = "folder"
    const val ICON_GROUPS = "groups"

    private val _teams = MutableStateFlow<Set<String>>(emptySet())
    val teams: StateFlow<Set<String>> = _teams

    // Icona scelta per ogni squadra (chiave assente = cartella predefinita).
    private val _teamIcons = MutableStateFlow<Map<String, String>>(emptyMap())
    val teamIcons: StateFlow<Map<String, String>> = _teamIcons

    /** Giorni attivi per squadra (ISO 1=lun … 7=dom); assente = tutti i giorni. */
    private val _teamDays = MutableStateFlow<Map<String, Set<Int>>>(emptyMap())
    val teamDays: StateFlow<Map<String, Set<Int>>> = _teamDays

    /** Comandante Custode: fasce orarie per squadra (vuoto = a tutte le ore). */
    private val _teamBands = MutableStateFlow<Map<String, List<Band>>>(emptyMap())
    val teamBands: StateFlow<Map<String, List<Band>>> = _teamBands

    /** Comandante Vedetta: luoghi/Wi-Fi per squadra (assente/vuoto = ovunque). */
    private val _teamPlaces = MutableStateFlow<Map<String, TeamPlaces>>(emptyMap())
    val teamPlaces: StateFlow<Map<String, TeamPlaces>> = _teamPlaces

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
        prefs(context).getString(KEY_TEAM_ICONS, null)?.let { rawIcons ->
            _teamIcons.value = try {
                val o = org.json.JSONObject(rawIcons)
                buildMap { o.keys().forEach { t -> put(t, o.getString(t)) } }
            } catch (_: Exception) {
                emptyMap()
            }
        }
        prefs(context).getString(KEY_TEAM_BANDS, null)?.let { raw ->
            _teamBands.value = try {
                val o = org.json.JSONObject(raw)
                buildMap {
                    o.keys().forEach { t ->
                        val arr = o.getJSONArray(t)
                        put(t, (0 until arr.length()).map {
                            val b = arr.getJSONArray(it)
                            Band(b.getInt(0), b.getInt(1))
                        })
                    }
                }
            } catch (_: Exception) {
                emptyMap()
            }
        }
        prefs(context).getString(KEY_TEAM_PLACES, null)?.let { raw ->
            _teamPlaces.value = try {
                val o = org.json.JSONObject(raw)
                buildMap {
                    o.keys().forEach { t ->
                        val to = o.getJSONObject(t)
                        val pts = to.optJSONArray("points")?.let { arr ->
                            (0 until arr.length()).map {
                                val p = arr.getJSONArray(it)
                                Place(p.getDouble(0), p.getDouble(1), p.getInt(2))
                            }
                        } ?: emptyList()
                        val ssids = to.optJSONArray("ssids")?.let { arr ->
                            (0 until arr.length()).map { arr.getString(it) }
                        } ?: emptyList()
                        put(t, TeamPlaces(pts, ssids))
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

    /** L'icona della squadra: "folder", "groups" o il nome di un TimerType. */
    fun iconFor(team: String): String = _teamIcons.value[team] ?: ICON_FOLDER

    fun setIcon(context: Context, team: String, icon: String) {
        _teamIcons.value = _teamIcons.value + (team to icon)
        val o = org.json.JSONObject()
        _teamIcons.value.forEach { (t, i) -> o.put(t, i) }
        prefs(context).edit().putString(KEY_TEAM_ICONS, o.toString()).apply()
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

    // ---- Comandante CUSTODE (fasce orarie, max 3) ----
    fun bandsFor(team: String): List<Band> = _teamBands.value[team] ?: emptyList()

    /** True se l'ORA di adesso è consentita (nessuna fascia = a tutte le ore). */
    fun isTimeActiveNow(team: String): Boolean {
        val bands = bandsFor(team)
        if (bands.isEmpty()) return true
        val m = java.time.LocalTime.now().let { it.hour * 60 + it.minute }
        return bands.any { it.activeAt(m) }
    }

    fun setBands(context: Context, team: String, bands: List<Band>) {
        val safe = bands.take(3)
        _teamBands.value = _teamBands.value + (team to safe)
        val o = org.json.JSONObject()
        _teamBands.value.forEach { (t, bs) ->
            o.put(t, JSONArray(bs.map { JSONArray(listOf(it.startMin, it.endMin)) }))
        }
        prefs(context).edit().putString(KEY_TEAM_BANDS, o.toString()).apply()
    }

    // ---- Comandante VEDETTA (luoghi GPS + reti Wi-Fi) ----
    fun placesFor(team: String): TeamPlaces =
        _teamPlaces.value[team] ?: TeamPlaces(emptyList(), emptyList())

    fun setPlaces(context: Context, team: String, places: TeamPlaces) {
        _teamPlaces.value = _teamPlaces.value + (team to places)
        val o = org.json.JSONObject()
        _teamPlaces.value.forEach { (t, p) ->
            o.put(t, org.json.JSONObject().apply {
                put("points", JSONArray(p.points.map {
                    JSONArray(listOf(it.lat, it.lon, it.radiusM))
                }))
                put("ssids", JSONArray(p.ssids))
            })
        }
        prefs(context).edit().putString(KEY_TEAM_PLACES, o.toString()).apply()
    }

    fun add(context: Context, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        persist(context, _teams.value + trimmed)
    }

    /** Copia icona, giorni, fasce e luoghi da una squadra a un'altra. */
    fun copyConfig(context: Context, from: String, to: String) {
        _teamIcons.value[from]?.let { setIcon(context, to, it) }
        _teamDays.value[from]?.let { setDays(context, to, it) }
        _teamBands.value[from]?.let { setBands(context, to, it) }
        _teamPlaces.value[from]?.let { setPlaces(context, to, it) }
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
