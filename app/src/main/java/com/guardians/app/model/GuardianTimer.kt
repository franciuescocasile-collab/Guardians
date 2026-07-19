package com.guardians.app.model

import com.guardians.app.data.tr

/** Unità di tempo selezionabili per le durate dei timer. */
enum class TimeUnit(
    private val nameIt: String,
    private val nameEn: String,
    val seconds: Long,
) {
    SECONDS("secondi", "seconds", 1L),
    MINUTES("minuti", "minutes", 60L),
    HOURS("ore", "hours", 3600L),
    DAYS("giorni", "days", 86400L),
    ;

    val displayName: String get() = tr(nameIt, nameEn)
}

/**
 * Ritmo degli avvisi del Messaggero:
 * - PROGRAMMABILE: cadenza fissa (ogni tot).
 * - GENTILE/MEDIA/INCALZANTE: intervalli decrescenti (più insistenti col tempo)
 *   con un pavimento minimo, così non diventano estenuanti.
 */
enum class MessengerPace(
    private val nameIt: String,
    private val nameEn: String,
    /** Fattore di decrescita per gap successivi; 0 = cadenza fissa (Programmabile). */
    val factor: Double,
) {
    PROGRAMMABILE("Programmabile", "Scheduled", 0.0),
    GENTILE("Gentile", "Gentle", 0.75),
    MEDIA("Media", "Medium", 0.55),
    INCALZANTE("Incalzante", "Pressing", 0.4),

    /** Bombardamento: gap che crolla a ogni avviso, pavimento più basso (10s). */
    INSOPPORTABILE("Insopportabile", "Unbearable", 0.22),
    ;

    val displayName: String get() = tr(nameIt, nameEn)
}

/** Intervallo minimo tra due avvisi del Messaggero (pavimento anti-sfinimento). */
const val MESSENGER_FLOOR_MS = 30_000L

/** Durata in testo leggibile: "45 secondi", "1 minuto", "10 ore", "3 giorni"... */
fun formatAmount(amount: Int, unit: TimeUnit): String = when (unit) {
    TimeUnit.SECONDS ->
        if (amount == 1) tr("1 secondo", "1 second") else "$amount ${tr("secondi", "seconds")}"
    TimeUnit.MINUTES ->
        if (amount == 1) tr("1 minuto", "1 minute") else "$amount ${tr("minuti", "minutes")}"
    TimeUnit.HOURS ->
        if (amount == 1) tr("1 ora", "1 hour") else "$amount ${tr("ore", "hours")}"
    TimeUnit.DAYS ->
        if (amount == 1) tr("1 giorno", "1 day") else "$amount ${tr("giorni", "days")}"
}

/**
 * Come [formatMs] ma con i SECONDI quando manca meno di un'ora: per i countdown
 * dal vivo (es. pausa della Sentinella) dove "1 min" nascondeva i secondi.
 * Esempi: "45 sec", "1 min 59 sec", "12 min". Oltre l'ora usa [formatMs].
 */
fun formatMsPrecise(ms: Long): String {
    val totalSec = ms / 1000L
    return when {
        totalSec < 60L -> "$totalSec sec"
        totalSec < 3600L -> {
            val minutes = totalSec / 60L
            val seconds = totalSec % 60L
            if (seconds == 0L) "$minutes min" else "$minutes min $seconds sec"
        }
        else -> formatMs(ms)
    }
}

/** Minuti dalla mezzanotte in formato orologio: 1320 -> "22:00". */
fun formatTimeOfDay(minuteOfDay: Int): String =
    "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)

/** Millisecondi in testo breve: "45 sec", "12 min", "2 ore e 5 min", "3 giorni e 4 ore". */
fun formatMs(ms: Long): String {
    val totalSec = ms / 1000L
    val days = totalSec / 86400L
    val hours = (totalSec % 86400L) / 3600L
    val minutes = (totalSec % 3600L) / 60L
    return when {
        totalSec < 60L -> "$totalSec sec"
        totalSec < 3600L -> "$minutes min"
        days == 0L -> {
            val hoursText =
                if (hours == 1L) tr("1 ora", "1 hour") else "$hours ${tr("ore", "hours")}"
            if (minutes == 0L) hoursText else "$hoursText ${tr("e", "and")} $minutes min"
        }
        else -> {
            val daysText =
                if (days == 1L) tr("1 giorno", "1 day") else "$days ${tr("giorni", "days")}"
            if (hours == 0L) {
                daysText
            } else {
                val hoursText =
                    if (hours == 1L) tr("1 ora", "1 hour") else "$hours ${tr("ore", "hours")}"
                "$daysText ${tr("e", "and")} $hoursText"
            }
        }
    }
}

