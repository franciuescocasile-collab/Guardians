package com.guardians.app.data

import android.content.Context
import com.guardians.app.model.AppCategory
import com.guardians.app.model.MacroCategory
import com.guardians.app.model.categoryOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Categorie delle app PERSONALIZZABILI (7): l'utente può assegnare a mano la
 * categoria di un'app (anche diversa da quella che diamo di default) e persino
 * CREARE categorie nuove che non esistono.
 *
 * - override: pacchetto → chiave categoria ("b:NOME" = una predefinita,
 *   "c:<id>" = una personalizzata).
 * - custom: le categorie inventate dall'utente (id, nome, colore).
 *
 * Le categorie personalizzate sono NEUTRE per la Condotta (macro Utility): il
 * motore della condotta continua a usare le macro delle categorie predefinite.
 */
object CategoryRepository {

    private const val PREFS = "guardians_prefs"
    private const val KEY_OVERRIDES = "cat_overrides"
    private const val KEY_CUSTOM = "cat_custom"

    data class CustomCat(val id: String, val name: String, val colorArgb: Long)

    /** La categoria "effettiva" di un'app, per mostrarla (nome + colore + macro). */
    data class ResolvedCat(
        val key: String,          // "b:NOME" o "c:<id>"
        val label: String,
        val colorArgb: Long,
        val macro: MacroCategory,
    )

    private val _custom = MutableStateFlow<List<CustomCat>>(emptyList())
    val custom: StateFlow<List<CustomCat>> = _custom

    private val _overrides = MutableStateFlow<Map<String, String>>(emptyMap())
    val overrides: StateFlow<Map<String, String>> = _overrides

    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val p = prefs(context)
        p.getString(KEY_CUSTOM, null)?.let { raw ->
            _custom.value = try {
                val arr = JSONArray(raw)
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    CustomCat(o.getString("id"), o.getString("name"), o.getLong("color"))
                }
            } catch (_: Exception) {
                emptyList()
            }
        }
        p.getString(KEY_OVERRIDES, null)?.let { raw ->
            _overrides.value = try {
                val o = JSONObject(raw)
                buildMap { o.keys().forEach { k -> put(k, o.getString(k)) } }
            } catch (_: Exception) {
                emptyMap()
            }
        }
    }

    /** Chiave di una categoria predefinita. */
    fun builtinKey(c: AppCategory) = "b:${c.name}"

    /** Crea una categoria personalizzata e ne ritorna la chiave. */
    fun addCustom(context: Context, name: String, colorArgb: Long): String {
        load(context)
        val cat = CustomCat(UUID.randomUUID().toString(), name.trim(), colorArgb)
        _custom.value = _custom.value + cat
        val arr = JSONArray()
        _custom.value.forEach { c ->
            arr.put(JSONObject().apply {
                put("id", c.id); put("name", c.name); put("color", c.colorArgb)
            })
        }
        prefs(context).edit().putString(KEY_CUSTOM, arr.toString()).apply()
        return "c:${cat.id}"
    }

    /** Assegna (o riporta al default con null) la categoria di un'app. */
    fun setOverride(context: Context, pkg: String, key: String?) {
        load(context)
        _overrides.value = if (key == null) _overrides.value - pkg
        else _overrides.value + (pkg to key)
        val o = JSONObject()
        _overrides.value.forEach { (k, v) -> o.put(k, v) }
        prefs(context).edit().putString(KEY_OVERRIDES, o.toString()).apply()
    }

    /** La categoria effettiva di un'app: override se c'è, sennò quella di default. */
    fun resolve(context: Context, pkg: String): ResolvedCat {
        load(context)
        val ov = _overrides.value[pkg]
        if (ov != null) {
            if (ov.startsWith("c:")) {
                val c = _custom.value.firstOrNull { "c:${it.id}" == ov }
                if (c != null) {
                    return ResolvedCat(ov, c.name, c.colorArgb, MacroCategory.UTILITY)
                }
            } else if (ov.startsWith("b:")) {
                val name = ov.removePrefix("b:")
                val ac = AppCategory.entries.firstOrNull { it.name == name }
                if (ac != null) return ResolvedCat(ov, ac.label, ac.colorArgb, ac.macro)
            }
        }
        val ac = categoryOf(context, pkg)
        return ResolvedCat(builtinKey(ac), ac.label, ac.colorArgb, ac.macro)
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
