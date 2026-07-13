package com.guardians.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.guardians.app.data.SealRepository
import com.guardians.app.data.SpellsRepository
import com.guardians.app.data.TeamsRepository
import com.guardians.app.data.TimerRepository
import com.guardians.app.data.UsageStateStore
import com.guardians.app.data.tr
import com.guardians.app.model.formatMs
// ALL_TEAMS_FILTER è dichiarato in HomeScreen.kt (stesso package).
import com.guardians.app.ui.theme.RedGuardiano

/** Le Squadre: cartelle che raggruppano i guardiani. Da qui si gestiscono. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TeamsScreen(
    onBack: () -> Unit,
    onOpenTeam: (String) -> Unit,
    onCreateTeam: (String) -> Unit,
) {
    val context = LocalContext.current
    TeamsRepository.load(context)
    val timers by TimerRepository.timers.collectAsState()
    val customTeams by TeamsRepository.teams.collectAsState()
    UsageStateStore.state.collectAsState().value // aggiorna i countdown
    SealRepository.waitReadyAt.collectAsState().value
    val sealed = !SealRepository.canEditNow()

    // Le squadre con guardiani + quelle create col "+" (che vivono anche vuote).
    val grouped = timers.groupBy { it.teamName }
    val teams = (grouped.keys + customTeams).toSortedSet()
        .associateWith { grouped[it] ?: emptyList() }

    // Dialoghi: creazione squadra + conferme di eliminazione/incantesimo.
    var showCreate by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    var shadowTarget by remember { mutableStateOf<String?>(null) }

    // Menù rapido del header: scegli la squadra su cui agire.
    var pickTeamFor by remember { mutableStateOf<String?>(null) } // "shadow" | "delete"
    pickTeamFor?.let { mode ->
        AlertDialog(
            onDismissRequest = { pickTeamFor = null },
            title = {
                Text(
                    if (mode == "shadow") tr("Ombra su quale squadra?", "Shadow which team?")
                    else tr("Quale squadra eliminare?", "Which team to delete?")
                )
            },
            text = {
                Column {
                    teams.keys.forEach { team ->
                        TextButton(
                            onClick = {
                                if (mode == "shadow") shadowTarget = team
                                else deleteTarget = team
                                pickTeamFor = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(team) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { pickTeamFor = null }) { Text(tr("Annulla", "Cancel")) }
            },
        )
    }

    if (showCreate) {
        NewTeamDialog(
            onDismiss = { showCreate = false },
            onCreate = { name ->
                showCreate = false
                // La squadra viene registrata subito: resta anche se vuota.
                TeamsRepository.add(context, name)
                onCreateTeam(name)
            },
        )
    }
    deleteTarget?.let { team ->
        ConfirmDeleteTeamDialog(team = team, sealed = sealed, onDismiss = { deleteTarget = null })
    }
    shadowTarget?.let { team ->
        ConfirmShadowTeamDialog(team = team, sealed = sealed, onDismiss = { shadowTarget = null })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
            }
            Text(
                tr("Squadre", "Teams"),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            // Il "+" sta in alto a destra, lontano dal titolo.
            IconButton(onClick = { showCreate = true }) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = tr("Nuova squadra", "New team"),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            // A destra del "+": le azioni rapide sulle squadre.
            Box {
                var menuOpen by remember { mutableStateOf(false) }
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = tr("Azioni rapide", "Quick actions"),
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(tr("Lancia Ombra", "Cast Shadow")) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        onClick = { menuOpen = false; pickTeamFor = "shadow" },
                    )
                    DropdownMenuItem(
                        text = { Text(tr("Elimina squadre", "Delete teams")) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = RedGuardiano,
                            )
                        },
                        onClick = { menuOpen = false; pickTeamFor = "delete" },
                    )
                }
            }
        }

        Text(
            tr(
                "Tieni premuta una squadra per eliminarla o lanciarle un incantesimo.",
                "Long-press a team to delete it or cast a spell on it.",
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (teams.isEmpty()) {
            Text(
                tr(
                    "Nessuna squadra: creane una con il \"+\" qui sopra, oppure " +
                        "crea un guardiano dalla home e assegnagli una squadra " +
                        "(vuota = Squadra Generale).",
                    "No teams yet: create one with the \"+\" above, or create a " +
                        "guardian from the home and assign it a team " +
                        "(empty = Squadra Generale).",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (timers.isNotEmpty()) {
            // Cartella speciale con TUTTI i guardiani, per ritrovarne uno smarrito.
            Card(
                onClick = { onOpenTeam(ALL_TEAMS_FILTER) },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Icon(
                        Icons.Default.FolderSpecial,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(tr("Tutti i guardiani", "All guardians"), fontWeight = FontWeight.Bold)
                        Text(
                            tr(
                                "${timers.size} guardiani in totale",
                                "${timers.size} guardians in total",
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        teams.forEach { (team, members) ->
            val shadowed = SpellsRepository.isShadowed(team)
            // Box come àncora del menù contestuale aperto dalla pressione prolungata.
            Box {
                var menuOpen by remember(team) { mutableStateOf(false) }
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.medium)
                        .combinedClickable(
                            onClick = { onOpenTeam(team) },
                            onLongClick = { menuOpen = true },
                        ),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) {
                        Icon(
                            Icons.Default.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(team, fontWeight = FontWeight.Bold)
                            Text(
                                tr(
                                    "${members.size} guardiani · " +
                                        "${members.count { it.enabled }} attivi",
                                    "${members.size} guardians · " +
                                        "${members.count { it.enabled }} active",
                                ) + if (shadowed) tr(" · SOSPESA DALL'OMBRA", " · SHADOWED") else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (shadowed) RedGuardiano
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            // I giorni attivi a colpo d'occhio.
                            WeekDaysStrip(TeamsRepository.daysFor(team))
                        }
                        // Switch di attivazione a DESTRA di ogni riga squadra.
                        val anyOn = members.any { it.enabled }
                        Switch(
                            checked = anyOn,
                            onCheckedChange = { on ->
                                members.forEach {
                                    TimerRepository.setEnabled(context, it.id, on)
                                }
                            },
                        )
                    }
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
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
                            shadowTarget = team
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
                            deleteTarget = team
                        },
                    )
                }
            }
        }
    }
}

/** Chiede il nome della nuova squadra; alla conferma si crea il suo primo guardiano. */
@Composable
private fun NewTeamDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Nuova squadra", "New team")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    tr(
                        "Dai un nome alla squadra: subito dopo potrai crearle il " +
                            "primo guardiano (ma la squadra resta anche vuota).",
                        "Name the team: right after you can create its first " +
                            "guardian (but the team stays even if empty).",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(tr("Nome squadra", "Team name")) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onCreate(name.trim()) },
            ) {
                Text(tr("Crea", "Create"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(tr("Annulla", "Cancel")) }
        },
    )
}

