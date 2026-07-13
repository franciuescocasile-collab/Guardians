package com.guardians.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.guardians.app.data.SettingsRepository
import com.guardians.app.data.tr

/** Tutte le impostazioni delle notifiche, raccolte in una pagina dedicata. */
@Composable
fun NotificationsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val vibrate by SettingsRepository.vibrateOnAlert.collectAsState()
    val sound by SettingsRepository.soundOnAlert.collectAsState()
    val weeklyReport by SettingsRepository.weeklyReport.collectAsState()
    val monitor by SettingsRepository.monitorNotification.collectAsState()
    val notifierCard by SettingsRepository.showNotifierCard.collectAsState()

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
                tr("Notifiche", "Notifications"),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }

        NotifRow(
            title = tr("Notifica di monitoraggio", "Monitoring notification"),
            description = tr(
                "La notifica fissa \"Guardians attivo\". Spegnendola resta " +
                    "discreta e silenziosa (Android ne richiede comunque una " +
                    "minima per il servizio in background).",
                "The persistent \"Guardians active\" notification. Turning it off " +
                    "keeps it discreet and silent (Android still requires a minimal " +
                    "one for the background service).",
            ),
            checked = monitor,
            onCheckedChange = { SettingsRepository.setMonitorNotification(context, it) },
        )
        NotifRow(
            title = tr("Vibrazione per gli avvisi", "Vibrate on alerts"),
            description = tr(
                "Il telefono vibra quando un guardiano ti blocca un'app",
                "The phone vibrates when a guardian blocks an app",
            ),
            checked = vibrate,
            onCheckedChange = { SettingsRepository.setVibrateOnAlert(context, it) },
        )
        NotifRow(
            title = tr("Suono per gli avvisi", "Sound on alerts"),
            description = tr(
                "Un suono di notifica accompagna il popup di blocco",
                "A notification sound plays with the blocking popup",
            ),
            checked = sound,
            onCheckedChange = { SettingsRepository.setSoundOnAlert(context, it) },
        )
        NotifRow(
            title = tr("Resoconto settimanale", "Weekly report"),
            description = tr(
                "Il primo giorno della settimana, al mattino quando ti svegli, " +
                    "una notifica confronta la settimana con la tua media",
                "On the first day of the week, in the morning when you wake up, " +
                    "a notification compares the week with your average",
            ),
            checked = weeklyReport,
            onCheckedChange = { SettingsRepository.setWeeklyReport(context, it) },
        )
        NotifRow(
            title = tr("Mostra il Notificatore in home", "Show the Notifier on home"),
            description = tr(
                "Aggiunge la card del Notificatore nella schermata iniziale. " +
                    "Spegnendola la card sparisce ma i promemoria restano salvati.",
                "Adds the Notifier card to the home screen. Turning it off hides " +
                    "the card but keeps your reminders saved.",
            ),
            checked = notifierCard,
            onCheckedChange = { SettingsRepository.setShowNotifierCard(context, it) },
        )
    }
}

@Composable
private fun NotifRow(
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
            Spacer(Modifier.padding(6.dp))
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
