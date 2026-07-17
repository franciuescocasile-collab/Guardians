package com.guardians.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.guardians.app.data.ExclusionsRepository
import com.guardians.app.data.SealRepository
import com.guardians.app.data.SettingsRepository
import com.guardians.app.data.TimerRepository
import com.guardians.app.model.GuardianTimer
import com.guardians.app.model.TimeUnit
import com.guardians.app.model.TimerType
import com.guardians.app.ui.AppPickerScreen
import com.guardians.app.ui.CongelamentoScreen
import com.guardians.app.ui.EditScreen
import com.guardians.app.ui.ALL_TEAMS_FILTER
import com.guardians.app.ui.GuidesScreen
import com.guardians.app.ui.HomeScreen
import com.guardians.app.ui.HubScreen
import com.guardians.app.ui.NewsScreen
import com.guardians.app.ui.AdvancedScreen
import com.guardians.app.ui.BatteryScreen
import com.guardians.app.ui.NotificationsScreen
import com.guardians.app.ui.PersonalizationScreen
import com.guardians.app.ui.SleepScreen
import com.guardians.app.ui.NotifierScreen
import com.guardians.app.ui.OnboardingScreen
import com.guardians.app.ui.ProfileScreen
import com.guardians.app.ui.TravelScreen
import com.guardians.app.ui.UserProfileScreen
import com.guardians.app.ui.SettingsScreen
import com.guardians.app.ui.SigilloScreen
import com.guardians.app.ui.SpellsScreen
import com.guardians.app.ui.StatsScreen
import com.guardians.app.ui.TeamsScreen
import com.guardians.app.ui.theme.GuardiansTheme
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TimerRepository.load(applicationContext)
        SettingsRepository.load(applicationContext)
        ExclusionsRepository.load(applicationContext)
        SealRepository.load(applicationContext)
        com.guardians.app.data.ProfileRepository.load(applicationContext)
        com.guardians.app.data.SpellsRepository.load(applicationContext)
        com.guardians.app.data.NewsRepository.load(applicationContext)
        com.guardians.app.data.AraldoData.load(applicationContext)
        com.guardians.app.data.TravelRepository.load(applicationContext)
        com.guardians.app.data.ConductRepository.load(applicationContext)
        com.guardians.app.data.NotifierRepository.load(applicationContext)
        setContent {
            val darkTheme by SettingsRepository.darkTheme.collectAsState()
            // Cambiando lingua si ricompone tutto con i testi nuovi.
            val english by SettingsRepository.english.collectAsState()
            androidx.compose.runtime.key(english) {
            GuardiansTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    GuardiansNav()
                }
            }
            }
        }
    }

    // Il countdown del Sigillo scorre SOLO mentre Guardians è in primo piano:
    // uscendo dall'app l'attesa si annulla e al rientro riparte da capo.
    override fun onResume() {
        super.onResume()
        SealRepository.startSession()
    }

    override fun onPause() {
        super.onPause()
        SealRepository.pauseSession()
    }
}

private enum class Screen {
    HUB, TEAMS, TIMERS, EDIT, PICKER, EXCLUSIONS, SETTINGS, STATS, SIGILLO, PROFILE, SPELLS,
    GUIDES, NEWS, USER_PROFILE, FREEZE, NOTIFIER, TRAVEL, NOTIFICATIONS, BATTERY,
    PERSONALIZATION, ADVANCED, SLEEP,
}

