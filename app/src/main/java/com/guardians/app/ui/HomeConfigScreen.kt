package com.guardians.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.guardians.app.data.HomeConfigRepository
import com.guardians.app.data.tr

/**
 * "La homepage": scegli quali card vedere in home e in che ordine. Le card
 * essenziali (guardiani, squadre, congelamento, statistiche) si possono solo
 * spostare; Guide, Notificatore e Sonno si possono anche nascondere.
 * Nascondere il Sonno mette in standby l'Araldo (popup di conferma, 19.2).
 */
@Composable
fun HomeConfigScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    HomeConfigRepository.load(context)
    val order by HomeConfigRepository.order.collectAsState()
    val hidden by HomeConfigRepository.hidden.collectAsState()

    // Popup di conferma per nascondere il SONNO (l'Araldo va in standby).
    var confirmHideSleep by remember { mutableStateOf(false) }

    if (confirmHideSleep) {
        AlertDialog(
            onDismissRequest = { confirmHideSleep = false },
            title = { Text(tr("Nascondere il Sonno?", "Hide Sleep?")) },
            text = {
                Text(
                    tr(
                        "Nascondendo la sezione Sonno, anche l'Araldo andrà in " +
                            "standby e tutti i suoi blocchi orari notturni verranno " +
                            "temporaneamente disattivati. Vuoi procedere?\n\n" +
                            "(I dati sul sonno continuano comunque a essere raccolti: " +
                            "rimostrando la card, l'Araldo si riattiva da solo.)",
                        "Hiding the Sleep section will also put the Herald on " +
                            "standby, temporarily disabling all its nighttime blocks. " +
                            "Do you want to proceed?\n\n(Sleep data keeps being " +
                            "collected: show the card again and the Herald wakes up.)",
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        HomeConfigRepository.setHidden(
                            context, HomeConfigRepository.CARD_SLEEP, true,
                        )
                        confirmHideSleep = false
                    },
                ) { Text(tr("Nascondi e metti in standby", "Hide and stand by")) }
            },
            dismissButton = {
                TextButton(onClick = { confirmHideSleep = false }) {
                    Text(tr("Annulla", "Cancel"))
                }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
            }
            Text(
                tr("La homepage", "The home page"),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            tr(
                "Sposta le card con le frecce e scegli quali mostrare. Le card " +
                    "essenziali non si possono nascondere. Nascondere una card non " +
                    "cancella mai i suoi dati.",
                "Move the cards with the arrows and choose which to show. " +
                    "Essential cards can't be hidden. Hiding a card never deletes " +
                    "its data.",
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        order.forEachIndexed { index, key ->
            val isHidden = key in hidden
            val hideable = key in HomeConfigRepository.HIDEABLE
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    // Frecce su/giù per l'ordine.
                    Column {
                        IconButton(
                            onClick = { HomeConfigRepository.move(context, key, -1) },
                            enabled = index > 0,
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = tr("Su", "Up"))
                        }
                        IconButton(
                            onClick = { HomeConfigRepository.move(context, key, +1) },
                            enabled = index < order.size - 1,
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = tr("Giù", "Down"),
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            HomeConfigRepository.displayName(key),
                            fontWeight = FontWeight.Bold,
                            color = if (isHidden) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                        if (!hideable) {
                            Text(
                                tr("Essenziale", "Essential"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (hideable) {
                        Switch(
                            checked = !isHidden,
                            onCheckedChange = { show ->
                                if (!show && key == HomeConfigRepository.CARD_SLEEP) {
                                    // Il Sonno spegne anche l'Araldo: chiedi conferma.
                                    confirmHideSleep = true
                                } else {
                                    HomeConfigRepository.setHidden(context, key, !show)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
