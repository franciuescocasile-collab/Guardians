package com.guardians.app.ui

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.guardians.app.data.TimerRepository
import com.guardians.app.data.tr
import com.guardians.app.model.GuardianTimer
import com.guardians.app.model.TimeUnit
import com.guardians.app.model.TimerType
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Una proposta: descrizione + il guardiano pronto da aggiungere. */
private class Suggestion(val text: String, val timer: GuardianTimer)

@Composable
fun SuggestionsScreen(onBack: () -> Unit, onOpenTeams: () -> Unit) {
    val context = LocalContext.current
    var added by remember { mutableStateOf(setOf<String>()) }

    val suggestions by produceState<List<Suggestion>?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { buildSuggestions(context) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
            }
            Text(
                tr("Suggerimenti", "Suggestions"),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            tr(
                "Proposte basate su come hai usato il telefono di recente.",
                "Ideas based on how you used your phone recently.",
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        val list = suggestions
        when {
            list == null -> Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) {
                CircularProgressIndicator()
            }

            list.isEmpty() -> Text(
                tr(
                    "Nessun suggerimento per ora: usa il telefono ancora un po' " +
                        "(o serve il permesso \"Accesso ai dati di utilizzo\").",
                    "No suggestions yet: use the phone a bit more " +
                        "(or the \"Usage access\" permission is needed).",
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            else -> list.forEach { s ->
                val isAdded = added.contains(s.timer.id)
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    ) {
                        TimerShapeIcon(s.timer.type, Modifier.size(32.dp))
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.timer.name, fontWeight = FontWeight.Bold)
                            Text(
                                s.text,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                TimerRepository.upsert(context, s.timer)
                                added = added + s.timer.id
                            },
                            enabled = !isAdded,
                        ) {
                            Text(if (isAdded) tr("Aggiunto", "Added") else tr("Aggiungi", "Add"))
                        }
                    }
                }
            }
        }

        if (added.isNotEmpty()) {
            Button(onClick = onOpenTeams, modifier = Modifier.fillMaxWidth()) {
                Text(tr("Vai ai guardiani", "Go to guardians"))
            }
        }
    }
}

private fun buildSuggestions(context: Context): List<Suggestion> {
    if (!hasUsageAccess(context)) return emptyList()
    val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val zone = ZoneId.systemDefault()
    val start = LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()
    val now = System.currentTimeMillis()

    // App già sorvegliate da qualche guardiano: non le riproponiamo.
    val guarded = TimerRepository.timers.value.flatMap { it.packages }.toSet()
    val ignored = setOf(context.packageName, "com.android.systemui", "com.android.settings")

    // Tempo di oggi per app.
    val timeByApp = try {
        usm.queryAndAggregateUsageStats(start, now)
            .mapValues { it.value.totalTimeInForeground }
            .filterValues { it > 0L }
    } catch (_: Exception) {
        emptyMap()
    }

    // Aperture di oggi per app (eventi MOVE_TO_FOREGROUND).
    val opensByApp = mutableMapOf<String, Int>()
    try {
        val events = usm.queryEvents(start, now)
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            @Suppress("DEPRECATION")
            if (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                opensByApp[e.packageName] = (opensByApp[e.packageName] ?: 0) + 1
            }
        }
    } catch (_: Exception) {
    }

    fun label(pkg: String): String = try {
        context.packageManager.getApplicationLabel(
            context.packageManager.getApplicationInfo(pkg, 0)
        ).toString()
    } catch (_: Exception) {
        pkg.substringAfterLast('.')
    }

    val result = mutableListOf<Suggestion>()

    // 1) App aperta tante volte oggi → proponi un Gendarme.
    opensByApp.entries
        .filter { it.key !in guarded && it.key !in ignored && it.value >= 20 }
        .maxByOrNull { it.value }
        ?.let { (pkg, opens) ->
            val name = label(pkg)
            result += Suggestion(
                text = tr(
                    "Oggi hai aperto $name $opens volte. Un Gendarme può limitarne le aperture.",
                    "You opened $name $opens times today. A Gendarme can limit its opens.",
                ),
                timer = GuardianTimer(
                    id = UUID.randomUUID().toString(),
                    name = tr("Gendarme $name", "$name Gendarme"),
                    type = TimerType.GENDARME,
                    limitAmount = 0, limitUnit = TimeUnit.MINUTES,
                    maxOpensPerDay = (opens / 2).coerceAtLeast(5),
                    packages = listOf(pkg),
                ),
            )
        }

    // 2) App usata a lungo oggi → proponi un Guardiano (limite giornaliero).
    timeByApp.entries
        .filter { it.key !in guarded && it.key !in ignored && it.value >= 45 * 60_000L }
        .maxByOrNull { it.value }
        ?.let { (pkg, ms) ->
            val name = label(pkg)
            val minutes = (ms / 60_000L).toInt()
            result += Suggestion(
                text = tr(
                    "Oggi hai passato ~$minutes min su $name. Un Guardiano può mettere un tetto giornaliero.",
                    "You spent ~$minutes min on $name today. A Guardian can cap the daily time.",
                ),
                timer = GuardianTimer(
                    id = UUID.randomUUID().toString(),
                    name = tr("Guardiano $name", "$name Guardian"),
                    type = TimerType.GUARDIANO,
                    limitAmount = (minutes / 2).coerceAtLeast(15), limitUnit = TimeUnit.MINUTES,
                    packages = listOf(pkg),
                ),
            )
        }

    return result
}
