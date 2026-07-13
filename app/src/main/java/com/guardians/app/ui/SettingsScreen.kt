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
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
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
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenExclusions: () -> Unit,
    onOpenSigillo: () -> Unit,
    onOpenTravel: () -> Unit,
    onOpenNotifications: () -> Unit,
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

        // ------------------------------------------------ risparmio batteria
        val batterySaver by SettingsRepository.batterySaver.collectAsState()
        val batteryThreshold by SettingsRepository.batteryThreshold.collectAsState()
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("Risparmio batteria", "Battery saver"), fontWeight = FontWeight.Bold)
                        Text(
                            tr(
                                "Sotto la soglia i motori si spengono del tutto: blocchi " +
                                    "e pedaggi in corso vengono rimossi e Guardians non " +
                                    "consuma più nulla. Riprende da solo quando il telefono " +
                                    "è in carica o risale sopra la soglia.",
                                "Below the threshold the engines shut down completely: " +
                                    "ongoing blocks and tolls are removed and Guardians " +
                                    "consumes nothing. It resumes on its own when the phone " +
                                    "is charging or back above the threshold.",
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = batterySaver,
                        onCheckedChange = { SettingsRepository.setBatterySaver(context, it) },
                    )
                }
                if (batterySaver) {
                    Text(
                        tr(
                            "Si attiva sotto il $batteryThreshold% di batteria",
                            "Kicks in below $batteryThreshold% battery",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Slider(
                        value = batteryThreshold.toFloat(),
                        onValueChange = {
                            SettingsRepository.setBatteryThreshold(context, it.roundToInt())
                        },
                        valueRange = 10f..20f,
                        steps = 9,
                    )
                }
            }
        }

        // ------------------------------- tienimi sempre attivo (anti-kill Samsung)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp)) {
                Text(tr("Tienimi sempre attivo", "Keep me always running"), fontWeight = FontWeight.Bold)
                Text(
                    tr(
                        "Alcuni telefoni (soprattutto Samsung) mettono Guardians \"a " +
                            "dormire\" quando apri un gioco o un'app pesante, e i guardiani " +
                            "smettono di controllare. Tocca qui e concedi a Guardians di " +
                            "ignorare l'ottimizzazione della batteria, così resta di guardia.",
                        "Some phones (especially Samsung) put Guardians \"to sleep\" when " +
                            "you open a game or a heavy app, and the guardians stop " +
                            "watching. Tap here and allow Guardians to ignore battery " +
                            "optimization, so it stays on duty.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = { openBatteryExemption(context) }) {
                    Text(tr("Apri le impostazioni batteria", "Open battery settings"))
                }
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

/**
 * Porta l'utente dove concedere a Guardians di ignorare l'ottimizzazione della
 * batteria (la richiesta diretta; con ripieghi verso la lista e la scheda app),
 * così i telefoni aggressivi non lo mettono a dormire durante i giochi.
 */
private fun openBatteryExemption(context: android.content.Context) {
    val pkg = android.net.Uri.parse("package:" + context.packageName)
    val intents = listOf(
        android.content.Intent(
            android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
        ).setData(pkg),
        android.content.Intent(
            android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
        ),
        android.content.Intent(
            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        ).setData(pkg),
    )
    for (intent in intents) {
        try {
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return
        } catch (_: Throwable) {
        }
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
