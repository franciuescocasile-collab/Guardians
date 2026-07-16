package com.guardians.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
    onOpenPersonalization: () -> Unit,
    onOpenAdvanced: () -> Unit,
) {
    val context = LocalContext.current

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

        // ------------------------------------------ personalizzazione dell'app
        Card(
            onClick = onOpenPersonalization,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Icon(
                    Icons.Default.Palette,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(tr("Personalizzazione dell'app", "App personalization"), fontWeight = FontWeight.Bold)
                    Text(
                        tr(
                            "Settimana, inizio del giorno, conferme, tema e lingua",
                            "Week, start of the day, confirmations, theme and language",
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

        // ------------------------------------------------ impostazioni avanzate
        Card(
            onClick = onOpenAdvanced,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Icon(
                    Icons.Default.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(tr("Impostazioni avanzate", "Advanced settings"), fontWeight = FontWeight.Bold)
                    Text(
                        tr("Per gli utenti esperti (in arrivo)", "For power users (coming soon)"),
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

