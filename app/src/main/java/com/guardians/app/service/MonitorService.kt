package com.guardians.app.service

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.guardians.app.MainActivity
import com.guardians.app.R
import com.guardians.app.data.AraldoData
import com.guardians.app.data.ConductRepository
import com.guardians.app.data.ExclusionsRepository
import com.guardians.app.data.TeamsRepository
import com.guardians.app.data.TravelRepository
import com.guardians.app.data.ProfileRepository
import com.guardians.app.data.SettingsRepository
import com.guardians.app.data.SpellsRepository
import com.guardians.app.data.tr
import com.guardians.app.data.StatsRepository
import com.guardians.app.data.TimerRepository
import com.guardians.app.data.UsageHistoryRepository
import com.guardians.app.data.UsageStateStore
import com.guardians.app.model.GuardianTimer
import com.guardians.app.model.MESSENGER_FLOOR_MS
import com.guardians.app.model.TimerType
import com.guardians.app.model.formatMs
import com.guardians.app.model.formatMsPrecise
import com.guardians.app.model.formatTimeOfDay
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime

/**
 * Servizio in foreground che ogni secondo controlla quale app è in primo piano
 * (tramite UsageStatsManager) e applica le regole dei timer:
 *
 * - SENTINELLA: conteggio dell'uso continuo; al superamento del limite butta
 *   fuori dall'app (schermata home + popup) e impone una pausa: finché non è
 *   passata, ogni tentativo di rientrare viene respinto. Finita la pausa il
 *   conteggio riparte da zero.
 * - GUARDIANO: conteggio dell'uso totale giornaliero; al superamento del limite
 *   l'app è "bloccata": ogni volta che viene riaperta si viene rimandati alla
 *   home con il popup, fino a mezzanotte.
 */
class MonitorService : Service() {