/** Iniziali dei giorni (lun→dom), nella lingua dell'app. */
private fun dayInitials(): List<String> =
    if (com.guardians.app.data.SettingsRepository.english.value) {
        listOf("M", "T", "W", "T", "F", "S", "S")
    } else {
        listOf("L", "M", "M", "G", "V", "S", "D")
    }

/** Striscia read-only dei giorni attivi (evidenzia i giorni in cui la squadra opera). */
@Composable
fun WeekDaysStrip(days: Set<Int>) {
    if (days.size == 7) {
        Text(
            tr("Tutti i giorni", "Every day"),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        dayInitials().forEachIndexed { i, label ->
            val on = days.contains(i + 1)
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                color = if (on) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
        }
    }
}

/** Matrice Lun-Dom toccabile per scegliere i giorni attivi della squadra. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WeekDaysEditor(days: Set<Int>, onToggle: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        dayInitials().forEachIndexed { i, label ->
            val iso = i + 1
            val on = days.contains(iso)
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(
                        if (on) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .combinedClickable { onToggle(iso) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (on) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Conferma l'eliminazione di una squadra (= di TUTTI i suoi guardiani).
 * Con il Sigillo attivo l'eliminazione è bloccata, come ogni altra modifica.
 * Usato anche dalla pagina della squadra (HomeScreen).
 */
@Composable
fun ConfirmDeleteTeamDialog(
    team: String,
    sealed: Boolean,
    onDismiss: () -> Unit,
    onDeleted: () -> Unit = {},
) {
    val context = LocalContext.current
    val members = TimerRepository.timers.collectAsState().value.filter { it.teamName == team }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Eliminare la squadra?", "Delete the team?")) },
        text = {
            Text(
                if (sealed) {
                    tr(
                        "Sigillo attivo: non puoi eliminare squadre finché l'attesa non è finita.",
                        "Seal active: you can't delete teams until the wait is over.",
                    )
                } else {
                    tr(
                        "Sei sicuro di voler eliminare \"$team\"? Verranno eliminati " +
                            "anche i suoi ${members.size} guardiani. " +
                            "L'azione non si può annullare.",
                        "Are you sure you want to delete \"$team\"? Its " +
                            "${members.size} guardians will be deleted too. " +
                            "This cannot be undone.",
                    )
                }
            )
        },
        confirmButton = {
            if (!sealed) {
                TextButton(
                    onClick = {
                        members.forEach { TimerRepository.delete(context, it.id) }
                        TeamsRepository.remove(context, team)
                        onDismiss()
                        onDeleted()
                    },
                ) {
                    Text(tr("Elimina", "Delete"), color = RedGuardiano)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (sealed) "OK" else tr("Annulla", "Cancel"))
            }
        },
    )
}

