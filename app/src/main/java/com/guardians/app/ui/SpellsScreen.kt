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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.guardians.app.data.SealRepository
import com.guardians.app.data.SpellsRepository
import com.guardians.app.data.StatsRepository
import com.guardians.app.data.TimerRepository
import com.guardians.app.data.tr
import com.guardians.app.model.GuardianTimer
import com.guardians.app.data.UsageStateStore
import com.guardians.app.model.TimeUnit
import com.guardians.app.model.TimerType
import com.guardians.app.model.formatMs
import com.guardians.app.ui.theme.RedGuardiano

/** Gli Incantesimi: Congelamento (blocco totale) e Ombra (sospende le squadre). */
@Composable
fun SpellsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val timers by TimerRepository.timers.collectAsState()
    val freezeUntil by SpellsRepository.freezeUntil.collectAsState()
    val shadowUntil by SpellsRepository.shadowUntil.collectAsState()
    val shadowTeams by SpellsRepository.shadowTeams.collectAsState()
    SealRepository.delayMs.collectAsState().value
    SealRepository.waitReadyAt.collectAsState().value
    UsageStateStore.state.collectAsState().value // tick per i countdown

    val now = System.currentTimeMillis()
    val sealed = !SealRepository.canEditNow()
    val teams = timers.map { it.teamName }.distinct().sorted()

    var freezeValue by remember { mutableStateOf("") }
    var freezeUnit by remember { mutableStateOf(TimeUnit.MINUTES) }
    var shadowValue by remember { mutableStateOf("") }
    var shadowUnit by remember { mutableStateOf(TimeUnit.MINUTES) }
    var selectedTeams by remember { mutableStateOf<Set<String>>(emptySet()) }
    var confirmBreakFreeze by remember { mutableStateOf(false) }

    if (confirmBreakFreeze) {
        AlertDialog(
            onDismissRequest = { confirmBreakFreeze = false },
            title = { Text(tr("Spezzare il Congelamento?", "Break the Freeze?")) },
            text = {
                Text(
                    tr(
                        "Il blocco totale finirebbe in anticipo. Sei sicuro?",
                        "The total block would end early. Are you sure?",
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        SpellsRepository.breakFreeze(context)
                        // L'interruzione è una "resa" nelle statistiche.
                        StatsRepository.recordSurrender(
                            context,
                            GuardianTimer(
                                id = "incantesimo-congelamento",
                                name = "Congelamento",
                                type = TimerType.CUSTODE,
                                limitAmount = 1,
                                limitUnit = TimeUnit.MINUTES,
                            ),
                        )
                        confirmBreakFreeze = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = RedGuardiano),
                ) { Text(tr("Sì, spezza", "Yes, break it")) }
            },
            dismissButton = {
                TextButton(onClick = { confirmBreakFreeze = false }) {
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
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
            }
            Text(
                tr("Incantesimi", "Spells"),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }

        // ------------------------------------------------------ Congelamento
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    tr("❄ Incantesimo di Congelamento", "❄ Freeze Spell"),
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    tr(
                        "Congela TUTTO il telefono per il tempo scelto, per concentrarti. " +
                            "Restano usabili solo telefonate, impostazioni e app escluse.",
                        "Freezes the WHOLE phone for the chosen time, so you can focus. " +
                            "Only calls, settings and excluded apps stay usable.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (now < freezeUntil) {
                    Text(
                        tr("Attivo: ancora ", "Active: ") +
                            formatMs((freezeUntil - now).coerceAtLeast(1000L)),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    OutlinedButton(onClick = { confirmBreakFreeze = true }) {
                        Text(tr("Spezza l'incantesimo", "Break the spell"), color = RedGuardiano)
                    }
                } else {
                    DurationField(
                        label = tr("Durata del congelamento", "Freeze duration"),
                        value = freezeValue,
                        unit = freezeUnit,
                        onValueChange = { freezeValue = it },
                        onUnitChange = { freezeUnit = it },
                    )
                    val v = freezeValue.toIntOrNull()
                    Button(
                        onClick = {
                            SpellsRepository.castFreeze(
                                context, now + v!! * freezeUnit.seconds * 1000L
                            )
                        },
                        enabled = v != null && v > 0,
                    ) { Text(tr("Lancia il Congelamento", "Cast the Freeze")) }
                }
            }
        }

        // ------------------------------------------------------------- Ombra
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    tr("🌑 Incantesimo d'Ombra", "🌑 Shadow Spell"),
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    tr(
                        "Sospende le squadre di guardiani per il tempo scelto: è l'unico " +
                            "modo per disattivare una Squadra. Allo scadere, la protezione " +
                            "torna da sola com'era. Nessuna squadra selezionata = tutte.",
                        "Suspends guardian teams for the chosen time: it is the only " +
                            "way to deactivate a Team. When it expires, protection " +
                            "returns by itself. No team selected = all of them.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                when {
                    now < shadowUntil -> {
                        Text(
                            tr("Attiva su ", "Active on ") +
                                (if (shadowTeams.isEmpty()) tr("TUTTE le squadre", "ALL teams")
                                else shadowTeams.joinToString(", ")) +
                                ": " + formatMs((shadowUntil - now).coerceAtLeast(1000L)),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        OutlinedButton(onClick = { SpellsRepository.breakShadow(context) }) {
                            Text(
                                tr(
                                    "Annulla l'Ombra (riattiva la protezione)",
                                    "Cancel the Shadow (restore protection)",
                                )
                            )
                        }
                    }

                    sealed -> Text(
                        tr(
                            "Il Sigillo è attivo: l'Ombra non può essere lanciata.",
                            "The Seal is active: the Shadow cannot be cast.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = RedGuardiano,
                    )

                    else -> {
                        teams.forEach { team ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Checkbox(
                                    checked = selectedTeams.contains(team),
                                    onCheckedChange = { checked ->
                                        selectedTeams =
                                            if (checked) selectedTeams + team
                                            else selectedTeams - team
                                    },
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(team)
                            }
                        }
                        DurationField(
                            label = tr("Durata dell'Ombra", "Shadow duration"),
                            value = shadowValue,
                            unit = shadowUnit,
                            onValueChange = { shadowValue = it },
                            onUnitChange = { shadowUnit = it },
                        )
                        val v = shadowValue.toIntOrNull()
                        Button(
                            onClick = {
                                SpellsRepository.castShadow(
                                    context,
                                    now + v!! * shadowUnit.seconds * 1000L,
                                    selectedTeams,
                                )
                            },
                            enabled = v != null && v > 0,
                        ) {
                            Text(
                                if (selectedTeams.isEmpty()) {
                                    tr("Lancia l'Ombra su tutto", "Cast the Shadow on everything")
                                } else {
                                    tr(
                                        "Lancia l'Ombra sulle squadre scelte",
                                        "Cast the Shadow on the chosen teams",
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