/** Bozza del timer in modifica, con i campi numerici come testo per la digitazione. */
data class TimerDraft(
    val id: String?,
    val name: String,
    val type: TimerType,
    val limitValue: String,
    val limitUnit: TimeUnit,
    /** Guardiano: ciclo di azzeramento del limite (giorno/settimana/mese). */
    val resetCycle: com.guardians.app.model.ResetCycle,
    val resetValue: String,
    val resetUnit: TimeUnit,
    /** Orari del Custode in minuti dalla mezzanotte; null = non ancora scelti. */
    val startTime: Int?,
    val endTime: Int?,
    /** Gendarme: aperture giornaliere, cooldown di riapertura, notifica dopo N aperture. */
    val opensValue: String,
    val reopenCooldownValue: String,
    val notifyAfterOpensValue: String,
    /** Turno attivo del Custode: va conservato quando si modifica il timer. */
    val activeUntil: Long,
    /** Notifica di preavviso prima del blocco (spenta di default). */
    val warnEnabled: Boolean,
    val warnValue: String,
    val warnUnit: TimeUnit,
    /** Squadra (vuota = Squadra Generale). */
    val team: String,
    /** Araldo: fase mattutina (dal risveglio) e serale (prima della nanna). */
    val araldoMorning: Boolean,
    val araldoEvening: Boolean,
    /** Vedetta: potere prestato + luogo + raggio in km (testo). */
    val innerType: TimerType,
    val latitude: Double,
    val longitude: Double,
    val radiusKm: String,
    /** Messaggero: ritmo, tetto avvisi e messaggi personalizzati. */
    val pace: com.guardians.app.model.MessengerPace,
    val maxNoticesValue: String,
    val messages: List<String>,
    val allApps: Boolean,
    val packages: Set<String>,
    val enabled: Boolean,
) {
    val hasLocation: Boolean get() = !latitude.isNaN() && !longitude.isNaN()

    companion object {
        // I campi durata partono vuoti (scelta dell'utente), con unità "minuti".
        fun new() = TimerDraft(
            id = null,
            name = "",
            type = TimerType.SENTINELLA,
            limitValue = "",
            limitUnit = TimeUnit.MINUTES,
            resetCycle = com.guardians.app.model.ResetCycle.DAILY,
            resetValue = "",
            resetUnit = TimeUnit.MINUTES,
            startTime = null,
            endTime = null,
            opensValue = "",
            reopenCooldownValue = "",
            notifyAfterOpensValue = "",
            activeUntil = 0L,
            warnEnabled = false,
            warnValue = "",
            warnUnit = TimeUnit.MINUTES,
            team = "",
            araldoMorning = true,
            araldoEvening = false,
            innerType = TimerType.SENTINELLA,
            latitude = Double.NaN,
            longitude = Double.NaN,
            radiusKm = "",
            pace = com.guardians.app.model.MessengerPace.PROGRAMMABILE,
            maxNoticesValue = "",
            messages = emptyList(),
            allApps = false,
            packages = emptySet(),
            enabled = true,
        )

        fun from(timer: GuardianTimer) = TimerDraft(
            id = timer.id,
            name = timer.name,
            type = timer.type,
            limitValue = if (timer.limitAmount > 0) timer.limitAmount.toString() else "",
            limitUnit = timer.limitUnit,
            resetCycle = timer.resetCycle,
            resetValue = if (timer.resetAmount > 0) timer.resetAmount.toString() else "",
            resetUnit = timer.resetUnit,
            // La fascia oraria vale per Custode (turno) e Araldo (finestra mattutina).
            startTime = if (
                timer.effectiveType == TimerType.CUSTODE ||
                timer.effectiveType == TimerType.ARALDO
            ) timer.startMinuteOfDay else null,
            endTime = if (
                timer.effectiveType == TimerType.CUSTODE ||
                timer.effectiveType == TimerType.ARALDO
            ) timer.endMinuteOfDay else null,
            opensValue = if (timer.maxOpensPerDay > 0) timer.maxOpensPerDay.toString() else "",
            reopenCooldownValue =
                if (timer.reopenCooldownMinutes > 0) timer.reopenCooldownMinutes.toString() else "",
            notifyAfterOpensValue =
                if (timer.notifyAfterOpens > 0) timer.notifyAfterOpens.toString() else "",
            activeUntil = timer.activeUntilEpochMs,
            warnEnabled = timer.warnAmount > 0,
            warnValue = if (timer.warnAmount > 0) timer.warnAmount.toString() else "",
            warnUnit = timer.warnUnit,
            team = timer.team,
            araldoMorning = timer.araldoMorning,
            araldoEvening = timer.araldoEvening,
            innerType = timer.innerType,
            latitude = timer.latitude,
            longitude = timer.longitude,
            radiusKm = if (timer.radiusMeters > 0) (timer.radiusMeters / 1000.0).toString() else "",
            pace = timer.pace,
            maxNoticesValue = if (timer.maxNotices > 0) timer.maxNotices.toString() else "",
            messages = timer.messages,
            allApps = timer.allApps,
            packages = timer.packages.toSet(),
            enabled = timer.enabled,
        )
    }

    /** Ritorna il timer valido, o null se i dati non sono completi. */
    fun toTimer(): GuardianTimer? {
        if (!allApps && packages.isEmpty()) return null

        // La Vedetta presta il potere di configType e vale solo nel suo luogo.
        val configType = if (type == TimerType.VEDETTA) innerType else type
        var radiusM = 150
        if (type == TimerType.VEDETTA) {
            if (!hasLocation) return null
            val km = radiusKm.toDoubleOrNull() ?: return null
            if (km <= 0.0) return null
            radiusM = (km * 1000).toInt().coerceAtLeast(1)
        }

        val base = GuardianTimer(
            id = id ?: UUID.randomUUID().toString(),
            // Nome vuoto = si usa il nome del tipo scelto (es. "Sentinella").
            name = name.trim().ifBlank { type.shortName },
            type = type,
            limitAmount = 0,
            limitUnit = limitUnit,
            allApps = allApps,
            packages = packages.toList(),
            enabled = enabled,
            team = team.trim(),
            innerType = innerType,
            latitude = if (type == TimerType.VEDETTA) latitude else Double.NaN,
            longitude = if (type == TimerType.VEDETTA) longitude else Double.NaN,
            radiusMeters = radiusM,
        )
        // Preavviso: se acceso, il tempo dev'essere valido; se spento, vale 0.
        val warn = if (warnEnabled) warnValue.toIntOrNull() ?: return null else 0
        if (warnEnabled && warn <= 0) return null

        return when (configType) {
            TimerType.SENTINELLA -> {
                val limit = limitValue.toIntOrNull()
                if (limit == null || limit <= 0) return null
                // Pausa vuota o 0 = nessuna pausa obbligatoria.
                val reset = resetValue.toIntOrNull() ?: 0
                base.copy(
                    limitAmount = limit, resetAmount = reset, resetUnit = resetUnit,
                    warnAmount = warn, warnUnit = warnUnit,
                )
            }

            TimerType.GUARDIANO -> {
                val limit = limitValue.toIntOrNull()
                if (limit == null || limit <= 0) return null
                base.copy(
                    limitAmount = limit, warnAmount = warn, warnUnit = warnUnit,
                    resetCycle = resetCycle,
                )
            }

            TimerType.CUSTODE -> {
                val start = startTime ?: return null
                val end = endTime ?: return null
                if (start == end) return null
                base.copy(
                    startMinuteOfDay = start, endMinuteOfDay = end,
                    warnAmount = warn, warnUnit = warnUnit,
                )
            }

            TimerType.GENDARME -> {
                val opens = opensValue.toIntOrNull() ?: 0
                val cooldown = reopenCooldownValue.toIntOrNull() ?: 0
                val notify = notifyAfterOpensValue.toIntOrNull() ?: 0
                // Serve almeno una delle tre funzioni attiva.
                if (opens <= 0 && cooldown <= 0 && notify <= 0) return null
                base.copy(
                    maxOpensPerDay = opens.coerceAtLeast(0),
                    reopenCooldownMinutes = cooldown.coerceAtLeast(0),
                    notifyAfterOpens = notify.coerceAtLeast(0),
                )
            }

            TimerType.ARALDO -> {
                // Serve almeno una fase; ogni fase attiva vuole la sua durata.
                if (!araldoMorning && !araldoEvening) return null
                val duration = limitValue.toIntOrNull() ?: 0
                if (araldoMorning && duration <= 0) return null
                val anticipation = resetValue.toIntOrNull() ?: 0
                if (araldoEvening && anticipation <= 0) return null
                base.copy(
                    limitAmount = duration.coerceAtLeast(0),
                    resetAmount = anticipation.coerceAtLeast(0),
                    resetUnit = resetUnit,
                    // Finestra del mattino: default 05:00-12:00 se non toccata.
                    startMinuteOfDay = startTime ?: (5 * 60),
                    endMinuteOfDay = endTime ?: (12 * 60),
                    araldoMorning = araldoMorning,
                    araldoEvening = araldoEvening,
                )
            }

            TimerType.ESATTORE -> {
                // Il pedaggio d'ingresso: la durata dell'attesa a ogni apertura.
                val toll = limitValue.toIntOrNull()
                if (toll == null || toll <= 0) return null
                // Tempo di riattivazione: finestra di tolleranza per uscire e
                // rientrare senza ripagare il pedaggio (0 o vuoto = nessuna).
                val reactivation = resetValue.toIntOrNull() ?: 0
                base.copy(
                    limitAmount = toll,
                    resetAmount = reactivation.coerceAtLeast(0),
                    resetUnit = resetUnit,
                )
            }

            TimerType.MESSAGGERO -> {
                val first = limitValue.toIntOrNull()   // primo avviso dopo (uso continuo)
                if (first == null || first <= 0) return null
                val reset = resetValue.toIntOrNull() ?: 0
                // In modalità Programmabile serve l'intervallo fisso "ogni".
                if (pace == com.guardians.app.model.MessengerPace.PROGRAMMABILE && reset <= 0) {
                    return null
                }
                base.copy(
                    limitAmount = first,
                    resetAmount = reset,
                    resetUnit = resetUnit,
                    pace = pace,
                    maxNotices = maxNoticesValue.toIntOrNull() ?: 0,
                    messages = messages.map { it.trim() }.filter { it.isNotBlank() },
                )
            }

            // configType non è mai VEDETTA (il potere interno non è una Vedetta).
            TimerType.VEDETTA -> null
        }
    }
}

