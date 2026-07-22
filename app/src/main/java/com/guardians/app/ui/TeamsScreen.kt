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
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
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
import androidx.compose.runtime.LaunchedEffect
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

    // Dialoghi: creazione squadra + conferma di eliminazione.
    var showCreate by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }

    // Menù rapido del header: scegli la squadra da eliminare.
    var pickTeamFor by remember { mutableStateOf<String?>(null) } // "delete"
    pickTeamFor?.let {
        AlertDialog(
            onDismissRequest = { pickTeamFor = null },
            title = { Text(tr("Quale squadra eliminare?", "Which team to delete?")) },
            text = {
                Column {
                    teams.keys.forEach { team ->
                        TextButton(
                            onClick = {
                                deleteTarget = team
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
                "Tieni premuta una squadra per cambiarne l'icona o eliminarla.",
                "Long-press a team to change its icon or delete it.",
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // ------------------------------------------------ PAUSA dei guardiani
        // Due pulsanti: a sinistra la pausa PERSONALIZZATA (chiede quanto), a
        // destra quella PREIMPOSTATA (long-press per cambiarla). Durante la
        // pausa i pulsanti lasciano il posto a un banner col conto alla
        // rovescia; allo scadere tutto si riattiva da solo.
        PauseControls()

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

        // Icona personalizzata per squadra (7): cartella, gruppo o un elmo.
        val teamIcons by TeamsRepository.teamIcons.collectAsState()
        var iconTarget by remember { mutableStateOf<String?>(null) }
        iconTarget?.let { t ->
            TeamIconDialog(team = t, onDismiss = { iconTarget = null })
        }

        teams.forEach { (team, members) ->
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
                        TeamIcon(
                            teamIcons[team] ?: TeamsRepository.ICON_FOLDER,
                            Modifier.size(30.dp),
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
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        text = { Text(tr("Cambia icona", "Change icon")) },
                        leadingIcon = {
                            TeamIcon(
                                teamIcons[team] ?: TeamsRepository.ICON_FOLDER,
                                Modifier.size(22.dp),
                            )
                        },
                        onClick = {
                            menuOpen = false
                            iconTarget = team
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

/**
 * L'icona di una squadra: cartella classica, gruppo di guardiani (colorato col
 * giallo dell'app) oppure l'elmo di uno dei tipi (7).
 */
@Composable
private fun TeamIcon(iconKey: String, modifier: Modifier = Modifier) {
    when (iconKey) {
        TeamsRepository.ICON_GROUPS -> Icon(
            Icons.Default.Groups,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = modifier,
        )

        TeamsRepository.ICON_FOLDER -> Icon(
            Icons.Default.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = modifier,
        )

        else -> {
            val type = com.guardians.app.model.TimerType.entries
                .firstOrNull { it.name == iconKey }
            if (type != null) {
                TimerShapeIcon(type, modifier)
            } else {
                Icon(
                    Icons.Default.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = modifier,
                )
            }
        }
    }
}

/** La scelta dell'icona della squadra: cartella, gruppo o uno degli elmi. */
@Composable
private fun TeamIconDialog(team: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val current = TeamsRepository.teamIcons.collectAsState().value[team]
        ?: TeamsRepository.ICON_FOLDER

    @Composable
    fun choice(key: String, content: @Composable () -> Unit) {
        Card(
            onClick = {
                TeamsRepository.setIcon(context, team, key)
                onDismiss()
            },
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.background,
            ),
            border = if (current == key) {
                androidx.compose.foundation.BorderStroke(
                    2.dp, MaterialTheme.colorScheme.primary,
                )
            } else {
                null
            },
        ) {
            Box(
                Modifier.size(64.dp),
                contentAlignment = Alignment.Center,
            ) { content() }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Icona di \"$team\"", "Icon for \"$team\"")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    choice(TeamsRepository.ICON_FOLDER) {
                        Icon(
                            Icons.Default.Folder,
                            contentDescription = tr("Cartella", "Folder"),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp),
                        )
                    }
                    choice(TeamsRepository.ICON_GROUPS) {
                        Icon(
                            Icons.Default.Groups,
                            contentDescription = tr("Gruppo", "Group"),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp),
                        )
                    }
                }
                com.guardians.app.model.TimerType.entries.chunked(4).forEach { rowTypes ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowTypes.forEach { type ->
                            choice(type.name) {
                                TimerShapeIcon(type, Modifier.size(44.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(tr("Chiudi", "Close")) }
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
    // Sempre L M M G V S D: i giorni attivi in giallo grassetto, gli spenti
    // sbiaditi (11). Niente più "Tutti i giorni" scritto.
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        dayInitials().forEachIndexed { i, label ->
            val on = days.contains(i + 1)
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                color = if (on) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
            )
        }
    }
}

/**
 * I controlli della PAUSA (sostituiscono gli incantesimi): due pulsanti —
 * personalizzata a sinistra, preimpostata a destra (long-press per cambiarla;
 * la prima volta chiede subito i minuti). In pausa, un banner col countdown.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PauseControls() {
    val context = LocalContext.current
    com.guardians.app.data.PauseRepository.load(context)
    val pauseUntil by com.guardians.app.data.PauseRepository.pauseUntil.collectAsState()
    val preset by com.guardians.app.data.PauseRepository.presetMinutes.collectAsState()

    // Ticker locale per il conto alla rovescia del banner.
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(pauseUntil) {
        while (System.currentTimeMillis() < pauseUntil) {
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000L)
        }
        now = System.currentTimeMillis()
    }
    val paused = now < pauseUntil

    // Dialogo dei minuti: per la pausa personalizzata o per cambiare il preset.
    // mode: "custom" = avvia subito; "preset" = salva soltanto il preset;
    // "presetAndStart" = primo uso del pulsante destro (salva e avvia).
    var askMinutes by remember { mutableStateOf<String?>(null) }
    askMinutes?.let { mode ->
        var text by remember(mode) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { askMinutes = null },
            title = {
                Text(
                    if (mode == "preset") tr("Pausa preimpostata", "Preset pause")
                    else tr("Quanto vuoi fare la pausa?", "How long should the pause be?")
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        tr(
                            "Minuti di pausa (massimo 12 ore = 720). I guardiani " +
                                "si riattiveranno da soli allo scadere.",
                            "Pause minutes (max 12 hours = 720). Guardians will " +
                                "re-arm on their own when it ends.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = text,
                        onValueChange = { v -> text = v.filter { it.isDigit() } },
                        label = { Text(tr("Minuti", "Minutes")) },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                        ),
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                val v = text.toIntOrNull() ?: 0
                TextButton(
                    enabled = v in 1..com.guardians.app.data.PauseRepository.MAX_MINUTES,
                    onClick = {
                        when (mode) {
                            "custom" -> com.guardians.app.data.PauseRepository
                                .startPause(context, v)
                            "preset" -> com.guardians.app.data.PauseRepository
                                .setPreset(context, v)
                            else -> {   // presetAndStart: primo uso del destro
                                com.guardians.app.data.PauseRepository.setPreset(context, v)
                                com.guardians.app.data.PauseRepository.startPause(context, v)
                            }
                        }
                        askMinutes = null
                    },
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { askMinutes = null }) { Text(tr("Annulla", "Cancel")) }
            },
        )
    }

    if (paused) {
        // Banner del conto alla rovescia al posto dei pulsanti.
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            ),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(14.dp),
            ) {
                Icon(
                    Icons.Default.Pause,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        tr("Pausa attiva: ", "Pause active: ") +
                            com.guardians.app.model.formatMsPrecise(
                                (pauseUntil - now).coerceAtLeast(1000L),
                            ),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        tr(
                            "Allo scadere i guardiani si riattivano da soli.",
                            "When it ends, guardians re-arm on their own.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(
                    onClick = { com.guardians.app.data.PauseRepository.endPause(context) },
                ) { Text(tr("Termina", "End")) }
            }
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // SINISTRA: pausa personalizzata (chiede i minuti ogni volta).
            OutlinedButton(
                onClick = { askMinutes = "custom" },
                modifier = Modifier.weight(1f).height(46.dp),
            ) {
                Text(tr("Pausa…", "Pause…"), fontWeight = FontWeight.Bold, maxLines = 1)
            }
            // DESTRA: pausa preimpostata; long-press per cambiarla. La prima
            // volta (preset = 0) chiede subito i minuti.
            Box(
                Modifier
                    .weight(1f)
                    .height(46.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.primary)
                    .combinedClickable(
                        onClick = {
                            if (preset > 0) {
                                com.guardians.app.data.PauseRepository
                                    .startPause(context, preset)
                            } else {
                                askMinutes = "presetAndStart"
                            }
                        },
                        onLongClick = { askMinutes = "preset" },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (preset > 0) {
                        tr("Pausa ", "Pause ") + formatMs(preset * 60_000L)
                    } else {
                        tr("Pausa rapida", "Quick pause")
                    },
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Matrice Lun-Dom toccabile per scegliere i giorni attivi della squadra. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WeekDaysEditor(days: Set<Int>, onToggle: (Int) -> Unit) {
    // CENTRATI nella riga (8): prima erano incollati a sinistra.
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        modifier = Modifier.fillMaxWidth(),
    ) {
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