    companion object {
        private const val CHANNEL_ID = "guardians_monitor"
        private const val CHANNEL_ID_MIN = "guardians_monitor_min"
        private const val WARN_CHANNEL_ID = "guardians_warnings"
        private const val NOTIFICATION_ID = 1
        private const val TICK_MS = 1000L
        private const val PERSIST_EVERY_MS = 15_000L
        private const val TRIGGER_THROTTLE_MS = 5_000L
        // Sotto questa pausa, tornare sull'app NON conta come nuova apertura.
        private const val OPEN_GAP_MS = 20_000L
        // La posizione (Vedetta) si aggiorna al massimo ogni 45 secondi, per la batteria.
        private const val LOCATION_EVERY_MS = 45_000L
        // Araldo: uno spegnimento schermo di almeno 4 ore consecutive = una dormita.
        private const val WAKE_GAP_MS = 4L * 3600_000L
        private const val KEY_STATE = "usage_state"
        private const val KEY_WEEKLY_PENDING = "weekly_report_pending"

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context, Intent(context, MonitorService::class.java)
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MonitorService::class.java))
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var usageStats: UsageStatsManager
    private lateinit var power: PowerManager
    private lateinit var uiMode: android.app.UiModeManager
    private lateinit var prefs: SharedPreferences

    private var currentForeground: String? = null
    private var lastQueryTime = 0L
    private var lastTickAt = 0L
    private var lastPersistAt = 0L
    private var today = ""
    private var excluded: Set<String> = emptySet()

    // Resoconto settimanale IN ATTESA: fissato al cambio settimana, spedito solo
    // al mattino (al primo schermo acceso dopo l'orario di sveglia), non a mezzanotte.
    private var weeklyReportPending = false

    // Stato per id del timer (millisecondi)
    private val dailyMs = mutableMapOf<String, Long>()
    private val continuousMs = mutableMapOf<String, Long>()
    private val lastSeenMs = mutableMapOf<String, Long>()
    private val blockedToday = mutableSetOf<String>()
    private val lastTriggerMs = mutableMapOf<String, Long>()

    // Fine della pausa obbligatoria della Sentinella (epoch ms), per id timer.
    private val cooldownUntil = mutableMapOf<String, Long>()

    // Aperture di oggi contate dal Gendarme, per id timer.
    private val gendarmeOpens = mutableMapOf<String, Int>()

    // Ultimo istante in cui l'app sorvegliata dal Gendarme era in primo piano.
    private val gendarmeLastSeen = mutableMapOf<String, Long>()

    // App in primo piano al tick precedente (per rilevare le "aperture").
    private var previousForeground: String? = null

    // Timer per cui è già stata mandata la notifica di preavviso (in questo ciclo).
    private val warned = mutableSetOf<String>()

    // Posizione corrente per la Vedetta, aggiornata al massimo ogni LOCATION_EVERY_MS.
    private var lastLocation: android.location.Location? = null
    private var lastLocationAt = 0L

    // Risparmio batteria: true quando la carica è sotto la soglia scelta (non in carica).
    private var batteryPaused = false
    private var lastBatteryCheckAt = 0L

    // "Sonno profondo": ticker fermo del tutto, nessun consumo di CPU. Si esce
    // solo quando la batteria risale o il telefono va in carica (eventi di sistema).
    private var deepSleeping = false

    private val batteryWakeReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            maybeWakeFromDeepSleep()
        }
    }

    // Rete di sicurezza: se gli eventi batteria non arrivassero, un controllo
    // leggero ogni 5 minuti prova comunque a svegliare i guardiani.
    private val sleepFallbackCheck = object : Runnable {
        override fun run() {
            maybeWakeFromDeepSleep()
            if (deepSleeping) handler.postDelayed(this, 300_000L)
        }
    }

    // Messaggero: avvisi mandati e istante del prossimo avviso, per id timer.
    private val messengerCount = mutableMapOf<String, Int>()
    private val messengerNextAt = mutableMapOf<String, Long>()

    // Esattore: ultimo istante in cui l'app sorvegliata era in primo piano.
    private val esattoreLastSeen = mutableMapOf<String, Long>()

    // Esattore: chi ha toccato "lascia perdere" ripaga il pedaggio al prossimo
    // tentativo, anche se rientra subito (niente finestra di tolleranza).
    private val esattoreGaveUp = mutableSetOf<String>()

    // Anti-farming: ultimo istante in cui abbiamo LOGGATO un rientro respinto,
    // per raggruppare i tentativi ripetuti entro 5 minuti (per id timer).
    private val lastRejectionLog = mutableMapOf<String, Long>()

    // Ultimo valore noto della preferenza "notifica di monitoraggio", per
    // ricreare la notifica fissa appena l'utente la cambia.
    private var lastMonitorPref: Boolean? = null

    private val ticker = object : Runnable {
        override fun run() {
            // BULLETPROOF (Modulo 1.1): il ciclo di polling non deve MAI far
            // crashare il servizio. I picchi di carico del sistema (es. gaming
            // pesante) possono lanciare NPE o ConcurrentModificationException
            // dentro UsageStatsManager: le intercettiamo in modo pulito e
            // liberiamo i sample temporanei di questo ciclo.
            try {
                tick()
            } catch (_: NullPointerException) {
                releaseTransientSamples()
            } catch (_: ConcurrentModificationException) {
                releaseTransientSamples()
            } catch (_: Throwable) {
                releaseTransientSamples()
            } finally {
                handler.postDelayed(this, TICK_MS)
            }
        }
    }

    /** Libera i buffer temporanei di un ciclo per non trascinare stato sporco. */
    private fun releaseTransientSamples() {
        warned.clear()
        previousForeground = null
    }

    override fun onCreate() {
        super.onCreate()
        TimerRepository.load(this)
        SettingsRepository.load(this)
        StatsRepository.load(this)
        ExclusionsRepository.load(this)
        ProfileRepository.load(this)
        SpellsRepository.load(this)
        UsageHistoryRepository.load(this)
        UsageHistoryRepository.loadGoalSnapshots(this)
        TeamsRepository.load(this)
        TravelRepository.load(this)
        ConductRepository.load(this)
        usageStats = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        power = getSystemService(Context.POWER_SERVICE) as PowerManager
        uiMode = getSystemService(Context.UI_MODE_SERVICE) as android.app.UiModeManager
        prefs = getSharedPreferences("guardians_prefs", Context.MODE_PRIVATE)

        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        excluded = computeExcluded()
        today = logicalToday()
        restoreState()
        weeklyReportPending = prefs.getBoolean(KEY_WEEKLY_PENDING, false)
        AraldoData.load(this)
        // Se il risveglio è avvenuto mentre il servizio era giù, ricostruiscilo.
        reconstructAraldoWake()

        val now = System.currentTimeMillis()
        lastQueryTime = now - 60_000
        lastTickAt = now
        lastPersistAt = now
        handler.post(ticker)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        handler.removeCallbacks(sleepFallbackCheck)
        if (deepSleeping) {
            try {
                unregisterReceiver(batteryWakeReceiver)
            } catch (_: Exception) {
            }
        }
        persistState()
        UsageStateStore.serviceStopped()
        super.onDestroy()
    }

    // ------------------------------------------------------------------ tick

    private fun tick() {
        val now = System.currentTimeMillis()
        // Se il telefono è rimasto in sospensione, non conteggiare il tempo trascorso.
        val elapsed = (now - lastTickAt).coerceIn(0L, 10_000L)
        lastTickAt = now

        // Se l'utente ha cambiato la preferenza sulla notifica di monitoraggio,
        // ricostruiamo la notifica fissa col nuovo stile.
        val monitorPref = SettingsRepository.monitorNotification.value
        if (lastMonitorPref != null && lastMonitorPref != monitorPref) {
            updateServiceNotification(sleeping = deepSleeping)
        }
        lastMonitorPref = monitorPref

        // Cambio giornata: azzera tutto (tranne i Guardiani con ciclo settimanale
        // o mensile, che si azzerano solo il lunedì / il primo del mese).
        // Il "giorno" inizia all'orario scelto nelle Impostazioni (default 00:00).
        val date = logicalToday()
        if (date != today) {
            // Salva nello storico il totale di ieri e, di lunedì, manda il resoconto.
            recordDayInHistory(today)
            // Istantanea IMMUTABILE dell'obiettivo di ieri: sotto/sopra la soglia
            // in vigore QUEL giorno (mai ricalcolata se domani cambi obiettivo).
            recordGoalSnapshot(today)
            // Motore della Condotta: aggiorna la barra sui fatti di ieri. Il
            // viaggio non cambia nulla qui: conta tutto normalmente.
            rolloverConduct(today)
            val newDay = LocalDate.parse(date)
            // Il resoconto NON parte a mezzanotte: si mette in attesa il primo
            // giorno della settimana (lunedì o domenica, a scelta) e verrà spedito
            // al mattino, quando ti svegli (vedi il controllo più sotto nel tick).
            val firstDow = if (SettingsRepository.weekStartMonday.value)
                java.time.DayOfWeek.MONDAY else java.time.DayOfWeek.SUNDAY
            if (newDay.dayOfWeek == firstDow && SettingsRepository.weeklyReport.value) {
                weeklyReportPending = true
                prefs.edit().putBoolean(KEY_WEEKLY_PENDING, true).apply()
            }
            today = date
            val timersById = TimerRepository.timers.value.associateBy { it.id }
            fun cycleResets(id: String): Boolean {
                val t = timersById[id] ?: return true
                if (t.effectiveType != TimerType.GUARDIANO) return true
                return when (t.resetCycle) {
                    com.guardians.app.model.ResetCycle.DAILY -> true
                    com.guardians.app.model.ResetCycle.WEEKLY ->
                        newDay.dayOfWeek == java.time.DayOfWeek.MONDAY
                    com.guardians.app.model.ResetCycle.MONTHLY -> newDay.dayOfMonth == 1
                }
            }
            dailyMs.keys.removeAll { cycleResets(it) }
            blockedToday.removeAll { cycleResets(it) }
            continuousMs.clear()
            lastSeenMs.clear()
            cooldownUntil.clear()
            gendarmeOpens.clear()
            gendarmeLastSeen.clear()
            messengerCount.clear()
            messengerNextAt.clear()
            esattoreLastSeen.clear()
            esattoreGaveUp.clear()
            warned.clear()
            persistState()
        }

        // Risparmio batteria: sotto la soglia scelta i motori si SPENGONO del
        // tutto (ticker fermo, blocchi rimossi, zero CPU). La sveglia arriva
        // dagli eventi di sistema quando la carica risale o si va in carica.
        updateBatteryPause(now)
        if (batteryPaused) {
            enterDeepSleep()
            return
        }

        // A schermo spento non si usa nessuna app: evitiamo del tutto le letture
        // di UsageStats per non consumare batteria.
        val screenOn = power.isInteractive
        // Araldo: osserva accensioni e spegnimenti dello schermo FISICO.
        trackScreenForAraldo(screenOn, now)

        // Resoconto settimanale IN ATTESA: lo spediamo al MATTINO, al primo schermo
        // acceso dopo l'orario di sveglia abituale (o le 07:00), così arriva
        // "quando ti svegli" e non a mezzanotte. Un flag persistito evita doppioni.
        if (weeklyReportPending && screenOn) {
            val nowMin = java.time.LocalTime.now().let { it.hour * 60 + it.minute }
            val usualWake = ProfileRepository.usualWakeMinute.value
            val wakeThreshold = if (usualWake in 0..1439) usualWake else 7 * 60
            if (nowMin >= wakeThreshold) {
                if (SettingsRepository.weeklyReport.value) sendWeeklyReport()
                weeklyReportPending = false
                prefs.edit().putBoolean(KEY_WEEKLY_PENDING, false).apply()
            }
        }
        // ANDROID AUTO: se il sistema è in modalità auto, il tempo passato con
        // lo schermo dell'auto NON conta e i guardiani non bloccano. Vale solo
        // lo schermo fisico del telefono.
        val carMode = try {
            uiMode.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_CAR
        } catch (_: Exception) {
            false
        }
        val interactive = screenOn && !carMode
        if (interactive) updateForeground(now)
        val foreground = if (interactive) currentForeground else null
        // Nuova app in primo piano rispetto al tick precedente = una "apertura".
        val foregroundJustChanged = foreground != null && foreground != previousForeground
        previousForeground = foreground
        // MODALITÀ VIAGGIO: sospende soltanto i guardiani per la durata
        // impostata; il tempo e i dati contano NORMALMENTE. Allo scadere la
        // protezione si ripristina da sola.
        val traveling = TravelRepository.isActive()

        // L'Incantesimo d'Ombra sospende le squadre colpite; scaduto il countdown
        // la protezione riprende da sola. Inoltre una squadra vale solo nei
        // giorni scelti nella sua pianificazione settimanale.
        val timers = if (traveling) emptyList() else TimerRepository.timers.value.filter {
            it.enabled && !SpellsRepository.isShadowed(it.teamName) &&
                TeamsRepository.isTeamActiveToday(it.teamName)
        }
        // Aggiorna la posizione (Vedetta) solo se serve, e non troppo spesso.
        if (interactive && timers.any { it.type == TimerType.VEDETTA && it.hasLocation } &&
            now - lastLocationAt >= LOCATION_EVERY_MS
        ) {
            refreshLocation()
            lastLocationAt = now
        }
        // Esclusioni fisse (telefono, impostazioni…) + quelle scelte dall'utente.
        val excludedNow = excluded + ExclusionsRepository.excluded.value

        // IL TEMPO SCORRE ANCHE SOTTO INCANTESIMO (regola dell'utente): i
        // guardiani sospesi da Viaggio, Ombra o giorni di riposo NON bloccano,
        // ma i loro conteggi (tempo giornaliero, uso continuo, aperture)
        // continuano a correre. Un'Ombra di 2h non "regala" 2h di Instagram:
        // finito l'incantesimo, il guardiano riprende coi conteggi veri.
        run {
            val activeIds = timers.mapTo(HashSet()) { it.id }
            val suspended = TimerRepository.timers.value
                .filter { it.enabled && it.id !in activeIds }
            if (suspended.isEmpty()) return@run
            for (timer in suspended) {
                val effType =
                    if (timer.type == TimerType.VEDETTA) timer.innerType else timer.type
                val isTarget = foreground != null && !excludedNow.contains(foreground) &&
                    (timer.allApps || timer.packages.contains(foreground))
                when (effType) {
                    TimerType.GUARDIANO -> if (isTarget) {
                        dailyMs[timer.id] = (dailyMs[timer.id] ?: 0L) + elapsed
                    }

                    TimerType.SENTINELLA -> if (isTarget) {
                        continuousMs[timer.id] = (continuousMs[timer.id] ?: 0L) + elapsed
                        lastSeenMs[timer.id] = now
                    } else {
                        val seen = lastSeenMs[timer.id] ?: 0L
                        if (seen != 0L && now - seen >= timer.resetMs) {
                            continuousMs[timer.id] = 0L
                            lastSeenMs.remove(timer.id)
                        }
                    }

                    TimerType.GENDARME -> if (isTarget) {
                        val lastSeen = gendarmeLastSeen[timer.id] ?: 0L
                        val away = if (lastSeen == 0L) Long.MAX_VALUE else now - lastSeen
                        if (foregroundJustChanged && away > OPEN_GAP_MS) {
                            gendarmeOpens[timer.id] = (gendarmeOpens[timer.id] ?: 0) + 1
                        }
                        gendarmeLastSeen[timer.id] = now
                    }

                    // Custode, Esattore, Messaggero e Araldo non hanno contatori
                    // cumulativi: sospesi, semplicemente non intervengono.
                    else -> Unit
                }
            }
        }

        // Incantesimo di Congelamento: blocco totale del telefono. Con il
        // conteggio oltre-scadenza attivo, il blocco regge anche dopo lo zero,
        // finché la sessione non viene terminata a mano da Guardians.
        val freezeUntil = SpellsRepository.freezeUntil.value
        if (foreground != null && SpellsRepository.isFreezeSessionActive() &&
            !excludedNow.contains(foreground)
        ) {
            trigger(freezeTimer(freezeUntil), foreground)
        }

        for (timer in timers) {
            // La Vedetta presta il suo potere a un altro guardiano, ma solo quando
            // sei nel luogo scelto. Senza posizione (GPS spento, nessun dato) NON
            // fa nulla: meglio non bloccare che bloccare a vuoto.
            val locationOk = timer.type != TimerType.VEDETTA || isWithinVedetta(timer)
            val effectiveType =
                if (timer.type == TimerType.VEDETTA) timer.innerType else timer.type

            val isTarget = foreground != null && !excludedNow.contains(foreground) &&
                (timer.allApps || timer.packages.contains(foreground)) && locationOk

            when (effectiveType) {
                TimerType.GUARDIANO -> {
                    if (isTarget) {
                        if (blockedToday.contains(timer.id)) {
                            trigger(timer, foreground!!, rejected = true)
                        } else {
                            val used = (dailyMs[timer.id] ?: 0L) + elapsed
                            dailyMs[timer.id] = used
                            maybeWarn(timer, timer.limitMs - used)
                            if (used >= timer.limitMs) {
                                blockedToday.add(timer.id)
                                persistState()
                                trigger(timer, foreground!!)
                            }
                        }
                    }
                }

                TimerType.SENTINELLA -> {
                    val cooldownEnd = cooldownUntil[timer.id] ?: 0L
                    if (isTarget) {
                        if (now < cooldownEnd) {
                            // Pausa obbligatoria in corso: respingi il rientro.
                            trigger(
                                timer, foreground!!,
                                cooldownRemainingMs = cooldownEnd - now,
                                rejected = true,
                            )
                        } else {
                            if (cooldownEnd != 0L) {
                                cooldownUntil.remove(timer.id)
                            }
                            val used = (continuousMs[timer.id] ?: 0L) + elapsed
                            continuousMs[timer.id] = used
                            lastSeenMs[timer.id] = now
                            maybeWarn(timer, timer.limitMs - used)
                            if (used >= timer.limitMs) {
                                continuousMs[timer.id] = 0L
                                warned.remove(timer.id)
                                // Con pausa 0 non c'è pausa obbligatoria: solo espulsione.
                                if (timer.resetMs > 0L) {
                                    cooldownUntil[timer.id] = now + timer.resetMs
                                }
                                persistState()
                                trigger(timer, foreground!!)
                            }
                        }
                    } else {
                        val seen = lastSeenMs[timer.id] ?: 0L
                        if (seen != 0L && now - seen >= timer.resetMs) {
                            continuousMs[timer.id] = 0L
                            lastSeenMs.remove(timer.id)
                            warned.remove(timer.id)
                        }
                        if (cooldownEnd != 0L && now >= cooldownEnd) {
                            cooldownUntil.remove(timer.id)
                        }
                    }
                }

                TimerType.CUSTODE -> {
                    val minuteOfDay = LocalTime.now().let { it.hour * 60 + it.minute }
                    if (isTarget && timer.isActiveAt(minuteOfDay)) {
                        trigger(timer, foreground!!)
                    }
                    // Preavviso: poco prima dell'inizio del turno.
                    if (timer.warnMs > 0L && !warned.contains(timer.id) &&
                        !timer.isActiveAt(minuteOfDay)
                    ) {
                        val minutesToStart =
                            (timer.startMinuteOfDay - minuteOfDay + 1440) % 1440
                        if (minutesToStart * 60_000L <= timer.warnMs) {
                            warned.add(timer.id)
                            sendWarning(
                                timer,
                                tr(
                                    "Tra ${formatMs(minutesToStart * 60_000L)} ${timer.name} " +
                                        "bloccherà le app sorvegliate (fino alle " +
                                        "${formatTimeOfDay(timer.endMinuteOfDay)}).",
                                    "In ${formatMs(minutesToStart * 60_000L)} ${timer.name} " +
                                        "will block the watched apps (until " +
                                        "${formatTimeOfDay(timer.endMinuteOfDay)}).",
                                )
                            )
                        }
                    }
                }

                TimerType.GENDARME -> {
                    if (isTarget) {
                        if (blockedToday.contains(timer.id)) {
                            trigger(timer, foreground!!, rejected = true)
                        } else {
                            // Un'apertura vera = l'app torna in primo piano dopo
                            // essere stata via per un po' (i cambi di schermata
                            // rapidi, popup e sblocchi non contano).
                            val lastSeen = gendarmeLastSeen[timer.id] ?: 0L
                            val away = if (lastSeen == 0L) Long.MAX_VALUE else now - lastSeen
                            val isNewOpen = foregroundJustChanged && away > OPEN_GAP_MS
                            val cooldownMs = timer.reopenCooldownMinutes * 60_000L

                            if (cooldownMs > 0L && isNewOpen && away < cooldownMs) {
                                // Riaperta troppo presto: blocca finché non passa il
                                // cooldown. NON aggiorna lastSeen (l'attesa conta dalla
                                // chiusura vera) e non conta come apertura.
                                trigger(
                                    timer, foreground!!,
                                    cooldownRemainingMs = cooldownMs - away,
                                    rejected = true,
                                )
                            } else {
                                if (isNewOpen) {
                                    val opens = (gendarmeOpens[timer.id] ?: 0) + 1
                                    gendarmeOpens[timer.id] = opens
                                    persistState()
                                    when {
                                        timer.maxOpensPerDay > 0 && opens > timer.maxOpensPerDay -> {
                                            blockedToday.add(timer.id)
                                            trigger(timer, foreground!!)
                                        }
                                        timer.notifyAfterOpens > 0 && opens == timer.notifyAfterOpens ->
                                            sendWarning(
                                                timer,
                                                tr(
                                                    "Hai aperto ${appLabelOf(timer)} $opens volte oggi.",
                                                    "You've opened ${appLabelOf(timer)} $opens times today.",
                                                )
                                            )
                                    }
                                }
                                gendarmeLastSeen[timer.id] = now
                            }
                        }
                    }
                }

                TimerType.MESSAGGERO -> {
                    if (isTarget) {
                        val used = (continuousMs[timer.id] ?: 0L) + elapsed
                        continuousMs[timer.id] = used
                        lastSeenMs[timer.id] = now
                        val count = messengerCount[timer.id] ?: 0
                        val cappedOut = timer.maxNotices > 0 && count >= timer.maxNotices
                        val due = if (count == 0) {
                            used >= timer.limitMs        // primo avviso: soglia d'uso continuo
                        } else {
                            now >= (messengerNextAt[timer.id] ?: Long.MAX_VALUE)
                        }
                        if (!cappedOut && due) {
                            sendMessengerNotice(timer, count + 1)
                            messengerCount[timer.id] = count + 1
                            messengerNextAt[timer.id] = now + timer.messengerGapMs(count + 1)
                        }
                    } else {
                        // Lasciata l'app abbastanza a lungo: azzera e ricomincia da capo.
                        val seen = lastSeenMs[timer.id] ?: 0L
                        if (seen != 0L && now - seen >= timer.resetMs.coerceAtLeast(MESSENGER_FLOOR_MS)) {
                            continuousMs[timer.id] = 0L
                            lastSeenMs.remove(timer.id)
                            messengerCount.remove(timer.id)
                            messengerNextAt.remove(timer.id)
                        }
                    }
                }

                TimerType.ARALDO -> {
                    // STANDBY (19.2): con la card Sonno nascosta dalla home,
                    // l'Araldo non blocca. I dati (risvegli, nanne) continuano
                    // a essere raccolti da trackScreenForAraldo: rimostrando la
                    // card, l'Araldo si risveglia con lo storico intatto.
                    com.guardians.app.data.HomeConfigRepository.load(this)
                    if (com.guardians.app.data.HomeConfigRepository.isSleepHidden()) {
                        // niente blocchi finché è in standby
                    } else if (isTarget) {
                        val minuteOfDay = LocalTime.now().let { it.hour * 60 + it.minute }
                        // Mattina: blocco per limitMs dal VERO risveglio (se caduto
                        // nella finestra del timer). Se oggi non ne abbiamo ancora
                        // visto uno, l'orario INDICATIVO del profilo fa da base:
                        // così l'Araldo protegge fin dal primo giorno.
                        var wakeAt = AraldoData.wakeAt.value
                        if (wakeAt == 0L) {
                            val usual = ProfileRepository.usualWakeMinute.value
                            if (usual >= 0) {
                                val t = todayAtMinute(usual)
                                if (now >= t) wakeAt = t
                            }
                        }
                        val morningActive = timer.araldoMorning && wakeAt != 0L &&
                            now < wakeAt + timer.limitMs &&
                            timer.isActiveAt(minuteOfEpoch(wakeAt))
                        // Sera: dall'ora della nanna meno l'anticipo (resetMs) fino
                        // alle 04:00. La nanna è la MEDIANA imparata (≥3 notti);
                        // finché non c'è, vale l'orario indicativo del profilo.
                        val bedtime = AraldoData.medianBedtimeMinute.value
                            ?: ProfileRepository.usualBedMinute.value.takeIf { it >= 0 }
                        val eveningActive = timer.araldoEvening && bedtime != null &&
                            run {
                                val anticipMin = (timer.resetMs / 60_000L).toInt()
                                val start = ((bedtime - anticipMin) % 1440 + 1440) % 1440
                                inWindow(minuteOfDay, start, 240)
                            }
                        if (morningActive || eveningActive) {
                            trigger(timer, foreground!!)
                        }
                    }
                }

                TimerType.ESATTORE -> {
                    if (isTarget) {
                        // Pedaggio a ogni rientro oltre il "tempo di riattivazione"
                        // (resetMs; 0 = pedaggio a OGNI rientro, anche dopo 1 secondo).
                        // Dopo un "lascia perdere" il pedaggio torna comunque subito.
                        val lastSeen = esattoreLastSeen[timer.id] ?: 0L
                        val away = if (lastSeen == 0L) Long.MAX_VALUE else now - lastSeen
                        val gaveUp = esattoreGaveUp.contains(timer.id)
                        if (foregroundJustChanged && (gaveUp || away > timer.resetMs)) {
                            esattoreGaveUp.remove(timer.id)
                            StatsRepository.record(this, timer, rejected = false)
                            val appLabel = if (timer.allApps) {
                                tr("il telefono", "the phone")
                            } else {
                                labelOf(foreground!!)
                            }
                            val timerId = timer.id
                            OverlayManager.showToll(
                                this, timer.type, timer.name,
                                tr(
                                    "Vuoi davvero entrare in $appLabel? " +
                                        "Prenditi un attimo di respiro.",
                                    "Do you really want to open $appLabel? " +
                                        "Take a moment to breathe.",
                                ) + "\n\n" + motivationalLine(),
                                timer.limitMs.coerceAtLeast(5_000L),
                                onGiveUp = {
                                    esattoreGaveUp.add(timerId)
                                    esattoreLastSeen.remove(timerId)
                                },
                            )
                        }
                        esattoreLastSeen[timer.id] = now
                    }
                }

                // L'innerType di una Vedetta non è mai a sua volta una Vedetta.
                TimerType.VEDETTA -> Unit
            }
        }

        UsageStateStore.publish(dailyMs, continuousMs, blockedToday, cooldownUntil, gendarmeOpens)

        if (now - lastPersistAt >= PERSIST_EVERY_MS) {
            persistState()
            lastPersistAt = now
        }
    }

    /** Aggiorna [currentForeground] leggendo gli eventi di UsageStatsManager. */
    private fun updateForeground(now: Long) {
        try {
            val events = usageStats.queryEvents(lastQueryTime, now)
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                @Suppress("DEPRECATION")
                if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    currentForeground = event.packageName
                }
            }
        } catch (_: Exception) {
            // Senza permesso "accesso ai dati di utilizzo" non riceviamo eventi.
        }
        lastQueryTime = now
    }

    private fun trigger(
        timer: GuardianTimer,
        packageName: String,
        cooldownRemainingMs: Long? = null,
        rejected: Boolean = false,
    ) {
        val now = System.currentTimeMillis()
        val last = lastTriggerMs[timer.id] ?: 0L
        if (now - last < TRIGGER_THROTTLE_MS) return
        lastTriggerMs[timer.id] = now

        goHome()
        // THROTTLING ANTI-FARMING: i tentativi ripetuti sulla stessa app bloccata
        // sono eventi meccanici neutri. Raggruppiamo i log entro 5 minuti in un
        // solo evento, così il DB non si gonfia e non si "coltivano" statistiche.
        if (rejected) {
            val lastLog = lastRejectionLog[timer.id] ?: 0L
            if (now - lastLog >= com.guardians.app.data.ConductConfig.BLOCK_LOG_GROUP_MS) {
                lastRejectionLog[timer.id] = now
                StatsRepository.record(this, timer, rejected = true)
            }
        } else {
            StatsRepository.record(this, timer, rejected = false)
        }

        // Miglior sforzo contro il Picture-in-Picture: proviamo a chiudere il
        // processo dell'app bloccata, così l'eventuale finestrella video sparisce.
        try {
            (getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
                .killBackgroundProcesses(packageName)
        } catch (_: Exception) {
        }

        val appLabel = if (timer.allApps) tr("il telefono", "the phone") else labelOf(packageName)
        val message = when {
            cooldownRemainingMs != null -> tr(
                "La pausa non è ancora finita: potrai riaprire $appLabel tra ",
                "The break is not over yet: you can reopen $appLabel in ",
            ) + "${formatMsPrecise(cooldownRemainingMs.coerceAtLeast(1000L))}."

            timer.type == TimerType.SENTINELLA && timer.resetMs > 0L -> tr(
                "Hai usato $appLabel per ${timer.limitText} di fila. " +
                    "Pausa obbligatoria: potrai rientrare tra ${timer.resetText}.",
                "You used $appLabel for ${timer.limitText} in a row. " +
                    "Mandatory break: you can come back in ${timer.resetText}.",
            )

            timer.type == TimerType.SENTINELLA -> tr(
                "Hai usato $appLabel per ${timer.limitText} di fila. " +
                    "Il conteggio è ripartito da zero.",
                "You used $appLabel for ${timer.limitText} in a row. " +
                    "The counter restarted from zero.",
            )

            timer.type == TimerType.CUSTODE && timer.id == "incantesimo-congelamento" ->
                if (now < timer.activeUntilEpochMs) {
                    tr(
                        "Telefono congelato per concentrarti: ancora ",
                        "Phone frozen so you can focus: ",
                    ) + formatMs((timer.activeUntilEpochMs - now).coerceAtLeast(1000L)) +
                        tr(".", " left.")
                } else {
                    // Oltre-scadenza: cronometro dei minuti extra guadagnati.
                    tr(
                        "Tempo scaduto e stai ancora resistendo: +",
                        "Time's up and you're still holding out: +",
                    ) + formatMs((now - timer.activeUntilEpochMs).coerceAtLeast(1000L)) +
                        tr(
                            ". Apri Guardians per terminare la sessione.",
                            ". Open Guardians to end the session.",
                        )
                }

            timer.type == TimerType.CUSTODE -> tr(
                "Orario protetto (${formatTimeOfDay(timer.startMinuteOfDay)} → " +
                    "${formatTimeOfDay(timer.endMinuteOfDay)}): " +
                    "$appLabel non si può usare adesso.",
                "Protected hours (${formatTimeOfDay(timer.startMinuteOfDay)} → " +
                    "${formatTimeOfDay(timer.endMinuteOfDay)}): " +
                    "$appLabel cannot be used right now.",
            )

            timer.type == TimerType.GENDARME -> tr(
                "Hai già aperto $appLabel ${timer.maxOpensPerDay} volte oggi: " +
                    "limite raggiunto, bloccata fino a domani.",
                "You already opened $appLabel ${timer.maxOpensPerDay} times today: " +
                    "limit reached, blocked until tomorrow.",
            )

            timer.type == TimerType.VEDETTA -> tr(
                "Sei nella zona sorvegliata da ${timer.name}: $appLabel non si " +
                    "può usare qui adesso.",
                "You are in the area watched by ${timer.name}: $appLabel cannot " +
                    "be used here right now.",
            )

            timer.type == TimerType.ARALDO -> {
                val wakeAt = AraldoData.wakeAt.value
                if (timer.araldoMorning && wakeAt != 0L && now < wakeAt + timer.limitMs) {
                    tr(
                        "Buongiorno! ${timer.name} custodisce i primi " +
                            "${timer.limitText} della tua giornata: $appLabel può aspettare.",
                        "Good morning! ${timer.name} guards the first " +
                            "${timer.limitText} of your day: $appLabel can wait.",
                    )
                } else {
                    tr(
                        "È quasi ora di dormire: $appLabel riposa fino a domattina.",
                        "It's almost bedtime: $appLabel is resting until morning.",
                    )
                }
            }

            else -> when (timer.resetCycle) {
                com.guardians.app.model.ResetCycle.WEEKLY -> tr(
                    "Limite settimanale di ${timer.limitText} raggiunto per $appLabel. " +
                        "Bloccato fino a lunedì.",
                    "Weekly limit of ${timer.limitText} reached for $appLabel. " +
                        "Blocked until Monday.",
                )
                com.guardians.app.model.ResetCycle.MONTHLY -> tr(
                    "Limite mensile di ${timer.limitText} raggiunto per $appLabel. " +
                        "Bloccato fino al 1° del mese.",
                    "Monthly limit of ${timer.limitText} reached for $appLabel. " +
                        "Blocked until the 1st.",
                )
                else -> tr(
                    "Limite giornaliero di ${timer.limitText} raggiunto per $appLabel. " +
                        "Bloccato fino a domani.",
                    "Daily limit of ${timer.limitText} reached for $appLabel. " +
                        "Blocked until tomorrow.",
                )
            }
        }
        OverlayManager.show(
            this, timer.type, timer.name, message + "\n\n" + motivationalLine(),
            // Durante il Congelamento il popup mostra il FIOCCO DI NEVE, non il
            // cerchio del Custode: è un incantesimo, non un guardiano.
            snowflake = timer.id == "incantesimo-congelamento",
        )
    }

    /** Timer sintetico che rappresenta l'Incantesimo di Congelamento nel popup e nelle statistiche. */
    private fun freezeTimer(untilMs: Long): GuardianTimer = GuardianTimer(
        id = "incantesimo-congelamento",
        name = "Congelamento",
        type = TimerType.CUSTODE,
        limitAmount = ((untilMs - System.currentTimeMillis()) / 60_000L).toInt().coerceAtLeast(1),
        limitUnit = com.guardians.app.model.TimeUnit.MINUTES,
        activeUntilEpochMs = untilMs,
        allApps = true,
    )

    /** Frase motivazionale sempre diversa, con richiamo agli obiettivi del profilo. */
    private fun motivationalLine(): String {
        val goals = ProfileRepository.goals.value
        return if (goals.isNotEmpty()) {
            val goal = goals.random()
            if (SettingsRepository.english.value) {
                listOf(
                    "It's the perfect moment for: $goal.",
                    "Your time is yours again: go for \"$goal\"!",
                    "Remember why you do this: $goal.",
                    "\"$goal\" is waiting for you: seize the moment.",
                    "One step less here, one step closer to: $goal.",
                ).random()
            } else {
                listOf(
                    "È il momento perfetto per dedicarti a: $goal.",
                    "Il tuo tempo torna tuo: vai su \"$goal\"!",
                    "Ricorda perché lo fai: $goal.",
                    "\"$goal\" ti aspetta: approfittane adesso.",
                    "Un passo in meno qui, un passo in più verso: $goal.",
                ).random()
            }
        } else if (SettingsRepository.english.value) {
            listOf(
                "Breathe: this time is yours now.",
                "Every block is time earned back.",
                "Look up: the world is out there.",
                "The phone can wait, you can't.",
                "Small breaks, big results.",
            ).random()
        } else {
            listOf(
                "Respira: questo tempo ora è tuo.",
                "Ogni blocco è tempo guadagnato.",
                "Alza lo sguardo: il mondo è là fuori.",
                "Il telefono può aspettare, tu no.",
                "Piccole pause, grandi risultati.",
            ).random()
        }
    }

    /** Manda la notifica di preavviso se manca meno di [remainingMs] al blocco. */
    private fun maybeWarn(timer: GuardianTimer, remainingMs: Long) {
        if (timer.warnMs <= 0L || warned.contains(timer.id)) return
        if (remainingMs <= 0L || remainingMs > timer.warnMs) return
        warned.add(timer.id)
        sendWarning(
            timer,
            tr(
                "Tra ${formatMs(remainingMs.coerceAtLeast(1000L))} ${timer.name} " +
                    "chiuderà le app sorvegliate.",
                "In ${formatMs(remainingMs.coerceAtLeast(1000L))} ${timer.name} " +
                    "will close the watched apps.",
            )
        )
    }

    // ---------------------------------------------------------------- Araldo

    /**
     * Osserva le transizioni dello schermo: quando finisce uno spegnimento di
     * almeno 4 ore, l'inizio era la NANNA e la fine è il RISVEGLIO di oggi.
     * L'inizio dello spegnimento vive nelle prefs, così sopravvive ai riavvii.
     */
    private fun trackScreenForAraldo(interactive: Boolean, now: Long) {
        val offSince = AraldoData.screenOffSince(this)
        if (!interactive) {
            if (offSince == 0L) AraldoData.setScreenOffSince(this, now)
            return
        }
        if (offSince == 0L) return
        AraldoData.setScreenOffSince(this, 0L)
        if (now - offSince >= WAKE_GAP_MS) {
            AraldoData.recordBedtime(this, offSince)
            AraldoData.recordWake(this, now)
        }
    }

    /**
     * Fallback: se il sistema ha riavviato il servizio, lo spegnimento lungo
     * potrebbe essere finito mentre eravamo giù. Gli eventi di UsageStats
     * (schermo interattivo, tipo 15) dicono QUANDO è stato riacceso davvero.
     */
    private fun reconstructAraldoWake() {
        val offSince = AraldoData.screenOffSince(this)
        if (offSince == 0L) return
        val now = System.currentTimeMillis()
        if (now - offSince < WAKE_GAP_MS) return
        try {
            val events = usageStats.queryEvents(offSince + 1, now)
            val e = UsageEvents.Event()
            var firstOn = 0L
            while (events.hasNextEvent()) {
                events.getNextEvent(e)
                if (e.eventType == 15 && firstOn == 0L) firstOn = e.timeStamp
            }
            if (firstOn != 0L) {
                AraldoData.setScreenOffSince(this, 0L)
                if (firstOn - offSince >= WAKE_GAP_MS) {
                    AraldoData.recordBedtime(this, offSince)
                    AraldoData.recordWake(this, firstOn)
                }
            }
            // Nessun evento trovato: ci pensa il primo tick con schermo acceso.
        } catch (_: Exception) {
        }
    }

    /**
     * La data "logica" di oggi: il giorno dei conteggi cambia all'orario
     * "Inizio del giorno" delle Impostazioni, non per forza a mezzanotte
     * (es. alle 04:00 per chi va a letto tardi).
     */
    private fun logicalToday(): String {
        val now = java.time.LocalDateTime.now()
        val start = SettingsRepository.dayStartMinute.value
        var day = now.toLocalDate()
        if (now.hour * 60 + now.minute < start) day = day.minusDays(1)
        return day.toString()
    }

    /** Epoch ms di oggi al minuto [minuteOfDay] (ora locale). */
    private fun todayAtMinute(minuteOfDay: Int): Long =
        LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault())
            .plusMinutes(minuteOfDay.toLong()).toInstant().toEpochMilli()

    /** Minuti dalla mezzanotte dell'istante [epochMs], nell'ora locale. */
    private fun minuteOfEpoch(epochMs: Long): Int {
        val t = java.time.Instant.ofEpochMilli(epochMs)
            .atZone(java.time.ZoneId.systemDefault()).toLocalTime()
        return t.hour * 60 + t.minute
    }

    /** True se [minute] cade nella finestra [start]→[end] (gestisce la mezzanotte). */
    private fun inWindow(minute: Int, start: Int, end: Int): Boolean =
        if (start < end) minute in start until end else minute >= start || minute < end

    /**
     * True se ora siamo entro il raggio della Vedetta. Se non abbiamo posizione
     * (GPS spento o nessun dato) torna FALSE: la Vedetta resta silente.
     */
    private fun isWithinVedetta(timer: GuardianTimer): Boolean {
        if (!timer.hasLocation) return false
        val loc = lastLocation ?: return false
        val result = FloatArray(1)
        android.location.Location.distanceBetween(
            loc.latitude, loc.longitude, timer.latitude, timer.longitude, result,
        )
        return result[0] <= timer.radiusMeters
    }

    /**
     * Sonno profondo del risparmio batteria: ferma il ticker (zero CPU), toglie
     * l'overlay e RIMUOVE i blocchi in corso — l'utente usa il telefono senza
     * alcuna interferenza. La sveglia arriva dagli eventi batteria di sistema
     * (nessun polling), con un controllo di riserva ogni 5 minuti.
     */
    private fun enterDeepSleep() {
        if (deepSleeping) return
        deepSleeping = true
        handler.removeCallbacks(ticker)
        // Blocchi e pedaggi in corso spariscono subito.
        OverlayManager.hide()
        blockedToday.clear()
        cooldownUntil.clear()
        messengerNextAt.clear()
        esattoreGaveUp.clear()
        previousForeground = null
        persistState()
        UsageStateStore.publish(dailyMs, continuousMs, blockedToday, cooldownUntil, gendarmeOpens)
        try {
            registerReceiver(
                batteryWakeReceiver,
                android.content.IntentFilter().apply {
                    addAction(Intent.ACTION_BATTERY_CHANGED)
                    addAction(Intent.ACTION_POWER_CONNECTED)
                },
            )
        } catch (_: Exception) {
        }
        handler.postDelayed(sleepFallbackCheck, 300_000L)
        updateServiceNotification(sleeping = true)
    }

    /** Esce dal sonno profondo se la batteria è risalita, è in carica o il risparmio è spento. */
    private fun maybeWakeFromDeepSleep() {
        if (!deepSleeping) return
        val stillLow = try {
            val bm = getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
            val level = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
            SettingsRepository.batterySaver.value && !bm.isCharging &&
                level in 1..SettingsRepository.batteryThreshold.value
        } catch (_: Exception) {
            false
        }
        if (stillLow) return

        deepSleeping = false
        batteryPaused = false
        lastBatteryCheckAt = 0L
        try {
            unregisterReceiver(batteryWakeReceiver)
        } catch (_: Exception) {
        }
        handler.removeCallbacks(sleepFallbackCheck)
        // Riparte pulito: niente conteggi del tempo dormito, niente eventi vecchi.
        val now = System.currentTimeMillis()
        lastTickAt = now
        lastQueryTime = now
        handler.post(ticker)
        updateServiceNotification(sleeping = false)
    }

    /** Aggiorna la notifica fissa (in servizio / a riposo per batteria). */
    private fun updateServiceNotification(sleeping: Boolean) {
        try {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(NOTIFICATION_ID, if (sleeping) buildSleepNotification() else buildNotification())
        } catch (_: Exception) {
        }
    }

    /**
     * Aggiorna [batteryPaused] leggendo il livello di batteria (al massimo ogni 30s,
     * la lettura è una chiamata di sistema). In carica non si va mai in pausa.
     */
    private fun updateBatteryPause(now: Long) {
        if (lastBatteryCheckAt != 0L && now - lastBatteryCheckAt < 30_000L) return
        lastBatteryCheckAt = now
        if (!SettingsRepository.batterySaver.value) {
            batteryPaused = false
            return
        }
        batteryPaused = try {
            val bm = getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
            val level = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
            !bm.isCharging && level in 1..SettingsRepository.batteryThreshold.value
        } catch (_: Exception) {
            false
        }
    }

    /** Legge l'ultima posizione nota (senza tenere il GPS acceso, per la batteria). */
    private fun refreshLocation() {
        if (ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return
        try {
            val lm = getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            var best: android.location.Location? = null
            for (provider in lm.getProviders(true)) {
                @Suppress("MissingPermission")
                val loc = lm.getLastKnownLocation(provider) ?: continue
                if (best == null || loc.time > best.time) best = loc
            }
            if (best != null) lastLocation = best
        } catch (_: Exception) {
        }
    }

    /**
     * Totale d'uso del telefono in un intervallo, calcolato dagli eventi reali
     * (una sola app in primo piano alla volta) così non supera mai le 24h.
     */
    private fun totalUsageMs(startMs: Long, endMs: Long): Long {
        var total = 0L
        var curStart = 0L
        var inForeground = false
        var curPkg: String? = null
        try {
            val events = usageStats.queryEvents(startMs, endMs)
            val e = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(e)
                when (e.eventType) {
                    UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                        if (inForeground) {
                            val d = e.timeStamp - curStart
                            if (d in 1 until 2L * 3600_000L) total += d
                        }
                        curPkg = e.packageName
                        curStart = e.timeStamp
                        inForeground = !excluded.contains(e.packageName)
                    }
                    UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                        if (inForeground && e.packageName == curPkg) {
                            val d = e.timeStamp - curStart
                            if (d in 1 until 2L * 3600_000L) total += d
                            inForeground = false
                        }
                    }
                    // Schermo spento / blocco / spegnimento: chiudi l'intervallo.
                    16, 17, 26 -> {
                        if (inForeground) {
                            val d = e.timeStamp - curStart
                            if (d in 1 until 2L * 3600_000L) total += d
                            inForeground = false
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
        return total.coerceAtMost(24L * 3600_000L)
    }

    /** Registra nello storico il totale del giorno [dateString]. */
    private fun recordDayInHistory(dateString: String) {
        try {
            val zone = java.time.ZoneId.systemDefault()
            val day = LocalDate.parse(dateString)
            val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
            val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            UsageHistoryRepository.record(this, dateString, totalUsageMs(start, end))
        } catch (_: Exception) {
        }
    }

    /**
     * Motore della Condotta: sui dati del giorno chiuso aggiorna la barra e il
     * Focus Streak, e fissa la baseline dell'Ombra la prima volta.
     */
    private fun rolloverConduct(dateString: String) {
        try {
            val zone = java.time.ZoneId.systemDefault()
            val day = LocalDate.parse(dateString)
            val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
            val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val total = totalUsageMs(start, end)
            val perMacro = com.guardians.app.data.UsageAnalytics.perMacroMs(this, start, end)
            ConductRepository.ensureShadowBaseline(this, perMacro)
            val goalMs = ProfileRepository.dailyGoalMinutes.value * 60_000L
            val histAvg = UsageHistoryRepository.dailyAverageMs()
            ConductRepository.dailyRollover(this, dateString, total, perMacro, goalMs, histAvg)
        } catch (_: Exception) {
        }
    }

    /**
     * Istantanea immutabile: il giorno [dateString] è rimasto sotto l'obiettivo?
     * Si scrive una sola volta, con l'obiettivo di quel giorno.
     */
    private fun recordGoalSnapshot(dateString: String) {
        val goalMin = ProfileRepository.dailyGoalMinutes.value
        if (goalMin <= 0) return
        try {
            val zone = java.time.ZoneId.systemDefault()
            val day = LocalDate.parse(dateString)
            val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
            val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val used = totalUsageMs(start, end)
            UsageHistoryRepository.recordGoalSnapshot(this, dateString, used <= goalMin * 60_000L)
        } catch (_: Exception) {
        }
    }


    /** Notifica del lunedì: la settimana appena finita contro la media storica. */
    private fun sendWeeklyReport() {
        val history = UsageHistoryRepository.history.value
        val lastWeek = (1..7).mapNotNull { i ->
            history[LocalDate.now().minusDays(i.toLong()).toString()]
        }
        if (lastWeek.isEmpty()) return
        val weekAvg = lastWeek.sum() / lastWeek.size
        val overallAvg = UsageHistoryRepository.dailyAverageMs()
        val comparison = if (overallAvg > 0L) {
            val diff = ((weekAvg - overallAvg) * 100 / overallAvg)
            when {
                diff <= -5 -> tr(
                    " ${-diff}% sotto la tua media: ottimo lavoro!",
                    " ${-diff}% below your average: great job!",
                )
                diff >= 5 -> tr(
                    " ${diff}% sopra la tua media.",
                    " ${diff}% above your average.",
                )
                else -> tr(" In linea con la tua media.", " In line with your average.")
            }
        } else {
            ""
        }
        val text = tr(
            "Settimana scorsa: ${formatMs(weekAvg)} al giorno al telefono.",
            "Last week: ${formatMs(weekAvg)} per day on the phone.",
        ) + comparison
        val notification = NotificationCompat.Builder(this, WARN_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_guardian)
            .setContentTitle(tr("Il resoconto dei guardiani", "The guardians' report"))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        try {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(424242, notification)
        } catch (_: Exception) {
        }
    }

    /** Avviso del Messaggero numero [level]: messaggio a tono crescente + vibrazione crescente. */
    private fun sendMessengerNotice(timer: GuardianTimer, level: Int) {
        val appLabel = if (timer.allApps) tr("il telefono", "the phone") else appLabelOf(timer)
        val base = if (timer.messages.isNotEmpty()) {
            timer.messages[(level - 1).coerceIn(0, timer.messages.size - 1)]
        } else {
            val it = listOf(
                "Ehi, sei ancora su $appLabel.",
                "Sei su $appLabel da un po'…",
                "$appLabel di nuovo? Prova a fermarti.",
                "Sul serio, ancora $appLabel?",
                "Basta $appLabel per ora. Chiudi.",
            )
            val en = listOf(
                "Hey, you're still on $appLabel.",
                "You've been on $appLabel for a while…",
                "$appLabel again? Try to stop.",
                "Really, still $appLabel?",
                "Enough $appLabel for now. Close it.",
            )
            val list = if (SettingsRepository.english.value) en else it
            list[(level - 1).coerceIn(0, list.size - 1)]
        }
        // Ogni tanto richiama un obiettivo dal profilo.
        val goals = ProfileRepository.goals.value
        val text = if (level % 3 == 0 && goals.isNotEmpty()) {
            base + tr(" — Ricorda: ${goals.random()}.", " — Remember: ${goals.random()}.")
        } else {
            base
        }

        vibrateEscalating(level)
        val notification = NotificationCompat.Builder(this, WARN_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_guardian)
            .setContentTitle(timer.name)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        try {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(timer.id.hashCode(), notification)
        } catch (_: Exception) {
        }
    }

    /** Vibrazione via app che si allunga con il livello dell'avviso (disattivabile). */
    private fun vibrateEscalating(level: Int) {
        if (!SettingsRepository.vibrateOnAlert.value) return
        try {
            val vib = if (Build.VERSION.SDK_INT >= 31) {
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE)
                    as android.os.VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            }
            val ms = (120L * level).coerceAtMost(900L)
            vib.vibrate(
                android.os.VibrationEffect.createOneShot(
                    ms, android.os.VibrationEffect.DEFAULT_AMPLITUDE
                )
            )
        } catch (_: Exception) {
        }
    }

    private fun appLabelOf(timer: GuardianTimer): String =
        timer.packages.firstOrNull()?.let { labelOf(it) } ?: tr("le app", "the apps")

    private fun sendWarning(timer: GuardianTimer, message: String) {
        val notification = NotificationCompat.Builder(this, WARN_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_guardian)
            .setContentTitle(timer.name)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .build()
        try {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(timer.id.hashCode(), notification)
        } catch (_: Exception) {
        }
    }

    private fun goHome() {
        try {
            val home = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(home)
        } catch (_: Exception) {
        }
    }

    private fun labelOf(packageName: String): String = try {
        val info = packageManager.getApplicationInfo(packageName, 0)
        packageManager.getApplicationLabel(info).toString()
    } catch (_: Exception) {
        packageName
    }

    /**
     * App mai monitorate: noi stessi, launcher, impostazioni, telefono (emergenze)
     * e la systemui.
     */
    private fun computeExcluded(): Set<String> {
        // Base comune (Guardians, systemui, launcher, ANDROID AUTO)…
        val set = com.guardians.app.data.UsageAnalytics.ignored(this).toMutableSet()
        // …più il telefono (emergenze: mai bloccato, mai contato).
        packageManager.resolveActivity(
            Intent(Intent.ACTION_DIAL),
            PackageManager.MATCH_DEFAULT_ONLY
        )?.activityInfo?.packageName?.let { set.add(it) }
        return set
    }

    // ----------------------------------------------------------- persistenza

    private fun persistState() {
        val state = JSONObject().apply {
            put("date", today)
            put("daily", JSONObject(dailyMs.toMap()))
            put("continuous", JSONObject(continuousMs.toMap()))
            put("blocked", JSONArray(blockedToday.toList()))
            put("cooldown", JSONObject(cooldownUntil.toMap()))
            put("opens", JSONObject(gendarmeOpens.toMap()))
        }
        prefs.edit().putString(KEY_STATE, state.toString()).apply()
    }

    private fun restoreState() {
        val raw = prefs.getString(KEY_STATE, null) ?: return
        try {
            val state = JSONObject(raw)
            if (state.getString("date") != today) return
            state.getJSONObject("daily").let { d ->
                d.keys().forEach { k -> dailyMs[k] = d.getLong(k) }
            }
            state.getJSONObject("continuous").let { c ->
                c.keys().forEach { k -> continuousMs[k] = c.getLong(k) }
            }
            val blocked = state.getJSONArray("blocked")
            for (i in 0 until blocked.length()) blockedToday.add(blocked.getString(i))
            state.optJSONObject("cooldown")?.let { c ->
                c.keys().forEach { k -> cooldownUntil[k] = c.getLong(k) }
            }
            state.optJSONObject("opens")?.let { g ->
                g.keys().forEach { k -> gendarmeOpens[k] = g.getInt(k) }
            }
        } catch (_: Exception) {
        }
    }

    // ----------------------------------------------------------- notifica FGS

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Monitoraggio Guardians",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notifica fissa mentre i guardiani sorvegliano l'uso delle app"
        }
        // Canale a importanza minima: usato quando l'utente sceglie di NON
        // mostrare la notifica di monitoraggio (resta la minima richiesta da
        // Android per un foreground service, ma collassata e silenziosa).
        val minChannel = NotificationChannel(
            CHANNEL_ID_MIN,
            "Guardians (discreto)",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "Notifica di monitoraggio ridotta al minimo"
            setShowBadge(false)
        }
        val warnChannel = NotificationChannel(
            WARN_CHANNEL_ID,
            "Preavvisi di blocco",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Avvisi qualche minuto prima che un guardiano chiuda un'app"
        }
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).apply {
            createNotificationChannel(channel)
            createNotificationChannel(minChannel)
            createNotificationChannel(warnChannel)
        }
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        // Se l'utente ha disattivato la notifica di monitoraggio, la mostriamo
        // sul canale a importanza minima (Android obbliga comunque un foreground
        // service ad averne una, ma così resta collassata e silenziosa).
        val show = SettingsRepository.monitorNotification.value
        val channel = if (show) CHANNEL_ID else CHANNEL_ID_MIN
        return NotificationCompat.Builder(this, channel)
            .setSmallIcon(R.drawable.ic_stat_guardian)
            .setContentTitle(tr("Guardians attivo", "Guardians active"))
            .setContentText(
                if (show) tr(
                    "I tuoi guardiani stanno vegliando sul tempo di utilizzo",
                    "Your guardians are watching over your screen time",
                ) else null
            )
            .setOngoing(true)
            .setSilent(!show)
            .setPriority(if (show) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_MIN)
            .setContentIntent(openApp)
            .build()
    }

    /** Notifica mostrata durante il sonno profondo del risparmio batteria. */
    private fun buildSleepNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val threshold = SettingsRepository.batteryThreshold.value
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_guardian)
            .setContentTitle(tr("Guardiani a riposo", "Guardians resting"))
            .setContentText(
                tr(
                    "Risparmio batteria: nessun blocco attivo. Riprendono sopra " +
                        "il $threshold% o in carica.",
                    "Battery saver: no blocks active. They resume above " +
                        "$threshold% or while charging.",
                )
            )
            .setOngoing(true)
            .setContentIntent(openApp)
            .build()
    }
}
