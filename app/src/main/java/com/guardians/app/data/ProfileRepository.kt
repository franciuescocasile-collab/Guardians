package com.guardians.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray

/**
 * Il profilo motivazionale: i motivi per cui l'utente vuole limitare il telefono
 * (hobby, famiglia, studio, sport…). I popup di blocco li richiamano.
 */
object ProfileRepository {

    private const val PREFS = "guardians_prefs"
    private const val KEY_GOALS = "profile_goals"
    private const val KEY_NICKNAME = "profile_nickname"
    private const val KEY_FIRST_USE = "profile_first_use_date"
    private const val KEY_ONBOARDED = "profile_onboarded"
    private const val KEY_AVATAR = "profile_avatar"
    private const val KEY_DAILY_GOAL_MIN = "profile_daily_goal_minutes"
    private const val KEY_USUAL_BED = "profile_usual_bed_minute"
    private const val KEY_USUAL_WAKE = "profile_usual_wake_minute"

    private val _goals = MutableStateFlow<List<String>>(emptyList())
    val goals: StateFlow<List<String>> = _goals

    /** Come vuole farsi chiamare l'utente (vuoto = nessun saluto personale). */
    private val _nickname = MutableStateFlow("")
    val nickname: StateFlow<String> = _nickname

    /** Primo giorno d'uso dell'app (aaaa-mm-gg), fissato al primo avvio. */
    private val _firstUseDate = MutableStateFlow("")
    val firstUseDate: StateFlow<String> = _firstUseDate

    /** True quando l'utente ha completato (o saltato) l'onboarding iniziale. */
    private val _onboarded = MutableStateFlow(true)
    val onboarded: StateFlow<Boolean> = _onboarded

    /** Stemma del profilo: il nome di un TimerType, o vuoto se non scelto. */
    private val _avatar = MutableStateFlow("")
    val avatar: StateFlow<String> = _avatar

    /** Obiettivo personale di tempo al telefono (minuti al giorno; 0 = non impostato). */
    private val _dailyGoalMinutes = MutableStateFlow(0)
    val dailyGoalMinutes: StateFlow<Int> = _dailyGoalMinutes

    /** Orari indicativi di nanna e risveglio (minuti dalla mezzanotte; -1 = non impostati).
     *  L'Araldo li usa come base nei primi giorni, finché non ha imparato i tuoi. */
    private val _usualBedMinute = MutableStateFlow(-1)
    val usualBedMinute: StateFlow<Int> = _usualBedMinute

    private val _usualWakeMinute = MutableStateFlow(-1)
    val usualWakeMinute: StateFlow<Int> = _usualWakeMinute

    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val p = prefs(context)
        _nickname.value = p.getString(KEY_NICKNAME, "") ?: ""
        _onboarded.value = p.getBoolean(KEY_ONBOARDED, false)
        _avatar.value = p.getString(KEY_AVATAR, "") ?: ""
        _dailyGoalMinutes.value = p.getInt(KEY_DAILY_GOAL_MIN, 0)
        _usualBedMinute.value = p.getInt(KEY_USUAL_BED, -1)
        _usualWakeMinute.value = p.getInt(KEY_USUAL_WAKE, -1)
        // La data di inizio si fissa una volta sola, alla prima apertura.
        val first = p.getString(KEY_FIRST_USE, null)
        if (first == null) {
            val today = java.time.LocalDate.now().toString()
            p.edit().putString(KEY_FIRST_USE, today).apply()
            _firstUseDate.value = today
        } else {
            _firstUseDate.value = first
        }
        val raw = p.getString(KEY_GOALS, null) ?: return
        _goals.value = try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun setNickname(context: Context, name: String) {
        _nickname.value = name.trim()
        prefs(context).edit().putString(KEY_NICKNAME, _nickname.value).apply()
    }

    fun setOnboarded(context: Context) {
        _onboarded.value = true
        prefs(context).edit().putBoolean(KEY_ONBOARDED, true).apply()
    }

    fun setAvatar(context: Context, typeName: String) {
        _avatar.value = typeName
        prefs(context).edit().putString(KEY_AVATAR, typeName).apply()
    }

    fun setDailyGoalMinutes(context: Context, minutes: Int) {
        _dailyGoalMinutes.value = minutes.coerceAtLeast(0)
        prefs(context).edit().putInt(KEY_DAILY_GOAL_MIN, _dailyGoalMinutes.value).apply()
    }

    fun setUsualBedMinute(context: Context, minute: Int) {
        _usualBedMinute.value = minute
        prefs(context).edit().putInt(KEY_USUAL_BED, minute).apply()
    }

    fun setUsualWakeMinute(context: Context, minute: Int) {
        _usualWakeMinute.value = minute
        prefs(context).edit().putInt(KEY_USUAL_WAKE, minute).apply()
    }

    fun setGoals(context: Context, goals: List<String>) {
        _goals.value = goals.map { it.trim() }.filter { it.isNotBlank() }
        prefs(context).edit()
            .putString(KEY_GOALS, JSONArray(_goals.value).toString())
            .apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
