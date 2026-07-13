package com.guardians.app.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.guardians.app.data.SettingsRepository
import com.guardians.app.data.tr
import kotlin.math.roundToInt

/** Tutte le impostazioni della batteria, raccolte in una pagina dedicata. */
@Composable
fun BatteryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val batterySaver by SettingsRepository.batterySaver.collectAsState()
    val batteryThreshold by SettingsRepository.batteryThreshold.collectAsState()

    // "Tienimi sempre attivo" riflette l'esenzione reale dall'ottimizzazione:
    // la rileggiamo ogni volta che si torna qui dalle impostazioni di sistema.
    var alwaysOn by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                alwaysOn = isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
                tr("Batteria", "Battery"),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }

        // ------------------------------------ tienimi sempre attivo (interruttore)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("Tienimi sempre attivo", "Keep me always running"), fontWeight = FontWeight.Bold)
                        Text(
                            tr(
                                "Alcuni telefoni (soprattutto Samsung) mettono Guardians " +
                                    "\"a dormire\" quando apri un gioco o un'app pesante. " +
                                    "Attivalo per esentarlo dall'ottimizzazione batteria e " +
                                    "restare sempre di guardia.",
                                "Some phones (especially Samsung) put Guardians \"to sleep\" " +
                                    "when you open a game or a heavy app. Turn this on to " +
                                    "exempt it from battery optimization and stay on duty.",
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = alwaysOn,
                        onCheckedChange = { want ->
                            // Attivando: se non è esente, apri la richiesta di sistema.
                            // Disattivando: apri le impostazioni per toglierlo a mano
                            // (Android non lo revoca da solo).
                            if (want && !alwaysOn) openBatteryExemption(context)
                            else if (!want && alwaysOn) openBatteryOptimizationSettings(context)
                        },
                    )
                }
            }
        }

        // ------------------------------------------------ risparmio batteria
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("Risparmio batteria", "Battery saver"), fontWeight = FontWeight.Bold)
                        Text(
                            tr(
                                "Sotto la soglia i motori si spengono del tutto: blocchi e " +
                                    "pedaggi in corso vengono rimossi e Guardians non consuma " +
                                    "più nulla. Riprende da solo in carica o sopra la soglia.",
                                "Below the threshold the engines shut down completely: " +
                                    "ongoing blocks and tolls are removed and Guardians " +
                                    "consumes nothing. It resumes on its own when charging " +
                                    "or back above the threshold.",
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
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

        Spacer(Modifier.height(4.dp))
    }
}

/** True se Guardians è già esente dall'ottimizzazione della batteria. */
private fun isIgnoringBatteryOptimizations(context: Context): Boolean = try {
    val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
    pm.isIgnoringBatteryOptimizations(context.packageName)
} catch (_: Throwable) {
    false
}

/** Chiede direttamente l'esenzione (con ripieghi verso la lista e la scheda app). */
private fun openBatteryExemption(context: Context) {
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
    startFirst(context, intents)
}

/** Apre la lista dell'ottimizzazione batteria (per togliere l'esenzione a mano). */
private fun openBatteryOptimizationSettings(context: Context) {
    val intents = listOf(
        android.content.Intent(
            android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
        ),
        android.content.Intent(
            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        ).setData(android.net.Uri.parse("package:" + context.packageName)),
    )
    startFirst(context, intents)
}

private fun startFirst(context: Context, intents: List<android.content.Intent>) {
    for (intent in intents) {
        try {
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return
        } catch (_: Throwable) {
        }
    }
}
