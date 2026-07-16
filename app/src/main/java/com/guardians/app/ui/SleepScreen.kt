package com.guardians.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.guardians.app.data.AraldoData
import com.guardians.app.data.ProfileRepository
import com.guardians.app.data.tr
import com.guardians.app.model.TimerType
import com.guardians.app.model.formatTimeOfDay

/**
 * La pagina SONNO: raccoglie tutto ciò che riguarda il riposo — i dati di
 * Health Connect (ultima dormita e legame Distacco→Sonno, spostati qui dalle
 * Statistiche) e l'Araldo con i suoi orari (spostato qui dal Profilo).
 */
@Composable
fun SleepScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    AraldoData.load(context)
    ProfileRepository.load(context)
    val median by AraldoData.medianBedtimeMinute.collectAsState()
    val bedMinute by ProfileRepository.usualBedMinute.collectAsState()
    val wakeMinute by ProfileRepository.usualWakeMinute.collectAsState()

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
                tr("Sonno", "Sleep"),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }

        // Health Connect: ultima dormita + il legame Distacco → Sonno.
        SleepConnectCard()

        // ------------------------------------------ L'ARALDO E IL SONNO
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TimerShapeIcon(TimerType.ARALDO, Modifier.size(24.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        tr("L'Araldo e il tuo sonno", "The Herald and your sleep"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    if (median != null) {
                        tr(
                            "Nanna imparata: ~${formatTimeOfDay(median!!)}",
                            "Learned bedtime: ~${formatTimeOfDay(median!!)}",
                        )
                    } else {
                        tr(
                            "L'Araldo usa i tuoi orari indicativi e intanto impara i veri.",
                            "The Herald uses your indicative times while learning the real ones.",
                        )
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = if (median != null) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (median != null) FontWeight.Bold else FontWeight.Normal,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TimeOfDayPicker(
                        label = tr("A dormire verso le", "To sleep around"),
                        minuteOfDay = bedMinute.takeIf { it >= 0 },
                        onChange = { ProfileRepository.setUsualBedMinute(context, it) },
                        modifier = Modifier.weight(1f),
                    )
                    TimeOfDayPicker(
                        label = tr("Sveglia verso le", "Wake around"),
                        minuteOfDay = wakeMinute.takeIf { it >= 0 },
                        onChange = { ProfileRepository.setUsualWakeMinute(context, it) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
