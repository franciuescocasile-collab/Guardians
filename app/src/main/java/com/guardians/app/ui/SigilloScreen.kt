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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.guardians.app.data.SealRepository
import com.guardians.app.data.UsageStateStore
import com.guardians.app.data.tr
import com.guardians.app.model.TimeUnit
import com.guardians.app.model.formatMs
import com.guardians.app.ui.theme.AmberSentinella

/**
 * Sigillo = ritardo impostato alle modifiche (0 o vuoto = nessun sigillo).
 * Per modificare i guardiani si richiede lo sblocco e si aspetta il ritardo.
 */
@Composable
fun SigilloScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val delayMs by SealRepository.delayMs.collectAsState()
    val unlockReadyAt by SealRepository.waitReadyAt.collectAsState()

    // Ticker locale: aggiorna il conto alla rovescia ogni secondo, anche se il
    // servizio dei guardiani non è in esecuzione.
    var nowTick by remember { mutableStateOf(System.currentTimeMillis()) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            nowTick = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000L)
        }
    }
    val now = nowTick
    var durationValue by remember { mutableStateOf("") }
    var durationUnit by remember { mutableStateOf(TimeUnit.MINUTES) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
            }
            Icon(Icons.Default.Lock, contentDescription = null, tint = AmberSentinella)
            Spacer(Modifier.width(12.dp))
            Text(
                tr("Sigillo", "Seal"),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }

        Text(
            tr(
                "Il Sigillo è un tempo di attesa contro le modifiche d'impulso. Quando " +
                    "è impostato, a OGNI apertura dell'app parte un conto alla rovescia: " +
                    "finché non scade non puoi modificare, spegnere o eliminare i " +
                    "guardiani (né cambiare le app escluse o rimuovere il Sigillo). " +
                    "Il countdown scorre solo mentre sei DENTRO Guardians: se esci, " +
                    "al rientro riparte da capo. Vuoto o 0 = nessun sigillo. " +
                    "Per sicurezza il massimo è 5 minuti.",
                "The Seal is a waiting time against impulsive changes. When set, a " +
                    "countdown starts at EVERY app open: until it ends you can't modify, " +
                    "disable or delete guardians (nor change excluded apps or remove the " +
                    "Seal). The countdown only runs while you are INSIDE Guardians: if " +
                    "you leave, it restarts on your return. Empty or 0 = no seal. " +
                    "For safety the maximum is 5 minutes.",
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        when {
            delayMs > 0L -> {
                Text(
                    tr("Sigillo attivo: attesa di ", "Seal active: wait of ") +
                        formatMs(delayMs),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = AmberSentinella,
                )
                if (SealRepository.canEditNow()) {
                    OutlinedButton(
                        onClick = { SealRepository.setDelay(context, 0L) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(tr("Rimuovi il Sigillo", "Remove the Seal"))
                    }
                } else {
                    Text(
                        tr("Potrai rimuoverlo tra ", "You can remove it in ") +
                            formatMs((unlockReadyAt - now).coerceAtLeast(1000L)),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Nessun sigillo impostato: si può impostare.
            else -> {
                DurationField(
                    label = tr("Ritardo del Sigillo", "Seal delay"),
                    value = durationValue,
                    unit = durationUnit,
                    onValueChange = { durationValue = it },
                    onUnitChange = { durationUnit = it },
                )
                val duration = durationValue.toIntOrNull()
                val durationMs = (duration ?: 0) * durationUnit.seconds * 1000L
                // Tetto INVALICABILE di 5 minuti: sopra, non si può proprio salvare
                // (e SealRepository taglia comunque a 5 minuti per sicurezza).
                val tooLong = durationMs > SealRepository.MAX_DELAY_MS
                if (tooLong) {
                    Text(
                        tr(
                            "Troppo lungo: il Sigillo può durare al massimo 5 minuti.",
                            "Too long: the Seal can last at most 5 minutes.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Button(
                    onClick = { SealRepository.setDelay(context, durationMs) },
                    enabled = duration != null && duration > 0 && !tooLong,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Text(tr("Imposta il Sigillo", "Set the Seal"), fontWeight = FontWeight.Bold)
                }
            }

        }
    }
}
