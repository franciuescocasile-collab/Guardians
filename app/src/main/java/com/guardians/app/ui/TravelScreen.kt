package com.guardians.app.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.guardians.app.data.TravelRepository
import com.guardians.app.data.tr
import com.guardians.app.model.formatMs
import java.time.LocalDate
import java.time.ZoneId

/**
 * Modalità Viaggio: sospende tutti i guardiani per un timer o fino a una data.
 * Allo scadere la protezione torna da sola. Il tempo e i dati contano
 * NORMALMENTE: viaggio = solo pausa dei blocchi, niente isolamento statistiche.
 */
@Composable
fun TravelScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    TravelRepository.load(context)
    val until by TravelRepository.activeUntil.collectAsState()

    var nowTick by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowTick = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000L)
        }
    }
    val active = nowTick < until

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
            Icon(Icons.Default.Flight, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(
                tr("Modalità Viaggio", "Travel Mode"),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }

        Text(
            tr(
                "Mette in pausa tutti i guardiani per il tempo scelto, così in " +
                    "trasferta non devi disattivarli e riattivarli a mano. Il " +
                    "tempo e i dati contano normalmente: cambia solo che i blocchi " +
                    "non scattano.",
                "Pauses all guardians for the chosen time, so on the road you " +
                    "don't have to turn them off and on by hand. Time and data " +
                    "count normally: the only change is that blocks don't fire.",
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (active) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(
                    Modifier.padding(20.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        tr("Viaggio in corso", "Travel in progress"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        tr("Guardiani in pausa ancora per ", "Guardians paused for ") +
                            formatMs((until - nowTick).coerceAtLeast(1000L)),
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Button(
                onClick = { TravelRepository.stop(context) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(tr("Termina il viaggio", "End travel"), fontWeight = FontWeight.Bold)
            }
        } else {
            // Durata personalizzabile: quante ore o quanti giorni (a scelta libera).
            var value by remember { mutableStateOf("") }
            var unitDays by remember { mutableStateOf(false) }
            Text(tr("Per quanto tempo?", "For how long?"), fontWeight = FontWeight.Bold)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.OutlinedTextField(
                    value = value,
                    onValueChange = { v -> value = v.filter { it.isDigit() } },
                    label = { Text(tr("Quantità", "Amount")) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                    ),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                DropdownPickerButton(
                    text = if (unitDays) tr("giorni", "days") else tr("ore", "hours"),
                    options = listOf(tr("ore", "hours"), tr("giorni", "days")),
                    onSelected = { i -> unitDays = i == 1 },
                    modifier = Modifier.weight(1f),
                )
            }
            Button(
                enabled = (value.toIntOrNull() ?: 0) > 0,
                onClick = {
                    val n = value.toInt()
                    val ms = if (unitDays) n * 24L * 3_600_000L else n * 3_600_000L
                    TravelRepository.setUntil(context, System.currentTimeMillis() + ms)
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(tr("Avvia il viaggio", "Start travel"), fontWeight = FontWeight.Bold)
            }

            // Oppure fino a una data: il giorno scelto è INCLUSO (i guardiani
            // restano spenti per tutta quella giornata, fino a mezzanotte).
            OutlinedButton(
                onClick = {
                    val cal = java.util.Calendar.getInstance()
                    android.app.DatePickerDialog(
                        context,
                        { _, y, m, d ->
                            // +1 giorno a mezzanotte = fine del giorno scelto incluso.
                            val end = LocalDate.of(y, m + 1, d).plusDays(1).atStartOfDay()
                            TravelRepository.setUntil(
                                context,
                                end.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                            )
                        },
                        cal.get(java.util.Calendar.YEAR),
                        cal.get(java.util.Calendar.MONTH),
                        cal.get(java.util.Calendar.DAY_OF_MONTH),
                    ).show()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(tr("…oppure fino a una data (inclusa)", "…or until a date (inclusive)"))
            }
        }
    }
}
