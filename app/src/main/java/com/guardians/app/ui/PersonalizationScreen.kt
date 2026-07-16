package com.guardians.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
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
import com.guardians.app.data.SettingsRepository
import com.guardians.app.data.tr

/**
 * Personalizzazione dell'app: le preferenze "di gusto" raccolte in una pagina,
 * nell'ordine voluto dall'utente: primo giorno della settimana, inizio del
 * giorno, conferma per le modifiche, tema scuro, lingua.
 */
@Composable
fun PersonalizationScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val darkTheme by SettingsRepository.darkTheme.collectAsState()
    val english by SettingsRepository.english.collectAsState()
    val confirmActions by SettingsRepository.confirmActions.collectAsState()
    val weekStartMonday by SettingsRepository.weekStartMonday.collectAsState()
    val dayStart by SettingsRepository.dayStartMinute.collectAsState()
    com.guardians.app.data.SealRepository.waitReadyAt.collectAsState().value
    val daySealed = !com.guardians.app.data.SealRepository.canEditNow() &&
        com.guardians.app.data.SealRepository.delayMs.collectAsState().value > 0L

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
                tr("Personalizzazione dell'app", "App personalization"),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }

        // ------------------------------------- primo giorno della settimana
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

        // ----------------------------------------------- inizio del giorno
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

        // -------------------------------------------------- conferma globale
        PersonalRow(
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

        // -------------------------------------------------------- tema scuro
        PersonalRow(
            title = tr("Tema scuro", "Dark theme"),
            description = tr(
                "Spegnilo per il tema chiaro (bianco e blu)",
                "Turn off for the light theme (white and blue)",
            ),
            checked = darkTheme,
            onCheckedChange = { SettingsRepository.setDarkTheme(context, it) },
        )

        // ------------------------------------------------------------ lingua
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
    }
}

@Composable
private fun PersonalRow(
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
