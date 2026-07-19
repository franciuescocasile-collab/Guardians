package com.guardians.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import java.time.LocalDate

/**
 * Un TRAGUARDO sbloccabile: una spilla colorata con un piccolo emblema (emoji).
 * A differenza della bacheca (che mostra i RECORD), i badge sono traguardi più
 * standard che si sbloccano compiendo certe azioni. Alcuni sono NASCOSTI: se ne
 * vede lo stemma ma non la missione, così restano un mistero da scoprire.
 */
enum class Badge(
    val titleIt: String, val titleEn: String,
    val descIt: String, val descEn: String,
    val hidden: Boolean,
    /** I due colori del gradiente della spilla (ARGB). */
    val color1: Long, val color2: Long,
    /** L'emblema al centro della spilla. */
    val emoji: String,
) {
    UNDER_2H(
        "Giornata leggera", "Light day",
        "Hai usato il telefono meno di 2 ore in un giorno",
        "You used the phone less than 2 hours in a day",
        false, 0xFF66BB6A, 0xFF2E7D32, "🌱",
    ),
    UNDER_1H(
        "Quasi libero", "Almost free",
        "Hai usato il telefono meno di 1 ora in un giorno",
        "You used the phone less than 1 hour in a day",
        false, 0xFF26C6DA, 0xFF00838F, "🍃",
    ),
    FREEZE_2H(
        "Ghiaccio saldo", "Solid ice",
        "Hai superato le 2 ore di congelamento",
        "You passed 2 hours of freeze",
        false, 0xFF4FC3F7, 0xFF1976D2, "❄️",
    ),
    FREEZE_4H(
        "Era glaciale", "Ice age",
        "Il tuo congelamento ha superato le 4 ore",
        "Your freeze passed 4 hours",
        false, 0xFF7986CB, 0xFF303F9F, "🧊",
    ),
    GOAL_10(
        "Costanza", "Consistency",
        "Hai rispettato l'obiettivo giornaliero per 10 giorni",
        "You met your daily goal for 10 days",
        false, 0xFFFFCA28, 0xFFF57C00, "🔥",
    ),
    GOAL_30(
        "Disciplina", "Discipline",
        "Hai rispettato l'obiettivo giornaliero per 30 giorni",
        "You met your daily goal for 30 days",
        false, 0xFFFF7043, 0xFFD84315, "🏅",
    ),

    // ---------------------------------------------------------- NASCOSTI
    GOAL_365(
        "Leggenda", "Legend",
        "Hai rispettato l'obiettivo giornaliero per un anno intero",
        "You met your daily goal for a whole year",
        true, 0xFFFFD54F, 0xFFFF8F00, "👑",
    ),
    FREEZE_1DAY(
        "Iceberg", "Iceberg",
        "Hai superato un giorno intero di congelamento",
        "You passed a full day of freeze",
        true, 0xFF9575CD, 0xFF512DA8, "🏔️",
    ),
    UNDER_10MIN(
        "Fantasma", "Ghost",
        "Hai usato il telefono meno di 10 minuti in un giorno",
        "You used the phone less than 10 minutes in a day",
        true, 0xFF90A4AE, 0xFF455A64, "👻",
    ),
    UNDER_GOAL_NO_GUARDIANS(
        "Forza di volontà", "Willpower",
        "Sei rimasto sotto l'obiettivo senza nessun guardiano attivo",
        "You stayed under your goal with no active guardian",
        true, 0xFFF06292, 0xFFC2185B, "💪",
    ),
    ;

    val title: String get() = tr(titleIt, titleEn)
    val desc: String get() = tr(descIt, descEn)
}

/**
 * Tiene traccia dei traguardi sbloccati (in SharedPreferences) e li valuta a
 * partire dai dati che l'app raccoglie già (storico d'uso, congelamento, striscia
 * dell'obiettivo). Sbloccare un badge è per sempre: anche se poi le condizioni
 * non valgono più, il badge resta conquistato.
 */
object BadgesRepository {
    private const val PREFS = "guardians_prefs"
    private const val KEY = "badges_unlocked"

    private val _unlocked = MutableStateFlow<Set<String>>(emptySet())
    val unlocked: StateFlow<Set<String>> = _unlocked
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        _unlocked.value = parse(prefs(context).getString(KEY, null))
    }

    fun isUnlocked(b: Badge): Boolean = b.name in _unlocked.value

    private fun unlock(context: Context, b: Badge) {
        if (b.name in _unlocked.value) return
        _unlocked.value = _unlocked.value + b.name
        prefs(context).edit()
            .putString(KEY, JSONArray(_unlocked.value.toList()).toString())
            .apply()
    }

    /**
     * Valuta TUTTE le condizioni con i dati disponibili e sblocca i traguardi
     * raggiunti. Da chiamare quando si apre il profilo e alla fine di una
     * sessione di congelamento.
     */
    fun evaluate(context: Context) {
        load(context)
        UsageHistoryRepository.load(context)
        SpellsRepository.load(context)

        // Minimo d'uso in un singolo giorno (giorni a 0 esclusi: è "nessun dato").
        val history = UsageHistoryRepository.history.value
        val minDay = history.values.filter { it > 0L }.minOrNull() ?: Long.MAX_VALUE
        if (minDay < 2L * 3_600_000L) unlock(context, Badge.UNDER_2H)
        if (minDay < 1L * 3_600_000L) unlock(context, Badge.UNDER_1H)
        if (minDay < 10L * 60_000L) unlock(context, Badge.UNDER_10MIN)

        // Congelamento più lungo mai completato.
        val longestFreeze = SpellsRepository.longestFreezeMs(context)
        if (longestFreeze > 2L * 3_600_000L) unlock(context, Badge.FREEZE_2H)
        if (longestFreeze > 4L * 3_600_000L) unlock(context, Badge.FREEZE_4H)
        if (longestFreeze > 24L * 3_600_000L) unlock(context, Badge.FREEZE_1DAY)

        // Striscia record di giorni consecutivi sotto l'obiettivo.
        val best = ConductRepository.bestFocusStreak(context)
        if (best >= 10) unlock(context, Badge.GOAL_10)
        if (best >= 30) unlock(context, Badge.GOAL_30)
        if (best >= 365) unlock(context, Badge.GOAL_365)

        // Sotto l'obiettivo SENZA guardiani: nessun timer attivo e un giorno
        // completato (ieri) sotto la soglia.
        val goalMs = ProfileRepository.dailyGoalMinutes.value * 60_000L
        if (goalMs > 0L && TimerRepository.timers.value.none { it.enabled }) {
            val yesterday = history[LocalDate.now().minusDays(1).toString()] ?: -1L
            if (yesterday in 0 until goalMs) unlock(context, Badge.UNDER_GOAL_NO_GUARDIANS)
        }
    }

    private fun parse(raw: String?): Set<String> = try {
        if (raw == null) emptySet()
        else JSONArray(raw).let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
    } catch (_: Exception) {
        emptySet()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
