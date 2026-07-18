package com.guardians.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.guardians.app.TimerDraft
import com.guardians.app.data.tr
import com.guardians.app.model.GuardianTimer
import com.guardians.app.model.ResetCycle
import com.guardians.app.model.TimeUnit
import com.guardians.app.model.formatMs
import com.guardians.app.model.TimerType
import com.guardians.app.ui.theme.RedGuardiano
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun EditScreen(
    draft: TimerDraft,
    onDraftChange: (TimerDraft) -> Unit,
    onPickApps: () -> Unit,
    onBack: () -> Unit,
    onSave: (GuardianTimer) -> Unit,
    onDelete: (String) -> Unit,
) {
    val scroll = rememberScrollState()
    // Quando scegli il TIPO, la pagina scende da sola alla personalizzazione
    // (4): niente più cercare a mano le opzioni sotto la lista dei tipi.
    var configY by remember { mutableStateOf(0) }
    var scrollToConfig by remember { mutableStateOf(false) }
    LaunchedEffect(scrollToConfig) {
        if (scrollToConfig) {
            kotlinx.coroutines.delay(120)
            scroll.animateScrollTo(configY)
            scrollToConfig = false
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(scroll)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
            }
            Text(
                if (draft.id == null) tr("Nuovo timer", "New timer")
                else tr("Modifica timer", "Edit timer"),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }

        Text(
            tr("Tipo di guardiano", "Guardian type"),
            style = MaterialTheme.typography.titleMedium,
        )
        // Le card si generano da TimerType.entries: un tipo aggiunto in futuro
        // compare qui da solo.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Il Custode non compare qui: si configura solo dalla home.
            TimerType.entries.filter { !it.configuredFromHub }.forEach { type ->
                TypeCard(
                    type = type,
                    selected = draft.type == type,
                    onClick = {
                        onDraftChange(draft.copy(type = type))
                        scrollToConfig = true
                    },
                )
            }
        }

        OutlinedTextField(
            value = draft.name,
            onValueChange = { onDraftChange(draft.copy(name = it)) },
            label = { Text(tr("Nome del timer", "Timer name")) },
            placeholder = { Text(draft.type.nameExample) },
            supportingText = {
                Text(
                    tr(
                        "Vuoto = si chiamerà \"${draft.type.shortName}\"",
                        "Empty = it will be called \"${draft.type.shortName}\"",
                    )
                )
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                // Qui inizia la personalizzazione: il punto dello scroll (4).
                .onGloballyPositioned { configY = it.positionInParent().y.toInt() },
        )

        // Squadra: scelta rapida da un elenco delle esistenti + campo per una nuova.
        TeamSelector(
            current = draft.team,
            onSelect = { onDraftChange(draft.copy(team = it)) },
        )

        // La Vedetta presta il potere di un altro guardiano: qui scegli quale
        // (i suoi campi compaiono sotto) e il luogo in cui vale.
        val configType = if (draft.type == TimerType.VEDETTA) draft.innerType else draft.type
        if (draft.type == TimerType.VEDETTA) {
            Text(
                tr("Potere della Vedetta", "Lookout's power"),
                style = MaterialTheme.typography.titleMedium,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    tr("Agisce come:", "Acts as:"),
                    modifier = Modifier.weight(1f),
                )
                Box {
                    var expanded by remember { mutableStateOf(false) }
                    OutlinedButton(onClick = { expanded = true }) {
                        Text(draft.innerType.shortName)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        // Ogni guardiano tranne la Vedetta stessa (niente Vedetta dentro Vedetta).
                        TimerType.entries
                            .filter { it != TimerType.VEDETTA && !it.configuredFromHub }
                            .forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.displayName) },
                                    onClick = {
                                        onDraftChange(draft.copy(innerType = option))
                                        expanded = false
                                    },
                                )
                            }
                    }
                }
            }
            LocationField(draft = draft, onDraftChange = onDraftChange)
        }

        when (configType) {
            TimerType.SENTINELLA -> {
                DurationField(
                    label = tr("Uso continuo consentito", "Allowed continuous use"),
                    value = draft.limitValue,
                    unit = draft.limitUnit,
                    onValueChange = { onDraftChange(draft.copy(limitValue = it)) },
                    onUnitChange = { onDraftChange(draft.copy(limitUnit = it)) },
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    DurationField(
                        label = tr("Pausa per rientrare", "Break before re-entry"),
                        value = draft.resetValue,
                        unit = draft.resetUnit,
                        onValueChange = { onDraftChange(draft.copy(resetValue = it)) },
                        onUnitChange = { onDraftChange(draft.copy(resetUnit = it)) },
                    )
                    Text(
                        tr(
                            "Quando la Sentinella ti butta fuori devi aspettare questo tempo " +
                                "prima di poter rientrare. Con 0 (o lasciando vuoto) non c'è " +
                                "pausa obbligatoria: vieni buttato fuori ma puoi rientrare subito.",
                            "When the Sentinel kicks you out you must wait this long before " +
                                "coming back. With 0 (or empty) there is no mandatory break: " +
                                "you get kicked out but can come right back.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            TimerType.GUARDIANO -> {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DurationField(
                        label = when (draft.resetCycle) {
                            ResetCycle.WEEKLY ->
                                tr("Tempo totale a settimana", "Total time per week")
                            ResetCycle.MONTHLY ->
                                tr("Tempo totale al mese", "Total time per month")
                            else -> tr("Tempo totale al giorno", "Total time per day")
                        },
                        value = draft.limitValue,
                        unit = draft.limitUnit,
                        onValueChange = { onDraftChange(draft.copy(limitValue = it)) },
                        onUnitChange = { onDraftChange(draft.copy(limitUnit = it)) },
                    )
                    // Opzioni avanzate: ciclo di azzeramento del limite.
                    var advanced by remember(draft.id) {
                        mutableStateOf(draft.resetCycle != ResetCycle.DAILY)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(tr("Opzioni avanzate", "Advanced options"))
                            Text(
                                tr(
                                    "Scegli ogni quanto si azzera il limite",
                                    "Choose how often the limit resets",
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = advanced,
                            onCheckedChange = { on ->
                                advanced = on
                                // Spegnendo le opzioni si torna al ciclo giornaliero.
                                if (!on) onDraftChange(draft.copy(resetCycle = ResetCycle.DAILY))
                            },
                        )
                    }
                    if (advanced) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                tr("Ciclo del limite:", "Limit cycle:"),
                                modifier = Modifier.weight(1f),
                            )
                            Box {
                                var expanded by remember { mutableStateOf(false) }
                                OutlinedButton(onClick = { expanded = true }) {
                                    Text(draft.resetCycle.displayName)
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                                DropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false },
                                ) {
                                    ResetCycle.entries.forEach { cycle ->
                                        DropdownMenuItem(
                                            text = { Text(cycle.displayName) },
                                            onClick = {
                                                onDraftChange(draft.copy(resetCycle = cycle))
                                                expanded = false
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        Text(
                            tr(
                                "Settimanale: il conteggio (e l'eventuale blocco) si azzera " +
                                    "ogni lunedì. Mensile: il primo del mese.",
                                "Weekly: the count (and any block) resets every Monday. " +
                                    "Monthly: on the first of the month.",
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            TimerType.CUSTODE -> {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        tr("Orario protetto", "Protected hours"),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TimeOfDayPicker(
                            label = tr("Dalle", "From"),
                            minuteOfDay = draft.startTime,
                            onChange = { onDraftChange(draft.copy(startTime = it)) },
                            modifier = Modifier.weight(1f),
                        )
                        TimeOfDayPicker(
                            label = tr("Alle", "To"),
                            minuteOfDay = draft.endTime,
                            onChange = { onDraftChange(draft.copy(endTime = it)) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Text(
                        tr(
                            "In questa fascia oraria le app sorvegliate non si possono usare. " +
                                "Va bene anche a cavallo della notte (es. dalle 22:00 alle 07:00).",
                            "During these hours the watched apps cannot be used. " +
                                "Overnight ranges work too (e.g. 22:00 to 07:00).",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            TimerType.GENDARME -> {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = draft.opensValue,
                        onValueChange = { value ->
                            onDraftChange(draft.copy(opensValue = value.filter { it.isDigit() }))
                        },
                        label = {
                            Text(tr("Aperture al giorno (0 = no)", "Opens per day (0 = off)"))
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        tr(
                            "Conta le volte, non il tempo: con 5, alla sesta apertura " +
                                "l'app è bloccata fino a domani.",
                            "Counts opens, not time: with 5, the sixth open blocks " +
                                "the app until tomorrow.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = draft.reopenCooldownValue,
                        onValueChange = { value ->
                            onDraftChange(draft.copy(reopenCooldownValue = value.filter { it.isDigit() }))
                        },
                        label = {
                            Text(tr("Cooldown di riapertura (min, 0 = no)", "Reopen cooldown (min, 0 = off)"))
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        tr(
                            "Dopo aver chiuso l'app, non potrai riaprirla per questi minuti.",
                            "After you close the app, you can't reopen it for these minutes.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = draft.notifyAfterOpensValue,
                        onValueChange = { value ->
                            onDraftChange(draft.copy(notifyAfterOpensValue = value.filter { it.isDigit() }))
                        },
                        label = {
                            Text(tr("Avvisami dopo N aperture (0 = no)", "Notify after N opens (0 = off)"))
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        tr(
                            "Ti arriva una notifica quando raggiungi questo numero di aperture.",
                            "You get a notification when you reach this number of opens.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            TimerType.MESSAGGERO -> MessengerConfig(draft, onDraftChange)

            TimerType.ARALDO -> {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // ------------------------------------------ fase mattutina
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(tr("Fase mattutina", "Morning phase"))
                            Text(
                                tr(
                                    "Blocca le app dal momento del vero risveglio",
                                    "Blocks the apps from the moment you really wake up",
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = draft.araldoMorning,
                            onCheckedChange = { onDraftChange(draft.copy(araldoMorning = it)) },
                        )
                    }
                    if (draft.araldoMorning) {
                        DurationField(
                            label = tr("Protezione dal risveglio", "Protection from wake-up"),
                            value = draft.limitValue,
                            unit = draft.limitUnit,
                            onValueChange = { onDraftChange(draft.copy(limitValue = it)) },
                            onUnitChange = { onDraftChange(draft.copy(limitUnit = it)) },
                        )
                        Text(
                            tr("Finestra del mattino", "Morning window"),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TimeOfDayPicker(
                                label = tr("Dalle", "From"),
                                minuteOfDay = draft.startTime ?: (5 * 60),
                                onChange = { onDraftChange(draft.copy(startTime = it)) },
                                modifier = Modifier.weight(1f),
                            )
                            TimeOfDayPicker(
                                label = tr("Alle", "To"),
                                minuteOfDay = draft.endTime ?: (12 * 60),
                                onChange = { onDraftChange(draft.copy(endTime = it)) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Text(
                            tr(
                                "Conta come risveglio solo uno schermo rimasto spento " +
                                    "almeno 4 ore, riacceso dentro questa finestra. " +
                                    "Sblocchi notturni e pisolini non attivano l'Araldo.",
                                "Only a screen that stayed off for at least 4 hours, " +
                                    "turned back on inside this window, counts as waking " +
                                    "up. Night checks and naps don't trigger the Herald.",
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // -------------------------------------------- fase serale
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(tr("Fase serale", "Evening phase"))
                            Text(
                                tr(
                                    "Impara quando vai a dormire e ti anticipa",
                                    "Learns your bedtime and gets there first",
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = draft.araldoEvening,
                            onCheckedChange = { onDraftChange(draft.copy(araldoEvening = it)) },
                        )
                    }
                    if (draft.araldoEvening) {
                        DurationField(
                            label = tr("Anticipo sulla nanna", "Head start on bedtime"),
                            value = draft.resetValue,
                            unit = draft.resetUnit,
                            onValueChange = { onDraftChange(draft.copy(resetValue = it)) },
                            onUnitChange = { onDraftChange(draft.copy(resetUnit = it)) },
                        )
                        Text(
                            tr(
                                "L'Araldo stima l'ora in cui ti addormenti di solito " +
                                    "(la mediana delle ultime due settimane) e blocca le " +
                                    "app da questo anticipo prima, fino alle 04:00. " +
                                    "Servono almeno 3 notti di osservazione per partire.",
                                "The Herald estimates when you usually fall asleep " +
                                    "(the median of the last two weeks) and blocks the " +
                                    "apps from this long before, until 04:00. It needs " +
                                    "at least 3 observed nights to start.",
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            TimerType.ESATTORE -> {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    DurationField(
                        label = tr("Pedaggio d'ingresso", "Entry toll"),
                        value = draft.limitValue,
                        unit = draft.limitUnit,
                        onValueChange = { onDraftChange(draft.copy(limitValue = it)) },
                        onUnitChange = { onDraftChange(draft.copy(limitUnit = it)) },
                    )
                    Text(
                        tr(
                            "A ogni apertura dell'app compare una schermata di respiro " +
                                "con questo conto alla rovescia (consigliati 30-60 secondi): " +
                                "puoi entrare solo alla fine, oppure lasciar perdere.",
                            "Every time you open the app, a breathing screen appears with " +
                                "this countdown (30-60 seconds recommended): you can only " +
                                "enter when it ends, or just let it go.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    DurationField(
                        label = tr("Tempo di riattivazione", "Reactivation time"),
                        value = draft.resetValue,
                        unit = draft.resetUnit,
                        onValueChange = { onDraftChange(draft.copy(resetValue = it)) },
                        onUnitChange = { onDraftChange(draft.copy(resetUnit = it)) },
                    )
                    Text(
                        tr(
                            "Con 0 (o vuoto) il pedaggio si ripresenta a OGNI rientro, " +
                                "anche dopo un secondo. Con un valore (es. 10 secondi) " +
                                "puoi uscire e rientrare entro quella finestra senza " +
                                "ripagare. Se tocchi \"lascia perdere\", al prossimo " +
                                "tentativo il pedaggio c'è comunque.",
                            "With 0 (or empty) the toll comes back at EVERY re-entry, " +
                                "even after one second. With a value (e.g. 10 seconds) " +
                                "you can leave and come back within that window without " +
                                "paying again. If you tap \"never mind\", the toll shows " +
                                "up anyway on your next attempt.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // L'innerType di una Vedetta non è mai a sua volta una Vedetta.
            TimerType.VEDETTA -> Unit
        }

        // Notifica di preavviso prima del blocco: non serve al Gendarme, al
        // Messaggero (È già fatto di avvisi), all'Esattore (non blocca) né
        // all'Araldo (al mattino stai ancora dormendo).
        if (configType != TimerType.GENDARME && configType != TimerType.MESSAGGERO &&
            configType != TimerType.ESATTORE && configType != TimerType.ARALDO
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("Avvisami prima del blocco", "Warn me before the block"))
                        Text(
                            tr(
                                "Una notifica ti avverte poco prima che il guardiano chiuda l'app",
                                "A notification warns you shortly before the guardian closes the app",
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = draft.warnEnabled,
                        onCheckedChange = { onDraftChange(draft.copy(warnEnabled = it)) },
                    )
                }
                if (draft.warnEnabled) {
                    DurationField(
                        label = tr("Quanto tempo prima", "How long before"),
                        value = draft.warnValue,
                        unit = draft.warnUnit,
                        onValueChange = { onDraftChange(draft.copy(warnValue = it)) },
                        onUnitChange = { onDraftChange(draft.copy(warnUnit = it)) },
                    )
                    // PIÙ preavvisi (5): quelli aggiunti col "+", eliminabili.
                    draft.extraWarnsMs.sortedDescending().forEach { ms ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                tr(
                                    "Avviso anche ${formatMs(ms)} prima",
                                    "Also warn ${formatMs(ms)} before",
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = {
                                    onDraftChange(
                                        draft.copy(extraWarnsMs = draft.extraWarnsMs - ms),
                                    )
                                },
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = tr("Rimuovi", "Remove"),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    // Il "+" per aggiungere un altro timer di preavviso.
                    var addingWarn by remember { mutableStateOf(false) }
                    if (!addingWarn) {
                        TextButton(onClick = { addingWarn = true }) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(tr("Aggiungi un altro avviso", "Add another warning"))
                        }
                    } else {
                        var extraValue by remember { mutableStateOf("") }
                        var extraUnit by remember { mutableStateOf(TimeUnit.MINUTES) }
                        DurationField(
                            label = tr("Altro avviso, quanto prima", "Another warning, how long before"),
                            value = extraValue,
                            unit = extraUnit,
                            onValueChange = { extraValue = it },
                            onUnitChange = { extraUnit = it },
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = {
                                    val v = extraValue.toIntOrNull() ?: 0
                                    if (v > 0) {
                                        val ms = v * extraUnit.seconds * 1000L
                                        onDraftChange(
                                            draft.copy(
                                                extraWarnsMs =
                                                    (draft.extraWarnsMs + ms).distinct(),
                                            ),
                                        )
                                        addingWarn = false
                                    }
                                },
                            ) { Text(tr("Aggiungi", "Add")) }
                            TextButton(onClick = { addingWarn = false }) {
                                Text(tr("Annulla", "Cancel"))
                            }
                        }
                    }
                }
            }
        }

        Text(
            tr("App da sorvegliare", "Apps to watch"),
            style = MaterialTheme.typography.titleMedium,
        )
        if (!draft.allApps) {
            OutlinedButton(
                onClick = onPickApps,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (draft.packages.isEmpty()) {
                        tr("Scegli le app da sorvegliare…", "Choose the apps to watch…")
                    } else {
                        tr(
                            "${draft.packages.size} app selezionate — tocca per modificare",
                            "${draft.packages.size} apps selected — tap to change",
                        )
                    }
                )
            }
            // Le app SCELTE, a colpo d'occhio (7): iconcina + nome, piccole.
            if (draft.packages.isNotEmpty()) {
                val context = LocalContext.current
                val chosen = remember(draft.packages) {
                    val pm = context.packageManager
                    draft.packages.map { pkg ->
                        val label = try {
                            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                        } catch (_: Exception) {
                            pkg.substringAfterLast('.')
                        }
                        val icon = try {
                            pm.getApplicationIcon(pkg).toBitmap(48, 48).asImageBitmap()
                        } catch (_: Exception) {
                            null
                        }
                        label to icon
                    }.sortedBy { it.first.lowercase() }
                }
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    chosen.forEach { (label, icon) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(50),
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            if (icon != null) {
                                androidx.compose.foundation.Image(
                                    bitmap = icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(5.dp))
                            }
                            Text(
                                label,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.weight(1f)) {
                Text(tr("Tutto il telefono", "Whole phone"))
                Text(
                    tr(
                        "Vale per qualsiasi app (tranne telefonate e impostazioni)",
                        "Applies to any app (except calls and settings)",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = draft.allApps,
                onCheckedChange = { onDraftChange(draft.copy(allApps = it)) },
            )
        }

        val scope = rememberCoroutineScope()
        val shakeOffset = remember { Animatable(0f) }
        var triedToSave by remember { mutableStateOf(false) }
        val timer = draft.toTimer()

        // Conferma globale (Impostazioni): il salvataggio chiede l'ok.
        var confirmSave by remember { mutableStateOf<GuardianTimer?>(null) }
        confirmSave?.let { pending ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { confirmSave = null },
                title = { Text(tr("Salvare le modifiche?", "Save the changes?")) },
                text = {
                    Text(
                        tr(
                            "\"${pending.name}\" verrà salvato con le impostazioni scelte.",
                            "\"${pending.name}\" will be saved with the chosen settings.",
                        )
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        confirmSave = null
                        onSave(pending)
                    }) { Text(tr("Salva", "Save")) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmSave = null }) {
                        Text(tr("Annulla", "Cancel"))
                    }
                },
            )
        }

        Button(
            onClick = {
                if (timer != null) {
                    if (com.guardians.app.data.SettingsRepository.confirmActions.value) {
                        confirmSave = timer
                    } else {
                        onSave(timer)
                    }
                } else {
                    // Dati incompleti: l'avviso diventa rosso e si scuote.
                    triedToSave = true
                    scope.launch {
                        shakeOffset.snapTo(0f)
                        listOf(24f, -20f, 14f, -10f, 6f, 0f).forEach { x ->
                            shakeOffset.animateTo(x, tween(durationMillis = 55))
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text(tr("Salva", "Save"), fontWeight = FontWeight.Bold)
        }
        if (timer == null) {
            Text(
                buildString {
                    append(tr("Per salvare: ", "To save: "))
                    if (draft.type == TimerType.VEDETTA) {
                        append(tr("imposta il luogo e il raggio, ", "set the place and radius, "))
                    }
                    append(
                        when (configType) {
                            TimerType.CUSTODE -> tr("imposta gli orari", "set the hours")
                            TimerType.GENDARME ->
                                tr("imposta almeno un'opzione", "set at least one option")
                            TimerType.MESSAGGERO ->
                                tr("imposta il primo avviso e il ritmo", "set the first alert and pace")
                            TimerType.ESATTORE ->
                                tr("imposta il pedaggio d'ingresso", "set the entry toll")
                            TimerType.ARALDO -> tr(
                                "attiva almeno una fase e imposta la sua durata",
                                "enable at least one phase and set its duration",
                            )
                            else -> tr("imposta i tempi", "set the times")
                        }
                    )
                    append(
                        tr(
                            " e scegli almeno un'app (o attiva \"Tutto il telefono\").",
                            " and pick at least one app (or enable \"Whole phone\").",
                        )
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (triedToSave) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (triedToSave) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(shakeOffset.value.roundToInt(), 0) },
            )
        }

        if (draft.id != null) {
            TextButton(
                onClick = { onDelete(draft.id) },
                colors = ButtonDefaults.textButtonColors(contentColor = RedGuardiano),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(tr("Elimina timer", "Delete timer"))
            }
        }
    }
}

/**
 * Selettore di squadra: dropdown delle squadre esistenti (nessuna digitazione
 * per associarne una già creata) + voce per crearne una nuova al volo.
 */
@Composable
private fun TeamSelector(current: String, onSelect: (String) -> Unit) {
    val context = LocalContext.current
    com.guardians.app.data.TeamsRepository.load(context)
    val customTeams by com.guardians.app.data.TeamsRepository.teams.collectAsState()
    val timers by com.guardians.app.data.TimerRepository.timers.collectAsState()
    // "Squadra Generale" è la chiave di default e non si traduce.
    val existing = (customTeams + timers.map { it.teamName } + "Squadra Generale")
        .distinct()
        .sorted()

    var newTeamDialog by remember { mutableStateOf(false) }
    if (newTeamDialog) {
        var name by remember { mutableStateOf("") }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { newTeamDialog = false },
            title = { Text(tr("Nuova squadra", "New team")) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(tr("Nome squadra", "Team name")) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        com.guardians.app.data.TeamsRepository.add(context, name.trim())
                        onSelect(name.trim())
                        newTeamDialog = false
                    },
                ) { Text(tr("Crea", "Create")) }
            },
            dismissButton = {
                TextButton(onClick = { newTeamDialog = false }) { Text(tr("Annulla", "Cancel")) }
            },
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(tr("Squadra", "Team"), style = MaterialTheme.typography.titleSmall)
        Box {
            var expanded by remember { mutableStateOf(false) }
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    current.trim().ifBlank { "Squadra Generale" },
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                existing.forEach { team ->
                    DropdownMenuItem(
                        text = { Text(team) },
                        onClick = {
                            onSelect(if (team == "Squadra Generale") "" else team)
                            expanded = false
                        },
                    )
                }
                DropdownMenuItem(
                    text = {
                        Text(
                            tr("Nuova squadra…", "New team…"),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
                    onClick = {
                        expanded = false
                        newTeamDialog = true
                    },
                )
            }
        }
        Text(
            tr(
                "Le squadre raggruppano i guardiani e si possono sospendere " +
                    "con l'Ombra o pianificare per giorni.",
                "Teams group guardians and can be suspended with the Shadow " +
                    "or scheduled by day.",
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Selettore di orario: apre il TimePickerDialog NATIVO di Android (24 ore).
 * I vecchi menù a tendina non permettevano di scorrere oltre le 22 su
 * alcuni telefoni: il dialog di sistema non ha limiti di scorrimento.
 */
@Composable
fun TimeOfDayPicker(
    label: String,
    minuteOfDay: Int?,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        OutlinedButton(
            onClick = {
                android.app.TimePickerDialog(
                    context,
                    { _, hour, minute -> onChange(hour * 60 + minute) },
                    (minuteOfDay ?: 8 * 60) / 60,
                    (minuteOfDay ?: 8 * 60) % 60,
                    true, // sempre formato 24 ore
                ).show()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                minuteOfDay?.let { "%02d:%02d".format(it / 60, it % 60) } ?: "--:--",
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** Configurazione del Messaggero: soglia, ritmo, tetto avvisi e messaggi propri. */
@Composable
private fun MessengerConfig(
    draft: TimerDraft,
    onDraftChange: (TimerDraft) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DurationField(
            label = tr("Primo avviso dopo (uso continuo)", "First alert after (continuous use)"),
            value = draft.limitValue,
            unit = draft.limitUnit,
            onValueChange = { onDraftChange(draft.copy(limitValue = it)) },
            onUnitChange = { onDraftChange(draft.copy(limitUnit = it)) },
        )

        // Insistenza: Programmabile (fissa) oppure decrescente.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(tr("Insistenza:", "Insistence:"), modifier = Modifier.weight(1f))
            Box {
                var expanded by remember { mutableStateOf(false) }
                OutlinedButton(onClick = { expanded = true }) {
                    Text(draft.pace.displayName)
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    com.guardians.app.model.MessengerPace.entries.forEach { p ->
                        DropdownMenuItem(
                            text = { Text(p.displayName) },
                            onClick = {
                                onDraftChange(draft.copy(pace = p))
                                expanded = false
                            },
                        )
                    }
                }
            }
        }

        if (draft.pace == com.guardians.app.model.MessengerPace.PROGRAMMABILE) {
            DurationField(
                label = tr("Ogni", "Every"),
                value = draft.resetValue,
                unit = draft.resetUnit,
                onValueChange = { onDraftChange(draft.copy(resetValue = it)) },
                onUnitChange = { onDraftChange(draft.copy(resetUnit = it)) },
            )
            Text(
                tr(
                    "Avvisi a cadenza fissa finché resti nell'app.",
                    "Alerts at a fixed pace while you stay in the app.",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                tr(
                    "Gli avvisi si fanno via via più frequenti (fino a un minimo di 30 " +
                        "secondi), poi restano costanti: insistente ma non estenuante.",
                    "Alerts get more and more frequent (down to a 30-second minimum), " +
                        "then stay constant: insistent but not exhausting.",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        OutlinedTextField(
            value = draft.maxNoticesValue,
            onValueChange = { v ->
                onDraftChange(draft.copy(maxNoticesValue = v.filter { it.isDigit() }))
            },
            label = { Text(tr("Smetti dopo (avvisi, 0 = mai)", "Stop after (alerts, 0 = never)")) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        // Messaggi personalizzati (facoltativi): se vuoti, ne usa di predefiniti
        // a tono crescente.
        Text(
            tr("Messaggi personalizzati (facoltativi)", "Custom messages (optional)"),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            tr(
                "Se ne scrivi più di uno, diventano più fermi mano a mano.",
                "If you write more than one, they get firmer over time.",
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        var newMsg by remember { mutableStateOf("") }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                value = newMsg,
                onValueChange = { newMsg = it },
                label = { Text(tr("Nuovo messaggio", "New message")) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = {
                if (newMsg.isNotBlank()) {
                    onDraftChange(draft.copy(messages = draft.messages + newMsg.trim()))
                    newMsg = ""
                }
            }) { Text(tr("Aggiungi", "Add")) }
        }
        draft.messages.forEach { msg ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("• $msg", modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    onDraftChange(draft.copy(messages = draft.messages - msg))
                }) { Text(tr("Togli", "Remove")) }
            }
        }
    }
}

/** Cattura la posizione attuale (con permesso) e imposta il raggio in km. */
@Composable
private fun LocationField(
    draft: TimerDraft,
    onDraftChange: (TimerDraft) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var address by remember { mutableStateOf("") }

    // Cerca un luogo per indirizzo/nome (geocoding), su thread di sfondo.
    fun searchAddress() {
        if (address.isBlank()) return
        status = tr("Ricerca in corso…", "Searching…")
        scope.launch {
            val found = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    @Suppress("DEPRECATION")
                    android.location.Geocoder(context)
                        .getFromLocationName(address, 1)
                        ?.firstOrNull()
                } catch (_: Exception) {
                    null
                }
            }
            if (found != null) {
                onDraftChange(draft.copy(latitude = found.latitude, longitude = found.longitude))
                status = tr("Luogo trovato ✓", "Place found ✓")
            } else {
                status = tr(
                    "Luogo non trovato (serve la connessione). Riprova.",
                    "Place not found (needs internet). Try again.",
                )
            }
        }
    }

    fun readLocation() {
        try {
            val lm = context.getSystemService(android.content.Context.LOCATION_SERVICE)
                as android.location.LocationManager
            var best: android.location.Location? = null
            for (p in lm.getProviders(true)) {
                @Suppress("MissingPermission")
                val loc = lm.getLastKnownLocation(p) ?: continue
                if (best == null || loc.time > best!!.time) best = loc
            }
            if (best != null) {
                onDraftChange(draft.copy(latitude = best!!.latitude, longitude = best!!.longitude))
                status = tr("Posizione salvata ✓", "Location saved ✓")
            } else {
                status = tr(
                    "Posizione non disponibile: attiva il GPS e riprova.",
                    "Location not available: turn on GPS and try again.",
                )
            }
        } catch (_: Exception) {
            status = tr("Impossibile leggere la posizione.", "Could not read the location.")
        }
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) readLocation()
        else status = tr("Permesso posizione negato.", "Location permission denied.")
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(tr("Luogo della Vedetta", "Lookout's place"), style = MaterialTheme.typography.titleSmall)
        OutlinedButton(
            onClick = {
                val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.ACCESS_FINE_LOCATION
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (granted) readLocation()
                else permLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (draft.hasLocation) {
                    tr("Aggiorna alla posizione attuale", "Update to current location")
                } else {
                    tr("Usa la posizione attuale", "Use current location")
                }
            )
        }
        // In alternativa: cerca un altro luogo per indirizzo/nome.
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                label = { Text(tr("…o cerca un indirizzo", "…or search an address")) },
                placeholder = { Text(tr("Es. Via Roma 1, Milano", "E.g. 1 Main St, London")) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = { searchAddress() }) {
                Text(tr("Cerca", "Search"))
            }
        }
        if (draft.hasLocation) {
            Text(
                tr("Punto: ", "Point: ") +
                    "%.4f, %.4f".format(draft.latitude, draft.longitude),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        status?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        OutlinedTextField(
            value = draft.radiusKm,
            onValueChange = { v ->
                onDraftChange(draft.copy(radiusKm = v.filter { it.isDigit() || it == '.' }))
            },
            label = { Text(tr("Raggio (km)", "Radius (km)")) },
            supportingText = {
                Text(
                    tr(
                        "Entro questa distanza dal punto il potere è attivo (es. 0.2 = 200 m).",
                        "Within this distance from the point the power is active (e.g. 0.2 = 200 m).",
                    )
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun TypeCard(
    type: TimerType,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = Color(type.colorArgb)
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (selected) BorderStroke(2.dp, accent) else null,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp),
        ) {
            TimerShapeIcon(type, Modifier.size(32.dp))
            Spacer(Modifier.width(16.dp))
            Column {
                Text(type.displayName, fontWeight = FontWeight.Bold)
                Text(
                    type.tagline,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
