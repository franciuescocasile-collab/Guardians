package com.guardians.app.data

import android.content.Context
import com.guardians.app.model.GuardianTimer
import com.guardians.app.model.TimeUnit
import com.guardians.app.model.TimerType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistenza dei timer su SharedPreferences (JSON) + StateFlow per la UI e il servizio.
 */
object TimerRepository {

    private const val PREFS = "guardians_prefs"
    private const val KEY_TIMERS = "timers"

    private val _timers = MutableStateFlow<List<GuardianTimer>>(emptyList())
    val timers: StateFlow<List<GuardianTimer>> = _timers

    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val raw = prefs(context).getString(KEY_TIMERS, null) ?: return
        _timers.value = try {
            fromJson(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun upsert(context: Context, timer: GuardianTimer) {
        val list = _timers.value.toMutableList()
        val index = list.indexOfFirst { it.id == timer.id }
        if (index >= 0) list[index] = timer else list.add(timer)
        persist(context, list)
    }

    fun delete(context: Context, id: String) {
        persist(context, _timers.value.filterNot { it.id == id })
    }

    fun setEnabled(context: Context, id: String, enabled: Boolean) {
        persist(context, _timers.value.map { if (it.id == id) it.copy(enabled = enabled) else it })
    }

    private fun persist(context: Context, list: List<GuardianTimer>) {
        _timers.value = list
        prefs(context).edit().putString(KEY_TIMERS, toJson(list)).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun toJson(list: List<GuardianTimer>): String {
        val arr = JSONArray()
        for (t in list) {
            arr.put(JSONObject().apply {
                put("id", t.id)
                put("name", t.name)
                put("type", t.type.name)
                put("limitAmount", t.limitAmount)
                put("limitUnit", t.limitUnit.name)
                put("resetCycle", t.resetCycle.name)
                put("resetAmount", t.resetAmount)
                put("resetUnit", t.resetUnit.name)
                put("startMinuteOfDay", t.startMinuteOfDay)
                put("endMinuteOfDay", t.endMinuteOfDay)
                put("maxOpensPerDay", t.maxOpensPerDay)
                put("reopenCooldownMinutes", t.reopenCooldownMinutes)
                put("notifyAfterOpens", t.notifyAfterOpens)
                put("activeUntilEpochMs", t.activeUntilEpochMs)
                put("warnAmount", t.warnAmount)
                put("warnUnit", t.warnUnit.name)
                // JSON non ammette NaN: salva le coordinate solo se impostate.
                if (t.hasLocation) {
                    put("latitude", t.latitude)
                    put("longitude", t.longitude)
                }
                put("radiusMeters", t.radiusMeters)
                put("innerType", t.innerType.name)
                put("araldoMorning", t.araldoMorning)
                put("araldoEvening", t.araldoEvening)
                put("pace", t.pace.name)
                put("maxNotices", t.maxNotices)
                put("messages", JSONArray(t.messages))
                put("allApps", t.allApps)
                put("packages", JSONArray(t.packages))
                put("enabled", t.enabled)
                put("team", t.team)
            })
        }
        return arr.toString()
    }

    private fun fromJson(raw: String): List<GuardianTimer> {
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val pkgs = o.optJSONArray("packages") ?: JSONArray()
            GuardianTimer(
                id = o.getString("id"),
                name = o.getString("name"),
                type = TimerType.valueOf(o.getString("type")),
                // "limitMinutes"/"resetMinutes" sono le chiavi del vecchio formato.
                limitAmount = o.optInt("limitAmount", o.optInt("limitMinutes", 30)),
                limitUnit = TimeUnit.valueOf(o.optString("limitUnit", TimeUnit.MINUTES.name)),
                resetCycle = com.guardians.app.model.ResetCycle.valueOf(
                    o.optString("resetCycle", com.guardians.app.model.ResetCycle.DAILY.name)
                ),
                resetAmount = o.optInt("resetAmount", o.optInt("resetMinutes", 0)),
                resetUnit = TimeUnit.valueOf(o.optString("resetUnit", TimeUnit.MINUTES.name)),
                startMinuteOfDay = o.optInt("startMinuteOfDay", 0),
                endMinuteOfDay = o.optInt("endMinuteOfDay", 0),
                maxOpensPerDay = o.optInt("maxOpensPerDay", 0),
                reopenCooldownMinutes = o.optInt("reopenCooldownMinutes", 0),
                notifyAfterOpens = o.optInt("notifyAfterOpens", 0),
                activeUntilEpochMs = o.optLong("activeUntilEpochMs", 0L),
                warnAmount = o.optInt("warnAmount", 0),
                warnUnit = TimeUnit.valueOf(o.optString("warnUnit", TimeUnit.MINUTES.name)),
                latitude = o.optDouble("latitude", Double.NaN),
                longitude = o.optDouble("longitude", Double.NaN),
                radiusMeters = o.optInt("radiusMeters", 150),
                innerType = TimerType.valueOf(
                    o.optString("innerType", TimerType.SENTINELLA.name)
                ),
                araldoMorning = o.optBoolean("araldoMorning", true),
                araldoEvening = o.optBoolean("araldoEvening", false),
                pace = com.guardians.app.model.MessengerPace.valueOf(
                    o.optString("pace", com.guardians.app.model.MessengerPace.PROGRAMMABILE.name)
                ),
                maxNotices = o.optInt("maxNotices", 0),
                messages = o.optJSONArray("messages")?.let { a ->
                    (0 until a.length()).map { a.getString(it) }
                } ?: emptyList(),
                allApps = o.optBoolean("allApps", false),
                packages = (0 until pkgs.length()).map { pkgs.getString(it) },
                enabled = o.optBoolean("enabled", true),
                team = o.optString("team", ""),
            )
        }
    }

    /** Sostituisce tutti i timer (usato dal ripristino della configurazione). */
    fun replaceAll(context: Context, list: List<GuardianTimer>) = persist(context, list)

    /** JSON dei timer per il backup. */
    fun exportJson(): String = toJson(_timers.value)

    /** Importa i timer da un JSON di backup (lancia eccezione se non valido). */
    fun importJson(context: Context, raw: String) = replaceAll(context, fromJson(raw))
}