/**
 * Ogni quanto si azzera il conteggio del Guardiano (e il suo eventuale blocco):
 * ogni giorno (predefinito), ogni lunedì o il primo del mese.
 */
enum class ResetCycle(
    private val nameIt: String,
    private val nameEn: String,
) {
    DAILY("Giornaliero", "Daily"),
    WEEKLY("Settimanale", "Weekly"),
    MONTHLY("Mensile", "Monthly"),
    ;

    val displayName: String get() = tr(nameIt, nameEn)
}

/**
 * I tipi di guardiano disponibili. Ogni tipo porta con sé nome, descrizione,
 * colore e forma, così le schermate e il popup si adattano da soli.
 *
 * Per aggiungere un nuovo tipo in futuro:
 * 1. aggiungi una voce a questo enum (nomi IT/EN, colore);
 * 2. disegna la sua forma in `TimerShapeIcon` (ui/Components.kt) e in
 *    `OverlayManager.ShapeView` — il compilatore segnala i `when` da completare;
 * 3. aggiungi la sua regola nel `when` di `MonitorService.tick()`;
 * 4. aggiungi i suoi campi in `EditScreen`/`TimerDraft` se ne servono di nuovi.
 */
enum class TimerType(
    private val nameIt: String,
    private val nameEn: String,
    private val shortIt: String,
    private val shortEn: String,
    private val taglineIt: String,
    private val taglineEn: String,
    private val exampleIt: String,
    private val exampleEn: String,
    /** Colore del tipo in formato ARGB (usato sia in Compose sia nel popup). */
    val colorArgb: Long,
    /** True se per questo tipo ha senso contare i "rientri respinti" nelle statistiche. */
    val tracksRejections: Boolean,
    /** True se il tipo si configura SOLO dalla home (non dall'editor dei timer). */
    val configuredFromHub: Boolean = false,
) {
    /** Triangolo giallo: uso continuo + pausa (0 = nessuna pausa obbligatoria). */
    SENTINELLA(
        "La Sentinella", "The Sentinel",
        "Sentinella", "Sentinel",
        "Ti butta fuori dopo troppo uso continuo e ti impone una pausa",
        "Kicks you out after too much continuous use and imposes a break",
        "Es. Sentinella social", "E.g. Social Sentinel",
        0xFFFFC107,
        tracksRejections = true,
    ),

    /** Quadrato rosso: tempo totale giornaliero, poi blocco fino a domani. */
    GUARDIANO(
        "Il Guardiano", "The Guardian",
        "Guardiano", "Guardian",
        "Superato il limite giornaliero di tempo, blocca fino a domani",
        "Once the daily time limit is reached, blocks until tomorrow",
        "Es. Guardiano giochi", "E.g. Games Guardian",
        0xFFE53935,
        tracksRejections = true,
    ),

    /** Cerchio blu: fascia oraria protetta (anche a cavallo della mezzanotte). */
    CUSTODE(
        "Il Custode", "The Keeper",
        "Custode", "Keeper",
        "In certi orari non ti fa usare le app sorvegliate",
        "During certain hours the watched apps cannot be used",
        "Es. Custode della notte", "E.g. Night Keeper",
        0xFF42A5F5,
        tracksRejections = false,
    ),

    /** Rombo viola: limita le APERTURE giornaliere, poi blocco fino a domani. */
    GENDARME(
        "Il Gendarme", "The Gendarme",
        "Gendarme", "Gendarme",
        "Limita quante volte al giorno puoi aprire le app",
        "Limits how many times a day you can open the apps",
        "Es. Gendarme mail", "E.g. Mail Gendarme",
        0xFFAB47BC,
        tracksRejections = true,
    ),

    /**
     * Pentagono arancione: NON blocca, ma manda notifiche sempre più insistenti
     * mentre continui a usare l'app, con messaggi a tono crescente. La cadenza
     * si genera da sola (vedi [MessengerPace]).
     */
    MESSAGGERO(
        "Il Messaggero", "The Messenger",
        "Messaggero", "Messenger",
        "Non blocca: ti avvisa con insistenza crescente",
        "Doesn't block: nudges you with growing insistence",
        "Es. Messaggero social", "E.g. Social Messenger",
        0xFFFF7043,
        tracksRejections = false,
    ),

    /**
     * Alba d'argento (semicerchio): protegge i momenti fragili della giornata
     * SENZA orari fissi. Fase mattutina: blocca le app per [GuardianTimer.limitAmount]
     * dal momento del VERO risveglio (schermo spento ≥4h + finestra oraria).
     * Fase serale: impara l'ora della nanna e blocca le app da
     * [GuardianTimer.resetAmount] prima di quell'ora fino alle 04:00.
     */
    ARALDO(
        "L'Araldo", "The Herald",
        "Araldo", "Herald",
        "Protegge il risveglio e la sera, imparando i tuoi orari",
        "Guards your mornings and evenings, learning your rhythm",
        "Es. Araldo dell'alba", "E.g. Dawn Herald",
        0xFF9FA8DA,
        tracksRejections = false,
    ),

    /**
     * Esagono bronzo: NON vieta l'app, ma a ogni apertura fa "pagare un pedaggio"
     * di attesa ([GuardianTimer.limitAmount], tip. 30-60 secondi): una schermata
     * di respiro con countdown, per spezzare le aperture compulsive.
     */
    ESATTORE(
        "L'Esattore", "The Tollkeeper",
        "Esattore", "Tollkeeper",
        "A ogni apertura ti fa pagare un pedaggio di attesa",
        "Every time you open the app, it charges a waiting toll",
        "Es. Esattore social", "E.g. Social Tollkeeper",
        0xFFB08D57,
        tracksRejections = false,
    ),

    /**
     * Stella verde acqua: blocca le app sorvegliate solo quando ti trovi in un
     * PUNTO preciso (entro [GuardianTimer.radiusMeters]) e nella fascia oraria
     * scelta. Es. "a casa dalle 00 all'1 blocca Instagram".
     */
    VEDETTA(
        "La Vedetta", "The Lookout",
        "Vedetta", "Lookout",
        "Agisce solo quando sei in un certo posto",
        "Acts only when you are in a certain place",
        "Es. Vedetta di casa", "E.g. Home Lookout",
        0xFF26A69A,
        tracksRejections = false,
    ),

    /**
     * Torre di pietra merlata: NON guarda il tempo ma il GIORNO della settimana.
     * Le app sorvegliate sono del tutto sigillate nei giorni scelti (es. dal
     * lunedì al venerdì) e libere negli altri. Vedi [GuardianTimer.blockedDays].
     */
    CASTELLANO(
        "Il Castellano", "The Castellan",
        "Castellano", "Castellan",
        "Sigilla del tutto le app in certi giorni della settimana",
        "Fully seals the apps on certain days of the week",
        "Es. Castellano feriale", "E.g. Weekday Castellan",
        0xFF607D8B,
        tracksRejections = true,
    ),
    ;

    val displayName: String get() = tr(nameIt, nameEn)
    val shortName: String get() = tr(shortIt, shortEn)
    val tagline: String get() = tr(taglineIt, taglineEn)
    val nameExample: String get() = tr(exampleIt, exampleEn)
}

