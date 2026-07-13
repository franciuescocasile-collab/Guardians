package com.guardians.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Esporta/importa TUTTO in un file JSON: configurazione (guardiani, squadre,
 * esclusioni, obiettivi, impostazioni) più storico d'uso e statistiche dei
 * guardiani. Per cambiare telefono senza perdere nulla.
 */
object BackupManager {

    fun exportJson(context: Context): String = JSONObject().apply {
        TeamsRepository.load(context)
        put("app", "Guardians")
        put("version", 2)
        put("timers", JSONArray(TimerRepository.exportJson()))
        put("customTeams", JSONArray(TeamsRepository.teams.value.toList()))
        put("excludedApps", JSONArray(ExclusionsRepository.excluded.value.toList()))
        put("goals", JSONArray(ProfileRepository.goals.value))
        put("vibrateOnAlert", SettingsRepository.vibrateOnAlert.value)
        put("soundOnAlert", SettingsRepository.soundOnAlert.value)
        put("darkTheme", SettingsRepository.darkTheme.value)
        // Dal formato 2: anche i dati, non solo la configurazione.
        put("usageHistory", JSONObject(UsageHistoryRepository.exportJson(context)))
        put("stats", JSONObject(StatsRepository.exportJson(context)))
    }.toString(2)

    /** Applica un backup. Lancia eccezione se il file non è un backup valido. */
    fun import(context: Context, raw: String) {
        val root = JSONObject(raw)
        require(root.optString("app") == "Guardians") { "File non riconosciuto" }
        TimerRepository.importJson(context, root.getJSONArray("timers").toString())
        root.optJSONArray("customTeams")?.let { arr ->
            (0 until arr.length()).forEach { TeamsRepository.add(context, arr.getString(it)) }
        }
        root.optJSONArray("excludedApps")?.let { arr ->
            ExclusionsRepository.setExcluded(
                context,
                (0 until arr.length()).map { arr.getString(it) }.toSet(),
            )
        }
        root.optJSONArray("goals")?.let { arr ->
            ProfileRepository.setGoals(
                context,
                (0 until arr.length()).map { arr.getString(it) },
            )
        }
        SettingsRepository.setVibrateOnAlert(context, root.optBoolean("vibrateOnAlert", true))
        SettingsRepository.setSoundOnAlert(context, root.optBoolean("soundOnAlert", true))
        SettingsRepository.setDarkTheme(context, root.optBoolean("darkTheme", true))
        // Storico e statistiche: presenti solo nei backup dal formato 2 in poi.
        root.optJSONObject("usageHistory")?.let {
            UsageHistoryRepository.importJson(context, it.toString())
        }
        root.optJSONObject("stats")?.let {
            StatsRepository.importJson(context, it.toString())
        }
    }
}