@Composable
private fun GuardiansNav() {
    val context = LocalContext.current

    // Primo avvio: la Bussola. Finché l'onboarding non è completato (o saltato)
    // si vede solo quello; il flag scatta una volta sola e non torna più.
    val onboarded by com.guardians.app.data.ProfileRepository.onboarded.collectAsState()
    if (!onboarded) {
        OnboardingScreen(onDone = { /* il flag fa ricomporre la schermata */ })
        return
    }

    var draft by remember { mutableStateOf<TimerDraft?>(null) }
    var selectedTeam by remember { mutableStateOf<String?>(null) }

    // PILA di navigazione: ogni schermata aperta viene impilata, e "indietro"
    // (freccia della barra O tasto di sistema: fanno la STESSA cosa) toglie
    // l'ultima, tornando sempre alla schermata da cui sei arrivato.
    // Es.: home → squadre → squadra → indietro → squadre → indietro → home.
    val backStack = remember { mutableStateListOf(Screen.HUB) }
    val screen = backStack.last()

    fun navigate(to: Screen) {
        if (backStack.last() != to) backStack.add(to)
    }

    fun goBack() {
        if (backStack.size <= 1) return
        // Uscendo dall'editor la bozza si scarta (salvataggi già fatti in onSave).
        if (backStack.last() == Screen.EDIT) draft = null
        backStack.removeAt(backStack.lastIndex)
    }

    BackHandler(enabled = backStack.size > 1) { goBack() }

    // Ricalcolato a ogni ricomposizione; i tick del servizio aggiornano la UI.
    SealRepository.delayMs.collectAsState().value
    SealRepository.waitReadyAt.collectAsState().value
    val sealed = !SealRepository.canEditNow()

    when (screen) {
        Screen.HUB -> HubScreen(
            onCreateTimer = {
                draft = TimerDraft.new()
                selectedTeam = null
                navigate(Screen.EDIT)
            },
            onOpenTeams = { navigate(Screen.TEAMS) },
            onOpenGuides = { navigate(Screen.GUIDES) },
            onOpenUserProfile = { navigate(Screen.USER_PROFILE) },
            onOpenNews = { navigate(Screen.NEWS) },
            onOpenFreeze = { navigate(Screen.FREEZE) },
            onOpenNotifier = { navigate(Screen.NOTIFIER) },
            onOpenStats = { navigate(Screen.STATS) },
            onOpenSleep = { navigate(Screen.SLEEP) },
            onOpenSettings = { navigate(Screen.SETTINGS) },
        )

        Screen.TEAMS -> TeamsScreen(
            onBack = { goBack() },
            onOpenTeam = { team ->
                selectedTeam = team
                navigate(Screen.TIMERS)
            },
            onCreateTeam = { name ->
                // La squadra nasce con il suo primo guardiano, già precompilato.
                selectedTeam = name
                draft = TimerDraft.new().copy(team = name)
                navigate(Screen.EDIT)
            },
        )

        Screen.TIMERS -> HomeScreen(
            teamFilter = selectedTeam,
            onBack = { goBack() },
            onAdd = {
                // Precompila la squadra corrente, salvo la vista "tutti" o la Generale.
                val presetTeam = selectedTeam
                    ?.takeIf { it != "Squadra Generale" && it != ALL_TEAMS_FILTER }
                    ?: ""
                draft = TimerDraft.new().copy(team = presetTeam)
                navigate(Screen.EDIT)
            },
            onEdit = { timer ->
                draft = TimerDraft.from(timer)
                navigate(Screen.EDIT)
            },
        )

        Screen.GUIDES -> GuidesScreen(
            onBack = { goBack() },
            onOpenTeams = { selectedTeam = null; navigate(Screen.TEAMS) },
            onCreateGuardian = { type ->
                draft = TimerDraft.new().copy(type = type)
                selectedTeam = null
                navigate(Screen.EDIT)
            },
        )

        Screen.SIGILLO -> SigilloScreen(onBack = { goBack() })

        Screen.PROFILE -> ProfileScreen(onBack = { goBack() })

        Screen.USER_PROFILE -> UserProfileScreen(
            onBack = { goBack() },
            onOpenWhy = { navigate(Screen.PROFILE) },
            onOpenGoalGuide = { navigate(Screen.GUIDES) },
        )

        Screen.NEWS -> NewsScreen(onBack = { goBack() })

        Screen.FREEZE -> CongelamentoScreen(onBack = { goBack() })

        Screen.NOTIFIER -> NotifierScreen(onBack = { goBack() })

        Screen.TRAVEL -> TravelScreen(onBack = { goBack() })

        Screen.SPELLS -> SpellsScreen(onBack = { goBack() })

        Screen.SETTINGS -> SettingsScreen(
            onBack = { goBack() },
            // App escluse, Sigillo, Viaggio e Notifiche vivono qui dentro.
            onOpenExclusions = { navigate(Screen.EXCLUSIONS) },
            onOpenSigillo = { navigate(Screen.SIGILLO) },
            onOpenTravel = { navigate(Screen.TRAVEL) },
            onOpenNotifications = { navigate(Screen.NOTIFICATIONS) },
            onOpenBattery = { navigate(Screen.BATTERY) },
            onOpenPersonalization = { navigate(Screen.PERSONALIZATION) },
            onOpenAdvanced = { navigate(Screen.ADVANCED) },
        )

        Screen.NOTIFICATIONS -> NotificationsScreen(onBack = { goBack() })

        Screen.BATTERY -> BatteryScreen(onBack = { goBack() })

        Screen.PERSONALIZATION -> PersonalizationScreen(onBack = { goBack() })

        Screen.ADVANCED -> AdvancedScreen(onBack = { goBack() })

        Screen.SLEEP -> SleepScreen(onBack = { goBack() })

        Screen.STATS -> StatsScreen(onBack = { goBack() })

        Screen.EXCLUSIONS -> if (sealed) {
            SealedNotice(onBack = { goBack() })
        } else {
            AppPickerScreen(
                selected = ExclusionsRepository.excluded.collectAsState().value,
                onDone = { selection ->
                    ExclusionsRepository.setExcluded(context, selection)
                    goBack()
                },
                title = com.guardians.app.data.tr("App escluse", "Excluded apps"),
                subtitle = com.guardians.app.data.tr(
                    "Queste app non verranno mai bloccate da nessun guardiano " +
                        "(telefonate e impostazioni sono sempre al sicuro comunque).",
                    "These apps will never be blocked by any guardian " +
                        "(calls and settings are always safe anyway).",
                ),
            )
        }

        Screen.EDIT -> {
            val current = draft
            if (current == null) {
                // Bozza mancante (non dovrebbe succedere): esci dall'editor.
                androidx.compose.runtime.SideEffect { goBack() }
            } else {
                // Uscita dall'editor (freccia, indietro, salva, elimina): si torna
                // sempre alla schermata da cui l'editor è stato aperto.
                EditScreen(
                    draft = current,
                    onDraftChange = { draft = it },
                    onPickApps = { navigate(Screen.PICKER) },
                    onBack = { goBack() },
                    onSave = { timer ->
                        TimerRepository.upsert(context, timer)
                        draft = null
                        goBack()
                    },
                    onDelete = { id ->
                        TimerRepository.delete(context, id)
                        draft = null
                        goBack()
                    },
                )
            }
        }

        Screen.PICKER -> AppPickerScreen(
            selected = draft?.packages ?: emptySet(),
            onDone = { selection ->
                draft = draft?.copy(packages = selection)
                goBack()
            },
            title = com.guardians.app.data.tr("App da sorvegliare", "Apps to watch"),
        )
    }
}

/** Avviso mostrato quando il Sigillo blocca una sezione. */
@Composable
private fun SealedNotice(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Sigillo attivo",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            "Le app escluse non si possono modificare finché il Sigillo non scade.",
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onBack) {
            Text("Torna indietro")
        }
    }
}
