package com.guardians.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.guardians.app.data.SealRepository
import com.guardians.app.data.SpellsRepository
import com.guardians.app.data.TimerRepository
import com.guardians.app.data.UsageStateStore
import com.guardians.app.data.tr
import com.guardians.app.model.GuardianTimer
import com.guardians.app.model.TimerType
import com.guardians.app.model.formatMs
import com.guardians.app.model.formatTimeOfDay
import com.guardians.app.ui.theme.AmberSentinella
import com.guardians.app.ui.theme.RedGuardiano
import java.time.LocalTime

/** Valore di [HomeScreen.teamFilter] che mostra TUTTI i guardiani di ogni squadra. */
const val ALL_TEAMS_FILTER = "__ALL_GUARDIANS__"

/** Lista dei guardiani ("timer") con creazione e modifica. Il Custode vive nella home. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    teamFilter: String? = null,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (GuardianTimer) -> Unit,
) {
    val context = LocalContext.current
    val timers by TimerRepository.timers.collectAsState()
    val usage by UsageStateStore.state.collectAsState()
    // Sigillo: l'attesa è già partita all'apertura dell'app (vedi MainActivity).
    val sealDelay by SealRepository.delayMs.collectAsState()
    val waitReadyAt by SealRepository.waitReadyAt.collectAsState()
    val sealed = !SealRepository.canEditNow()

    // Azioni sulla squadra aperta (incantesimo / eliminazione), con conferma.
    val realTeam = teamFilter?.takeIf { it != ALL_TEAMS_FILTER }
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    var shadowTarget by remember { mutableStateOf<String?>(null) }
    deleteTarget?.let { team ->
        ConfirmDeleteTeamDialog(
            team = team,
            sealed = sealed,
            onDismiss = { deleteTarget = null },
            onDeleted = onBack,
        )
    }
    shadowTarget?.let { team ->
        ConfirmShadowTeamDialog(team = team, sealed = sealed, onDismiss = { shadowTarget = null })
    }

    // Spegnimento squadra: mai al volo — si sceglie tra disattivare o Ombra.
    var deactivateChoice by remember { mutableStateOf(false) }
    if (deactivateChoice && realTeam != null) {
        val members = timers.filter { it.teamName == realTeam }
        AlertDialog(
            onDismissRequest = { deactivateChoice = false },
            title = { Text(tr("Fermare la squadra?", "Stop the team?")) },
            text = {
                Text(
                    tr(
                        "Vuoi DISATTIVARE la squadra (resta spenta finché non la " +
                            "riaccendi) o preferisci lanciare un'OMBRA temporanea " +
                            "(i guardiani tornano da soli allo scadere)?",
                        "Do you want to TURN OFF the team (stays off until you " +
                            "re-enable it) or cast a temporary SHADOW instead " +
                            "(guardians come back on their own)?",
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    members.forEach { TimerRepository.setEnabled(context, it.id, false) }
                    deactivateChoice = false
                }) {
                    Text(tr("Disattiva", "Turn off"), color = RedGuardiano)
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        deactivateChoice = false
                        shadowTarget = realTeam
                    }) { Text(tr("Lancia Ombra", "Cast Shadow")) }
                    TextButton(onClick = { deactivateChoice = false }) {
                        Text(tr("Annulla", "Cancel"))
                    }
                }
            },
        )
    }

    // Conferma globale (Impostazioni): spegnere un singolo guardiano chiede conferma.
    var toggleTarget by remember { mutableStateOf<GuardianTimer?>(null) }
    toggleTarget?.let { t ->
        AlertDialog(
            onDismissRequest = { toggleTarget = null },
            title = { Text(tr("Spegnere il guardiano?", "Turn the guardian off?")) },
            text = {
                Text(
                    tr(
                        "\"${t.name}\" smetterà di sorvegliare finché non lo riaccendi.",
                        "\"${t.name}\" will stop watching until you re-enable it.",
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    TimerRepository.setEnabled(context, t.id, false)
                    toggleTarget = null
                }) { Text(tr("Spegni", "Turn off"), color = RedGuardiano) }
            },
            dismissButton = {
                TextButton(onClick = { toggleTarget = null }) { Text(tr("Annulla", "Cancel")) }
            },
        )
    }

    // Eliminazione di un singolo guardiano (pressione prolungata sulla card).
    var deleteTimerTarget by remember { mutableStateOf<GuardianTimer?>(null) }
    deleteTimerTarget?.let { t ->
        AlertDialog(
            onDismissRequest = { deleteTimerTarget = null },
            title = { Text(tr("Eliminare il guardiano?", "Delete the guardian?")) },
            text = {
                Text(
                    if (sealed) {
                        tr(
                            "Sigillo attivo: non puoi eliminare guardiani finché " +
                                "l'attesa non è finita.",
                            "Seal active: you can't delete guardians until the " +
                                "wait is over.",
                        )
                    } else {
                        tr(
                            "Sei sicuro di voler eliminare \"${t.name}\"? " +
                                "L'azione non si può annullare.",
                            "Are you sure you want to delete \"${t.name}\"? " +
                                "This cannot be undone.",
                        )
                    }
                )
            },
            confirmButton = {
                if (!sealed) {
                    TextButton(onClick = {
                        TimerRepository.delete(context, t.id)
                        deleteTimerTarget = null
                    }) {
                        Text(tr("Elimina", "Delete"), color = RedGuardiano)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTimerTarget = null }) {
                    Text(if (sealed) "OK" else tr("Annulla", "Cancel"))
                }
            },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                containerColor = MaterialTheme.colorScheme.primary,
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(tr("Nuovo timer", "New timer"))
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                    Text(
                        when (teamFilter) {
                            null -> tr("I tuoi guardiani", "Your guardians")
                            ALL_TEAMS_FILTER -> tr("Tutti i guardiani", "All guardians")
                            else -> teamFilter
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    // Le azioni sulla squadra vivono nel menù a 3 puntini a destra.
                    if (realTeam != null) {
                        Box {
                            var menuOpen by remember { mutableStateOf(false) }
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = tr("Azioni squadra", "Team actions"),
                                )
                            }
                            DropdownMenu(
                                expanded = menuOpen,
                                onDismissRequest = { menuOpen = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(tr("Lancia Incantesimo", "Cast a spell")) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    },
                                    onClick = {
                                        menuOpen = false
                                        shadowTarget = realTeam
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(tr("Elimina squadra", "Delete team")) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = null,
                                            tint = RedGuardiano,
                                        )
                                    },
                                    onClick = {
                                        menuOpen = false
                                        deleteTarget = realTeam
                                    },
                                )
                            }
                        }
                    }
                }
            }

            // Interruttore generale: spegne o riaccende TUTTI i guardiani della squadra.
            if (realTeam != null) {
                val members = timers.filter { it.teamName == realTeam }
                if (members.isNotEmpty()) {
                    // Un'unica card: interruttore generale + giorni di servizio.
                    item {
                        com.guardians.app.data.TeamsRepository.load(context)
                        val teamDays by com.guardians.app.data.TeamsRepository.teamDays
                            .collectAsState()
                        val days = teamDays[realTeam]
                            ?: setOf(1, 2, 3, 4, 5, 6, 7)
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            tr("Squadra in servizio", "Team on duty"),
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Text(
                                            tr(
                                                "Accende o spegne tutti i guardiani; " +
                                                    "sotto scegli i giorni di servizio.",
                                                "Turns all guardians on or off; pick the " +
                                                    "on-duty days below.",
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Switch(
                                        checked = members.any { it.enabled },
                                        enabled = !sealed,
                                        onCheckedChange = { on ->
                                            if (on) {
                                                members.forEach {
                                                    TimerRepository.setEnabled(context, it.id, true)
                                                }
                                            } else {
                                                // Spegnere non è mai immediato: dialog di scelta.
                                                deactivateChoice = true
                                            }
                                        },
                                    )
                                }
                                Spacer(Modifier.height(12.dp))
                                WeekDaysEditor(days) { iso ->
                                    if (!sealed) {
                                        val next = if (days.contains(iso)) days - iso else days + iso
                                        com.guardians.app.data.TeamsRepository
                                            .setDays(context, realTeam, next)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (sealDelay > 0L) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = AmberSentinella.copy(alpha = 0.15f)
                        ),
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = AmberSentinella)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                if (sealed) {
                                    tr("Sigillo: potrai modificare tra ", "Seal: editable in ") +
                                        formatMs(
                                            (waitReadyAt - System.currentTimeMillis())
                                                .coerceAtLeast(1000L)
                                        )
                                } else {
                                    tr(
                                        "Sigillo superato: ora puoi modificare.",
                                        "Seal wait over: you can edit now.",
                                    )
                                },
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            val visibleTimers = timers.filter {
                teamFilter == null || teamFilter == ALL_TEAMS_FILTER ||
                    it.teamName == teamFilter
            }
            if (visibleTimers.isEmpty()) {
                item { EmptyState() }
            } else {
                items(visibleTimers, key = { it.id }) { timer ->
                    TimerCard(
                        timer = timer,
                        usage = usage,
                        onClick = { if (!sealed) onEdit(timer) },
                        onLongPress = { deleteTimerTarget = timer },
                        onToggle = { enabled ->
                            if (!sealed) {
                                // Con la conferma globale attiva, spegnere chiede l'ok.
                                if (!enabled &&
                                    com.guardians.app.data.SettingsRepository
                                        .confirmActions.value
                                ) {
                                    toggleTarget = timer
                                } else {
                                    TimerRepository.setEnabled(context, timer.id, enabled)
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TimerType.entries.filter { !it.configuredFromHub }
                    .forEachIndexed { index, type ->
                        if (index > 0) Spacer(Modifier.width(12.dp))
                        TimerShapeIcon(type, Modifier.size(30.dp))
                    }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Nessun guardiano in servizio",
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Crea il tuo primo guardiano con il pulsante qui sotto: ogni tipo " +
                    "ha la sua forma, il suo colore e la sua regola.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TimerCard(
    timer: GuardianTimer,
    usage: UsageStateStore.Snapshot,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    val now = System.currentTimeMillis()
    // La Vedetta si comporta come il suo potere interno (effectiveType).
    val eff = timer.effectiveType
    val usedMs = when (eff) {
        TimerType.GUARDIANO -> usage.dailyMs[timer.id] ?: 0L
        TimerType.SENTINELLA -> usage.continuousMs[timer.id] ?: 0L
        TimerType.GENDARME, TimerType.CUSTODE, TimerType.VEDETTA,
        TimerType.MESSAGGERO, TimerType.ESATTORE, TimerType.ARALDO -> 0L
    }
    val opens = usage.opens[timer.id] ?: 0
    val isBlocked = usage.blocked.contains(timer.id)
    val cooldownRemainingMs = (usage.cooldownUntil[timer.id] ?: 0L) - now
    val nowMinute = LocalTime.now().let { it.hour * 60 + it.minute }
    val windowActive = eff == TimerType.CUSTODE && timer.isActiveAt(nowMinute)
    val accent = Color(timer.type.colorArgb)

    val progress = when {
        eff == TimerType.GENDARME && timer.maxOpensPerDay > 0 ->
            (opens.toFloat() / timer.maxOpensPerDay.toFloat()).coerceIn(0f, 1f)
        (eff == TimerType.SENTINELLA || eff == TimerType.GUARDIANO) && timer.limitMs > 0L ->
            (usedMs.toFloat() / timer.limitMs.toFloat()).coerceIn(0f, 1f)
        else -> 0f
    }
    val showProgress = when (eff) {
        TimerType.SENTINELLA, TimerType.GUARDIANO, TimerType.GENDARME -> true
        TimerType.CUSTODE, TimerType.VEDETTA, TimerType.MESSAGGERO,
        TimerType.ESATTORE, TimerType.ARALDO -> false
    }

    val subtitle = buildString {
        append("${timer.type.shortName} · ")
        append(
            when (eff) {
                TimerType.SENTINELLA ->
                    tr("max ${timer.limitText} di fila", "max ${timer.limitText} in a row")
                TimerType.GUARDIANO -> when (timer.resetCycle) {
                    com.guardians.app.model.ResetCycle.WEEKLY -> tr(
                        "max ${timer.limitText} a settimana",
                        "max ${timer.limitText} per week",
                    )
                    com.guardians.app.model.ResetCycle.MONTHLY -> tr(
                        "max ${timer.limitText} al mese",
                        "max ${timer.limitText} per month",
                    )
                    else -> tr("max ${timer.limitText} al giorno", "max ${timer.limitText} per day")
                }
                TimerType.CUSTODE -> tr(
                    "dalle ${formatTimeOfDay(timer.startMinuteOfDay)} " +
                        "alle ${formatTimeOfDay(timer.endMinuteOfDay)}",
                    "from ${formatTimeOfDay(timer.startMinuteOfDay)} " +
                        "to ${formatTimeOfDay(timer.endMinuteOfDay)}",
                )
                TimerType.GENDARME -> if (timer.maxOpensPerDay > 0) {
                    tr(
                        "max ${timer.maxOpensPerDay} aperture al giorno",
                        "max ${timer.maxOpensPerDay} opens per day",
                    )
                } else {
                    tr("controllo aperture", "opens control")
                }
                TimerType.MESSAGGERO -> tr(
                    "avvisi ${timer.pace.displayName.lowercase()} dopo ${timer.limitText}",
                    "${timer.pace.displayName.lowercase()} alerts after ${timer.limitText}",
                )
                TimerType.ESATTORE -> tr(
                    "pedaggio di ${timer.limitText} a ogni apertura",
                    "${timer.limitText} toll at every open",
                )
                TimerType.ARALDO -> when {
                    timer.araldoMorning && timer.araldoEvening ->
                        tr("protegge risveglio e sera", "guards morning and evening")
                    timer.araldoEvening ->
                        tr("protegge la sera", "guards the evening")
                    else -> tr(
                        "${timer.limitText} dal risveglio",
                        "${timer.limitText} from wake-up",
                    )
                }
                TimerType.VEDETTA -> ""
            }
        )
        if (timer.type == TimerType.VEDETTA) {
            append(
                tr(
                    " nel luogo (r ${"%.1f".format(timer.radiusMeters / 1000.0)} km)",
                    " at the place (r ${"%.1f".format(timer.radiusMeters / 1000.0)} km)",
                )
            )
        }
        append(" · ")
        append(
            if (timer.allApps) tr("tutto il telefono", "whole phone")
            else "${timer.packages.size} app"
        )
        append(" · ")
        append(timer.teamName)
    }
    val shadowed = SpellsRepository.isShadowed(timer.teamName)

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        // Tocco = modifica; pressione prolungata = eliminazione (con conferma).
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TimerShapeIcon(timer.type, Modifier.size(40.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(timer.name, fontWeight = FontWeight.Bold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (timer.enabled) {
                    Spacer(Modifier.height(8.dp))
                    if (showProgress) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                            color = accent,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    val statusHighlighted =
                        isBlocked || cooldownRemainingMs > 0L || windowActive
                    Text(
                        when {
                            shadowed -> tr("Sospeso dall'Incantesimo d'Ombra", "Shadowed")
                            isBlocked -> when {
                                eff == TimerType.GUARDIANO &&
                                    timer.resetCycle == com.guardians.app.model.ResetCycle.WEEKLY ->
                                    tr("Bloccata fino a lunedì", "Blocked until Monday")
                                eff == TimerType.GUARDIANO &&
                                    timer.resetCycle == com.guardians.app.model.ResetCycle.MONTHLY ->
                                    tr("Bloccata fino al 1° del mese", "Blocked until the 1st")
                                else -> tr("Bloccata fino a domani", "Blocked until tomorrow")
                            }
                            cooldownRemainingMs > 0L ->
                                tr("In pausa: puoi rientrare tra ", "On break: back in ") +
                                    formatMs(cooldownRemainingMs.coerceAtLeast(1000L))
                            timer.type == TimerType.VEDETTA -> tr(
                                "Attivo solo nel luogo impostato",
                                "Active only at the set place",
                            )
                            eff == TimerType.CUSTODE ->
                                if (windowActive) {
                                    tr(
                                        "Di guardia adesso: app bloccate fino alle ",
                                        "On duty now: apps blocked until ",
                                    ) + formatTimeOfDay(timer.endMinuteOfDay)
                                } else {
                                    tr("Prossimo turno dalle ", "Next shift from ") +
                                        formatTimeOfDay(timer.startMinuteOfDay)
                                }
                            eff == TimerType.GENDARME -> if (timer.maxOpensPerDay > 0) {
                                tr(
                                    "$opens / ${timer.maxOpensPerDay} aperture oggi",
                                    "$opens / ${timer.maxOpensPerDay} opens today",
                                )
                            } else {
                                tr("$opens aperture oggi", "$opens opens today")
                            }
                            eff == TimerType.MESSAGGERO -> tr(
                                "Ti avvisa mentre usi l'app",
                                "Nudges you while you use the app",
                            )
                            eff == TimerType.ESATTORE -> tr(
                                "Ti fa respirare prima di entrare",
                                "Makes you breathe before entering",
                            )
                            eff == TimerType.ARALDO -> {
                                val median =
                                    com.guardians.app.data.AraldoData.medianBedtimeMinute.value
                                when {
                                    timer.araldoEvening && median != null -> tr(
                                        "Nanna stimata: ~${formatTimeOfDay(median)}",
                                        "Estimated bedtime: ~${formatTimeOfDay(median)}",
                                    )
                                    timer.araldoEvening -> tr(
                                        "Sto imparando i tuoi orari (qualche notte)…",
                                        "Learning your rhythm (a few nights)…",
                                    )
                                    else -> tr(
                                        "In ascolto del tuo risveglio",
                                        "Listening for your wake-up",
                                    )
                                }
                            }
                            eff == TimerType.SENTINELLA -> tr(
                                "${formatMs(usedMs)} / ${timer.limitText} di fila",
                                "${formatMs(usedMs)} / ${timer.limitText} in a row",
                            )
                            else -> when (timer.resetCycle) {
                                com.guardians.app.model.ResetCycle.WEEKLY -> tr(
                                    "${formatMs(usedMs)} / ${timer.limitText} questa settimana",
                                    "${formatMs(usedMs)} / ${timer.limitText} this week",
                                )
                                com.guardians.app.model.ResetCycle.MONTHLY -> tr(
                                    "${formatMs(usedMs)} / ${timer.limitText} questo mese",
                                    "${formatMs(usedMs)} / ${timer.limitText} this month",
                                )
                                else -> tr(
                                    "${formatMs(usedMs)} / ${timer.limitText} oggi",
                                    "${formatMs(usedMs)} / ${timer.limitText} today",
                                )
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            isBlocked -> RedGuardiano
                            cooldownRemainingMs > 0L -> AmberSentinella
                            windowActive -> accent
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontWeight = if (statusHighlighted) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = timer.enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(checkedTrackColor = accent),
            )
        }
    }
}
