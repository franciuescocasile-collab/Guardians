package com.guardians.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray

/**
 * App escluse da OGNI guardiano, scelte dall'utente.
 * (Telefonate, impostazioni e launcher sono sempre escluse a prescindere.)
 */
object ExclusionsRepository {

    private const val PREFS = "guardians_prefs"
    private const val KEY_EXCLUDED = "excluded_apps"

    private val _excluded = MutableStateFlow<Set<String>>(emptySet())
    val excluded: StateFlow<Set<String>> = _excluded

    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val raw = prefs(context).getString(KEY_EXCLUDED, null) ?: return
        _excluded.value = try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }.toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    fun setExcluded(context: Context, packages: Set<String>) {
        _excluded.value = packages
        prefs(context).edit()
            .putString(KEY_EXCLUDED, JSONArray(packages.toList()).toString())
            .apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