/**
 * Conferma il lancio dell'Incantesimo d'Ombra su una squadra, scegliendo la
 * durata: scelte rapide (le più usate dall'utente) o un tempo su misura.
 * Vietato mentre il Sigillo è attivo. Usato anche dalla pagina della squadra.
 */
@Composable
fun ConfirmShadowTeamDialog(team: String, sealed: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var customValue by remember { mutableStateOf("") }
    var customUnit by remember { mutableStateOf(com.guardians.app.model.TimeUnit.MINUTES) }
    val customMinutes = customValue.toIntOrNull()
        ?.let { (it * customUnit.seconds / 60L).toInt() }

    fun cast(minutes: Int) {
        castQuickShadow(context, team, minutes)
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Lanciare l'Ombra?", "Cast the Shadow?")) },
        text = {
            if (sealed) {
                Text(
                    tr(
                        "Sigillo attivo: gli incantesimi che sospendono la protezione " +
                            "non si possono lanciare finché l'attesa non è finita.",
                        "Seal active: spells that suspend protection can't be cast " +
                            "until the wait is over.",
                    )
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        tr(
                            "I guardiani di \"$team\" riposeranno per la durata scelta, " +
                                "poi la protezione tornerà da sola.",
                            "The guardians of \"$team\" will rest for the chosen " +
                                "duration, then protection resumes on its own.",
                        )
                    )
                    // Scelte rapide: le durate che usi più spesso.
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SpellsRepository.quickShadowMinutes(context).forEach { minutes ->
                            OutlinedButton(
                                onClick = { cast(minutes) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(formatMs(minutes * 60_000L))
                            }
                        }
                    }
                    DurationField(
                        label = tr("…o durata su misura", "…or custom duration"),
                        value = customValue,
                        unit = customUnit,
                        onValueChange = { customValue = it },
                        onUnitChange = { customUnit = it },
                    )
                }
            }
        },
        confirmButton = {
            if (!sealed) {
                TextButton(
                    enabled = customMinutes != null && customMinutes > 0,
                    onClick = { cast(customMinutes!!) },
                ) {
                    Text(tr("Lancia", "Cast"))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (sealed) "OK" else tr("Annulla", "Cancel"))
            }
        },
    )
}

/** Ombra di [minutes] minuti su [team], senza accorciare le squadre già in ombra. */
private fun castQuickShadow(context: android.content.Context, team: String, minutes: Int) {
    val now = System.currentTimeMillis()
    val active = now < SpellsRepository.shadowUntil.value
    val teams = when {
        !active -> setOf(team)
        // Insieme vuoto = Ombra globale: contiene già questa squadra.
        SpellsRepository.shadowTeams.value.isEmpty() -> emptySet()
        else -> SpellsRepository.shadowTeams.value + team
    }
    val until = maxOf(now + minutes * 60_000L, SpellsRepository.shadowUntil.value)
    SpellsRepository.castShadow(context, until, teams)
    SpellsRepository.recordShadowDuration(context, minutes)
}
