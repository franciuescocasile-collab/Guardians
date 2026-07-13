package com.guardians.app.data

import android.content.Context
import com.guardians.app.model.GuardianTimer
import com.guardians.app.model.TimerType
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

/**
 * Statistiche dei guardiani: quante volte ogni timer ha bloccato (scatti) e
 * quanti tentativi di rientro ha respinto, giorno per giorno.
 * Il nome e il tipo vengono salvati insieme ai numeri, così le statistiche
 * restano leggibili anche se il timer viene eliminato.
 */
object StatsRepository {

    private const val PREFS = "guardians_prefs"
    private const val KEY_STATS = "stats"

    data class DayStat(val blocks: Int = 0, val rejected: Int = 0, val surrenders: Int = 0)

    data class TimerStat(
        val name: String,
        val type: TimerType?,
        val days: Map<String, DayStat>,
    ) {
        val totalBlocks: Int get() = days.values.sumOf { it.blocks }
        val totalRejected: Int get() = days.values.sumOf { it.rejected }
        val totalSurrenders: Int get() = days.values.sumOf { it.surrenders }
        fun today(date: String): DayStat = days[date] ?: DayStat()
    }

    private val _stats = MutableStateFlow<Map<String, TimerStat>>(emptyMap())
    val stats: StateFlow<Map<String, TimerStat>> = _stats

    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val raw = prefs(context).getString(KEY_STATS, null) ?: return
        _stats.value = try {
            fromJson(raw)
        } catch (_: Exception) {
            emptyMap()
        }
    }

    /** Registra uno scatto ([rejected] = false) o un rientro respinto ([rejected] = true). */
    fun record(context: Context, timer: GuardianTimer, rejected: Boolean) {
        update(context, timer) { day ->
            if (rejected) day.copy(rejected = day.rejected + 1)
            else day.copy(blocks = day.blocks + 1)
        }
    }

    /** Registra un'interruzione anticipata del Custode (una "resa"). */
    fun recordSurrender(context: Context, timer: GuardianTimer) {
        update(context, timer) { day -> day.copy(surrenders = day.surrenders + 1) }
    }

    private fun update(
        context: Context,
        timer: GuardianTimer,
        change: (DayStat) -> DayStat,
    ) {
        load(context)
        val date = LocalDate.now().toString()
        val current = _stats.value.toMutableMap()
        val stat = current[timer.id] ?: TimerStat(timer.name, timer.type, emptyMap())
        val newDay = change(stat.days[date] ?: DayStat())
        current[timer.id] = stat.copy(
            name = timer.name,
            type = timer.type,
            days = stat.days + (date to newDay),
        )
        _stats.value = current
        prefs(context).edit().putString(KEY_STATS, toJson(current)).apply()
    }

    fun clear(context: Context) {
        _stats.value = emptyMap()
        prefs(context).edit().remove(KEY_STATS).apply()
    }

    /** JSON delle statistiche per il backup. */
    fun exportJson(context: Context): String {
        load(context)
        return toJson(_stats.value)
    }

    /** Ripristina le statistiche da un backup (lancia eccezione se non valido). */
    fun importJson(context: Context, raw: String) {
        val parsed = fromJson(raw)
        _stats.value = parsed
        prefs(context).edit().putString(KEY_STATS, toJson(parsed)).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun toJson(map: Map<String, TimerStat>): String {
        val root = JSONObject()
        for ((id, stat) in map) {
            root.put(id, JSONObject().apply {
                put("name", stat.name)
                put("type", stat.type?.name ?: "")
                val days = JSONObject()
                for ((date, day) in stat.days) {
                    days.put(date, JSONObject().apply {
                        put("blocks", day.blocks)
                        put("rejected", day.rejected)
                        put("surrenders", day.surrenders)
                    })
                }
                put("days", days)
            })
        }
        return root.toString()
    }

    private fun fromJson(raw: String): Map<String, TimerStat> {
        val root = JSONObject(raw)
        val result = mutableMapOf<String, TimerStat>()
        root.keys().forEach { id ->
            val o = root.getJSONObject(id)
            val days = mutableMapOf<String, DayStat>()
            o.optJSONObject("days")?.let { d ->
                d.keys().forEach { date ->
                    val day = d.getJSONObject(date)
                    days[date] = DayStat(
                        day.optInt("blocks"),
                        day.optInt("rejected"),
                        day.optInt("surrenders"),
                    )
                }
            }
            val type = try {
                TimerType.valueOf(o.optString("type"))
            } catch (_: Exception) {
                null
            }
            result[id] = TimerStat(o.optString("name", "Timer"), type, days)
        }
        return result
    }
}
