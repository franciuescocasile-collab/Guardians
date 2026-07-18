package com.guardians.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.guardians.app.data.NotifierRepository
import com.guardians.app.data.tr
import com.guardians.app.model.formatMs
import com.guardians.app.model.formatTimeOfDay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Il Notificatore — promemoria USA E GETTA. Scorciatoie rapide (+15m/+30m/+1h)
 * o orario preciso; la notifica scatta una sola volta e si auto-distrugge.
 * Un filtro notturno silenzia gli avvisi durante le ore scelte.
 */
@Composable
fun NotifierScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    NotifierRepository.load(context)
    val reminders by NotifierRepository.reminders.collectAsState()

    var text by remember { mutableStateOf("") }

    fun add(fireAt: Long) {
        val t = text.ifBlank { tr("Promemoria", "Reminder") }
        NotifierRepository.schedule(context, t, fireAt)
        text = ""
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
        // Il filtro notturno vive nel menù a 3 puntini in alto a destra (22).
        var showNightFilter by remember { mutableStateOf(false) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
            }
            Icon(
                Icons.Default.Alarm,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                tr("Il Notificatore", "The Notifier"),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Box {
                var menu by remember { mutableStateOf(false) }
                IconButton(onClick = { menu = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = tr("Altre opzioni", "More options"),
                    )
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(tr("Filtro notturno", "Night filter")) },
                        onClick = {
                            menu = false
                            showNightFilter = true
                        },
                    )
                }
            }
        }

        if (showNightFilter) {
            val nightOn by NotifierRepository.nightFilter.collectAsState()
            val nightFrom by NotifierRepository.nightFrom.collectAsState()
            val nightTo by NotifierRepository.nightTo.collectAsState()
            AlertDialog(
                onDismissRequest = { showNightFilter = false },
                title = { Text(tr("Filtro notturno", "Night filter")) },
                text = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                tr(
                                    "Durante queste ore i promemoria arrivano in silenzio",
                                    "During these hours reminders arrive silently",
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            Switch(
                                checked = nightOn,
                                onCheckedChange = {
                                    NotifierRepository.setNightFilter(context, it)
                                },
                            )
                        }
                        if (nightOn) {
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                TimeOfDayPicker(
                                    label = tr("Dalle", "From"),
                                    minuteOfDay = nightFrom,
                                    onChange = { NotifierRepository.setNightFrom(context, it) },
                                    modifier = Modifier.weight(1f),
                                )
                                TimeOfDayPicker(
                                    label = tr("Alle", "To"),
                                    minuteOfDay = nightTo,
                                    onChange = { NotifierRepository.setNightTo(context, it) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showNightFilter = false }) {
                        Text(tr("Chiudi", "Close"))
                    }
                },
            )
        }
        Text(
            tr(
                "Promemoria una-tantum (scatta e sparisce) o ricorrenti (si " +
                    "ripetono a intervalli, es. bevi acqua ogni 2 ore).",
                "One-shot reminders (fire and vanish) or recurring ones (repeat " +
                    "at intervals, e.g. drink water every 2 hours).",
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // ------------------------------------------------ nuovo promemoria
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(tr("Nuovo promemoria", "New reminder"), fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(tr("Testo (facoltativo)", "Text (optional)")) },
                    placeholder = { Text(tr("Es. Bevi un bicchiere d'acqua", "E.g. Drink a glass of water")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    tr("Scorciatoie", "Shortcuts"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 30, 60).forEach { min ->
                        OutlinedButton(
                            onClick = { add(System.currentTimeMillis() + min * 60_000L) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                tr("tra ", "in ") +
                                    if (min < 60) "$min min" else tr("1 ora", "1 hour"),
                            )
                        }
                    }
                }
                // Orario preciso: apre il TimePicker nativo e programma per oggi
                // (o domani se l'orario è già passato).
                OutlinedButton(
                    onClick = {
                        android.app.TimePickerDialog(
                            context,
                            { _, h, m ->
                                var when0 = LocalDate.now().atTime(h, m)
                                if (when0.isBefore(LocalDateTime.now())) {
                                    when0 = when0.plusDays(1)
                                }
                                add(when0.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
                            },
                            8, 0, true,
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(tr("Scegli un orario preciso…", "Pick an exact time…"))
                }

                // ------------------------------- promemoria RICORRENTE (10)
                androidx.compose.material3.HorizontalDivider(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f),
                )
                Text(
                    tr("Ripeti a intervalli", "Repeat at intervals"),
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    tr(
                        "Es. \"bevi acqua\" ogni 2 ore: parte adesso e si ripete da " +
                            "solo, senza impostare ogni volta.",
                        "E.g. \"drink water\" every 2 hours: starts now and repeats " +
                            "by itself, no need to set it each time.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                var everyValue by remember { mutableStateOf("2") }
                var everyHours by remember { mutableStateOf(true) }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = everyValue,
                        onValueChange = { v -> everyValue = v.filter { it.isDigit() }.take(3) },
                        label = { Text(tr("Ogni", "Every")) },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                        ),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    DropdownPickerButton(
                        text = if (everyHours) tr("ore", "hours") else tr("minuti", "minutes"),
                        options = listOf(tr("ore", "hours"), tr("minuti", "minutes")),
                        onSelected = { i -> everyHours = i == 0 },
                        modifier = Modifier.weight(1f),
                    )
                }
                Button(
                    enabled = (everyValue.toIntOrNull() ?: 0) > 0,
                    onClick = {
                        val n = everyValue.toInt()
                        val interval = if (everyHours) n * 60 else n
                        val txt = text.ifBlank { tr("Promemoria", "Reminder") }
                        NotifierRepository.schedule(
                            context,
                            txt,
                            System.currentTimeMillis() + interval * 60_000L,
                            intervalMin = interval,
                        )
                        text = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(tr("Attiva promemoria ricorrente", "Start recurring reminder"))
                }
            }
        }

        // ------------------------------------------------------- in programma
        if (reminders.isEmpty()) {
            Text(
                tr("Nessun promemoria in programma.", "No reminders scheduled."),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                tr("In programma", "Scheduled"),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        reminders.forEach { r ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(r.text, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f, false))
                            if (r.recurring) {
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    Icons.Default.Repeat,
                                    contentDescription = tr("Ricorrente", "Recurring"),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                        val remaining = (r.fireAt - System.currentTimeMillis()).coerceAtLeast(0L)
                        val at = java.time.Instant.ofEpochMilli(r.fireAt)
                            .atZone(ZoneId.systemDefault()).toLocalTime()
                        Text(
                            if (r.recurring) {
                                tr(
                                    "ogni ${formatMs(r.intervalMin * 60_000L)} · prossimo tra ${formatMs(remaining)}",
                                    "every ${formatMs(r.intervalMin * 60_000L)} · next in ${formatMs(remaining)}",
                                )
                            } else {
                                tr(
                                    "tra ${formatMs(remaining)} · alle ${formatTimeOfDay(at.hour * 60 + at.minute)}",
                                    "in ${formatMs(remaining)} · at ${formatTimeOfDay(at.hour * 60 + at.minute)}",
                                )
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { NotifierRepository.cancel(context, r.id) }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Elimina",
                            tint = com.guardians.app.ui.theme.RedGuardiano,
                        )
                    }
                }
            }
        }
    }
}
