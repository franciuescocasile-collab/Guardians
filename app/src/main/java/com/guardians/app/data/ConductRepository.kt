package com.guardians.app.data

import android.content.Context
import com.guardians.app.model.MacroCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * ============================================================================
 *  Motore Passivo della Condotta.
 * ============================================================================
 *
 * Osserva i FATTI reali d'uso e li traduce in due output, senza mai imporre
 * nulla all'utente (zero pop-up, zero voti numerici):
 *
 *  1) la POSIZIONE del cursore su una barra continua Verde→Rosso [liveCursor];
 *  2) il TEMPO RISPARMIATO stimato di oggi [timeSavedToday].
 *
 * Tutti i coefficienti vivono in [ConductConfig]: qui c'è solo la logica.
 * Il valore interno di condotta (0..100) è uno stato che si aggiorna una volta
 * al giorno ([dailyRollover]) e viene letto in tempo reale, sovrapponendo i
 * fatti di oggi ([liveCursor]).
 */
object ConductRepository {

    private const val PREFS = "guardians_prefs"
    private const val KEY_CONDUCT = "conduct_value"
    private const val KEY_LAST_DATE = "conduct_last_date"
    private const val KEY_STREAK = "conduct_focus_streak"
    private const val KEY_SHADOW = "conduct_shadow_baseline"   // ms/giorno per macro
    private const val KEY_MALUS = "conduct_malus_events"       // [{date, amount}]
    private const val KEY_CALIBRATION = "conduct_calibration"  // ricalibrazioni del motore

    // Aumenta questo numero quando i coefficienti cambiano abbastanza da voler
    // ripartire con una condotta pulita (evita che vecchi valori "sporchi" da
    // test restino incollati al rosso col motore nuovo, più indulgente).
    private const val CURRENT_CALIBRATION = 3

    /** Valore interno 0..100 (100 = massimo verde). Mai mostrato come numero. */
    private val _conduct = MutableStateFlow(ConductConfig.START_VALUE)

