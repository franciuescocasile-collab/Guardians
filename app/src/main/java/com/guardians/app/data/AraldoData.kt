package com.guardians.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * I dati che l'Araldo impara osservando lo schermo:
 * - il RISVEGLIO di oggi (fine di uno spegnimento lungo, ≥4h);
 * - le ultime ore della NANNA (inizio degli spegnimenti lunghi serali),
 *   da cui si stima l'ora di addormentamento con la MEDIANA.
 * Tutto locale, tutto nelle SharedPreferences: nessun sensore, nessuna rete.
 */
object AraldoData {

    private const val PREFS = "guardians_prefs"
    private const val KEY_SCREEN_OFF_SINCE = "araldo_screen_off_since"
    private const val KEY_WAKE_AT = "araldo_wake_at"
    private const val KEY_WAKE_DATE = "araldo_wake_date"
    private const val KEY_BEDTIMES = "araldo_bedtimes"
    // Timestamp INTERI (epoch ms) del "distacco" serale: quando il telefono viene
    // posato per la notte. Servono al grafico Distacco→Sonno (li incrociamo con
    // l'ora di addormentamento letta da Health Connect).
    private const val KEY_BEDTIME_EPOCHS = "araldo_bedtime_epochs"
    private const val MAX_BEDTIME_SAMPLES = 14
    private const val MIN_SAMPLES_FOR_ESTIMATE = 3

    /** Risveglio di oggi (epoch ms); 0 se oggi non è ancora avvenuto. */
    private val _wakeAt = MutableStateFlow(0L)
    val wakeAt: StateFlow<Long> = _wakeAt

    /** Ora stimata della nanna in minuti dalla mezzanotte; null finché osserva. */
    private val _medianBedtimeMinute = MutableStateFlow<Int?>(null)
    val medianBedtimeMinute: StateFlow<Int?> = _medianBedtimeMinute

    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val p = prefs(context)
        val today = LocalDate.now().toString()
        _wakeAt.value = if (p.getString(KEY_WAKE_DATE, "") == today) {
            p.getLong(KEY_WAKE_AT, 0L)
        } else {
            0L
        }
        _medianBedtimeMinute.value = computeMedian(readBedtimes(context))
    }

    /** Inizio dello spegnimento schermo in corso (0 = schermo acceso). Sopravvive ai riavvii. */
    fun screenOffSince(context: Context): Long =
        prefs(context).getLong(KEY_SCREEN_OFF_SINCE, 0L)

    fun setScreenOffSince(context: Context, epochMs: Long) {
        prefs(context).edit().putLong(KEY_SCREEN_OFF_SINCE, epochMs).apply()
    }

    /**
     * Registra il VERO risveglio di oggi (solo il primo del giorno conta:
     * i pisolini pomeridiani non lo sovrascrivono).
     */
    fun recordWake(context: Context, epochMs: Long) {
        val day = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault())
            .toLocalDate().toString()
        val p = prefs(context)
        if (p.getString(KEY_WAKE_DATE, "") == day) return
        p.edit().putString(KEY_WAKE_DATE, day).putLong(KEY_WAKE_AT, epochMs).apply()
        if (day == LocalDate.now().toString()) _wakeAt.value = epochMs
    }

    /**
     * Registra un'ora della nanna (inizio di uno spegnimento lungo), ma solo se
     * è un orario da sonno notturno (19:00-05:00): i pisolini non contano.
     */
    fun recordBedtime(context: Context, epochMs: Long) {
        val time = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalTime()
        val minute = time.hour * 60 + time.minute
        if (minute in 300..1139) return // tra le 05:00 e le 19:00 non è "nanna"
        val list = (readBedtimes(context) + minute).takeLast(MAX_BEDTIME_SAMPLES)
        // Salviamo anche il timestamp intero del distacco (per il grafico sonno).
        val epochs = (readBedtimeEpochs(context) + epochMs).takeLast(MAX_BEDTIME_SAMPLES)
        prefs(context).edit()
            .putString(KEY_BEDTIMES, JSONArray(list).toString())
            .putString(KEY_BEDTIME_EPOCHS, JSONArray(epochs).toString())
            .apply()
        _medianBedtimeMinute.value = computeMedian(list)
    }

    /** Quanti campioni di nanna abbiamo raccolto finora (per la UI "in osservazione"). */
    fun bedtimeSampleCount(context: Context): Int = readBedtimes(context).size

    /** I momenti del "distacco" serale (epoch ms), dal più vecchio al più recente. */
    fun bedtimeEpochs(context: Context): List<Long> = readBedtimeEpochs(context)

    private fun readBedtimeEpochs(context: Context): List<Long> {
        val raw = prefs(context).getString(KEY_BEDTIME_EPOCHS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getLong(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun readBedtimes(context: Context): List<Int> {
        val raw = prefs(context).getString(KEY_BEDTIMES, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getInt(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Mediana delle ore della nanna. Gli orari scavalcano la mezzanotte
     * (23:30 e 00:45 sono vicini): si spostano tutti su una scala continua
     * "dal mezzogiorno al mezzogiorno" prima di ordinare, poi si torna indietro.
     */
    private fun computeMedian(minutes: List<Int>): Int? {
        if (minutes.size < MIN_SAMPLES_FOR_ESTIMATE) return null
        val shifted = minutes.map { if (it < 720) it + 1440 else it }.sorted()
        return shifted[shifted.size / 2] % 1440
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