data class GuardianTimer(
    val id: String,
    val name: String,
    val type: TimerType,
    /** Quantità e unità del limite (uso continuo o totale giornaliero). */
    val limitAmount: Int,
    val limitUnit: TimeUnit,
    /** Guardiano: ogni quanto si azzerano conteggio e blocco (giorno/settimana/mese). */
    val resetCycle: ResetCycle = ResetCycle.DAILY,
    /** Quantità e unità della pausa della Sentinella (0 = nessuna pausa obbligatoria). */
    val resetAmount: Int = 0,
    val resetUnit: TimeUnit = TimeUnit.MINUTES,
    /** Fascia oraria del Custode, in minuti dalla mezzanotte (es. 1320 = 22:00). */
    val startMinuteOfDay: Int = 0,
    val endMinuteOfDay: Int = 0,
    /**
     * Castellano: i giorni in cui le app sono SIGILLATE (1 = lunedì … 7 =
     * domenica, come java.time.DayOfWeek.value). Vuoto = nessun blocco.
     */
    val blockedDays: Set<Int> = emptySet(),
    /** Aperture giornaliere consentite dal Gendarme (0 = nessun limite giornaliero). */
    val maxOpensPerDay: Int = 0,
    /** Gendarme: minuti di attesa prima di poter riaprire l'app (0 = nessuno). */
    val reopenCooldownMinutes: Int = 0,
    /** Gendarme: manda una notifica dopo tot aperture (0 = nessuna). */
    val notifyAfterOpens: Int = 0,
    /** Usato dal timer sintetico dell'Incantesimo di Congelamento. */
    val activeUntilEpochMs: Long = 0L,
    /** Preavviso prima del blocco (0 = nessuna notifica di avviso). */
    val warnAmount: Int = 0,
    val warnUnit: TimeUnit = TimeUnit.MINUTES,
    /** Preavvisi AGGIUNTIVI in millisecondi (5): es. a 10 e a 2 minuti dal blocco. */
    val extraWarnsMs: List<Long> = emptyList(),
    /** Posizione della Vedetta (NaN = non impostata) e raggio in metri. */
    val latitude: Double = Double.NaN,
    val longitude: Double = Double.NaN,
    val radiusMeters: Int = 150,
    /** Potere prestato alla Vedetta (il guardiano che agisce quando sei nel luogo). */
    val innerType: TimerType = TimerType.SENTINELLA,
    /** Araldo: fase mattutina (blocco dal risveglio) e serale (prima della nanna). */
    val araldoMorning: Boolean = true,
    val araldoEvening: Boolean = false,
    /** Ritmo degli avvisi del Messaggero. */
    val pace: MessengerPace = MessengerPace.PROGRAMMABILE,
    /** Numero massimo di avvisi del Messaggero (0 = illimitato). */
    val maxNotices: Int = 0,
    /** Messaggi personalizzati del Messaggero (vuoto = usa quelli predefiniti). */
    val messages: List<String> = emptyList(),
    val allApps: Boolean = false,
    val packages: List<String> = emptyList(),
    val enabled: Boolean = true,
    /** Squadra di appartenenza; vuota = "Squadra Generale". */
    val team: String = "",
) {
    // Nota: il nome è anche la chiave usata dall'Incantesimo d'Ombra, quindi
    // NON cambia con la lingua (altrimenti i riferimenti salvati si romperebbero).
    /** Nome della squadra, con il fallback automatico sulla Squadra Generale. */
    val teamName: String get() = team.trim().ifBlank { "Squadra Generale" }

    val limitMs: Long get() = limitAmount * limitUnit.seconds * 1000L
    val resetMs: Long get() = resetAmount * resetUnit.seconds * 1000L
    val warnMs: Long get() = warnAmount * warnUnit.seconds * 1000L

    /** Tutte le soglie di preavviso (principale + aggiuntive), più lontana prima. */
    val allWarnsMs: List<Long>
        get() = (extraWarnsMs + warnMs).filter { it > 0L }.distinct().sortedDescending()
    val limitText: String get() = formatAmount(limitAmount, limitUnit)
    val resetText: String get() = formatAmount(resetAmount, resetUnit)
    val warnText: String get() = formatAmount(warnAmount, warnUnit)
    val hasLocation: Boolean get() = !latitude.isNaN() && !longitude.isNaN()
    /** Comportamento effettivo: per la Vedetta è il potere prestato, altrimenti il tipo stesso. */
    val effectiveType: TimerType get() = if (type == TimerType.VEDETTA) innerType else type

    /**
     * Millisecondi da attendere prima dell'avviso successivo del Messaggero,
     * avendone già mandati [noticesSent]. Il primo avviso scatta a [limitMs]
     * (uso continuo); da lì i gap seguono il ritmo scelto, con pavimento minimo.
     */
    fun messengerGapMs(noticesSent: Int): Long = when (pace) {
        MessengerPace.PROGRAMMABILE -> resetMs.coerceAtLeast(MESSENGER_FLOOR_MS)
        // Insopportabile: pavimento più basso (10s), per il vero bombardamento.
        MessengerPace.INSOPPORTABILE ->
            (limitMs * Math.pow(pace.factor, noticesSent.toDouble()))
                .toLong().coerceAtLeast(10_000L)
        else -> (limitMs * Math.pow(pace.factor, noticesSent.toDouble()))
            .toLong().coerceAtLeast(MESSENGER_FLOOR_MS)
    }

    /** True se la fascia oraria del Custode è attiva al minuto dato (gestisce la mezzanotte). */
    fun isActiveAt(minuteOfDay: Int): Boolean =
        if (startMinuteOfDay < endMinuteOfDay) {
            minuteOfDay >= startMinuteOfDay && minuteOfDay < endMinuteOfDay
        } else {
            minuteOfDay >= startMinuteOfDay || minuteOfDay < endMinuteOfDay
        }
}