    /** Baseline dell'Ombra: impronta d'uso iniziale non controllata (ms/giorno). */
    private var shadow: MutableMap<MacroCategory, Long> = mutableMapOf()

    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val p = prefs(context)
        // Ricalibrazione del motore: se cambia CURRENT_CALIBRATION, la condotta
        // riparte pulita (i vecchi malus scadono da soli) col motore più morbido.
        if (p.getInt(KEY_CALIBRATION, 0) < CURRENT_CALIBRATION) {
            p.edit()
                .putFloat(KEY_CONDUCT, ConductConfig.START_VALUE.toFloat())
                .remove(KEY_MALUS)
                .remove(KEY_LAST_DATE)
                .putInt(KEY_CALIBRATION, CURRENT_CALIBRATION)
                .apply()
        }
        _conduct.value = p.getFloat(KEY_CONDUCT, ConductConfig.START_VALUE.toFloat()).toDouble()
        p.getString(KEY_SHADOW, null)?.let { raw ->
            try {
                val o = JSONObject(raw)
                shadow = MacroCategory.entries
                    .associateWith { o.optLong(it.name, 0L) }
                    .toMutableMap()
            } catch (_: Exception) {
            }
        }
    }

    // =====================================================================
    //  BASELINE DELL'OMBRA (Shadow Anchor)
    // =====================================================================
    /**
     * Fissa la baseline la PRIMA volta che vediamo una giornata d'uso reale.
     * È un'àncora: NON si aggiorna con le medie mobili recenti (che si
     * appiattirebbero a zero appena l'utente diventa stabilmente virtuoso,
     * azzerando il "tempo risparmiato"). Resta l'impronta di partenza.
     */
    fun ensureShadowBaseline(context: Context, todayPerMacro: Map<MacroCategory, Long>) {
        load(context)
        if (shadow.values.any { it > 0L }) return          // già fissata
        val total = todayPerMacro.values.sum()
        if (total < 20L * 60_000L) return                  // giornata troppo scarna
        shadow = MacroCategory.entries
            .associateWith { todayPerMacro[it] ?: 0L }
            .toMutableMap()
        val o = JSONObject()
        shadow.forEach { (m, v) -> o.put(m.name, v) }
        prefs(context).edit().putString(KEY_SHADOW, o.toString()).apply()
    }

    fun hasBaseline(): Boolean = shadow.values.any { it > 0L }

    // =====================================================================
    //  TEMPO RISPARMIATO OGGI (Anti-Appiattimento)
    // =====================================================================
    /**
     * Tempo Risparmiato Oggi = max(0, Σ_c [ (B_shadow,c − T_attuale,c) × W_c ])
     *                          + (T_GrandeCongelamento × 0.3)
     *
     * - B_shadow,c : baseline fissa per macro-categoria c;
     * - T_attuale,c: tempo di oggi nella categoria c;
     * - W_c        : peso dopaminergico della categoria (ConductConfig);
     * - il termine del Grande Congelamento premia l'isolamento volontario.
     *
     * Pavimento a 0: non può mai essere negativo (usare di più non "toglie"
     * tempo, semplicemente non ne fa risparmiare).
     */
    fun timeSavedToday(
        todayPerMacro: Map<MacroCategory, Long>,
        bigFreezeMs: Long,
    ): Long {
        if (!hasBaseline()) return (bigFreezeMs * ConductConfig.FREEZE_SAVED_FACTOR).toLong()
        var saved = 0.0
        for (macro in MacroCategory.entries) {
            val base = shadow[macro] ?: 0L
            val now = todayPerMacro[macro] ?: 0L
            val delta = (base - now).toDouble()            // può essere negativo
            saved += delta * ConductConfig.weightOf(macro)
        }
        saved = saved.coerceAtLeast(0.0)                   // pavimento a 0
        saved += bigFreezeMs * ConductConfig.FREEZE_SAVED_FACTOR
        return saved.toLong().coerceAtLeast(0L)
    }

    // =====================================================================
    //  CURSORE DELLA CONDOTTA (0 = Verde Smeraldo, 1 = Rosso Scuro)
    // =====================================================================
    /**
     * Posizione LIVE del cursore, come output passivo dei fatti di oggi.
     * Parte dal valore interno di condotta e vi SOVRAPPONE i veti reali:
     *
     *  - Veto Biologico: oltre 4h totali la barra flette verso l'arancione,
     *    anche se l'obiettivo personale è più largo (è salute, non disciplina).
     *  - Tetto di Tossicità: oltre 45 min su Social/Gaming parte subito un
     *    drenaggio prioritario verso il rosso.
     *
     * Ritorna 0f..1f: 0 = massimo Verde Smeraldo, 1 = Rosso Scuro.
     */
    fun liveCursor(
        todayTotalMs: Long,
        todayPerMacro: Map<MacroCategory, Long>,
    ): Float {
        // Base: il valore interno 0..100 → 0(verde)..1(rosso).
        var pos = (1.0 - _conduct.value / 100.0).coerceIn(0.0, 1.0)

        // Il tempo "neutro" (UTILITY: mappe/navigazione, messaggi, utilità — peso
        // 0) NON conta per la condotta: un'ora di Google Maps per guidare non è
        // dipendenza. Il veto salute guarda solo il tempo che pesa davvero.
        val neutralMs = todayPerMacro[MacroCategory.UTILITY] ?: 0L
        val relevantMs = (todayTotalMs - neutralMs).coerceAtLeast(0L)

        // Veto Biologico: eccesso oltre la soglia salute spinge verso l'arancione,
        // ma con mano leggera (spalmato su +4h, non +3h, e con pendenza ridotta).
        val overHealth = (relevantMs - ConductConfig.HEALTH_CEILING_MS).coerceAtLeast(0L)
        if (overHealth > 0L) {
            val f = (overHealth.toDouble() / (4L * 3_600_000L)).coerceIn(0.0, 1.0)
            pos += (1.0 - pos) * 0.35 * f
        }

        // Tetto di Tossicità: drenaggio verso il rosso, un po' più indulgente.
        val toxic = todayPerMacro[MacroCategory.SOCIAL_GAMING] ?: 0L
        val overTox = (toxic - ConductConfig.TOXICITY_CEILING_MS).coerceAtLeast(0L)
        if (overTox > 0L) {
            // Ogni ora oltre la soglia aggiunge una spinta al rosso.
            val f = (overTox.toDouble() / (60L * 60_000L)).coerceIn(0.0, 1.0)
            pos += (1.0 - pos) * 0.5 * f
        }

        return pos.coerceIn(0.0, 1.0).toFloat()
    }

    // =====================================================================
    //  AGGIORNAMENTO GIORNALIERO (decadimento capillare + recupero)
    // =====================================================================
    /**
     * Da chiamare al cambio di giornata, sui dati del giorno appena chiuso.
     * Applica il decadimento capillare al minuto sugli sforamenti e il
     * recupero passivo se la giornata è pulita. NON tocca gli eventi meccanici
     * neutri (i tentativi sui blocchi valgono 0).
     *
     * @param goalMs      obiettivo personale del giorno (0 = nessuno).
     * @param overshoot   true se la giornata ha sforato obiettivo o veti.
     */
    fun dailyRollover(
        context: Context,
        date: String,
        totalMs: Long,
        perMacro: Map<MacroCategory, Long>,
        goalMs: Long,
        histAvgMs: Long,
    ) {
        load(context)
        if (prefs(context).getString(KEY_LAST_DATE, "") == date) return  // già fatto

        var value = _conduct.value
        val startValue = value          // per limitare il calo massimo di oggi
        val toxicMs = perMacro[MacroCategory.SOCIAL_GAMING] ?: 0L
        val prodMs = (perMacro[MacroCategory.PRODUTTIVITA] ?: 0L) +
            (perMacro[MacroCategory.UTILITY] ?: 0L)

        // Il tempo "neutro" (UTILITY, peso 0: mappe, messaggi, utilità) non pesa
        // sulla condotta nemmeno nel rollover di fine giornata.
        val neutralMs = perMacro[MacroCategory.UTILITY] ?: 0L
        val relevantMs = (totalMs - neutralMs).coerceAtLeast(0L)

        // Zona di sforamento: oltre l'obiettivo personale O oltre il veto salute.
        val ceiling = if (goalMs > 0L) minOf(goalMs, ConductConfig.HEALTH_CEILING_MS)
        else ConductConfig.HEALTH_CEILING_MS
        val overMs = (relevantMs - ceiling).coerceAtLeast(0L)
        var clean = true

        if (overMs > 0L) {
            clean = false
            // Ripartiamo lo sforamento tra tossico e produttivo in proporzione
            // al tempo speso, applicando i due ritmi di decadimento al minuto.
            // In viaggio (travelMitigation<1) l'impatto è ridotto.
            val toxicShare = if (totalMs > 0) toxicMs.toDouble() / totalMs else 0.0
            val overMin = overMs / 60_000.0
            value -= overMin * toxicShare * ConductConfig.ALPHA_TOXIC
            value -= overMin * (1 - toxicShare) * ConductConfig.ALPHA_PRODUCTIVE
        }

        // Tetto di tossicità isolato: dreno anche se l'obiettivo globale regge.
        val overTox = (toxicMs - ConductConfig.TOXICITY_CEILING_MS).coerceAtLeast(0L)
        if (overTox > 0L) {
            clean = false
            value -= (overTox / 60_000.0) * ConductConfig.ALPHA_TOXIC
        }

        // Tetto al calo giornaliero da USO: una singola giornata-no non può far
        // crollare la barra (la condotta segue l'andamento, non un giorno solo).
        val usageDrain = startValue - value
        if (usageDrain > ConductConfig.MAX_DAILY_DRAIN) {
            value = startValue - ConductConfig.MAX_DAILY_DRAIN
        }

        // Malus degli incantesimi ancora nella finestra mobile (fuori dal tetto).
        value -= activeMalus(context, LocalDate.parse(date))

        // Recupero passivo nei giorni puliti.
        if (clean && overTox == 0L) value += ConductConfig.DAILY_RECOVERY

        _conduct.value = value.coerceIn(0.0, 100.0)

        prefs(context).edit()
            .putFloat(KEY_CONDUCT, _conduct.value.toFloat())
            .putString(KEY_LAST_DATE, date)
            .apply()
    }

    /**
     * Giorni CONSECUTIVI GIÀ CHIUSI in cui l'obiettivo è stato rispettato.
     * OGGI NON CONTA: la giornata è ancora in corso, quindi il fuocherello dice
     * "da quanti giorni di fila hai rispettato l'obiettivo" guardando solo i
     * giorni passati (istantanee immutabili). Se oggi resti sotto l'obiettivo,
     * il conteggio salirà solo domani, a giornata chiusa.
     * Serve un obiettivo impostato: senza obiettivo la streak non ha senso (0).
     */
    fun focusStreak(context: Context, goalMs: Long): Int {
        if (goalMs <= 0L) return 0
        UsageHistoryRepository.loadGoalSnapshots(context)
        val snapshots = UsageHistoryRepository.goalSnapshots.value
        var streak = 0
        var day = LocalDate.now().minusDays(1)   // si parte da IERI, non da oggi
        // Risalgo finché i giorni passati risultano "sotto l'obiettivo".
        while (true) {
            val under = snapshots[day.toString()] ?: break   // niente dato → stop
            if (!under) break
            streak++
            day = day.minusDays(1)
        }
        return streak
    }

    // =====================================================================
    //  MALUS (incantesimi e Grandi Congelamenti interrotti)
    // =====================================================================
    /** Incantesimo (Bypass): infrazione critica immediata, malus secco. */
    fun registerSpellMalus(context: Context) {
        addMalus(context, ConductConfig.SPELL_MALUS)
    }

    /**
     * Interruzione di un Grande Congelamento: penale PROPORZIONALE ai minuti
     * rimasti. Malus Effettivo = Malus Massimo × (Rimanenti / Totali).
     * I congelamenti sotto la soglia [ConductConfig.BIG_FREEZE_MIN_MS] non pesano.
     */
    fun registerFreezeInterruption(context: Context, remainingMs: Long, totalMs: Long) {
        if (totalMs < ConductConfig.BIG_FREEZE_MIN_MS) return
        val frac = (remainingMs.toDouble() / totalMs.toDouble()).coerceIn(0.0, 1.0)
        addMalus(context, ConductConfig.BIG_FREEZE_MAX_MALUS * frac)
    }

    private fun addMalus(context: Context, amount: Double) {
        load(context)
        // Applichiamo subito una parte al valore vivo (effetto immediato sul
        // cursore) e registriamo l'evento per la finestra mobile.
        _conduct.value = (_conduct.value - amount).coerceIn(0.0, 100.0)
        val arr = readMalus(context)
        arr.put(JSONObject().apply {
            put("date", LocalDate.now().toString())
            put("amount", amount)
        })
        prefs(context).edit()
            .putString(KEY_MALUS, arr.toString())
            .putFloat(KEY_CONDUCT, _conduct.value.toFloat())
            .apply()
    }

    /** Somma dei malus ancora dentro la finestra mobile rispetto a [refDate]. */
    private fun activeMalus(context: Context, refDate: LocalDate): Double {
        val arr = readMalus(context)
        var sum = 0.0
        val kept = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val d = try { LocalDate.parse(o.getString("date")) } catch (_: Exception) { null }
            if (d != null &&
                java.time.temporal.ChronoUnit.DAYS.between(d, refDate) < ConductConfig.MALUS_WINDOW_DAYS
            ) {
                sum += o.getDouble("amount")
                kept.put(o)
            }
        }
        // Poto gli eventi scaduti dalla finestra mobile.
        prefs(context).edit().putString(KEY_MALUS, kept.toString()).apply()
        return sum
    }

    private fun readMalus(context: Context): JSONArray =
        try {
            JSONArray(prefs(context).getString(KEY_MALUS, "[]"))
        } catch (_: Exception) {
            JSONArray()
        }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
