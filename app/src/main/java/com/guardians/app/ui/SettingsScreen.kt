package com.guardians.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.guardians.app.data.BackupManager
import com.guardians.app.data.SettingsRepository
import com.guardians.app.data.tr

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenExclusions: () -> Unit,
    onOpenSigillo: () -> Unit,
    onOpenTravel: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenBattery: () -> Unit,
) {
    val context = LocalContext.current
    val darkTheme by SettingsRepository.darkTheme.collectAsState()
    val english by SettingsRepository.english.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
            }
            Text(
                tr("Impostazioni", "Settings"),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }

        // -------------------------------- Notifiche (in una pagina dedicata)
        Card(
            onClick = onOpenNotifications,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Icon(
                    Icons.Default.Notifications,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(tr("Notifiche", "Notifications"), fontWeight = FontWeight.Bold)
                    Text(
                        tr(
                            "Monitoraggio, avvisi, vibrazione, suono e resoconto",
                            "Monitoring, alerts, vibration, sound and report",
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

        // ---------------------------------- Batteria (in una pagina dedicata)
        Card(
            onClick = onOpenBattery,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Icon(
                    Icons.Default.BatteryChargingFull,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(tr("Batteria", "Battery"), fontWeight = FontWeight.Bold)
                    Text(
                        tr(
                            "Risparmio batteria e \"tienimi sempre attivo\"",
                            "Battery saver and \"keep me always running\"",
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

        // -------------------------------------------------------- tema scuro
        SettingRow(
            title = tr("Tema scuro", "Dark theme"),
            description = tr(
                "Spegnilo per il tema chiaro (bianco e blu)",
                "Turn off for the light theme (white and blue)",
            ),
            checked = darkTheme,
            onCheckedChange = { SettingsRepository.setDarkTheme(context, it) },
        )

        // Lingua con menù a tendina.
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Lingua", "Language"), fontWeight = FontWeight.Bold)
                    Text(
                        tr("La lingua dell'app", "The app language"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box {
                    var expanded by remember { mutableStateOf(false) }
                    OutlinedButton(onClick = { expanded = true }) {
                        Text(if (english) "English" else "Italiano")
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Italiano") },
                            onClick = {
                                SettingsRepository.setEnglish(context, false)
                                expanded = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("English") },
                            onClick = {
                                SettingsRepository.setEnglish(context, true)
                                expanded = false
                            },
                        )
                    }
                }
            }
        }

        // -------------------------------------------------- conferma globale
        val confirmActions by SettingsRepository.confirmActions.collectAsState()
        SettingRow(
            title = tr("Richiedi conferma per modifiche e switch", "Ask to confirm changes and switches"),
            description = tr(
                "Attiva se vuoi più sicurezza nel salvataggio o disattiva per " +
                    "meno passaggi e più velocità in-app.",
                "Turn on for more safety when saving, or off for fewer steps " +
                    "and faster in-app actions.",
            ),
            checked = confirmActions,
            onCheckedChange = { SettingsRepository.setConfirmActions(context, it) },
        )

        // ----------------------------------------------- inizio del giorno
        val dayStart by SettingsRepository.dayStartMinute.collectAsState()
        com.guardians.app.data.SealRepository.waitReadyAt.collectAsState().value
        val daySealed = !com.guardians.app.data.SealRepository.canEditNow() &&
            com.guardians.app.data.SealRepository.delayMs.collectAsState().value > 0L
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Inizio del giorno", "Start of the day"), fontWeight = FontWeight.Bold)
                    Text(
                        tr(
                            "L'orario in cui si azzerano i conteggi giornalieri " +
                                "(es. 04:00 per chi va a letto tardi). Protetto dal Sigillo.",
                            "The time when the daily counters reset (e.g. 04:00 for " +
                                "night owls). Protected by the Seal.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (daySealed) {
                        Text(
                            tr(
                                "Sigillo attivo: attendi la fine del countdown.",
                                "Seal active: wait for the countdown.",
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    enabled = !daySealed,
                    onClick = {
                        android.app.TimePickerDialog(
                            context,
                            { _, hour, minute ->
                                SettingsRepository.setDayStartMinute(context, hour * 60 + minute)
                            },
                            dayStart / 60,
                            dayStart % 60,
                            true, // formato 24 ore
                        ).show()
                    },
                ) {
                    Text(
                        "%02d:%02d".format(dayStart / 60, dayStart % 60),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        // ------------------------------------- primo giorno della settimana
        val weekStartMonday by SettingsRepository.weekStartMonday.collectAsState()
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        tr("Primo giorno della settimana", "First day of the week"),
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        tr(
                            "Da dove parte la settimana nei grafici e nei conteggi",
                            "Where the week starts in charts and counters",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box {
                    var expanded by remember { mutableStateOf(false) }
                    OutlinedButton(onClick = { expanded = true }) {
                        Text(if (weekStartMonday) tr("Lunedì", "Monday") else tr("Domenica", "Sunday"))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(
                            text = { Text(tr("Lunedì", "Monday")) },
                            onClick = {
                                SettingsRepository.setWeekStartMonday(context, true)
                                expanded = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(tr("Domenica", "Sunday")) },
                            onClick = {
                                SettingsRepository.setWeekStartMonday(context, false)
                                expanded = false
                            },
                        )
                    }
                }
            }
        }

        // ------------------------------------------------------------ sigillo
        val sealDelay by com.guardians.app.data.SealRepository.delayMs.collectAsState()
        Card(
            onClick = onOpenSigillo,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(tr("Sigillo", "Seal"), fontWeight = FontWeight.Bold)
                    Text(
                        if (sealDelay > 0L) {
                            tr("Attesa per le modifiche: ", "Edit wait: ") +
                                com.guardians.app.model.formatMs(sealDelay)
                        } else {
                            tr(
                                "Nessun ritardo impostato alle modifiche",
                                "No change delay set",
                            )
                        },
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

        // --------------------------------------------------- modalità viaggio
        com.guardians.app.data.TravelRepository.load(context)
        val travelUntil by com.guardians.app.data.TravelRepository.activeUntil.collectAsState()
        val travelActive = System.currentTimeMillis() < travelUntil
        Card(
            onClick = onOpenTravel,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Icon(
                    Icons.Default.Flight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(tr("Modalità Viaggio", "Travel Mode"), fontWeight = FontWeight.Bold)
                    Text(
                        if (travelActive) {
                            tr("In corso · guardiani in pausa", "Active · guardians paused")
                        } else {
                            tr(
                                "Metti in pausa i guardiani in trasferta con pochi tocchi",
                                "Pause the guardians while travelling in a couple of taps",
                            )
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (travelActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // -------------------------------------------------------- app escluse
        Card(
            onClick = onOpenExclusions,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Icon(
                    Icons.Default.Block,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(tr("App escluse", "Excluded apps"), fontWeight = FontWeight.Bold)
                    Text(
                        tr(
                            "App che nessun guardiano può bloccare (telefonate e " +
                                "impostazioni sono sempre al sicuro comunque)",
                            "Apps no guardian can ever block (calls and settings " +
                                "are always safe anyway)",
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

        // ------------------------------------------------ backup e ripristino
        var importResult by remember { mutableStateOf<String?>(null) }
        val exportLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
        ) { uri ->
            if (uri != null) {
                importResult = try {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(BackupManager.exportJson(context).toByteArray())
                    }
                    tr("Configurazione salvata!", "Configuration saved!")
                } catch (_: Exception) {
                    tr("Errore nel salvataggio del file.", "Error saving the file.")
                }
            }
        }
        val importLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                importResult = try {
                    val raw = context.contentResolver.openInputStream(uri)
                        ?.bufferedReader()?.use { it.readText() } ?: ""
                    BackupManager.import(context, raw)
                    tr("Configurazione ripristinata!", "Configuration restored!")
                } catch (_: Exception) {
                    tr(
                        "File non valido: nessuna modifica applicata.",
                        "Invalid file: no changes applied.",
                    )
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    tr("Backup della configurazione", "Configuration backup"),
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    tr(
                        "Salva in un file guardiani, squadre, app escluse, obiettivi, " +
                            "storico d'uso e statistiche: per cambiare telefono o " +
                            "reinstallare senza perdere nulla.",
                        "Save guardians, teams, excluded apps, goals, usage history " +
                            "and statistics to a file: switch phones or reinstall " +
                            "without losing anything.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = { exportLauncher.launch("guardians-backup.json") }) {
                        Text(tr("Esporta", "Export"))
                    }
                    TextButton(
                        onClick = {
                            importLauncher.launch(
                                arrayOf("application/json", "text/plain", "*/*")
                            )
                        },
                    ) {
                        Text(tr("Importa", "Import"))
                    }
                }
                importResult?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        // ---------------------------------------------------------- versione
        val versionName = remember {
            try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            } catch (_: Exception) {
                null
            } ?: "?"
        }
        Text(
            tr("Versione", "Version") + ": $versionName",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Riga di impostazione DENTRO una card di sezione (es. Notifiche). */
@Composable
private fun InnerSettingRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
